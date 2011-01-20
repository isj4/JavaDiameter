package dk.i1.diameter.node;
import java.nio.ByteBuffer;
//import java.nio.channels.SocketChannel;

/**
 *
 */
abstract class ConnectionBuffers {
	abstract ByteBuffer netOutBuffer();
	abstract ByteBuffer netInBuffer();
	abstract ByteBuffer appInBuffer();
	abstract ByteBuffer appOutBuffer();
	abstract void processNetInBuffer();
	abstract void processAppOutBuffer();
	
	abstract void makeSpaceInNetInBuffer();
	abstract void makeSpaceInAppOutBuffer(int how_much);
	
	void consumeNetInBuffer(int bytes) {
		consume(netInBuffer(),bytes);
	}
	void consumeNetOutBuffer(int bytes) {
		consume(netOutBuffer(),bytes);
	}
	void consumeAppInBuffer(int bytes) {
		consume(appInBuffer(),bytes);
	}
	void consumeAppOutBuffer(int bytes) {
		consume(appOutBuffer(),bytes);
	}
	
	
	static ByteBuffer makeSpaceInBuffer(ByteBuffer bb, int how_much) {
		if(bb.position()+how_much > bb.capacity()) {
			int bytes = bb.position();
			int new_capacity = bb.capacity()+how_much;
			new_capacity = new_capacity + (4096-(new_capacity%4096));
			ByteBuffer tmp = ByteBuffer.allocate(new_capacity);
			bb.flip();
			tmp.put(bb);
			tmp.position(bytes);
			bb = tmp;
		}
		return bb;
	}
	static private void consume(ByteBuffer bb, int bytes) {
		bb.limit(bb.position());
		bb.position(bytes);
		bb.compact();
	}
}
