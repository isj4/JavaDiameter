package dk.i1.diameter.node;
import java.util.Random;

class NodeState {
	private int state_id;
	private int end_to_end_identifier;
	
	NodeState() {
		int now = (int)(System.currentTimeMillis()/1000);
		state_id = now;
		end_to_end_identifier = (now<<20) | (new Random().nextInt() & 0x000FFFFF);
	}
	
	public int stateId() {
		return state_id;
	}
	
	public synchronized int nextEndToEndIdentifier() {
		int v = end_to_end_identifier;
		end_to_end_identifier++;
		return v;
	}
}
