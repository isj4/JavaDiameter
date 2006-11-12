package dk.i1.diameter.node;
import dk.i1.diameter.Message;

/**
 * A simple Diameter client that support synchronous request-answer calls.
 * It does not support receiving requests.
 */
public class SimpleSyncClient extends NodeManager {
	private Peer peers[];
	
	/**
	 * Constructor for SimpleSyncClient
	 * @param settings The settings to use for this client
	 * @param peers    The upstream peers to use
	 */
	public SimpleSyncClient(NodeSettings settings, Peer peers[]) {
		super(settings);
		this.peers = peers;
	}
	
	/**
	 * Starts this client. The client must be started before sending
	 * requests. Connections to the configured upstream peers will be
	 * initiated but this method may return before they have been
	 * established.
	 *@see NodeManager#waitForConnection
	 */
	public void start() throws java.io.IOException, UnsupportedTransportProtocolException {
		super.start();
		for(Peer p : peers) {
			node().initiateConnection(p,true);
		}
	}
	
	private static class SyncCall {
		boolean answer_ready;
		Message answer;
	}
	
	/**
	 * Dispatches an answer to threads waiting for it.
	 */
	protected void handleAnswer(Message answer, ConnectionKey answer_connkey, Object state) {
		SyncCall sc = (SyncCall)state;
		synchronized(sc) {
			sc.answer = answer;
			sc.answer_ready = true;
			sc.notify();
		}
	}
	
	/**
	 * Send a request and wait for an answer.
	 * @param    request The request to send
	 * @return The answer to the request. Null if there is no answer (all peers down, or other error)
	 */
	public Message sendRequest(Message request) {
		SyncCall sc = new SyncCall();
		sc.answer_ready = false;
		sc.answer=null;
		
		try {
			sendRequest(request, peers, sc);
			//ok, sent
			synchronized(sc) {
				while(!sc.answer_ready)
					sc.wait();
			}
		} catch(NotRoutableException e) {
			System.out.println("SimpleSyncClient.sendRequest(): not routable");
		} catch(java.lang.InterruptedException e) {
		} catch(NotARequestException e) {
			//just return null
		}
		return sc.answer;
	}
}
