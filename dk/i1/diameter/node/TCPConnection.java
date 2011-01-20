package dk.i1.diameter.node;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.ArrayList;

import java.util.logging.Logger;
import java.util.logging.Level;

class TCPConnection extends Connection {
static Logger logger = Logger.getLogger("dk.i1.diameter.node");
	TCPNode node_impl;
	SocketChannel channel;
	ConnectionBuffers connection_buffers;
	
	public TCPConnection(TCPNode node_impl, long watchdog_interval, long idle_timeout) {
		super(node_impl,watchdog_interval,idle_timeout);
		this.node_impl = node_impl;
		connection_buffers = new NormalConnectionBuffers();
	}
	
	void makeSpaceInNetInBuffer() {
		connection_buffers.makeSpaceInNetInBuffer();
	}
	void makeSpaceInAppOutBuffer(int how_much) {
		connection_buffers.makeSpaceInAppOutBuffer(how_much);
	}
	void consumeAppInBuffer(int bytes) {
		connection_buffers.consumeAppInBuffer(bytes);
	}
	void consumeNetOutBuffer(int bytes) {
		connection_buffers.consumeNetOutBuffer(bytes);
	}
	boolean hasNetOutput() {
		return connection_buffers.netOutBuffer().position()!=0;
	}
	
	void processNetInBuffer() {
logger.log(Level.FINEST,"entered");
		connection_buffers.processNetInBuffer();
logger.log(Level.FINEST,"staet="+state);
		if(connection_buffers instanceof TLSConnectionBuffers &&
		   state==State.tls &&
		   ((TLSConnectionBuffers)connection_buffers).handshakeIsFinished())
			node_impl.TLSFinished(this);
logger.log(Level.FINEST,"leaving");	}
	void processAppOutBuffer() {
		connection_buffers.processAppOutBuffer();
	}
	
	InetAddress toInetAddress() {
		return ((InetSocketAddress)(channel.socket().getRemoteSocketAddress())).getAddress();
	}
	
	void sendMessage(byte[] raw) {
		node_impl.sendMessage(this,raw);
	}
	
	Object getRelevantNodeAuthInfo() {
		return channel;
	}
	
	Collection<InetAddress> getLocalAddresses() {
		Collection<InetAddress> coll = new ArrayList<InetAddress>();
		coll.add(channel.socket().getLocalAddress());
		return coll;
	}
	
	Peer toPeer() {
		return new Peer(toInetAddress(),channel.socket().getPort());
	}
	
	void switchToTLS(javax.net.ssl.SSLContext ssl_context, boolean client_mode) {
		connection_buffers = new TLSConnectionBuffers((NormalConnectionBuffers)connection_buffers,ssl_context,client_mode);
	}
}
