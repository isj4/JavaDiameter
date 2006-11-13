package dk.i1.diameter.node;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.ArrayList;

class TCPConnection extends Connection {
	SocketChannel channel;
	ConnectionBuffers connection_buffers;
	
	public TCPConnection(NodeImplementation node_impl, long watchdog_interval, long idle_timeout) {
		super(node_impl,watchdog_interval,idle_timeout);
		connection_buffers = new NormalConnectionBuffers();
	}
	
	public void makeSpaceInNetInBuffer() {
		connection_buffers.makeSpaceInNetInBuffer();
	}
	public void makeSpaceInAppOutBuffer(int how_much) {
		connection_buffers.makeSpaceInAppOutBuffer(how_much);
	}
	public void consumeAppInBuffer(int bytes) {
		connection_buffers.consumeAppInBuffer(bytes);
	}
	public void consumeNetOutBuffer(int bytes) {
		connection_buffers.consumeNetOutBuffer(bytes);
	}
	public boolean hasNetOutput() {
		return connection_buffers.netOutBuffer().position()!=0;
	}
	
	void processNetInBuffer() {
		connection_buffers.processNetInBuffer();
	}
	void processAppOutBuffer() {
		connection_buffers.processAppOutBuffer();
	}
	
	InetAddress toInetAddress() {
		return ((InetSocketAddress)(channel.socket().getRemoteSocketAddress())).getAddress();
	}
	
	void sendMessage(byte[] raw) {
		boolean was_empty = !hasNetOutput();
		makeSpaceInAppOutBuffer(raw.length);
		//System.out.println("sendMessage: A: position=" + out_buffer.position() + " limit=" + conn.out_buffer.limit());
		connection_buffers.appOutBuffer().put(raw);
		connection_buffers.processAppOutBuffer();
		//System.out.println("sendMessage: B: position=" + out_buffer.position() + " limit=" + conn.out_buffer.limit());
		
		if(was_empty)
			node_impl.outputBecameAvailable(this);
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
}
