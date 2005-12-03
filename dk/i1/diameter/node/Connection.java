package dk.i1.diameter.node;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.net.URI;
import java.net.InetSocketAddress;

class Connection {
	public Peer peer;  //initially null
	public String host_id; //always set, updated from CEA/CER
	public ConnectionTimers timers;
	public ConnectionKey key;
	private int hop_by_hop_identifier_seq;
	SocketChannel channel;
	ConnectionBuffers connection_buffers;
	
	public enum State {
		connecting,
		connected_in,  //connected, waiting for cer
		connected_out, //connected, waiting for cea
		tls,           //CE completed, negotiating TLS
		ready,         //ready
		closed
	}
	public State state;
	
	public Connection(InetSocketAddress address) {
		timers = new ConnectionTimers(30,3600); //todo
		key = new ConnectionKey();
		hop_by_hop_identifier_seq = new java.util.Random().nextInt();
		state = State.connected_in;
		connection_buffers = new NormalConnectionBuffers();
	}
	
	public synchronized int nextHopByHopIdentifier() {
		return hop_by_hop_identifier_seq++;
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
}
