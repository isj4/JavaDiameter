package dk.i1.diameter.node;

/**
 * Unsupported transport protocol exception.
 * This exception is thrown when {@link Node#start} is called and one of the
 * mandatory transport protocols are not supported.
 */
public class UnsupportedTransportProtocolException extends Exception {
	public UnsupportedTransportProtocolException(String message) {
		super(message);
	}
	public UnsupportedTransportProtocolException(String message, Throwable ex) {
		super(message,ex);
	}
}
