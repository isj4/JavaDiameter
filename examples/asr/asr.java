import dk.i1.diameter.*;
import dk.i1.diameter.node.*;

/**
 * A simple client that issues an ASR (Abort-Session-Request).
 */

class asr {
	public static final void main(String args[]) throws Exception {
		if(args.length!=3) {
			System.out.println("Usage: <peer> <auth-app-id> <session-id>");
			return;
		}
		
		String peer = args[0];
		int auth_app_id;
		if(args[1].equals("nasreq"))
			auth_app_id=ProtocolConstants.DIAMETER_APPLICATION_NASREQ;
		else if(args[1].equals("mobileip"))
			auth_app_id=ProtocolConstants.DIAMETER_APPLICATION_MOBILEIP;
		else
			auth_app_id = Integer.valueOf(args[1]);
		String session_id = args[2];
		String dest_host = args[0];
		String dest_realm = dest_host.substring(dest_host.indexOf('.')+1);
		
		Capability capability = new Capability();
		capability.addAuthApp(auth_app_id);
		
		NodeSettings node_settings;
		try {
			node_settings  = new NodeSettings(
				"somehost.example.net", "example.net",
				99999, //vendor-id
				capability,
				9999,
				"ASR client", 0x01000000);
		} catch (InvalidSettingException e) {
			System.out.println(e.toString());
			return;
		}

		Peer peers[] = new Peer[]{
			new Peer(peer)
		};
		
		SimpleSyncClient ssc = new SimpleSyncClient(node_settings,peers);
		ssc.start();
		Thread.sleep(2000); //allow connections to be established.
		
		//Build ASR
		Message asr = new Message();
		asr.add(new AVP_UTF8String(ProtocolConstants.DI_SESSION_ID,session_id));
		ssc.node().addOurHostAndRealm(asr);
		asr.add(new AVP_UTF8String(ProtocolConstants.DI_DESTINATION_REALM,dest_realm));
		asr.add(new AVP_UTF8String(ProtocolConstants.DI_DESTINATION_HOST,dest_host));
		asr.add(new AVP_Unsigned32(ProtocolConstants.DI_AUTH_APPLICATION_ID,auth_app_id));
		Utils.setMandatory_RFC3588(asr);
		
		//Send it
		Message asa = ssc.sendRequest(asr);
		if(asa==null) {
			System.out.println("No response");
			return;
		}
		
		//look at result-code
		AVP avp_result_code = asa.find(ProtocolConstants.DI_RESULT_CODE);
		if(avp_result_code==null) {
			System.out.println("No result-code in response (?)");
			return;
		}
		int result_code = new AVP_Unsigned32(avp_result_code).queryValue();
		if(result_code!=ProtocolConstants.DIAMETER_RESULT_SUCCESS) {
			System.out.println("Result-code was not success");
			return;
		}
		
		ssc.stop();
	}
}
