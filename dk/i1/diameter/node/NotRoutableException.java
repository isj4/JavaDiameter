package dk.i1.diameter.node;

/**
 * A message was not routable.
 * This exception is thrown when NodeManager could not send a request either
 * because no connection(s) was available or because no available peers
 * supported the message.
 */
public class NotRoutableException extends Exception {
	public NotRoutableException() {
	}
	public NotRoutableException(String message) {
		super(message);
	}
	public NotRoutableException(Throwable cause) {
		super(cause);
	}
}
