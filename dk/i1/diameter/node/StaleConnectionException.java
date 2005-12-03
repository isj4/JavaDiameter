package dk.i1.diameter.node;

/**
 * A reference to a closed connection was detected.
 * This exception is thrown when Node detects a reference to a closed connection.
 */
public class StaleConnectionException extends Exception {
	public StaleConnectionException() {
	}
}
