package dk.i1.diameter.session;
import dk.i1.diameter.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * A utility class for dealing with accounting.
 * It supports sub-sessions, interim accounting and other common stuff.
 * It can be used for incorporating into session classes. The session must dispatch ACAs to it.
 * The class uses the sub-session 0 for the session itself and it is always present.
 * It is the responsibility of the user to update the the usage data in the
 * SubSession instances and/or override the collectACRInfo() method.
 * Acct-Realtime-Required semantics are not directly supported.
 */
public class ACHandler {
	private BaseSession base_session;
	private long subsession_sequencer;
	private int accounting_record_number;
	/**The acct-multi-session-id to include in ACRs, if any*/
	public String acct_multi_session_id;
	/**The acct-application-id to include in ACRs. If not set, then collectACRInfo() must be overridden to add a vendor-specific-application AVP*/
	public Integer acct_application_id;
	
	/**
	 * A collection of data belonging to a (sub-)session.
	 * The user of the ACHandler class is supposed to update the acct_*
	 * fields either before calling {@link ACHandler#handleTimeout}, {@link ACHandler#startSubSession},
	 * {@link ACHandler#stopSubSession}, {@link ACHandler#stopSession} and {@link ACHandler#sendEvent}; or whenever new
	 * usage information is received for the user.
	 */
	public static class SubSession {
		final long subsession_id;
		boolean start_sent;
		public long interim_interval;
		long next_interim;
		int most_recent_record_number;
		/** The accounting session-time, in milliseconds. Can be null */
		public Long acct_session_time;
		/** The number of octets received from the user. Can be null */
		public Long acct_input_octets;
		/** The number of octets sent to the user. can be null */
		public Long acct_output_octets;
		/** The number of packets received from the user. Can be null */
		public Long acct_input_packets;
		/** The number of packets sent to the user. can be null */
		public Long acct_output_packets;
		
		SubSession(long ss_id) {
			subsession_id = ss_id;
			interim_interval = Long.MAX_VALUE;
			next_interim = Long.MAX_VALUE;
			most_recent_record_number = -1;
		}
	}
	private Map<Long,SubSession> subsessions;
	
	/**
	 * Constructor for ACHandler
	 * @param base_session The BaseSession (or subclass thereof) for which
	 *                     the accounting should be produced.
	 */
	public ACHandler(BaseSession base_session) {
		this.base_session = base_session;
		accounting_record_number = 0;
		subsessions = new HashMap<Long,SubSession>();
		subsession_sequencer = 0;
		subsessions.put(subsession_sequencer,new SubSession(subsession_sequencer++));
	}
	/**
	 * Calculate the next time that handleTimeouts() should be called.
	 * The timeout is calcualted based on the earliest timeout of interim
	 * for any of the subsessions
	 */
	public long calcNextTimeout() {
		long t = Long.MAX_VALUE;
		for(Map.Entry<Long,SubSession> e : subsessions.entrySet()) {
			SubSession ss = e.getValue();
			t = Math.min(t,ss.next_interim);
		}
		return t;
	}
	/**
	 * Process timeouts, if any.
	 * Accounting-interim requests may get sent.
	 */
	public void handleTimeout() {
		long now = System.currentTimeMillis();
		for(Map.Entry<Long,SubSession> e : subsessions.entrySet()) {
			SubSession ss = e.getValue();
			if(ss.next_interim<=now) {
				sendInterim(ss);
			}
		}
	}
	
	/**
	 * Creates a sub-session.
	 * Creates a subsession with a unique sub-session-id. It is the
	 * responsibility of the caller call startSubSession() afterward.
	 * The Sub-session is not automatically started.
	 * @return ID of the sub-session
	 */
	public long createSubSession() {
		SubSession ss = new SubSession(subsession_sequencer++);
		subsessions.put(ss.subsession_id,ss);
		return ss.subsession_id;
	}
	/**
	 * Retrieve a sub-session by id
	 * @param subsession_id The sub-session id
	 * @return The sub-session, or null if not found.
	 */
	public SubSession subSession(long subsession_id) {
		return subsessions.get(subsession_id);
	}
	/**
	 * Start sub-session accounting for the specified sub-session.
	 * This will result in the ACR start-record being sent.
	 * @param subsession_id The sub-session id
	 */
	public void startSubSession(long subsession_id) {
		if(subsession_id==0) return;
		SubSession ss = subSession(subsession_id);
		if(ss==null) return;
		if(ss.start_sent) return; //already started
		sendStart(ss);
	}
	/**
	 * Stop a sub-session.
	 * The sub-session is stopped (accounting-stop ACR will be generated)
	 * and the sub-session will be removed.
	 * @param subsession_id The sub-session id
	 */
	public void stopSubSession(long subsession_id) {
		if(subsession_id==0) return;
		SubSession ss = subSession(subsession_id);
		if(ss==null) return;
		sendStop(ss);
		subsessions.remove(ss.subsession_id);
	}
	
	/**
	 * Start accounting for the session
	 * This will result in the ACR start-record being sent.
	 */
	public void startSession() {
		SubSession ss = subSession(0);
		if(ss.start_sent) return;
		sendStart(ss);
	}
	/**
	 * Stop accounting.
	 * Stop accounting by sending ACRs (stop records) for all sub-sessions
	 * and deleting them, and then finally sending a ACR stop-record for the
	 * whole session.
	 */
	public void stopSession() {
		//Sending stop records for the sub sessions is not strictly needed but nice anyway
		for(Map.Entry<Long,SubSession> e : subsessions.entrySet()) {
			if(e.getValue().subsession_id==0) continue;
			sendStop(e.getValue());
		}
		SubSession ss = subSession(0);
		sendStop(ss);
		subsessions.clear();
	}
	
	
	/**
	 * Send an event record for the whole session.
	 * Implemented as sendEvent(0,null)
	 */
	public void sendEvent() {
		sendEvent(0,null);
	}
	/**
	 * Send an event record for the whole session with an additional set of AVPs
	 * Implemented as sendEvent(0,null)
	 */
	public void sendEvent(AVP avps[]) {
		sendEvent(0,avps);
	}
	/**
	 * Send an event record for the sub-session with an additional set of AVPs
	 * Implemented as sendEvent(subsession_id,null)
	 */
	public void sendEvent(long subsession_id) {
		sendEvent(subsession_id,null);
	}
	/**
	 * Send an event record for the sub-session with an additional set of AVPs
	 * collectACR() will be called and the AVPs will then be added to the ACR, and then sent.
	 */
	public void sendEvent(long subsession_id, AVP avps[]) {
		SubSession ss = subSession(0);
		if(ss==null) return; //todo: exception
		sendEvent(ss,avps);
	}
	
	private void sendStart(SubSession ss) {
		sendACR(makeACR(ss,ProtocolConstants.DI_ACCOUNTING_RECORD_TYPE_START_RECORD));
		if(ss.interim_interval!=Long.MAX_VALUE)
			ss.next_interim = System.currentTimeMillis()+ss.interim_interval;
		else
			ss.next_interim = Long.MAX_VALUE;
	}
	private void sendInterim(SubSession ss) {
		sendACR(makeACR(ss,ProtocolConstants.DI_ACCOUNTING_RECORD_TYPE_INTERIM_RECORD));
		if(ss.interim_interval!=Long.MAX_VALUE)
			ss.next_interim = System.currentTimeMillis()+ss.interim_interval;
		else
			ss.next_interim = Long.MAX_VALUE;
	}
	private void sendStop(SubSession ss) {
		sendACR(makeACR(ss,ProtocolConstants.DI_ACCOUNTING_RECORD_TYPE_STOP_RECORD));
	}
	private void sendEvent(SubSession ss, AVP avps[]) {
		Message acr = makeACR(ss,ProtocolConstants.DI_ACCOUNTING_RECORD_TYPE_EVENT_RECORD);
		if(avps!=null) {
			for(AVP a : avps)
				acr.add(a);
		}
		sendACR(acr);
	}
	
	private Message makeACR(SubSession ss, int record_type) {
		Message acr = new Message();
		acr.hdr.setRequest(true);
		acr.hdr.setProxiable(true);
		acr.hdr.application_id = base_session.authAppId();
		acr.hdr.command_code = ProtocolConstants.DIAMETER_COMMAND_ACCOUNTING;
		collectACRInfo(acr, ss, record_type);
		Utils.setMandatory_RFC3588(acr);
		return acr;
	}
	
	/**
	 * Process an ACA
	 */
	public void handleACA(Message answer) {
		//todo: handle acct-realtime-required
		
		if(answer==null) return;
		
		try {
			Iterator<AVP> it;
			it = answer.iterator(ProtocolConstants.DI_ACCOUNTING_RECORD_NUMBER);
			if(!it.hasNext()) {
				//Should probably complain about server not following RFC3588 section 9.7.2..
				return;
			}
			//locate (sub-)session from record number
			int record_number = new AVP_Unsigned32(it.next()).queryValue();
			for(Map.Entry<Long,SubSession> e : subsessions.entrySet()) {
				if(e.getValue().most_recent_record_number==record_number) {
					//clear record number
					e.getValue().most_recent_record_number = -1;
					return;
				}
			}
		} catch(dk.i1.diameter.InvalidAVPLengthException e) {
			//should probably complain here too
		}
	}
	
	private void sendACR(Message acr) {
		try {
			base_session.sessionManager().sendRequest(acr,base_session,null);
		} catch(dk.i1.diameter.node.NotARequestException ex) {
			//never happens
		} catch(dk.i1.diameter.node.NotRoutableException ex) {
			base_session.sessionManager().logger.log(Level.INFO,"Could not send ACR for session "+base_session.sessionId()+" :"+ex.toString());
			//peer unavailable?
			handleACA(null);
		}
	}
	
	/**
	 * Collect information and put it into an ACR.
	 * This implementation puts the following AVPs into the ACR:
	 *   session-id, origin-host, origin-realm, destination-realm, acct-record-type
	 *   accounting-record-number, acct-application-id (unless null),
	 *   accounting-sub-session-id (unless it is for the whole session),
	 *   acct-interim-interval (maybe), event-timestamp.
	 * For interim and stop records the following AVPs are added (if non-null)
	 *   acct-session-time, accounting-input-octets, accounting-output-octets,
	 *   accounting-input-packets, accounting-output-packets
	 * <p>
	 * Subclasses probably want to override this to add session-type specific AVPs.
	 *
	 * @param acr The ACR message being constructed
	 * @param ss The sub-session
	 * @param record_type The record type (start/interim/stop/event)
	 */
	public void collectACRInfo(Message acr, SubSession ss, int record_type) {
		base_session.addCommonStuff(acr);
		
		acr.add(new AVP_Unsigned32(ProtocolConstants.DI_ACCOUNTING_RECORD_TYPE,record_type));
		
		accounting_record_number++;
		acr.add(new AVP_Unsigned32(ProtocolConstants.DI_ACCOUNTING_RECORD_NUMBER,accounting_record_number));
		ss.most_recent_record_number = accounting_record_number;
		
		if(acct_application_id!=null)
			acr.add(new AVP_Unsigned32(ProtocolConstants.DI_ACCT_APPLICATION_ID,acct_application_id));
		
		if(ss.subsession_id!=0)
			acr.add(new AVP_Unsigned64(ProtocolConstants.DI_ACCOUNTING_SUB_SESSION_ID,ss.subsession_id));
		
		if(acct_multi_session_id!=null)
			acr.add(new AVP_UTF8String(ProtocolConstants.DI_ACCT_MULTI_SESSION_ID,acct_multi_session_id));
		
		if(ss.interim_interval!=Long.MAX_VALUE)
			acr.add(new AVP_Unsigned32(ProtocolConstants.DI_ACCT_INTERIM_INTERVAL,(int)(ss.interim_interval/1000)));
		
		acr.add(new AVP_Time(ProtocolConstants.DI_EVENT_TIMESTAMP,(int)(System.currentTimeMillis()/1000)));
		
		//and now for the actual usage information
		if(record_type!=ProtocolConstants.DI_ACCOUNTING_RECORD_TYPE_START_RECORD) {
			if(ss.acct_session_time!=null)
				acr.add(new AVP_Unsigned32(ProtocolConstants.DI_ACCT_SESSION_TIME,(int)(ss.acct_session_time/1000)));
			if(ss.acct_input_octets!=null)
				acr.add(new AVP_Unsigned64(ProtocolConstants.DI_ACCOUNTING_INPUT_OCTETS,ss.acct_input_octets));
			if(ss.acct_output_octets!=null)
				acr.add(new AVP_Unsigned64(ProtocolConstants.DI_ACCOUNTING_OUTPUT_OCTETS,ss.acct_output_octets));
			if(ss.acct_input_packets!=null)
				acr.add(new AVP_Unsigned64(ProtocolConstants.DI_ACCOUNTING_INPUT_PACKETS,ss.acct_input_packets));
			if(ss.acct_output_packets!=null)
				acr.add(new AVP_Unsigned64(ProtocolConstants.DI_ACCOUNTING_OUTPUT_PACKETS,ss.acct_output_packets));
		}
	}
}
