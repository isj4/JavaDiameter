package dk.i1.diameter.session;

public class InvalidStateException extends Exception {
	public InvalidStateException() {
	}
	public InvalidStateException(String message) {
		super(message);
	}
	public InvalidStateException(Throwable cause) {
		super(cause);
	}
}
