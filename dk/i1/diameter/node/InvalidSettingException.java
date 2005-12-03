package dk.i1.diameter.node;

/**
 * Invalid NodeSettings exception.
 * This exception is thrown when {@link NodeSettings} or
 * {@link dk.i1.diameter.session.SessionManager} detects an invalid setting.
 */
public class InvalidSettingException extends Exception {
	public InvalidSettingException(String message) {
		super(message);
	}
}
