package dk.i1.diameter.node;

/**
 * A peer hostname was empty.
 * This exception is thrown when trying to construct a {@link Peer} with an
 * empty hostname.
 */
public class EmptyHostNameException extends Exception {
	public EmptyHostNameException() {
	}
	public EmptyHostNameException(String message) {
		super(message);
	}
	public EmptyHostNameException(Throwable cause) {
		super(cause);
	}
}
