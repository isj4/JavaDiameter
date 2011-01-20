package dk.i1.diameter.node;
import javax.net.ssl.*;
import java.nio.ByteBuffer;

import java.util.logging.Logger;
import java.util.logging.Level;

class TLSConnectionBuffers extends ConnectionBuffers {
static Logger logger = Logger.getLogger("dk.i1.diameter.node");
	private ByteBuffer net_in_buffer;
	private ByteBuffer net_out_buffer;
	private ByteBuffer app_in_buffer;
	private ByteBuffer app_out_buffer;
	private SSLEngine ssl_engine;
	
	TLSConnectionBuffers(NormalConnectionBuffers nbconn, SSLContext ssl_context, boolean client_mode) {
		super();
		logger.log(Level.FINEST,"TLSConnectionBuffers()");
		logger.log(Level.FINEST,"nbconn.netInBuffer()=" + nbconn.netInBuffer().toString());
		logger.log(Level.FINEST,"nbconn.appOutBuffer()=" + nbconn.appOutBuffer().toString());
		
		//create&configure ssl engine
		ssl_engine = ssl_context.createSSLEngine();
		if(client_mode) {
			ssl_engine.setUseClientMode(true);
			try {
				ssl_engine.beginHandshake();
			} catch(SSLException ex) {
				throw new Error(ex); //todo
			}
		} else {
			ssl_engine.setUseClientMode(false);
			ssl_engine.setNeedClientAuth(true);
		}

		net_in_buffer =
			ByteBuffer.allocateDirect(ssl_engine.getSession().getPacketBufferSize());
		net_out_buffer =
			ByteBuffer.allocateDirect(ssl_engine.getSession().getPacketBufferSize());
		app_out_buffer =
			ByteBuffer.allocateDirect(ssl_engine.getSession().getApplicationBufferSize());
		app_in_buffer =
			ByteBuffer.allocateDirect(ssl_engine.getSession().getApplicationBufferSize());
		
		nbconn.netInBuffer().flip();
		app_in_buffer.put(nbconn.netInBuffer());
logger.log(Level.FINEST,"app_in_buffer = "+ app_in_buffer.toString());
logger.log(Level.FINEST,"TLSConnectionBuffers: grabbed "+app_in_buffer.position() + " bytes app-in");
		nbconn.appOutBuffer().flip();
		net_out_buffer.put(nbconn.appOutBuffer());
logger.log(Level.FINEST,"TLSConnectionBuffers: grabbed "+net_out_buffer.position() + " bytes net-out");
logger.log(Level.FINEST,"net_out_buffer = "+ net_out_buffer.toString());
		if(ssl_engine.getHandshakeStatus()==SSLEngineResult.HandshakeStatus.NEED_WRAP)
			processAppOutBuffer();
	}

	ByteBuffer netOutBuffer() {
		return net_out_buffer;
	}

	ByteBuffer netInBuffer() {
		return net_in_buffer;
	}

	ByteBuffer appInBuffer() {
		return app_in_buffer;
	}

	ByteBuffer appOutBuffer() {
		return app_out_buffer;
	}

	void processAppOutBuffer()  {
		//reserve space in net output
		net_out_buffer = makeSpaceInBuffer(net_out_buffer,app_out_buffer.position()+4096);
		
		try {
			app_out_buffer.flip();
			SSLEngineResult ser = ssl_engine.wrap(app_out_buffer,net_out_buffer);
logger.log(Level.FINEST,"processAppOutBuffer: consumed="+ser.bytesConsumed()+" produced="+ser.bytesProduced()+" ser="+ser.toString());
			consumeAppOutBuffer(ser.bytesConsumed());
			switch(ser.getStatus()) {
				case OK:
					if(ser.getHandshakeStatus()==SSLEngineResult.HandshakeStatus.NEED_TASK)
						runTasks(); //todo: delegate to other thread
					break;
				case BUFFER_UNDERFLOW:
					//never happens
				case BUFFER_OVERFLOW:
					//never happens as it is coded here
				case CLOSED:
					break;

			}
		} catch(SSLException ex) {
			throw new Error(ex); //todo
		}
	}

	void processNetInBuffer() {
		//todo: reserve space in app_in_buffer
		app_in_buffer = makeSpaceInBuffer(app_in_buffer,net_in_buffer.position());
		retry: for(;;) {
			try {
				net_in_buffer.flip();
				SSLEngineResult ser = ssl_engine.unwrap(net_in_buffer,app_in_buffer);
logger.log(Level.FINEST,"processNetInBuffer: consumed="+ser.bytesConsumed()+" produced="+ser.bytesProduced()+" ser="+ser.toString());
				net_in_buffer.compact();           //consumeNetInBuffer(ser.bytesConsumed());
				switch(ser.getStatus()) {
					case OK:
						if(ser.getHandshakeStatus()==SSLEngineResult.HandshakeStatus.NEED_TASK) {
							runTasks(); //todo: delegate to other thread
							break;
						} else if(ser.getHandshakeStatus()==SSLEngineResult.HandshakeStatus.NEED_WRAP) {
							processAppOutBuffer();
						} else if(ser.getHandshakeStatus()==SSLEngineResult.HandshakeStatus.NEED_UNWRAP &&
						          net_in_buffer.position()!=0) {
							break; //retry read
						}
						break retry;
					case BUFFER_UNDERFLOW:
						//could happen due to partial read. Ok
						break retry;
					case BUFFER_OVERFLOW:
						app_in_buffer = makeSpaceInBuffer(app_in_buffer,app_in_buffer.remaining()+4096);
						break;
					case CLOSED:
						break retry; //todo: do somethign here

				}
			} catch(SSLException ex) {
				throw new Error(ex); //todo
			}
		}
	}

	void makeSpaceInNetInBuffer() {
		net_in_buffer = makeSpaceInBuffer(net_in_buffer, 4096);
	}

	void makeSpaceInAppOutBuffer(int how_much) {
		app_out_buffer = makeSpaceInBuffer(app_out_buffer, how_much);
	}
	
	
	private void runTasks() {
logger.log(Level.FINEST,"runTasks()");
		Runnable r;
		while((r=ssl_engine.getDelegatedTask())!=null) {
			r.run();
		}
logger.log(Level.FINEST,"runTasks():end");
	}
	
	boolean handshakeIsFinished() {
		return ssl_engine.getHandshakeStatus()==SSLEngineResult.HandshakeStatus.FINISHED ||
		       ssl_engine.getHandshakeStatus()==SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;
	}
}
