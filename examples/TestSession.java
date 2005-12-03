import dk.i1.diameter.*;
import dk.i1.diameter.session.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * A quite simple session based on AASession, and supports simple accounting.
 * This class is used by some of the other examples.
 */
public class TestSession extends AASession {
	static private Logger logger = Logger.getLogger("TestSession");
	ACHandler achandler;
	public TestSession(int auth_app_id, SessionManager session_manager) {
		super(auth_app_id,session_manager);
		achandler = new ACHandler(this);
		achandler.acct_application_id = ProtocolConstants.DIAMETER_APPLICATION_NASREQ;
	}
	
	public void handleAnswer(Message answer, Object state) {
		logger.log(Level.FINE,"processing answer");
		switch(answer.hdr.command_code) {
			case ProtocolConstants.DIAMETER_COMMAND_ACCOUNTING:
				achandler.handleACA(answer);
				break;
			default:
				super.handleAnswer(answer,state);
				break;
		}
	}
	
	public void handleNonAnswer(int command_code, Object state) {
		logger.log(Level.FINE,"processing non-answer");
		switch(command_code) {
			case ProtocolConstants.DIAMETER_COMMAND_ACCOUNTING:
				achandler.handleACA(null);
				break;
			default:
				super.handleNonAnswer(command_code,state);
				break;
		}
	}

	protected void collectAARInfo(Message request) {
		super.collectAARInfo(request);
		request.add(new AVP_UTF8String(ProtocolConstants.DI_USER_NAME,"user@example.net"));
	}
	protected boolean processAAAInfo(Message answer) {
		try {
			Iterator<AVP> it=answer.iterator(ProtocolConstants.DI_ACCT_INTERIM_INTERVAL);
			if(it.hasNext()) {
				int interim_interval = new AVP_Unsigned32(it.next()).queryValue();
				if(interim_interval!=0)
					achandler.subSession(0).interim_interval = interim_interval*1000;
			}
		} catch(dk.i1.diameter.InvalidAVPLengthException e) {
			return false;
		}
		return super.processAAAInfo(answer);
	}
	
	public long calcNextTimeout() {
		long t = super.calcNextTimeout();
		if(state()==State.open)
			t = Math.min(t,achandler.calcNextTimeout());
		return t;
	}
	public void handleTimeout() {
		//update acct_session_time
		if(state()==State.open)
			achandler.subSession(0).acct_session_time = System.currentTimeMillis()-firstAuthTime();
		//Then do the timeout handling
		super.handleTimeout();
		achandler.handleTimeout();
	}
	
	
	public void newStatePre(State prev_state, State new_state, Message msg, int cause) {
		logger.log(Level.FINE,"prev="+prev_state+" new="+new_state);
		if(prev_state!=State.discon && new_state==State.discon)
			achandler.stopSession();
	}
	
	public void newStatePost(State prev_state, State new_state, Message msg, int cause) {
		logger.log(Level.FINE,"prev="+prev_state+" new="+new_state);
		if(prev_state!=State.open && new_state==State.open)
			achandler.startSession();
	}
}
