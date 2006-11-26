import dk.i1.diameter.*;
import dk.i1.diameter.node.*;
import dk.i1.diameter.session.*;

/**
 * Generate load by runing through a lot of sessions.
 * It is meant to be used with the TestSessionServer.
 */
class TestSessionTest2 {
	static int sessions_actually_opened=0;
	public static final void main(String args[]) throws Exception {
		if(args.length!=4) {
			System.out.println("Usage: <peer> <sessions> <rate> <duration>");
			return;
		}
		
		String peer = args[0];
		int sessions = Integer.parseInt(args[1]);
		double rate = Double.parseDouble(args[2]);
		final int session_duration = Integer.parseInt(args[3]);
		
		Capability capability = new Capability();
		capability.addAuthApp(ProtocolConstants.DIAMETER_APPLICATION_NASREQ);
		capability.addAcctApp(ProtocolConstants.DIAMETER_APPLICATION_NASREQ);
		
		NodeSettings node_settings;
		try {
			node_settings  = new NodeSettings(
				"TestSessionTest2.example.net", "example.net",
				99999, //vendor-id
				capability,
				9999,
				"TestSessionTest2", 0x01000000);
		} catch (InvalidSettingException e) {
			System.out.println(e.toString());
			return;
		}
		
		Peer peers[] = new Peer[]{
			new Peer(peer)
			//new Peer(peer,3868,Peer.TransportProtocol.sctp)
		};
		
		
		SessionManager session_manager = new SessionManager(node_settings,peers);
		
		session_manager.start();
		Thread.sleep(2000); //allow connections to be established.
		
		for(int i = 0; i!=sessions; i++) {
			TestSession ts = new TestSession(ProtocolConstants.DIAMETER_APPLICATION_NASREQ,session_manager) {
				public void newStatePost(State prev_state, State new_state, Message msg, int cause) {
					if(new_state==State.open) {
						updateSessionTimeout(session_duration);
						sessions_actually_opened++;
					}
					super.newStatePost(prev_state,new_state,msg,cause);
				}
			};
			ts.openSession();
			Thread.sleep((long)(1000/rate));
		}
		
		Thread.sleep(session_duration*1000+50);
		
		System.out.println("sessions_actually_opened="+sessions_actually_opened);
	}
}
