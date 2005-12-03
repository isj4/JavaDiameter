import dk.i1.diameter.*;
import dk.i1.diameter.node.*;
import dk.i1.diameter.session.*;

class TestSessionTest {
	public static final void main(String args[]) throws Exception {
		Capability capability = new Capability();
		capability.addAuthApp(ProtocolConstants.DIAMETER_APPLICATION_NASREQ);
		capability.addAcctApp(ProtocolConstants.DIAMETER_APPLICATION_NASREQ);
		
		NodeSettings node_settings;
		try {
			node_settings  = new NodeSettings(
				"java.isjsys.i1.dk", "i1.dk",
				6918, //vendor-id
				capability,
				0, //3868,
				"dk.i1.diameter.server", 0x01000000);
		} catch (InvalidSettingException e) {
			System.out.println(e.toString());
			return;
		}
		
		Peer peers[] = new Peer[]{
			new Peer("isjsys.int.i1.dk")
		};
		
		
		SessionManager session_manager = new SessionManager(node_settings,peers);
		
		session_manager.start();
		Thread.sleep(500);
		
		BaseSession session = new TestSession(ProtocolConstants.DIAMETER_APPLICATION_NASREQ, session_manager);
		
		session.openSession();
		System.out.println("Session state: " + session.state());
		
		Thread.sleep(100000);
		
		System.out.println("Session state: " + session.state());
	}
}
