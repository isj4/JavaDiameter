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
		node().addOurHostAndRealm(answer);
		avp = request.find(ProtocolConstants.DI_CC_REQUEST_TYPE);
		if(avp==null) {
			answerError(answer,connkey,ProtocolConstants.DIAMETER_RESULT_MISSING_AVP,
			            new AVP[] {new AVP_Grouped(ProtocolConstants.DI_FAILED_AVP,new AVP[]{new AVP(ProtocolConstants.DI_CC_REQUEST_TYPE,new byte[]{})})});
			return;
		}
		int cc_request_type=-1;
		try { cc_request_type = new AVP_Unsigned32(avp).queryValue(); } catch(InvalidAVPLengthException ex) {}
		if(cc_request_type!=ProtocolConstants.DI_CC_REQUEST_TYPE_INITIAL_REQUEST &&
		   cc_request_type!=ProtocolConstants.DI_CC_REQUEST_TYPE_UPDATE_REQUEST &&
		   cc_request_type!=ProtocolConstants.DI_CC_REQUEST_TYPE_TERMINATION_REQUEST &&
		   cc_request_type!=ProtocolConstants.DI_CC_REQUEST_TYPE_EVENT_REQUEST)
		{
			answerError(answer,connkey,ProtocolConstants.DIAMETER_RESULT_INVALID_AVP_VALUE,
			            new AVP[] {new AVP_Grouped(ProtocolConstants.DI_FAILED_AVP,new AVP[]{avp})});
			return;
		}
		
		//This test server does not support multiple-services-cc
		avp = request.find(ProtocolConstants.DI_MULTIPLE_SERVICES_CREDIT_CONTROL);
		if(avp!=null) {
			answerError(answer,connkey,ProtocolConstants.DIAMETER_RESULT_INVALID_AVP_VALUE,
			            new AVP[] {new AVP_Grouped(ProtocolConstants.DI_FAILED_AVP,new AVP[]{avp})});
			return;
		}
		avp = request.find(ProtocolConstants.DI_MULTIPLE_SERVICES_INDICATOR);
		if(avp!=null) {
			int indicator=-1;
			try { indicator=new AVP_Unsigned32(avp).queryValue(); } catch(InvalidAVPLengthException ex) {}
			if(indicator!=ProtocolConstants.DI_MULTIPLE_SERVICES_INDICATOR_MULTIPLE_SERVICES_NOT_SUPPORTED) {
				answerError(answer,connkey,ProtocolConstants.DIAMETER_RESULT_INVALID_AVP_VALUE,
				           new AVP[] {new AVP_Grouped(ProtocolConstants.DI_FAILED_AVP,new AVP[]{avp})});
				return;
			}
		}
		
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
		
		switch(cc_request_type) {
			case ProtocolConstants.DI_CC_REQUEST_TYPE_INITIAL_REQUEST:
			case ProtocolConstants.DI_CC_REQUEST_TYPE_UPDATE_REQUEST:
			case ProtocolConstants.DI_CC_REQUEST_TYPE_TERMINATION_REQUEST:
				//grant whatever is requested
				avp = request.find(ProtocolConstants.DI_REQUESTED_SERVICE_UNIT);
				if(avp!=null) {
					AVP g = new AVP(avp);
					g.code = ProtocolConstants.DI_GRANTED_SERVICE_UNIT;
					answer.add(avp);
				}
				break;
			case ProtocolConstants.DI_CC_REQUEST_TYPE_EVENT_REQUEST: {
				//examine requested-action
				avp = request.find(ProtocolConstants.DI_REQUESTED_ACTION);
				if(avp==null) {
					answerError(answer,connkey,ProtocolConstants.DIAMETER_RESULT_MISSING_AVP,
					            new AVP[] {new AVP_Grouped(ProtocolConstants.DI_FAILED_AVP,new AVP[]{new AVP(ProtocolConstants.DI_REQUESTED_ACTION,new byte[]{})})});
					return;
				}
				int requested_action=-1;
				try { requested_action = new AVP_Unsigned32(avp).queryValue(); } catch(InvalidAVPLengthException ex) {}
				switch(requested_action) {
					case ProtocolConstants.DI_REQUESTED_ACTION_DIRECT_DEBITING:
						//nothing. just indicate success
						break;
					case ProtocolConstants.DI_REQUESTED_ACTION_REFUND_ACCOUNT:
						//nothing. just indicate success
						break;
					case ProtocolConstants.DI_REQUESTED_ACTION_CHECK_BALANCE:
						//report back that the user has sufficient balance
						answer.add(new AVP_Unsigned32(ProtocolConstants.DI_CHECK_BALANCE_RESULT, ProtocolConstants.DI_DI_CHECK_BALANCE_RESULT_ENOUGH_CREDIT));
						break;
					case ProtocolConstants.DI_REQUESTED_ACTION_PRICE_ENQUIRY:
						//report back a price of DKK42.17 per kanelsnegl
						answer.add(new AVP_Grouped(ProtocolConstants.DI_COST_INFORMATION,
						                           new AVP[] { new AVP_Grouped(ProtocolConstants.DI_UNIT_VALUE,
						                                                       new AVP[] { new AVP_Integer64(ProtocolConstants.DI_VALUE_DIGITS,4217),
						                                                                   new AVP_Integer32(ProtocolConstants.DI_EXPONENT,-2)
						                                                                 }
						                                                      ),
						                                       new AVP_Unsigned32(ProtocolConstants.DI_CURRENCY_CODE, 208),
						                                       new AVP_UTF8String(ProtocolConstants.DI_COST_UNIT, "kanelsnegl")
						                                     }
						                          )
						          );
						break;
					default: {
						answerError(answer,connkey,ProtocolConstants.DIAMETER_RESULT_INVALID_AVP_VALUE,
						            new AVP[] {new AVP_Grouped(ProtocolConstants.DI_FAILED_AVP,new AVP[]{avp})});
						return;
					}
				}
			}
		}
		
		Utils.setMandatory_RFC3588(answer);
		
		try {
			answer(answer,connkey);
		} catch(dk.i1.diameter.node.NotAnAnswerException ex) { }
	}
	
	void answerError(dk.i1.diameter.Message answer, ConnectionKey connkey, int result_code, AVP [] error_avp) {
		answer.hdr.setError(true);
		answer.add(new AVP_Unsigned32(ProtocolConstants.DI_RESULT_CODE,result_code));
		for(AVP avp:error_avp)
			answer.add(avp);
		try {
			answer(answer,connkey);
		} catch(dk.i1.diameter.node.NotAnAnswerException ex) { }
	}
}
