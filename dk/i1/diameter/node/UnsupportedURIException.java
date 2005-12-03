package dk.i1.diameter.node;

/**
 * Thrown when giving {@link Peer#Peer(URI)} or {@link Peer#fromURIString(String)} an unsupported URI.
 */
public class UnsupportedURIException extends Exception {
	public UnsupportedURIException(String message) {
		super(message);
	}
	public UnsupportedURIException(Throwable cause) {
		super(cause);
	}
}
