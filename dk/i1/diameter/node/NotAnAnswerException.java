package dk.i1.diameter.node;

/**
 * A message was not an answer
 * This exception is thrown when trying to send a Message with a sendAnswer() or forwardAnswer()
 * method but the message was marked as a request.
 */
public class NotAnAnswerException extends Exception {
	public NotAnAnswerException() {
	}
}
