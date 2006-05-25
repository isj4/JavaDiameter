import dk.i1.diameter.*;
import dk.i1.diameter.node.*;

/**
 * A simple client that issues a CCR (credit-control request)
 */

class cc_test_client {
	public static final void main(String args[]) throws Exception {
		if(args.length!=4) {
			System.out.println("Usage: <host-id> <realm> <peer> <per-port>");
			return;
		}
		
		String host_id = args[0];
		String realm = args[1];
		String dest_host = args[2];
		int dest_port = Integer.parseInt(args[3]);
		
		Capability capability = new Capability();
		capability.addAuthApp(ProtocolConstants.DIAMETER_APPLICATION_CREDIT_CONTROL);
		capability.addAcctApp(ProtocolConstants.DIAMETER_APPLICATION_CREDIT_CONTROL);
		
		NodeSettings node_settings;
		try {
			node_settings  = new NodeSettings(
				host_id, realm,
				99999, //vendor-id
				capability,
				0,
				"cc_test_client", 0x01000000);
		} catch (InvalidSettingException e) {
			System.out.println(e.toString());
			return;
		}
		
		Peer peers[] = new Peer[]{
			new Peer(dest_host,dest_port)
		};
		
		SimpleSyncClient ssc = new SimpleSyncClient(node_settings,peers);
		ssc.start();
		ssc.waitForConnection(); //allow connection to be established.
		
		//Build Credit-Control Request
		// <Credit-Control-Request> ::= < Diameter Header: 272, REQ, PXY >
		Message ccr = new Message();
		ccr.hdr.command_code = ProtocolConstants.DIAMETER_COMMAND_CC;
		ccr.hdr.application_id = ProtocolConstants.DIAMETER_APPLICATION_CREDIT_CONTROL;
		ccr.hdr.setRequest(true);
		ccr.hdr.setProxiable(true);
		//  < Session-Id >
		ccr.add(new AVP_UTF8String(ProtocolConstants.DI_SESSION_ID,ssc.node().makeNewSessionId()));
		//  { Origin-Host }
		//  { Origin-Realm }
		ssc.node().addOurHostAndRealm(ccr);
		//  { Destination-Realm }
		ccr.add(new AVP_UTF8String(ProtocolConstants.DI_DESTINATION_REALM,"example.net"));
		//  { Auth-Application-Id }
		ccr.add(new AVP_Unsigned32(ProtocolConstants.DI_AUTH_APPLICATION_ID,ProtocolConstants.DIAMETER_APPLICATION_CREDIT_CONTROL)); // a lie but a minor one
		//  { Service-Context-Id }
		ccr.add(new AVP_UTF8String(ProtocolConstants.DI_SERVICE_CONTEXT_ID,"cc_test@example.net"));
		//  { CC-Request-Type }
		ccr.add(new AVP_Unsigned32(ProtocolConstants.DI_CC_REQUEST_TYPE,ProtocolConstants.DI_CC_REQUEST_TYPE_EVENT_REQUEST));;
		//  { CC-Request-Number }
		ccr.add(new AVP_Unsigned32(ProtocolConstants.DI_CC_REQUEST_NUMBER,0));
		//  [ Destination-Host ]
		//  [ User-Name ]
		ccr.add(new AVP_UTF8String(ProtocolConstants.DI_USER_NAME,"user@example.net"));
		//  [ CC-Sub-Session-Id ]
		//  [ Acct-Multi-Session-Id ]
		//  [ Origin-State-Id ]
		ccr.add(new AVP_Unsigned32(ProtocolConstants.DI_ORIGIN_STATE_ID,ssc.node().stateId()));
		//  [ Event-Timestamp ]
		ccr.add(new AVP_Time(ProtocolConstants.DI_EVENT_TIMESTAMP,(int)(System.currentTimeMillis()/1000)));
		// *[ Subscription-Id ]
		//  [ Service-Identifier ]
		//  [ Termination-Cause ]
		//  [ Requested-Service-Unit ]
		ccr.add(new AVP_Grouped(ProtocolConstants.DI_REQUESTED_SERVICE_UNIT,
		                        new AVP[] {new AVP_Unsigned64(ProtocolConstants.DI_CC_SERVICE_SPECIFIC_UNITS,42).setM()}
		                       )
		       );
		//  [ Requested-Action ]
		ccr.add(new AVP_Unsigned32(ProtocolConstants.DI_REQUESTED_ACTION,ProtocolConstants.DI_REQUESTED_ACTION_DIRECT_DEBITING));
		// *[ Used-Service-Unit ]
		//  [ Multiple-Services-Indicator ]
		// *[ Multiple-Services-Credit-Control ]
		// *[ Service-Parameter-Info ]
		ccr.add(new AVP_Grouped(ProtocolConstants.DI_SERVICE_PARAMETER_INFO,
		                        new AVP[] {new AVP_Unsigned32(ProtocolConstants.DI_SERVICE_PARAMETER_TYPE,42),
		                                   new AVP_UTF8String(ProtocolConstants.DI_SERVICE_PARAMETER_VALUE,"Hovercraft")
		                                  }
		                       )
		       );
		//  [ CC-Correlation-Id ]
		//  [ User-Equipment-Info ]
		// *[ Proxy-Info ]
		// *[ Route-Record ]
		// *[ AVP ]
		
		Utils.setMandatory_RFC3588(ccr);
		Utils.setMandatory_RFC4006(ccr);
		
		//Send it
		Message cca = ssc.sendRequest(ccr);
		
		//Now look at the result
		if(cca==null) {
			System.out.println("No response");
			return;
		}
		AVP result_code = cca.find(ProtocolConstants.DI_RESULT_CODE);
		if(result_code==null) {
			System.out.println("No result code");
			return;
		}
		try {
			AVP_Unsigned32 result_code_u32 = new AVP_Unsigned32(result_code);
			int rc = result_code_u32.queryValue();
			switch(rc) {
				case ProtocolConstants.DIAMETER_RESULT_SUCCESS:
					System.out.println("Success");
					break;
				case ProtocolConstants.DIAMETER_RESULT_END_USER_SERVICE_DENIED:
					System.out.println("End user service denied");
					break;
				case ProtocolConstants.DIAMETER_RESULT_CREDIT_CONTROL_NOT_APPLICABLE:
					System.out.println("Credit-control not applicable");
					break;
				case ProtocolConstants.DIAMETER_RESULT_CREDIT_LIMIT_REACHED:
					System.out.println("Credit-limit reached");
					break;
				case ProtocolConstants.DIAMETER_RESULT_USER_UNKNOWN:
					System.out.println("User unknown");
					break;
				case ProtocolConstants.DIAMETER_RESULT_RATING_FAILED:
					System.out.println("Rating failed");
					break;
				default:
					//Some other error
					//There are too many to decode them all.
					//We just print the classification
					if(rc>=1000 && rc<1999)
						System.out.println("Informational: " + rc);
					else if(rc>=2000 && rc<2999)
						System.out.println("Success: " + rc);
					else if(rc>=3000 && rc<3999)
						System.out.println("Protocl error: " + rc);
					else if(rc>=4000 && rc<4999)
						System.out.println("Transient failure: " + rc);
					else if(rc>=5000 && rc<5999)
						System.out.println("Permanent failure: " + rc);
					else
						System.out.println("(unknown error class): " + rc);
				
			}
		} catch(InvalidAVPLengthException ex) {
			System.out.println("result-code was illformed");
			return;
		}
		
		//Stop the stack
		ssc.stop();
	}
}
