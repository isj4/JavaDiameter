package dk.i1.diameter.node;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.ArrayList;
import dk.i1.sctp.*;
import java.util.LinkedList;

class SCTPConnection extends Connection {
	//Queue of pending messages
	private LinkedList<byte[]> queued_messages;
	private SCTPNode node_impl;
	AssociationId assoc_id;
	boolean closed;
	short sac_inbound_streams;
	short sac_outbound_streams;
	short out_stream_index;
	SCTPConnection(SCTPNode node_impl, long watchdog_interval, long idle_timeout) {
		super(node_impl,watchdog_interval,idle_timeout);
		queued_messages = new LinkedList<byte[]>();
		this.node_impl = node_impl;
		this.closed = false;
		this.sac_inbound_streams = 0;
		this.sac_outbound_streams = 0;
		this.out_stream_index = 0;
	}
	
	//Return the next stream number to use for sending
	short nextOutStream() {
		short i = out_stream_index;
		out_stream_index = (short)((out_stream_index+1)%sac_outbound_streams);
		return i;
	}
	
	InetAddress toInetAddress() {
		Collection<InetAddress> coll = getLocalAddresses();
		for(InetAddress ia : coll)
			return ia;
		return null;
	}
	
	void sendMessage(byte[] raw) {
		node_impl.sendMessage(this,raw);
	}
	
	Object getRelevantNodeAuthInfo() {
		return new RelevantSCTPAuthInfo(node_impl.sctp_socket,assoc_id);
	}
	
	Collection<InetAddress> getLocalAddresses() {
		try {
			return node_impl.sctp_socket.getLocalInetAddresses(assoc_id);
		} catch(java.net.SocketException ex) {
			return null;
		}
	}
	
	Peer toPeer() {
		try {
			return new Peer(toInetAddress(),node_impl.sctp_socket.getPeerInetPort(assoc_id));
		} catch(java.net.SocketException ex) {
			return null;
		}
	}
	
	void queueMessage(byte[] raw) {
		queued_messages.addLast(raw);
	}
	byte[] peekFirstQueuedMessage() {
		return queued_messages.peek();
	}
	void removeFirstQueuedMessage() {
		queued_messages.poll();
	}
	
	void switchToTLS(javax.net.ssl.SSLContext ssl_context, boolean client_mode) {
		//connection_buffers = new TLSConnectionBuffers((NormalConnectionBuffers)connection_buffers,ssl_context,client_mode);
		//todo
	}
}
