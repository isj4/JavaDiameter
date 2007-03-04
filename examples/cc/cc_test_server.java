import dk.i1.diameter.*;
import dk.i1.diameter.node.*;

/**
 * A simple Credit-control server that accepts and grants everything
 */
class cc_test_server extends NodeManager {
	cc_test_server(NodeSettings node_settings) {
		super(node_settings);
	}
	
	public static final void main(String args[]) throws Exception {
		if(args.length<2) {
			System.out.println("Usage: <host-id> <realm> [<port>]");
			return;
		}
		
		String host_id = args[0];
		String realm = args[1];
		int port;
		if(args.length>=3)
			port = Integer.parseInt(args[2]);
		else
			port = 3868;
		
		Capability capability = new Capability();
		capability.addAuthApp(ProtocolConstants.DIAMETER_APPLICATION_CREDIT_CONTROL);
		//capability.addAcctApp(ProtocolConstants.DIAMETER_APPLICATION_CREDIT_CONTROL);
		
		NodeSettings node_settings;
		try {
			node_settings  = new NodeSettings(
				host_id, realm,
				99999, //vendor-id
				capability,
				port,
				"cc_test_server", 0x01000000);
		} catch (InvalidSettingException e) {
			System.out.println(e.toString());
			return;
		}
		
		cc_test_server tss = new cc_test_server(node_settings);
		tss.start();
		
		System.out.println("Hit enter to terminate server");
		System.in.read();
		
		tss.stop(50); //Stop but allow 50ms graceful shutdown
	}
	
	protected void handleRequest(dk.i1.diameter.Message request, ConnectionKey connkey, Peer peer) {
		//this is not the way to do it, but fine for a lean-and-mean test server
		Message answer = new Message();
		answer.prepareResponse(request);
		AVP avp;
		avp = request.find(ProtocolConstants.DI_SESSION_ID);
		if(avp!=null)
			answer.add(avp);
		answer.add(new AVP_Unsigned32(ProtocolConstants.DI_RESULT_CODE,ProtocolConstants.DIAMETER_RESULT_SUCCESS));
		avp = request.find(ProtocolConstants.DI_AUTH_APPLICATION_ID);
		if(avp!=null)
			answer.add(avp);
		avp = request.find(ProtocolConstants.DI_CC_REQUEST_TYPE);
		if(avp!=null)
			answer.add(avp);
		avp = request.find(ProtocolConstants.DI_CC_REQUEST_NUMBER);
		if(avp!=null)
			answer.add(avp);
		avp = request.find(ProtocolConstants.DI_REQUESTED_SERVICE_UNIT);
		if(avp!=null) {
			AVP g = new AVP(avp);
			g.code = ProtocolConstants.DI_GRANTED_SERVICE_UNIT;
			answer.add(avp);
		}
		
		Utils.setMandatory_RFC3588(answer);
		
		try {
			answer(answer,connkey);
		} catch(dk.i1.diameter.node.NotAnAnswerException ex) { }
	}
}
