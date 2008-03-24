package dk.i1.diameter.node;

/**
 * ConnectionTimeout exception.
 * This exception is thrown when {@link dk.i1.diameter.node.Node#waitForConnectionTimeout} or
 * {@link dk.i1.diameter.node.NodeManager#waitForConnectionTimeout} times out.
 */
public class ConnectionTimeoutException extends java.util.concurrent.TimeoutException {
	public ConnectionTimeoutException(String message) {
		super(message);
	}
}
