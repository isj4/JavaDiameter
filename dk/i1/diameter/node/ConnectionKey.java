package dk.i1.diameter.node;

/**
 * A connection identifier.
 * ConnectionKey is used by {@link Node} to refer to a specific connection.
 * It can be used for remembering where a request came from then later send
 * an answer to it. If the connection has been lost in the meantime the Node
 * instance will not know that ConnectionKey and reject sending the message.
 * There is nothing interesting in the class except for equals()
 */
public class ConnectionKey {
	static private int i_seq=0;
	static final private synchronized int nextI() {
		return i_seq++;
	}
	private int i;
	public ConnectionKey() {
		i=nextI();
	}
	public int hashCode() { return i; }
	public boolean equals(Object o) {
		if(this==o)
			return true;
		if(o==null || o.getClass()!=this.getClass())
			return false;
		return ((ConnectionKey)o).i==i;
	}
}
