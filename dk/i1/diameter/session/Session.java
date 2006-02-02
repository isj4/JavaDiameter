package dk.i1.diameter.session;
import dk.i1.diameter.Message;

/**
 * The session interface is what the {@link SessionManager} operates on
 * @see BaseSession
 */
public interface Session {
	/**
	 * sessionId() is called by the SessionManager (and other classes) to
	 * obtain the Diameter Session-Id of the session. The BaseSession
	 * class implements this by following RFC3588 section 8.8
	 * @return The stable, eternally unique session-id of the session
	 */
	public String sessionId();
	
	/**
	 * This method is called when the SessionManager has received a request
	 * for this session.
	 * @param request The Diameter request for this session.
	 * @return the Diameter result-code (RFC3588 section 7.1)
	 */
	public int handleRequest(Message request);
	
	/**
	 * This method is called when the SessionManager has received an answer
	 * regarding this session.
	 * @param answer The Diameter answer for this session.
	 * @param state The state specified in the {@link SessionManager#sendRequest} call.
	 */
	public void handleAnswer(Message answer, Object state);
	
	/**
	 * This method is called when the SessionManager did not receive an
	 * answer.
	 * @param command_code The command_code in the original request.
	 * @param state The state specified in the {@link SessionManager#sendRequest} call.
	 */
	public void handleNonAnswer(int command_code, Object state);
	
	/**
	 * Calculate the next timeout for this session, if any. This method is
	 * called by the SessionManager at appropriate times in order to
	 * calculate when handleTimeouts() should be called.
	 * @return Next absolute timeout in milliseconds. Long.MAX_VAUE if none.
	 */
	public long calcNextTimeout();
	
	/**
	 * Handle timeouts, if any.
	 * This method is called by the Sessionmanager when it thinks that a
	 * timeout has expired for the session. The session can take any
	 * action it deems appropriate. The method may be called when no
	 * timeouts has expired so the implementation should not get upset
	 * about that.
	 */
	public void handleTimeout();
}
