package dk.i1.diameter.node;

/**
 * A message was not a request.
 * This exception is thrown when trying to send a Message with a sendRequest()
 * method but the message was not marked as a request.
 * You probably forgot {@link dk.i1.diameter.MessageHeader#setRequest}.
 */
public class NotARequestException extends Exception {
	public NotARequestException() {
	}
}
