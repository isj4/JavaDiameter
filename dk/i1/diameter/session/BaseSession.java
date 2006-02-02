package dk.i1.diameter.session;
import dk.i1.diameter.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * A basic implementation of a Diameter session
 * It implements a state model as described in RFC3588 section 8.1, and takes
 * care of generating unique session-ids.
 * <p>
 * Subclasses must override the methods:
 * <ul>
 *   <li>{@link #startAuth}</li>
 *   <li>{@link #startReauth}</li>
 *   <li>{@link #handleAnswer(Message,Object)}.</li>
 * </ul>
 * Subclasses should override the methods:
 * <ul>
 *   <li>{@link #getSessionIdOptionalPart}</li>
 * </ul>
 * Subclasses may want to override the methods:
 * <ul>
 *   <li>{@link #handleRequest(Message)}</li>
 *   <li>{@link #handleSTA(Message)}</li>
 *   <li>{@link #calcNextTimeout} and {@link #handleTimeout}</li>
 *   <li>{@link #newStatePre(State,State,Message,int)}</li>
 *   <li>{@link #newStatePost(State,State,Message,int)}</li>
 *   <li>{@link #collectSTRInfo(Message,int)}</li>
 *   <li>{@link #getDestinationRealm}</li>
 * </ul>
 */
public abstract class BaseSession implements Session {
	private SessionManager session_manager;
	private String session_id;
	/** The state of a session, as per RFC3588 section 8.1 */
	public enum State {
		idle,
		pending,
		open,
		discon
	};
	private State state;
	private int auth_app_id;
	//From AAA:
	private int session_timeout; //seconds
	private boolean state_maintained; //from Auth-Session-State
	//derived timeouts
	private long first_auth_time; //absolute, milliseconds
	protected SessionAuthTimers session_auth_timers;
	
	private boolean auth_in_progress;
	
	/**
	 * Constructor for BaseSession
	 * @param auth_app_id      The authentication application-id that will be reported in AAR and STR requests
	 * @param session_manager  The session manager that manages this session
	 */
	public BaseSession(int auth_app_id, SessionManager session_manager) {
		state = State.idle;
		this.auth_app_id = auth_app_id;
		this.session_manager = session_manager;
		this.session_auth_timers = new SessionAuthTimers();
		state_maintained = true;
	}
	
	/**
	 * @return The session manager that this session uses
	 */
	public final SessionManager sessionManager() {
		return session_manager;
	}
	/**
	 * Returns the session-id of the session. If the session-id has not been
	 * generated yet, null is returned.
	 * @return The session-id, or null
	 */
	public final String sessionId() {
		return session_id;
	}
	/**
	 * Returns the current state of the session. See RFC3588 section 8.1 for details
	 */
	public final State state() {
		return state;
	}
	/**
	 * Retrieve the auth-application-id specified when creating this session.
	 * @return The auth-app-id specified when this session was constructed
	 */
	public final int authAppId() {
		return auth_app_id;
	}
	/**
	 * Determine if authentication/(re-)authorization is currently in progress.
	 * @return true if initial authentication+authorization or re-authorization is in progress.
	 */
	public final boolean authInProgress() {
		return auth_in_progress;
	}
	/**
	 * Update the auth-in-progress flag.
	 */
	protected final void authInProgress(boolean auth_in_progress) {
		this.auth_in_progress = auth_in_progress;
	}
	
	/**
	 * Return whether the server is maintaining state about this sessions.
	 * Derived from Auth-Session-State AVP.
	 * @return true if server maintanis state and STR must be sent
	 */
	public boolean stateMaintained() {
		return state_maintained;
	}
	/**
	 * Specify if server is maintaining state
	 * @param state_maintained If true STR will be sent
	 */
	protected void stateMaintained(boolean state_maintained) {
		this.state_maintained = state_maintained;
	}
	
	/**
	 * Return the time when the session was first authorized.
	 * This is the time when authSuccessful() was first called.
	 * @return The absolute time in milliseconds when the session was first
	 *         authorized. 0 If it never was.
	 */
	public long firstAuthTime() {
		return first_auth_time;
	}
	
	
	//downcalls from sessionmanager
	/**
	 * Handle a request regarding this session.
	 * The BaseSession implementation knows how to handle re-auth-requests and
	 * abort-session requests by calling handleRAR() and handleASR(). If an
	 * unknown command is encountered COMMAND_UNSUPPORTED is returned.
	 * If a subclass implements a diameter application that has additional
	 * server-initiated commands it should override this method.
	 * @param request The request
	 * @return result from handleRAR(), handleASR() or COMMAND_UNSUPPORTED
	 */
	public int handleRequest(Message request) {
		switch(request.hdr.command_code) {
			case ProtocolConstants.DIAMETER_COMMAND_REAUTH:
				return handleRAR(request);
			case ProtocolConstants.DIAMETER_COMMAND_ABORT_SESSION:
				return handleASR(request);
			default:
				return ProtocolConstants.DIAMETER_RESULT_COMMAND_UNSUPPORTED;
		}
	}
	/**
	 * Handle an answer.
	 * The BaseSession implementation knows how to handle session-termination
	 * answers by calling handleSTA(). If an unknown command is encountered a
	 * warning is logged but otherwise ignored.
	 * @param answer The answer message
	 * @param state The state object specified in sendRequest()
	 */
	public void handleAnswer(Message answer, Object state) {
		switch(answer.hdr.command_code) {
			case ProtocolConstants.DIAMETER_COMMAND_SESSION_TERMINATION:
				handleSTA(answer);
				break;
			default:
				session_manager.logger.log(Level.WARNING,"Session '"+session_id+"' could not handle answer (command_code="+answer.hdr.command_code+")");
				break;
		}
	}
	/**
	 * Handle a non-answer.
	 * The BaseSession implementation knows how to deal with missing answers
	 * to session-termination.
	 * @param command_code The command_code from the original request.
	 * @param state The state object specified in sendRequest()
	 */
	public void handleNonAnswer(int command_code, Object state) {
		switch(command_code) {
			case ProtocolConstants.DIAMETER_COMMAND_SESSION_TERMINATION:
				handleSTA(null); //slightly naughty...
				break;
			default:
				session_manager.logger.log(Level.WARNING,"Session '"+session_id+"' could not handle non-answer (command_code="+command_code+")");
				break;
		}
	}
	
	//Misc message-handling methods
	/**
	 * Process an Re-Auth Request.
	 * This implementation starts re-authorization if not already in progress.
	 * @return result-code (success)
	 */
	protected int handleRAR(Message msg) {
		if(!auth_in_progress)
			startReauth();
		return ProtocolConstants.DIAMETER_RESULT_SUCCESS;
	}
	/**
	 * Process an Abort-Session Request.
	 * This implementation will stop the session unconditionally. An STR
	 * will be sent if the server said it kept state.
	 * @return result-code (success)
	 */
	protected int handleASR(Message msg) {
		if(state_maintained) {
			closeSession(ProtocolConstants.DI_TERMINATION_CAUSE_DIAMETER_ADMINISTRATIVE);
		} else {
			//go directly to idle state
			State old_state=state;
			State new_state=State.idle;
			newStatePre(state,new_state,msg,0);
			state=new_state;
			session_manager.unregister(this);
			newStatePost(old_state,state,msg,0);
		}
		return ProtocolConstants.DIAMETER_RESULT_SUCCESS;
	}
	
	/**
	 * Tell BaseSession that (re-)authorization succeeded.
	 * A subclass must call this method when it has received and
	 * successfully processed an authorization-answer.
	 * @param msg Message that caused the success. Can be null.
	 */
	protected void authSuccessful(Message msg) {
		if(state()==State.pending)
			first_auth_time = System.currentTimeMillis();
		State old_state=state;
		State new_state=State.open;
		newStatePre(old_state,new_state,msg,0);
		state=new_state;
		newStatePost(old_state,new_state,msg,0);
		sessionManager().updateTimeouts(this);
	}
	/**
	 * Tell BaseSession that (re-)authorization failed.
	 * The BaseSession implementation closes the session with a suitable termination-cause.
	 * A subclass must call this method when it has not received and
	 * successfully processed an authorization-answer.
	 * @param msg Message that caused the failure. Can be null.
	 */
	protected void authFailed(Message msg) {
		auth_in_progress = false;
		session_manager.logger.log(Level.INFO,"Authentication/Authorization failed, closing session "+session_id);
		if(state()==State.pending)
			closeSession(msg,ProtocolConstants.DI_TERMINATION_CAUSE_DIAMETER_ADMINISTRATIVE);
		else
			closeSession(msg,ProtocolConstants.DI_TERMINATION_CAUSE_DIAMETER_AUTH_EXPIRED);
	}
	
	
	
	/**
	 * Process STA (or lack of STA).
	 * This method is called when an STA has been received, or when an STA
	 * has not been received (broken link/server down)
	 * @param msg The STA message or null
	 */
	public void handleSTA(Message msg) {
		State old_state=state;
		State new_state=State.idle;
		newStatePre(state,new_state,msg,0);
		session_manager.unregister(this);
		state=new_state;
		newStatePost(old_state,new_state,msg,0);
	}
	
	/**
	 * Calculate the next timeout for this session.
	 * The BaseSession calculates this based on session-timeout, auth-lifetime and auth-grace-period.
	 * <p>Example override:
	 <pre>
	 public long calcNextTimeout() <i>//In your session class</i>
	     long timeout = BaseSession.calcNextTimeout();
	     timeout = Math.min(timeout, quota_timeout);
	     return timeout;
	 }
	 </pre>
	 * When overriding this method you should also override handleTimeout().
	 * @return The next timeout, or Long.MAX_VALUE if none
	 */
	public long calcNextTimeout() {
		long timeout = Long.MAX_VALUE;
		if(state==State.open) {
			if(session_timeout!=0)
				timeout = Math.min(timeout, first_auth_time+session_timeout*1000);
			if(!auth_in_progress)
				timeout = Math.min(timeout, session_auth_timers.getNextReauthTime());
			else
				timeout = Math.min(timeout, session_auth_timers.getMaxTimeout());
		}
		return timeout;
	}
	/**
	 * Handle timeout event.
	 * If the session-time (if any) has expired the session is closed.
	 * If the auth-lifetime+auth-grace-period has expired the session is closed.
	 * If the auth-lifetime is near a reauthorization is initiated.
	 */
	public void handleTimeout() {
		if(state==State.open) {
			long now = System.currentTimeMillis();
			if(session_timeout!=0 && now >= first_auth_time+session_timeout*1000) {
				session_manager.logger.log(Level.FINE,"Session-Timeout has expired, closing session");
				closeSession(null,ProtocolConstants.DI_TERMINATION_CAUSE_DIAMETER_SESSION_TIMEOUT);
				return;
			}
			if(now >= session_auth_timers.getMaxTimeout()) {
				session_manager.logger.log(Level.FINE,"authorization-lifetime has expired, closing session");
				closeSession(null,ProtocolConstants.DI_TERMINATION_CAUSE_DIAMETER_AUTH_EXPIRED);
				return;
			}
			if(now >= session_auth_timers.getNextReauthTime()) {
				session_manager.logger.log(Level.FINE,"authorization-lifetime(+grace-period) has expired, sending re-authorization");
				startReauth();
				sessionManager().updateTimeouts(this);
				return;
			}
		}
	}
	
	//downcalls to subclass
	/**
	 * State transition hook.
	 * This method is called before the session changes from one state to another
	 * @param prev_state The current state
	 * @param new_state  The next state
	 * @param msg        The message that caused this transition. May be null if the transition is not caused by a message.
	 * @param cause      The termination cause. 0 if next_state is not discon.
	 */
	public void newStatePre(State prev_state, State new_state, Message msg, int cause) {
	}
	/**
	 * State transition hook.
	 * This method is called after the session changes from one state to another
	 * @param prev_state The previous state
	 * @param new_state  The current (new) state
	 * @param msg        The message that caused this transition. May be null if the transition was not caused by a message.
	 * @param cause      The termination cause. 0 if next_state is not discon.
	 */
	public void newStatePost(State prev_state, State new_state, Message msg, int cause) {
	}
	
	
	/**
	 * Open a session.
	 * Initiate opening a session. If the session is not idle or is
	 * being reused InvalidStateException is thrown. The session does not
	 * switch to state 'open' immediately, but rather the session-specific
	 * authentication/authorization is initiated.
	 * 
	 */
	public void openSession() throws InvalidStateException {
		if(state!=State.idle)
			throw new InvalidStateException("Session cannot be opened unless it is idle");
		if(session_id!=null)
			throw new InvalidStateException("Sessions cannot be reused");
		session_id = makeNewSessionId();
		State new_state=State.pending;
		newStatePre(state,new_state,null,0);
		session_manager.register(this);
		state = new_state;
		newStatePost(State.idle,new_state,null,0);
		startAuth();
	}
	/**
	 * Close a session.
	 * Initiate session tear-down by issuing STR etc. It is harmless to try
	 * to close a session more than once.
	 * @param termination_cause The reason for closing the session. See
	 *                          RFC3588 section 8.15 for details
	 */
	public void closeSession(int termination_cause) {
		closeSession(null,termination_cause);
	}
	/**
	 * Close a session.
	 * @param msg The message that caused this session to close
	 * @param termination_cause The termination-cause for the session.
	 */
	protected void closeSession(Message msg, int termination_cause) {
		switch(state) {
			case idle:
				return;
			case pending:
				newStatePre(State.pending,State.discon,msg,termination_cause);
				sendSTR(termination_cause);
				state = State.discon;
				newStatePost(State.pending,state,msg,termination_cause);
				break;
			case open:
				if(state_maintained) {
					newStatePre(State.open,State.discon,msg,termination_cause);
					sendSTR(termination_cause);
					state = State.discon;
					newStatePost(State.open,state,msg,termination_cause);
				} else {
					newStatePre(State.open,State.idle,msg,termination_cause);
					state = State.idle;
					session_manager.unregister(this);
					newStatePost(State.open,state,msg,termination_cause);
				}
				break;
			case discon:
				return;
		}
	}
	
	
	/**
	 * Start the session by sending the first authentication/authorization.
	 * This method is called by BaseSession when the session is opened.
	 * If there are no authentication/authorization as such for the session
	 * type you implement you can call authSuccessful() immediately.
	 */
	protected abstract void startAuth();
	/**
 	 * Send re-authorization.
	 * This method is called by BaseSession when the authorization-lifetime
	 * expires or when an RAR has been received.
	 */
	protected abstract void startReauth();
	
	
	/**
	 * Update the session-timeout of this session.
	 * @param session_timeout The relative session-time in seconds
	 */
	protected void updateSessionTimeout(int session_timeout) {
		this.session_timeout = session_timeout;
		session_manager.updateTimeouts(this);
	}
	
	private final void sendSTR(int termination_cause) {
		session_manager.logger.log(Level.FINE,"Sending STR for session "+session_id);
		Message str = new Message();
		str.hdr.setRequest(true);
		str.hdr.setProxiable(true);
		str.hdr.application_id = authAppId();
		str.hdr.command_code = ProtocolConstants.DIAMETER_COMMAND_SESSION_TERMINATION;
		collectSTRInfo(str, termination_cause);
		Utils.setMandatory_RFC3588(str);
		try {
			session_manager.sendRequest(str,this,null);
		} catch(dk.i1.diameter.node.NotARequestException e) {
			//never happens
		} catch(dk.i1.diameter.node.NotRoutableException e) {
			//peer unavailable? then we just close the session.
			handleSTA(null);
		}
	}
	/**
	 * Collect information to send in STR message
	 * The BaseSession implementation adds Session-Id, Origin-Host,
	 * Origin-Realm, Destination-Realm, Auth-Application-Id and
	 * Termination-Cause. Subclasses may want to override this to add
	 * application-specific AVPs, such as user-name, calling-station-id, etc.
	 * When overriding this method, the subclass must first call this method, then add its own AVPs.
	 * @param request            The STR message
	 * @param termination_cause  The Termination-Cause for closing the session.
	 */
	protected void collectSTRInfo(Message request, int termination_cause) {
		addCommonStuff(request);
		request.add(new AVP_Unsigned32(ProtocolConstants.DI_AUTH_APPLICATION_ID,authAppId()));
		request.add(new AVP_Unsigned32(ProtocolConstants.DI_TERMINATION_CAUSE,termination_cause));
	}
	
	/**
	 * Get a suitable Destination-Realm value.
	 * This method is called when the session needs a value to put into destination-realm AVP (RFC3588 section 6.6)
	 * The BaseSession just returns the same realm as the realm in the SessionManager's settings.
	 * Subclasses may want to override this and eg. specify the user's realm instead.
	 * @return A suitable destination-realm.
	 */
	protected String getDestinationRealm() {
		return session_manager.settings().realm();
	}
	/**
	 * Get optional part of Session-Id.
	 * A Session-Id consists of a mandatory part and an optional part. A
	 * subclass can override this to provide some information that will be
	 * helpful in debugging in production environments. This implementation
	 * returns null because it does not have any additional useful
	 * information to add. A subclass may want to return eg. user-name or
	 * calling-station-id.
	 * @return null
	 */
	protected String getSessionIdOptionalPart() {
		return null;
	}
	
	
	//utility functions
	
	/**
	 * Extract the Result-Code AVP value from a message
	 * @return Result-Code value, or -1 if something fails.
	 */
	protected static final int getResultCode(Message msg) {
		AVP avp = msg.find(ProtocolConstants.DI_RESULT_CODE);
		if(avp!=null) {
			try {
				return new AVP_Unsigned32(avp).queryValue();
			} catch(dk.i1.diameter.InvalidAVPLengthException e) {
				return -1;
			}
		}
		return -1;
	}
	
	/**
	 * Add session-id, origin-host+realm and destination-realm to a request.
	 * Most Diameter messages have these 4 AVPs. The origin-host and
	 * origin-realm are the ones specified in the NodeManager's settings.
	 * Destination-realm will be the value returned by getDestinationRealm().
	 * @param request The request that should have the 4 AVPs added.
	 */
	public void addCommonStuff(Message request) {
		request.add(new AVP_UTF8String(ProtocolConstants.DI_SESSION_ID,session_id));
		request.add(new AVP_UTF8String(ProtocolConstants.DI_ORIGIN_HOST,session_manager.settings().hostId()));
		request.add(new AVP_UTF8String(ProtocolConstants.DI_ORIGIN_REALM,session_manager.settings().realm()));
		request.add(new AVP_UTF8String(ProtocolConstants.DI_DESTINATION_REALM,getDestinationRealm()));
	}
	
	private final String makeNewSessionId() {
		String mandatory_part = session_manager.settings().hostId() + ";" + second_part();
		String optional_part = getSessionIdOptionalPart();
		if(optional_part==null)
			return mandatory_part;
		else
			return mandatory_part + ";" + optional_part;
	}
	private static final int session_id_high=(int)(System.currentTimeMillis()/1000);
	private static int session_id_low=0;
	private static synchronized String second_part() {
		return new String("" + session_id_high + ";" + session_id_low++);
	}
}
