package dk.i1.diameter.node;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.ArrayList;

class TCPConnection extends Connection {
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
		connection_buffers.processNetInBuffer();
	}
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
}
