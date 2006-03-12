import dk.i1.diameter.*;
import dk.i1.diameter.node.*;
import dk.i1.diameter.session.*;

class TestSessionTest {
	public static final void main(String args[]) throws Exception {
		if(args.length!=1) {
			System.out.println("Usage: <remote server-name>");
			return;
		}
		
		Capability capability = new Capability();
		capability.addAuthApp(ProtocolConstants.DIAMETER_APPLICATION_NASREQ);
		capability.addAcctApp(ProtocolConstants.DIAMETER_APPLICATION_NASREQ);
		
		NodeSettings node_settings;
		try {
			node_settings  = new NodeSettings(
				"TestSessionTest.example.net", "example.net",
				99999, //vendor-id
				capability,
				9999, //3868, //port must be non-zero because we have sessions
				"dk.i1.diameter.session.SessionManager test", 0x01000001);
		} catch (InvalidSettingException e) {
			System.out.println(e.toString());
			return;
		}
		
		Peer peers[] = new Peer[]{
			new Peer(args[0])
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
