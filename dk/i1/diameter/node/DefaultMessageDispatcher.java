package dk.i1.diameter.node;
import dk.i1.diameter.Message;

class DefaultMessageDispatcher implements MessageDispatcher {
	public boolean handle(Message msg, ConnectionKey connkey, Peer peer) {
		return false;
	}
}
