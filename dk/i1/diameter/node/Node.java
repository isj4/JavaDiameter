package dk.i1.diameter.node;
import dk.i1.diameter.*;
import java.util.*;
import java.nio.channels.*;
import java.nio.ByteBuffer;
import java.net.*;
import java.util.logging.Logger;
import java.util.logging.Level;

import java.io.*;

/**
 * A Diameter node.
 * The Node class manages diameter transport connections and peers. It handles
 * the low-level messages itself (CER/CEA/DPR/DPA/DWR/DWA). The rest is sent to
 * the MessageDispatcher. When connections are established or closed the
 * ConnectionListener is notified. Message can be sent and received through the
 * node but no state is maintained per message.
 * <p>Node is quite low-level. You probably want to use NodeManager instead.
 * <p>Node instances logs with the name "dk.i1.diameter.node", so you can
 * get detailed logging (including hex-dumps of incoming and outgoing packets)
 * by putting "dk.i1.diameter.node.level = ALL" into your log.properties
 * file (or equivalent)
 * @see NodeManager
 */
public class Node {
	private MessageDispatcher message_dispatcher;
	private ConnectionListener connection_listener;
	private NodeSettings settings;
	private NodeValidator node_validator;
	private NodeState node_state;
	private Thread node_thread;
	private Thread reconnect_thread;
	private boolean please_stop;
	private long shutdown_deadline;
	private Selector selector;
	private ServerSocketChannel serverChannel;
	private Map<ConnectionKey,Connection> map_key_conn;
	private Set<Peer> persistent_peers;
	private Logger logger;
	private Object obj_conn_wait;
	
	/**
	 * Constructor for Node.
	 * Constructs a Node instance with the specified parameters.
	 * The node is not automatically started.
	 * Implemented as <tt>this(message_dispatcher,connection_listener,settings,null);</tt>
	 * @param message_dispatcher A message dispatcher. If null, a default dispatcher is used you. You probably dont want that one.
	 * @param connection_listener A connection observer. Can be null.
	 * @param settings The node settings.
	 */
	public Node(MessageDispatcher message_dispatcher,
		    ConnectionListener connection_listener,
	            NodeSettings settings
	           )
	{
		this(message_dispatcher,connection_listener,settings,null);
	}
	
	/**
	 * Constructor for Node.
	 * Constructs a Node instance with the specified parameters.
	 * The node is not automatically started.
	 * @param message_dispatcher A message dispatcher. If null, a default dispatcher is used you. You probably dont want that one.
	 * @param connection_listener A connection observer. Can be null.
	 * @param settings The node settings.
	 * @param node_validator a custom NodeValidator. If null then a {@link DefaultNodeValidator} is used.
	 * @since 0.9.4
	 */
	public Node(MessageDispatcher message_dispatcher,
		    ConnectionListener connection_listener,
	            NodeSettings settings,
		    NodeValidator node_validator
	           )
	{
		this.message_dispatcher = (message_dispatcher==null) ? new DefaultMessageDispatcher() : message_dispatcher;
		this.connection_listener = (connection_listener==null) ? new DefaultConnectionListener() : connection_listener;
		this.settings = settings;
		this.node_validator = (node_validator==null) ? new DefaultNodeValidator() : node_validator;
		this.node_state = new NodeState();
		this.logger = Logger.getLogger("dk.i1.diameter.node");
		this.obj_conn_wait = new Object();
	}
	
	/**
	 * Start the node.
	 * The node is started. If the port to listen on is already used by
	 * another application or some other initial network error occurs a java.io.IOException is thrown.
	 */
	public void start() throws java.io.IOException {
		logger.log(Level.INFO,"Starting Diameter node");
		please_stop = false;
		prepare();
		node_thread = new SelectThread();
		node_thread.setDaemon(true);
		node_thread.start();
		reconnect_thread = new ReconnectThread();
		reconnect_thread.setDaemon(true);
		reconnect_thread.start();
		logger.log(Level.INFO,"Diameter node started");
	}
	
	/**
	 * Stop the node.
	 * Implemented as stop(0)
	 */
	public void stop() {
		stop(0);
	}
	
	/**
	 * Stop the node.
	 * All connections are closed. A DPR is sent to the each connected peer
	 * unless the transport connection's buffers are full.
	 * Threads waiting in {@link #waitForConnection} are woken.
	 * Graceful connection close is not guaranteed in all cases.
	 * @param grace_time Maximum time (milliseconds) to wait for connections to close gracefully.
	 * @since grace_time parameter introduced in 0.9.3
	 */
	public void stop(long grace_time)  {
		logger.log(Level.INFO,"Stopping Diameter node");
		synchronized(map_key_conn) {
			shutdown_deadline = System.currentTimeMillis() + grace_time;
			please_stop = true;
			//Close all the non-ready connections, initiate close on ready ones.
			for(Iterator<Map.Entry<ConnectionKey,Connection>> it = map_key_conn.entrySet().iterator();
			    it.hasNext()
			   ;)
			{
				Map.Entry<ConnectionKey,Connection> e = it.next();
				Connection conn = e.getValue();
				switch(conn.state) {
					case connecting:
					case connected_in:
					case connected_out:
						logger.log(Level.FINE,"Closing connection to "+conn.host_id+" because we are shutting down");
						it.remove();
						try { conn.channel.close(); } catch(java.io.IOException ex) {}
						break;
					case tls:
						break; //don't know what to do here yet.
					case ready:
						initiateConnectionClose(conn,ProtocolConstants.DI_DISCONNECT_CAUSE_REBOOTING);
						break;
					case closing:
						break; //nothing to do
					case closed:
						break; //nothing to do
				}
			}
		}
		selector.wakeup();
		synchronized(map_key_conn) {
			map_key_conn.notify();
		}
		try {
			node_thread.join();
			reconnect_thread.join();
		} catch(java.lang.InterruptedException ex) {}
		node_thread = null;
		reconnect_thread = null;
		//other cleanup
		synchronized(obj_conn_wait) {
			obj_conn_wait.notifyAll();
		}
		map_key_conn = null;
		persistent_peers = null;
		if(serverChannel!=null) {
			try {
				serverChannel.close();
			} catch(java.io.IOException ex) {}
		}
		serverChannel=null;
		try {
			selector.close();
		} catch(java.io.IOException ex) {}
		selector = null;
		logger.log(Level.INFO,"Diameter node stopped");
	}
	
	private boolean anyReadyConnection() {
		synchronized(map_key_conn) {
			for(Map.Entry<ConnectionKey,Connection> e : map_key_conn.entrySet()) {
				Connection conn = e.getValue();
				if(conn.state==Connection.State.ready)
					return true;
			}
		}
		return false;
	}
	
	/**
	 * Wait until at least one connection has been established to a peer
	 * and capability-exchange has finished.
	 * @since 0.9.1
	 */
	public void waitForConnection() throws InterruptedException {
		synchronized(obj_conn_wait) {
			while(!anyReadyConnection())
				obj_conn_wait.wait();
		}
	}
	/**
	 * Wait until at least one connection has been established or until the timeout expires.
	 * Waits until at least one connection to a peer has been established
	 * and capability-exchange has finished, or the specified timeout has expired.
	 * @param timeout The maximum time to wait in milliseconds.
	 * @since 0.9.1
	 */
	public void waitForConnection(long timeout) throws InterruptedException {
		long wait_end = System.currentTimeMillis()+timeout;
		synchronized(obj_conn_wait) {
			long now = System.currentTimeMillis();
			while(!anyReadyConnection() && now<wait_end) {
				long t = wait_end - now;
				obj_conn_wait.wait(t);
				now = System.currentTimeMillis();
			}
		}
	}
	
	/**
	 * Returns the connection key for a peer.
	 * @return The connection key. Null if there is no connection to the peer.
	 */
	public ConnectionKey findConnection(Peer peer)  {
		logger.log(Level.FINER,"Finding '" + peer.host() +"'");
		synchronized(map_key_conn) {
			//System.out.println("Node.findConnection: size=" + map_key_conn.size());
			for(Map.Entry<ConnectionKey,Connection> e : map_key_conn.entrySet()) {
				Connection conn = e.getValue();
				//System.out.println("Node.findConnection(): examing " + ((conn.peer!=null)?conn.peer.host():"?"));
				if(conn.peer!=null
				&& conn.peer.equals(peer)) {
					//System.out.println("Node.findConnection(): found");
					return conn.key;
				}
			}
			logger.log(Level.FINER,peer.host()+" NOT found");
			return null;
		}
	}
	/**
	 * Returns if the connection is still valid.
	 * This method is usually only of interest to programs that do lengthy
	 * processing of requests nad are located in a poor network. It is
	 * usually much easier to just call sendMessage() and catch the
	 * exception if the connection has gone stale.
	 */
	public boolean isConnectionKeyValid(ConnectionKey connkey) {
		synchronized(map_key_conn) {
			return map_key_conn.get(connkey)!=null;
		}
	}
	/**
	 * Returns the Peer on a connection.
	 */
	public Peer connectionKey2Peer(ConnectionKey connkey) {
		synchronized(map_key_conn) {
			Connection conn = map_key_conn.get(connkey);
			if(conn!=null)
				return conn.peer;
			else
				return null;
		}
	}
	/**
	 * Returns the IP-address of the remote end of a connection.
	 */
	public InetAddress connectionKey2InetAddress(ConnectionKey connkey) {
		synchronized(map_key_conn) {
			Connection conn = map_key_conn.get(connkey);
			if(conn!=null)
				return ((InetSocketAddress)((conn.channel).socket().getRemoteSocketAddress())).getAddress();
			else
				return null;
		}
	}
	/**
	 * Returns the next hop-by-hop identifier for a connection
	 */
	public int nextHopByHopIdentifier(ConnectionKey connkey) throws StaleConnectionException {
		synchronized(map_key_conn) {
			Connection conn = map_key_conn.get(connkey);
			if(conn==null)
				throw new StaleConnectionException();
			return conn.nextHopByHopIdentifier();
		}
	}
	/**
	 * Send a message.
	 * Send the specified message on the specified connection.
	 * @param msg The message to be sent
	 * @param connkey The connection to use. If the connection has been closed in the meantime StaleConnectionException is thrown.
	 */
	public void sendMessage(Message msg, ConnectionKey connkey) throws StaleConnectionException {
		synchronized(map_key_conn) {
			Connection conn = map_key_conn.get(connkey);
			if(conn==null)
				throw new StaleConnectionException();
			if(conn.state!=Connection.State.ready)
				throw new StaleConnectionException();
			sendMessage(msg,conn);
		}
	}
	private void sendMessage(Message msg, Connection conn) {
		logger.log(Level.FINER,"command=" + msg.hdr.command_code +", to=" + (conn.peer!=null ? conn.peer.toString() : conn.host_id));
		byte[] raw = msg.encode();
		
		boolean was_empty = !conn.hasNetOutput();
		conn.makeSpaceInAppOutBuffer(raw.length);
		//System.out.println("sendMessage: A: position=" + conn.out_buffer.position() + " limit=" + conn.out_buffer.limit());
		conn.connection_buffers.appOutBuffer().put(raw);
		conn.connection_buffers.processAppOutBuffer();
		//System.out.println("sendMessage: B: position=" + conn.out_buffer.position() + " limit=" + conn.out_buffer.limit());
		
		if(logger.isLoggable(Level.FINEST))
			hexDump(Level.FINEST,"Raw packet encoded",raw,0,raw.length);

		if(was_empty) {
			handleWritable(conn.channel,conn);
			if(conn.hasNetOutput()) {
				try {
					conn.channel.register(selector, SelectionKey.OP_READ|SelectionKey.OP_WRITE, conn);
				} catch(java.nio.channels.ClosedChannelException ex) { }
			}
		}
	}
	
	/**
	 * Initiate a connection to a peer.
	 * A connection (if not already present) will be initiated to the peer.
	 * On return, the connection is probably not established and it may
	 * take a few seconds before it is. It is safe to call multiple times.
	 * If <code>persistent</code> true then the peer is added to a list of
	 * persistent peers and if the connection is lost it will automatically
	 * be re-established. There is no way to change a peer from persistent
	 * to non-persistent.
	 * <p>
	 * If/when the connection has been established and capability-exchange
	 * has finished threads waiting in {@link #waitForConnection} are woken.
	 * <p>
	 * You cannot initiate connections before the node has been started.
	 * @param peer The peer that the node should try to establish a connection to.
	 * @param persistent If true the Node wil try to keep a connection open to the peer.
	 */
	public void initiateConnection(Peer peer, boolean persistent) {
		if(persistent) {
			synchronized(persistent_peers) {
				persistent_peers.add(new Peer(peer));
			}
		}
		synchronized(map_key_conn) {
			for(Map.Entry<ConnectionKey,Connection> e : map_key_conn.entrySet()) {
				Connection conn = e.getValue();
				if(conn.peer!=null &&
				   conn.peer.equals(peer))
					return; //already has a connection to that peer
				//what if we are connecting and the host_id matches?
			}
			logger.log(Level.INFO,"Initiating connection to '" + peer.host() +"'");
			try {
				SocketChannel channel = SocketChannel.open();
				channel.configureBlocking(false);
				InetSocketAddress address = new InetSocketAddress(peer.host(),peer.port());
				Connection conn = new Connection(address,settings.watchdogInterval(),settings.idleTimeout());
				conn.host_id = peer.host();
				conn.peer = peer;
				try {
					channel.connect(address);
				} catch(java.nio.channels.UnresolvedAddressException ex) {
					return;
				}
				conn.state = Connection.State.connecting;
				conn.channel = channel;
				selector.wakeup();
				channel.register(selector, SelectionKey.OP_CONNECT, conn);
				//System.out.println("PRE: size=" + map_key_conn.size());
				map_key_conn.put(conn.key,conn);
				//System.out.println("POST: size=" + map_key_conn.size());
			} catch(java.io.IOException ex) {
				logger.log(Level.WARNING,"java.io.IOException caught while initiating connection to '" + peer.host() +"'.", ex);
			}
		}
	}
	
	private class ReconnectThread extends Thread {
		public ReconnectThread() {
			super("Diameter node reconnect thread");
		}
		public void run() {
			for(;;) {
				synchronized(map_key_conn) {
					if(please_stop) return;
					try {
						map_key_conn.wait(30000);
					} catch(java.lang.InterruptedException ex) {}
					if(please_stop) return;
				}
				synchronized(persistent_peers) {
					for(Peer peer : persistent_peers)
						initiateConnection(peer,false);
				}
			}
		}
	}
	
	private void prepare() throws java.io.IOException {
		// create a new Selector for use below
		selector = Selector.open();
		if(settings.port()!=0) {
			// allocate an unbound server socket channel
			serverChannel = ServerSocketChannel.open();
			// Get the associated ServerSocket to bind it with
			ServerSocket serverSocket = serverChannel.socket();
			// set the port the server channel will listen to
			serverSocket.bind(new InetSocketAddress (settings.port()));
		}
		map_key_conn = new HashMap<ConnectionKey,Connection>();
		persistent_peers = new HashSet<Peer>();
	}
	
	private class SelectThread extends Thread {
	    public SelectThread() {
			super("DiameterNode thread");
		}
	    public void run() {
			try {
				run_();
				if(serverChannel!=null)
					serverChannel.close();
			} catch(java.io.IOException ex) {}
		}
	    private void run_() throws java.io.IOException {
		if(serverChannel!=null) {
			// set non-blocking mode for the listening socket
			serverChannel.configureBlocking(false);
			
			// register the ServerSocketChannel with the Selector
			serverChannel.register(selector, SelectionKey.OP_ACCEPT);
		}
		
		for(;;) {
			if(please_stop) {
				if(System.currentTimeMillis()>=shutdown_deadline)
					break;
				synchronized(map_key_conn) {
					if(map_key_conn.isEmpty())
						break;
				}
			}
			long timeout = calcNextTimeout();
			int n;
			//System.out.println("selecting...");
			if(timeout!=-1) {
				long now=System.currentTimeMillis();
				if(timeout>now)
					n = selector.select(timeout-now);
				else
					n = selector.selectNow();
			} else
				n = selector.select();
			//System.out.println("Woke up from select()");
			
			// get an iterator over the set of selected keys
			Iterator it = selector.selectedKeys().iterator();
			// look at each key in the selected set
			while(it.hasNext()) {
				SelectionKey key = (SelectionKey)it.next();
				
				if(key.isAcceptable()) {
					logger.log(Level.FINE,"Got an inbound connection (key is acceptable)");
					ServerSocketChannel server = (ServerSocketChannel)key.channel();
					SocketChannel channel = server.accept();
					InetSocketAddress address = (InetSocketAddress)channel.socket().getRemoteSocketAddress();
					logger.log(Level.INFO,"Got an inbound connection from " + address.toString());
					if(!please_stop) {
						Connection conn = new Connection(address,settings.watchdogInterval(),settings.idleTimeout());
						conn.host_id = address.getAddress().getHostAddress();
						conn.state = Connection.State.connected_in;
						conn.channel = channel;
						channel.configureBlocking(false);
						channel.register(selector, SelectionKey.OP_READ, conn);

						synchronized(map_key_conn) {
							map_key_conn.put(conn.key,conn);
						}
					} else {
						//We don't want to add the connection if were are shutting down.
						channel.close();
					}
				} else if(key.isConnectable()) {
					logger.log(Level.FINE,"An outbound connection is ready (key is connectable)");
					SocketChannel channel = (SocketChannel)key.channel();
					Connection conn = (Connection)key.attachment();
					try {
						if(channel.finishConnect()) {
							logger.log(Level.FINEST,"Connected!");
							conn.state = Connection.State.connected_out;
							channel.register(selector, SelectionKey.OP_READ, conn);
							sendCER(channel,conn);
						}
					} catch(java.io.IOException ex) {
						logger.log(Level.WARNING,"Connection to '"+conn.host_id+"' failed", ex);
						try {
							channel.register(selector, 0);
							channel.close();
						} catch(java.io.IOException ex2) {}
						synchronized(map_key_conn) {
							map_key_conn.remove(conn.key);
						}
					}
				} else if(key.isReadable()) {
					logger.log(Level.FINEST,"Key is readable");
					//System.out.println("key is readable");
					SocketChannel channel = (SocketChannel)key.channel();
					Connection conn = (Connection)key.attachment();
					handleReadable(channel,conn);
					if(conn.state!=Connection.State.closed &&
					   conn.hasNetOutput())
						channel.register(selector, SelectionKey.OP_READ|SelectionKey.OP_WRITE, conn);
				} else if(key.isWritable()) {
					logger.log(Level.FINEST,"Key is writable");
					SocketChannel channel = (SocketChannel)key.channel();
					Connection conn = (Connection)key.attachment();
					synchronized(map_key_conn) {
						handleWritable(channel,conn);
						if(conn.state!=Connection.State.closed &&
						   conn.hasNetOutput())
							channel.register(selector, SelectionKey.OP_READ|SelectionKey.OP_WRITE, conn);
					}
				}
				
				// remove key from selected set, it's been handled
				it.remove();
			}
			
			runTimers();
		}
		
		//close all connections
		//(todo) if a connection's out-buffer is non-empty we should wait for it to empty.
		synchronized(map_key_conn) {
			for(Map.Entry<ConnectionKey,Connection> e : map_key_conn.entrySet()) {
				Connection conn = e.getValue();
				closeConnection(conn);
			}
		}
		
		//selector is closed in stop()
	    }
	}
	
	private long calcNextTimeout() {
		long timeout = -1;
		synchronized(map_key_conn) {
			for(Map.Entry<ConnectionKey,Connection> e : map_key_conn.entrySet()) {
				Connection conn = e.getValue();
				boolean ready = conn.state==Connection.State.ready;
				long conn_timeout = conn.timers.calcNextTimeout(ready);
				if(timeout==-1 || conn_timeout<timeout)
					timeout = conn_timeout;
			}
		}
		if(please_stop && shutdown_deadline<timeout)
			timeout=shutdown_deadline;
		return timeout;
	}
	
	private void runTimers() {
		synchronized(map_key_conn) {
			for(Map.Entry<ConnectionKey,Connection> e : map_key_conn.entrySet()) {
				Connection conn = e.getValue();
				boolean ready = conn.state==Connection.State.ready;
				switch(conn.timers.calcAction(ready)) {
					case none:
						break;
					case disconnect_no_cer:
						logger.log(Level.WARNING,"Disconnecting due to no CER/CEA");
						closeConnection(conn);
						break;
					case disconnect_idle:
						logger.log(Level.WARNING,"Disconnecting due to idle");
						//busy is the closest thing to "no traffic for a long time. No point in keeping the connection"
						initiateConnectionClose(conn,ProtocolConstants.DI_DISCONNECT_CAUSE_BUSY);
						break;
					case disconnect_no_dw:
						logger.log(Level.WARNING,"Disconnecting due to no DWA");
						closeConnection(conn);
						break;
					case dwr:
						sendDWR(conn);
						break;
				}
			}
		}
	}
	
	private void handleReadable(SocketChannel channel, Connection conn) {
		logger.log(Level.FINEST,"handlereadable()...");
		conn.makeSpaceInNetInBuffer();
		ConnectionBuffers connection_buffers = conn.connection_buffers;
		logger.log(Level.FINEST,"pre: conn.in_buffer.position=" + connection_buffers.netInBuffer().position());
 		int count;
		try {
			int loop_count=0;
	 		while((count=channel.read(connection_buffers.netInBuffer()))>0 && loop_count++<3) {
				logger.log(Level.FINEST,"readloop: connection_buffers.netInBuffer().position=" + connection_buffers.netInBuffer().position());
				conn.makeSpaceInNetInBuffer();
			}
		} catch(java.io.IOException ex) {
			logger.log(Level.FINE,"got IOException",ex);
			closeConnection(channel,conn);
			return;
		}
		conn.processNetInBuffer();
		processInBuffer(channel,conn);
 		if(count<0 && conn.state!=Connection.State.closed) {
			logger.log(Level.FINE,"count<0");
			closeConnection(channel,conn);
			return;
 		}
	}
	
	void hexDump(Level level, String msg, byte buf[], int offset, int bytes) {
		if(!logger.isLoggable(level))
			return;
		//For some reason this method is grotesquely slow, so we limit the raw dump to 1K
		if(bytes>1024) bytes=1024;
		StringBuffer sb = new StringBuffer(msg.length()+1+bytes*3+(bytes/16+1)*(6+3+5+1));
		sb.append(msg+"\n");
		for(int i=0; i<bytes; i+=16) {
			sb.append(String.format("%04X ", new Integer(i)));
			for(int j=i; j<i+16; j++) {
				if((j%4)==0)
					sb.append(' ');
				if(j<bytes) {
					byte b=buf[offset+j];
					sb.append(String.format("%02X",b));
				} else
					sb.append("  ");
			}
			sb.append("     ");
			for(int j=i; j<i+16 && j<bytes; j++) {
				byte b=buf[offset+j];
				if(b>=32 && b<127)
					sb.append((char)b);
				else
					sb.append('.');
			}
			sb.append('\n');
		}
		if(bytes>1024)
			sb.append("...\n"); //Maybe the string "(truncated)" would be a more direct hint
		logger.log(level,sb.toString());
	}
	
	private void processInBuffer(SocketChannel channel, Connection conn) {
		ByteBuffer app_in_buffer = conn.connection_buffers.appInBuffer();
		logger.log(Level.FINEST,"pre: app_in_buffer.position=" + app_in_buffer.position());
		int raw_bytes=app_in_buffer.position();
		byte[] raw = new byte[raw_bytes];
		app_in_buffer.position(0);
		app_in_buffer.get(raw);
		app_in_buffer.position(raw_bytes);
		int offset=0;
		//System.out.println("processInBuffer():looping");
		while(offset<raw.length) {
			//System.out.println("processInBuffer(): inside loop offset=" + offset);
			int bytes_left = raw.length-offset;
			if(bytes_left<4) break;
			int msg_size = Message.decodeSize(raw,offset);
			if(bytes_left<msg_size) break;
			Message msg = new Message();
			Message.decode_status status = msg.decode(raw,offset,msg_size);
			//System.out.println("processInBuffer():decoded, status=" + status);
			switch(status) {
				case decoded: {
					if(logger.isLoggable(Level.FINEST))
						hexDump(Level.FINEST,"Raw packet decoded",raw,offset,msg_size);
					offset += msg_size;
					boolean b = handleMessage(msg,conn);
					if(!b) {
						logger.log(Level.FINER,"handle error");
						closeConnection(channel,conn);
						return;
					}
					break;
				}
				case not_enough:
					break;
				case garbage:
					hexDump(Level.WARNING,"Garbage from "+conn.host_id,raw,offset,msg_size);
					closeConnection(channel,conn,true);
					return;
			}
			if(status==Message.decode_status.not_enough) break;
		}
		conn.consumeAppInBuffer(offset);
		//System.out.println("processInBuffer(): the end");
	}
	private void handleWritable(SocketChannel channel, Connection conn) {
		logger.log(Level.FINEST,"handleWritable():");
		ByteBuffer net_out_buffer = conn.connection_buffers.netOutBuffer();
		//int bytes = net_out_buffer.position();
		//net_out_buffer.rewind();
		//net_out_buffer.limit(bytes);
		net_out_buffer.flip();
		//logger.log(Level.FINEST,"                :bytes= " + bytes);
		int count;
		try {
			count = channel.write(net_out_buffer);
			if(count<0) {
				closeConnection(channel,conn);
				return;
			}
			//conn.consumeNetOutBuffer(count);
			net_out_buffer.compact();
			conn.processAppOutBuffer();
			if(!conn.hasNetOutput())
				channel.register(selector, SelectionKey.OP_READ, conn);
		} catch(java.io.IOException ex) {
			closeConnection(channel,conn);
			return;
		}
	}
	
	private void closeConnection(Connection conn)  {
		closeConnection(conn.channel,conn);
	}
	private void closeConnection(SocketChannel channel, Connection conn) {
		closeConnection(channel,conn,false);
	}
	private void closeConnection(SocketChannel channel, Connection conn, boolean reset) {
		if(conn.state==Connection.State.closed) return;
		logger.log(Level.INFO,"Closing connection to " + (conn.peer!=null ? conn.peer.toString() : conn.host_id));
		synchronized(map_key_conn) {
			try {
				channel.register(selector, 0);
				if(reset) {
					//Set lingertime to zero to force a RST when closing the socket
					//rfc3588, section 2.1
					channel.socket().setSoLinger(true,0);
				}
				channel.close();
			} catch(java.io.IOException ex) {}
			map_key_conn.remove(conn.key);
			conn.state = Connection.State.closed;
		}
		connection_listener.handle(conn.key, conn.peer, false);
	}
	
	//Send a DPR with the specified disconnect-cause, want change the state to 'closing'
	private void initiateConnectionClose(Connection conn, int why) {
		if(conn.state!=Connection.State.ready)
			return; //should probably never happen
		sendDPR(conn,why);
		conn.state = Connection.State.closing;
	}
	
	private boolean handleMessage(Message msg, Connection conn) {
		if(logger.isLoggable(Level.FINE))
			logger.log(Level.FINE,"command_code=" + msg.hdr.command_code + " application_id=" + msg.hdr.application_id + " connection_state=" + conn.state);
		conn.timers.markActivity();
		if(conn.state==Connection.State.connected_in) {
			//only CER allowed
			if(!msg.hdr.isRequest() ||
			   msg.hdr.command_code!=ProtocolConstants.DIAMETER_COMMAND_CAPABILITIES_EXCHANGE ||
			   msg.hdr.application_id!=ProtocolConstants.DIAMETER_APPLICATION_COMMON)
			{
				logger.log(Level.WARNING,"Got something that wasn't a CER");
				return false;
			}
			conn.timers.markRealActivity();
			return handleCER(msg,conn);
		} else if(conn.state==Connection.State.connected_out) {
			//only CEA allowed
			if(msg.hdr.isRequest() ||
			   msg.hdr.command_code!=ProtocolConstants.DIAMETER_COMMAND_CAPABILITIES_EXCHANGE ||
			   msg.hdr.application_id!=ProtocolConstants.DIAMETER_APPLICATION_COMMON)
			{
				logger.log(Level.WARNING,"Got something that wasn't a CEA");
				return false;
			}
			conn.timers.markRealActivity();
			return handleCEA(msg,conn);
		} else {
			switch(msg.hdr.command_code) {
				case ProtocolConstants.DIAMETER_COMMAND_CAPABILITIES_EXCHANGE:
					logger.log(Level.WARNING,"Got CER from "+conn.host_id+" after initial capability-exchange");
					//not allowed in this state
					return false;
				case ProtocolConstants.DIAMETER_COMMAND_DEVICE_WATCHDOG:
					if(msg.hdr.isRequest())
						return handleDWR(msg,conn);
					else
						return handleDWA(msg,conn);
				case ProtocolConstants.DIAMETER_COMMAND_DISCONNECT_PEER:
					if(msg.hdr.isRequest())
						return handleDPR(msg,conn);
					else
						return handleDPA(msg,conn);
				default:
					conn.timers.markRealActivity();
					if(msg.hdr.isRequest()) {
						if(isLoopedMessage(msg)) {
							rejectLoopedRequest(msg,conn);
							return true;
						}
						if(!isAllowedApplication(msg,conn.peer)) {
							rejectDisallowedRequest(msg,conn);
							return true;
						}
						//We could also reject requests if we ar shutting down, but there are no result-code for this.
					}
					if(!message_dispatcher.handle(msg,conn.key,conn.peer)) {
						if(msg.hdr.isRequest())
							return handleUnknownRequest(msg,conn);
						else
							return true; //unusual, but not impossible
					} else
						return true;
			}
		}
	}
	
	private boolean isLoopedMessage(Message msg) {
		//6.1.3
		for(AVP a : msg.subset(ProtocolConstants.DI_ROUTE_RECORD)) {
			AVP_UTF8String avp=new AVP_UTF8String(a);
			if(avp.queryValue().equals(settings.hostId()))
				return true;
		}
		return false;
	}
	private void rejectLoopedRequest(Message msg, Connection conn) {
		logger.log(Level.WARNING,"Rejecting looped request from " + conn.peer.host() + " (command=" + msg.hdr.command_code + ").");
		rejectRequest(msg,conn,ProtocolConstants.DIAMETER_RESULT_LOOP_DETECTED);
	}
	
	/**
	 * Determine if a message is supported by a peer.
	 * The auth-application-id, acct-application-id or
	 * vendor-specific-application AVP is extracted and tested against the
	 * peer's capabilities.
	 * @param msg The message
	 * @param peer The peer
	 * @return True if the peer should be able to handle the message.
	 */
	public boolean isAllowedApplication(Message msg, Peer peer) {
		try {
			AVP avp;
			avp = msg.find(ProtocolConstants.DI_AUTH_APPLICATION_ID);
			if(avp!=null) {
				int app = new AVP_Unsigned32(avp).queryValue();
				if(logger.isLoggable(Level.FINE))
					logger.log(Level.FINE,"auth-application-id="+app);
				return peer.capabilities.isAllowedAuthApp(app);
			}
			avp = msg.find(ProtocolConstants.DI_ACCT_APPLICATION_ID);
			if(avp!=null) {
				int app = new AVP_Unsigned32(avp).queryValue();
				if(logger.isLoggable(Level.FINE))
					logger.log(Level.FINE,"acct-application-id="+app);
				return peer.capabilities.isAllowedAcctApp(app);
			}
			avp = msg.find(ProtocolConstants.DI_VENDOR_SPECIFIC_APPLICATION_ID);
			if(avp!=null) {
				AVP g[] = new AVP_Grouped(avp).queryAVPs();
				if(g.length==2 &&
				   g[0].code==ProtocolConstants.DI_VENDOR_ID) {
					int vendor_id = new AVP_Unsigned32(g[0]).queryValue();
					int app = new AVP_Unsigned32(g[1]).queryValue();
					if(logger.isLoggable(Level.FINE))
						logger.log(Level.FINE,"vendor-id="+vendor_id+", app="+app);
					if(g[1].code==ProtocolConstants.DI_AUTH_APPLICATION_ID)
						return peer.capabilities.isAllowedAuthApp(vendor_id,app);
					if(g[1].code==ProtocolConstants.DI_ACCT_APPLICATION_ID)
						return peer.capabilities.isAllowedAcctApp(vendor_id,app);
				}
				return false;
			}
			logger.log(Level.WARNING,"No auth-app-id, acct-app-id nor vendor-app in packet");
		} catch(InvalidAVPLengthException ex) {
			logger.log(Level.INFO,"Encountered invalid AVP length while looking at application-id",ex);
		}
		return false;
	}
	private void rejectDisallowedRequest(Message msg, Connection conn) {
		logger.log(Level.WARNING,"Rejecting request  from " + conn.peer.host() + " (command=" + msg.hdr.command_code + ") because it is not allowed.");
		rejectRequest(msg,conn,ProtocolConstants.DIAMETER_RESULT_APPLICATION_UNSUPPORTED);
	}
	
	private void rejectRequest(Message msg, Connection conn, int result_code) {
		Message response = new Message();
		response.prepareResponse(msg);
		if(result_code>=3000 && result_code<=3999)
			response.hdr.setError(true);
		response.add(new AVP_Unsigned32(ProtocolConstants.DI_RESULT_CODE, result_code));
		addOurHostAndRealm(response);
		Utils.copyProxyInfo(msg,response);
		Utils.setMandatory_RFC3588(response);
		sendMessage(response,conn);
	}
	
	
	/**
	 * Add origin-host and origin-realm to a message.
	 * The configured host and realm is added to the message as origin-host
	 * and origin-realm AVPs
	 */
	public void addOurHostAndRealm(Message msg) {
		msg.add(new AVP_UTF8String(ProtocolConstants.DI_ORIGIN_HOST,settings.hostId()));
		msg.add(new AVP_UTF8String(ProtocolConstants.DI_ORIGIN_REALM,settings.realm()));
	}
	
	/**
	 * Returns an end-to-end identifier that is unique.
	 * The initial value is generated as described in RFC 3588 section 3 page 34.
	 */
	public int nextEndToEndIdentifier() {
		return node_state.nextEndToEndIdentifier();
	}
	
	/**
	 * Generate a new session-id.
	 * Implemented as makeNewSessionId(null)
	 * @since 0.9.2
	 */
	public String makeNewSessionId() {
		return makeNewSessionId(null);
	}
	
	/**
	 * Generate a new session-id.
	 * A Session-Id consists of a mandatory part and an optional part.
	 * The mandatory part consists of the host-id and two sequencer.
	 * The optional part can be anything. The caller provide some
	 * information that will be helpful in debugging in production
	 * environments, such as user-name or calling-station-id.
	 * @since 0.9.2
	 */
	public String makeNewSessionId(String optional_part) {
		String mandatory_part = settings.hostId() + ";" + node_state.nextSessionId_second_part();
		if(optional_part==null)
			return mandatory_part;
		else
			return mandatory_part + ";" + optional_part;
	}
	
	/**
	 * Returns the node's state-id.
	 * @since 0.9.2
	 */
	public int stateId() {
		return node_state.stateId();
	}
	
	
	private boolean doElection(String cer_host_id) {
		int cmp = settings.hostId().compareTo(cer_host_id);
		if(cmp==0) {
			logger.log(Level.WARNING,"Got CER with host-id="+cer_host_id+". Suspecting this is a connection from ourselves.");
			//this is a misconfigured peer or ourselves.
			return false;
		}
		boolean close_other_connection = cmp>0;
		synchronized(map_key_conn) {
			for(Map.Entry<ConnectionKey,Connection> e : map_key_conn.entrySet()) {
				Connection conn = e.getValue();
				if(conn.host_id!=null && conn.host_id.equals(cer_host_id)) {
					if(close_other_connection) {
						closeConnection(conn);
						return true;
					} else
						return false; //close this one
				}
			}
		}
		return true;
	}
	
	private boolean handleCER(Message msg, Connection conn) {
		logger.log(Level.FINE,"CER received from " + conn.host_id);
		//Handle election
		String host_id;
		{
			AVP avp = msg.find(ProtocolConstants.DI_ORIGIN_HOST);
			if(avp==null) {
				//Origin-Host-Id is missing
				logger.log(Level.FINE,"CER from " + conn.host_id+" is missing the Origin-Host_id AVP. Rejecting.");
				Message error_response = new Message();
				error_response.prepareResponse(msg);
				error_response.add(new AVP_Unsigned32(ProtocolConstants.DI_RESULT_CODE, ProtocolConstants.DIAMETER_RESULT_MISSING_AVP));
				addOurHostAndRealm(error_response);
				error_response.add(new AVP_FailedAVP(new AVP_UTF8String(ProtocolConstants.DI_ORIGIN_HOST,"")));
				Utils.setMandatory_RFC3588(error_response);
				sendMessage(error_response,conn);
				return false;
			}
			host_id = new AVP_UTF8String(avp).queryValue();
			logger.log(Level.FINER,"Peer's origin-host-id is " + host_id);
			
			//We must authenticate the host before doing election.
			//Otherwise a rogue node could trick us into
			//disconnecting legitimate peers.
			NodeValidator.AuthenticationResult ar = node_validator.authenticateNode(host_id,conn.channel);
			if(ar==null || !ar.known) {
				logger.log(Level.FINE,"We do not know " + conn.host_id+" Rejecting.");
				Message error_response = new Message();
				error_response.prepareResponse(msg);
				if(ar!=null && ar.result_code!=null)
					error_response.add(new AVP_Unsigned32(ProtocolConstants.DI_RESULT_CODE,  ar.result_code));
				else
					error_response.add(new AVP_Unsigned32(ProtocolConstants.DI_RESULT_CODE,  ProtocolConstants.DIAMETER_RESULT_UNKNOWN_PEER));
				addOurHostAndRealm(error_response);
				if(ar!=null && ar.error_message!=null)
					error_response.add(new AVP_UTF8String(ProtocolConstants.DI_ERROR_MESSAGE,ar.error_message));
				Utils.setMandatory_RFC3588(error_response);
				sendMessage(error_response,conn);
				return false;
				
			}
			
			if(!doElection(host_id)) {
				logger.log(Level.FINE,"CER from " + conn.host_id+" lost the election. Rejecting.");
				Message error_response = new Message();
				error_response.prepareResponse(msg);
				error_response.add(new AVP_Unsigned32(ProtocolConstants.DI_RESULT_CODE, ProtocolConstants.DIAMETER_RESULT_ELECTION_LOST));
				addOurHostAndRealm(error_response);
				Utils.setMandatory_RFC3588(error_response);
				sendMessage(error_response,conn);
				return false;
			}
		}
		
		conn.peer = new Peer(conn.channel.socket().getInetAddress(),conn.channel.socket().getPort());
		conn.peer.host(host_id);
		conn.host_id = host_id;
		
		if(handleCEx(msg,conn)) {
			//todo: check inband-security
			Message cea = new Message();
			cea.prepareResponse(msg);
			//Result-Code
			cea.add(new AVP_Unsigned32(ProtocolConstants.DI_RESULT_CODE, ProtocolConstants.DIAMETER_RESULT_SUCCESS));
			addCEStuff(cea,conn.peer.capabilities,conn);
			
			logger.log(Level.INFO,"Connection to " +conn.peer.toString() + " is now ready");
			Utils.setMandatory_RFC3588(cea);
			sendMessage(cea,conn);
			conn.state=Connection.State.ready;
			connection_listener.handle(conn.key, conn.peer, true);
			synchronized(obj_conn_wait) {
				obj_conn_wait.notifyAll();
			}
			return true;
		} else
			return false;
	}
	private boolean handleCEA(Message msg, Connection conn) {
		logger.log(Level.FINE,"CEA received from "+conn.host_id);
		AVP avp = msg.find(ProtocolConstants.DI_RESULT_CODE);
		if(avp==null) {
			logger.log(Level.WARNING,"CEA from "+conn.host_id+" did not contain a Result-Code AV=P. Dropping connection");
			return false;
		}
		int result_code;
		try {
			result_code = new AVP_Unsigned32(avp).queryValue();
		} catch(InvalidAVPLengthException ex) {
			logger.log(Level.INFO,"CEA from "+conn.host_id+" contained an ill-formed Result-Code. Dropping connection");
			return false;
		}
		if(result_code!=ProtocolConstants.DIAMETER_RESULT_SUCCESS) {
			logger.log(Level.INFO,"CEA from "+conn.host_id+" was rejected with Result-Code "+result_code+". Dropping connection");
			return false;
		}
		avp = msg.find(ProtocolConstants.DI_ORIGIN_HOST);
		if(avp==null) {
			logger.log(Level.WARNING,"Peer did not include origin-host-id in CEA");
			return false;
		}
		String host_id = new AVP_UTF8String(avp).queryValue();
		logger.log(Level.FINER,"Node:Peer's origin-host-id is '"+host_id+"'");
		
		conn.peer = new Peer(conn.channel.socket().getInetAddress(),conn.channel.socket().getPort());
		conn.peer.host(host_id);
		conn.host_id = host_id;
		boolean rc = handleCEx(msg,conn);
		if(rc) {
			conn.state=Connection.State.ready;
			logger.log(Level.INFO,"Connection to " +conn.peer.toString() + " is now ready");
			connection_listener.handle(conn.key, conn.peer, true);
			synchronized(obj_conn_wait) {
				obj_conn_wait.notifyAll();
			}
			return true;
		} else {
			return false;
		}
	}
	private boolean handleCEx(Message msg, Connection conn) {
		logger.log(Level.FINER,"Processing CER/CEA");
		//calculate capabilities and allowed applications
		try {
			Capability reported_capabilities = new Capability();
			for(AVP a : msg.subset(ProtocolConstants.DI_SUPPORTED_VENDOR_ID)) {
				int vendor_id = new AVP_Unsigned32(a).queryValue();
				logger.log(Level.FINEST,"peer supports vendor "+vendor_id);
				reported_capabilities.addSupportedVendor(vendor_id);
			}
			for(AVP a : msg.subset(ProtocolConstants.DI_AUTH_APPLICATION_ID)) {
				int app = new AVP_Unsigned32(a).queryValue();
				logger.log(Level.FINEST,"peer supports auth-app "+app);
				if(app!=ProtocolConstants.DIAMETER_APPLICATION_COMMON)
					reported_capabilities.addAuthApp(app);
			}
			for(AVP a : msg.subset(ProtocolConstants.DI_ACCT_APPLICATION_ID)) {
				int app = new AVP_Unsigned32(a).queryValue();
				logger.log(Level.FINEST,"peer supports acct-app "+app);
				if(app!=ProtocolConstants.DIAMETER_APPLICATION_COMMON)
					reported_capabilities.addAcctApp(app);
			}
			for(AVP a : msg.subset(ProtocolConstants.DI_VENDOR_SPECIFIC_APPLICATION_ID)) {
				AVP_Grouped ag = new AVP_Grouped(a);
				AVP g[] = ag.queryAVPs();
				if(g.length>=2 && g[0].code==ProtocolConstants.DI_VENDOR_ID) {
					int vendor_id = new AVP_Unsigned32(g[0]).queryValue();
					int app = new AVP_Unsigned32(g[1]).queryValue();
					if(g[1].code==ProtocolConstants.DI_AUTH_APPLICATION_ID) {
						reported_capabilities.addVendorAuthApp(vendor_id,app);
					} else if(g[1].code==ProtocolConstants.DI_ACCT_APPLICATION_ID) {
						reported_capabilities.addVendorAcctApp(vendor_id,app);
					} else
						throw new InvalidAVPValueException(a);
				} else
					throw new InvalidAVPValueException(a);
			}
			
			Capability result_capabilities = node_validator.authorizeNode(conn.host_id, settings, reported_capabilities);
			if(logger.isLoggable(Level.FINEST)) {
				String s = "";
				for(Integer i:result_capabilities.supported_vendor)
					s = s + "  supported_vendor "+i+"\n";
				for(Integer i:result_capabilities.auth_app)
					s = s + "  auth_app "+i+"\n";
				for(Integer i:result_capabilities.acct_app)
					s = s + "  acct_app "+i+"\n";
				for(Capability.VendorApplication va:result_capabilities.auth_vendor)
					s = s + "  vendor_auth_app: vendor "+va.vendor_id+", application "+ va.application_id+"\n";
				for(Capability.VendorApplication va:result_capabilities.acct_vendor)
					s = s + "  vendor_acct_app: vendor "+va.vendor_id+", application "+ va.application_id+"\n";
				logger.log(Level.FINEST,"Resulting capabilities:\n"+s);
			}
			if(result_capabilities.isEmpty()) {
				logger.log(Level.WARNING,"No application in common with "+conn.host_id);
				if(msg.hdr.isRequest()) {
					Message error_response = new Message();
					error_response.prepareResponse(msg);
					error_response.add(new AVP_Unsigned32(ProtocolConstants.DI_RESULT_CODE, ProtocolConstants.DIAMETER_RESULT_NO_COMMON_APPLICATION));
					addOurHostAndRealm(error_response);
					Utils.setMandatory_RFC3588(error_response);
					sendMessage(error_response,conn);
				}
				return false;
			}
			
			conn.peer.capabilities = result_capabilities;
		} catch(InvalidAVPLengthException ex) {
			logger.log(Level.WARNING,"Invalid AVP in CER/CEA",ex);
			if(msg.hdr.isRequest()) {
				Message error_response = new Message();
				error_response.prepareResponse(msg);
				error_response.add(new AVP_Unsigned32(ProtocolConstants.DI_RESULT_CODE, ProtocolConstants.DIAMETER_RESULT_INVALID_AVP_LENGTH));
				addOurHostAndRealm(error_response);
				error_response.add(new AVP_FailedAVP(ex.avp));
				Utils.setMandatory_RFC3588(error_response);
				sendMessage(error_response,conn);
			}
			return false;
		} catch(InvalidAVPValueException ex) {
			logger.log(Level.WARNING,"Invalid AVP in CER/CEA",ex);
			if(msg.hdr.isRequest()) {
				Message error_response = new Message();
				error_response.prepareResponse(msg);
				error_response.add(new AVP_Unsigned32(ProtocolConstants.DI_RESULT_CODE, ProtocolConstants.DIAMETER_RESULT_INVALID_AVP_VALUE));
				addOurHostAndRealm(error_response);
				error_response.add(new AVP_FailedAVP(ex.avp));
				Utils.setMandatory_RFC3588(error_response);
				sendMessage(error_response,conn);
			}
			return false;
		}
		return true;
	}
	
	private void sendCER(SocketChannel channel, Connection conn)  {
		logger.log(Level.FINE,"Sending CER to "+conn.host_id);
		Message cer = new Message();
		cer.hdr.setRequest(true);
		cer.hdr.command_code = ProtocolConstants.DIAMETER_COMMAND_CAPABILITIES_EXCHANGE;
		cer.hdr.application_id = ProtocolConstants.DIAMETER_APPLICATION_COMMON;
		cer.hdr.hop_by_hop_identifier = conn.nextHopByHopIdentifier();
		cer.hdr.end_to_end_identifier = node_state.nextEndToEndIdentifier();
		addCEStuff(cer,settings.capabilities(),conn);
		Utils.setMandatory_RFC3588(cer);
		
		sendMessage(cer,conn);
	}
	private void addCEStuff(Message msg, Capability capabilities, Connection conn) {
		//Origin-Host, Origin-Realm
		addOurHostAndRealm(msg);
		//Host-IP-Address
		//  This is not really that good..
		msg.add(new AVP_Address(ProtocolConstants.DI_HOST_IP_ADDRESS, conn.channel.socket().getLocalAddress()));
		//Vendor-Id
		msg.add(new AVP_Unsigned32(ProtocolConstants.DI_VENDOR_ID, settings.vendorId()));
		//Product-Name
		msg.add(new AVP_UTF8String(ProtocolConstants.DI_PRODUCT_NAME, settings.productName()));
		//Origin-State-Id
		msg.add(new AVP_Unsigned32(ProtocolConstants.DI_ORIGIN_STATE_ID,node_state.stateId()));
		//Error-Message, Failed-AVP: not in success
		//Supported-Vendor-Id
		for(Integer i : capabilities.supported_vendor) {
			msg.add(new AVP_Unsigned32(ProtocolConstants.DI_SUPPORTED_VENDOR_ID,i));
		}
		//Auth-Application-Id
		for(Integer i : capabilities.auth_app) {
			msg.add(new AVP_Unsigned32(ProtocolConstants.DI_AUTH_APPLICATION_ID,i));
		}
		//Inband-Security-Id
		//  todo
		//Acct-Application-Id
		for(Integer i : capabilities.acct_app) {
			msg.add(new AVP_Unsigned32(ProtocolConstants.DI_ACCT_APPLICATION_ID,i));
		}
		//Vendor-Specific-Application-Id
		for(Capability.VendorApplication va : capabilities.auth_vendor) {
			AVP g[] = new AVP[2];
			g[0] = new AVP_Unsigned32(ProtocolConstants.DI_VENDOR_ID,va.vendor_id).setM();
			g[1] = new AVP_Unsigned32(ProtocolConstants.DI_AUTH_APPLICATION_ID,va.application_id).setM();
			msg.add(new AVP_Grouped(ProtocolConstants.DI_VENDOR_SPECIFIC_APPLICATION_ID,g));
		}
		for(Capability.VendorApplication va : capabilities.acct_vendor) {
			AVP g[] = new AVP[2];
			g[0] = new AVP_Unsigned32(ProtocolConstants.DI_VENDOR_ID,va.vendor_id).setM();
			g[1] = new AVP_Unsigned32(ProtocolConstants.DI_ACCT_APPLICATION_ID,va.application_id).setM();
			msg.add(new AVP_Grouped(ProtocolConstants.DI_VENDOR_SPECIFIC_APPLICATION_ID,g));
		}
		//Firmware-Revision
		if(settings.firmwareRevision()!=0)
			msg.add(new AVP_Unsigned32(ProtocolConstants.DI_FIRMWARE_REVISION,settings.firmwareRevision()));
	}
	
	private boolean handleDWR(Message msg, Connection conn) {
		logger.log(Level.INFO,"DWR received from "+conn.host_id);
		conn.timers.markDWR();
		Message dwa = new Message();
		dwa.prepareResponse(msg);
		dwa.add(new AVP_Unsigned32(ProtocolConstants.DI_RESULT_CODE, ProtocolConstants.DIAMETER_RESULT_SUCCESS));
		addOurHostAndRealm(dwa);
		dwa.add(new AVP_Unsigned32(ProtocolConstants.DI_ORIGIN_STATE_ID,node_state.stateId()));
		Utils.setMandatory_RFC3588(dwa);
		
		sendMessage(dwa,conn);
		return true;
	}
	private boolean handleDWA(Message msg, Connection conn) {
		logger.log(Level.FINE,"DWA received from "+conn.host_id);
		conn.timers.markDWA();
		return true;
	}
	private boolean handleDPR(Message msg, Connection conn) {
		logger.log(Level.FINE,"DPR received from "+conn.host_id);
		Message dpa = new Message();
		dpa.prepareResponse(msg);
		dpa.add(new AVP_Unsigned32(ProtocolConstants.DI_RESULT_CODE, ProtocolConstants.DIAMETER_RESULT_SUCCESS));
		addOurHostAndRealm(dpa);
		Utils.setMandatory_RFC3588(dpa);
		
		sendMessage(dpa,conn);
		return false;
	}
	private boolean handleDPA(Message msg, Connection conn) {
		if(conn.state==Connection.State.closing)
			logger.log(Level.INFO,"Got a DPA from "+conn.host_id);
		else
			logger.log(Level.WARNING,"Got a DPA. This is not expected");
		return false; //in any case close the connection
	}
	private boolean handleUnknownRequest(Message msg, Connection conn) {
		logger.log(Level.INFO,"Unknown request received from "+conn.host_id);
		rejectRequest(msg,conn,ProtocolConstants.DIAMETER_RESULT_UNABLE_TO_DELIVER);
		return true;
	}
	
	private void sendDWR(Connection conn) {
		logger.log(Level.FINE,"Sending DWR to "+conn.host_id);
		Message dwr = new Message();
		dwr.hdr.setRequest(true);
		dwr.hdr.command_code = ProtocolConstants.DIAMETER_COMMAND_DEVICE_WATCHDOG;
		dwr.hdr.application_id = ProtocolConstants.DIAMETER_APPLICATION_COMMON;
		dwr.hdr.hop_by_hop_identifier = conn.nextHopByHopIdentifier();
		dwr.hdr.end_to_end_identifier = node_state.nextEndToEndIdentifier();
		addOurHostAndRealm(dwr);
		dwr.add(new AVP_Unsigned32(ProtocolConstants.DI_ORIGIN_STATE_ID, node_state.stateId()));
		Utils.setMandatory_RFC3588(dwr);
		
		sendMessage(dwr,conn);
		
		conn.timers.markDWR_out();
	}
	
	private void sendDPR(Connection conn, int why) {
		logger.log(Level.FINE,"Sending DPR to "+conn.host_id);
		Message dpr = new Message();
		dpr.hdr.setRequest(true);
		dpr.hdr.command_code = ProtocolConstants.DIAMETER_COMMAND_DISCONNECT_PEER;
		dpr.hdr.application_id = ProtocolConstants.DIAMETER_APPLICATION_COMMON;
		dpr.hdr.hop_by_hop_identifier = conn.nextHopByHopIdentifier();
		dpr.hdr.end_to_end_identifier = node_state.nextEndToEndIdentifier();
		addOurHostAndRealm(dpr);
		dpr.add(new AVP_Unsigned32(ProtocolConstants.DI_DISCONNECT_CAUSE, why));
		Utils.setMandatory_RFC3588(dpr);
		
		sendMessage(dpr,conn);
	}
}
