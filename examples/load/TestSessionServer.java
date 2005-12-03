import dk.i1.diameter.*;
import dk.i1.diameter.node.*;
import dk.i1.diameter.session.*;
import java.util.*;

class TestSessionServer extends NodeManager {
	TestSessionServer(NodeSettings node_settings) {
		super(node_settings);
	}
	
	public static final void main(String args[]) throws Exception {
		if(args.length!=1) {
			System.out.println("Usage: <host-id>");
			return;
		}
		Capability capability = new Capability();
		capability.addAuthApp(ProtocolConstants.DIAMETER_APPLICATION_NASREQ);
		capability.addAcctApp(ProtocolConstants.DIAMETER_APPLICATION_NASREQ);
		
		NodeSettings node_settings;
		try {
			node_settings  = new NodeSettings(
				args[0], "example.net",
				99999, //vendor-id
				capability,
				3868,
				"TestSessionServer", 0x01000000);
		} catch (InvalidSettingException e) {
			System.out.println(e.toString());
			return;
		}
		
		TestSessionServer tss = new TestSessionServer(node_settings);
		tss.start();
		
		System.out.println("Hit enter to terminate server");
		System.in.read();
		
		tss.stop();
	}
	
	protected void handleRequest(dk.i1.diameter.Message request, ConnectionKey connkey, Peer peer) {
		//this is not the way to do it, but fine for a lean-and-mean test server
		Message answer = new Message();
		answer.prepareResponse(request);
		AVP avp_session_id = request.find(ProtocolConstants.DI_SESSION_ID);
		if(avp_session_id!=null)
			answer.add(avp_session_id);
		answer.add(new AVP_Unsigned32(ProtocolConstants.DI_RESULT_CODE,ProtocolConstants.DIAMETER_RESULT_SUCCESS));
		AVP avp_auth_app_id = request.find(ProtocolConstants.DI_AUTH_APPLICATION_ID);
		if(avp_auth_app_id!=null)
			answer.add(avp_auth_app_id);
		
		switch(request.hdr.command_code) {
			case ProtocolConstants.DIAMETER_COMMAND_AA:
				//answer.add(new AVP_Unsigned32(ProtocolConstants.DI_AUTHORIZATION_LIFETIME,60));
				break;
		}
		
		Utils.setMandatory_RFC3588(answer);
		
		try {
			answer(answer,connkey);
		} catch(dk.i1.diameter.node.NotAnAnswerException ex) { }
	}
}
