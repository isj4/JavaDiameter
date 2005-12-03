package dk.i1.diameter.node;
import java.nio.ByteBuffer;

class NormalConnectionBuffers extends ConnectionBuffers {
	private ByteBuffer in_buffer;
	private ByteBuffer out_buffer;
	
	NormalConnectionBuffers() {
		in_buffer = ByteBuffer.allocate(8192);
		out_buffer = ByteBuffer.allocate(8192);
	}
	
	ByteBuffer netOutBuffer() {
		return out_buffer;
	}
	ByteBuffer netInBuffer() {
		return in_buffer;
	}
	ByteBuffer appInBuffer() {
		return in_buffer;
	}
	ByteBuffer appOutBuffer() {
		return out_buffer;
	}
	
	void processNetInBuffer() {
	}
	void processAppOutBuffer() {
	}
	
	void makeSpaceInNetInBuffer() {
		in_buffer = makeSpaceInBuffer(in_buffer,4096);
	}
	void makeSpaceInAppOutBuffer(int how_much) {
		out_buffer = makeSpaceInBuffer(out_buffer,how_much);
	}

}
