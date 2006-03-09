package dk.i1.diameter;
import java.util.*;

/**
 * A Diameter Message.
 * The Message is a container for the {@link MessageHeader} and the {@link AVP}s.
 * It supports converting to/from the on-the-wire format, and
 * manipulating the AVPs. The class is lean and mean, and does as little
 * checking as possible.
 * <p>Example of building a Message:
<pre>
Message msg = new Message();
msg.hdr.application_id = ProtocolConstants.DIAMETER_APPLICATION_ACCOUNTING;
msg.hdr.command_code = ProtocolConstants.DIAMETER_COMMAND_ACCOUNTING;
msg.hdr.setRequest(true);
msg.hdr.setProxiable(true);
//Add AVPs
...
msg.add(new AVP_UTF8String(ProtocolConstants.DI_USER_NAME,"user@example.net"));
msg.add(new AVP_Unsigned64(ProtocolConstants.DI_ACCOUNTING_INPUT_OCTETS,36758373691049));
...
</pre>
 * Example of processing a message:
<pre>
Message msg ...;
for(AVP avp : msg.subset(ProtocolConstants.DI_FRAMED_IP_ADDRESS)) {
    try {
        InetAddress address = new AVP_Address(avp).queryAddress();
	..do something useful with the address...
    } catch(InvalidAVPLengthException ex) {
        .. handle when peer sends garbage
    } catch(InvalidAddressTypeException ex) {
        .. handle when peer sends garbage
    }
}
AVP avp_reply_message = msg.find(ProtocolConstants.DI_REPLY_MESSAGE);
if(avp!=null) {
    ..do something sensible with reply-message
}
</pre>
 */
public class Message {
	/** The message header*/
	public MessageHeader hdr;
	private ArrayList<AVP> avp;
	
	/** The default constructor. The header is initialized to default
	 * values and the AVP list will be empty
	 */
	public Message() {
		hdr = new MessageHeader();
		avp = new ArrayList<AVP>();
	}
	/**
	 * Construct a message with a specific header. The AVP list will be empty.
	 * @param header The message header to use instead of a default one.
	 */
	public Message(MessageHeader header) {
		hdr = new MessageHeader(header);
		avp = new ArrayList<AVP>();
	}
	/**
	 * Copy-constructor.
	 * Implements a deep copy.
	 */
	public Message(Message msg) {
		this(msg.hdr);
		for(AVP a : msg.avp)
			avp.add(new AVP(a));
	}
	
	/**
	 * Calculate the size of the message in on-the-wire format
	 * @return The number of bytes the message will use on-the-wire.
	 */
	public int encodeSize() {
		int sz=0;
		sz += hdr.encodeSize();
		for(AVP a : avp) {
			sz += a.encodeSize();
		}
		return sz;
	}
	/**
	 * Encode the message in on-the-wire format to the specified byte array.
	 * @param b The byte array which must be large enough.
	 */
	public void encode(byte b[]) {
		int sz = encodeSize();
		int offset=0;
		offset += hdr.encode(b,offset,sz);
		for(AVP a : avp) {
			offset += a.encode(b,offset);
		}
	}
	/**
	 * Encode the message to on-the-wire format
	 * @return A on-the-wire message byte array
	 */
	public byte[] encode() {
		int sz = encodeSize();
		byte b[] = new byte[sz];
		int offset=0;
		offset += hdr.encode(b,offset,sz);
		for(AVP a : avp) {
			offset += a.encode(b,offset);
		}
		return b;
	}
	
	/**
	 * Determine the complete size of the message from a on-the-wire byte array.
	 * There must be at least 4 bytes available in the array.
	 * @param b The byte array
	 * @param offset The offset into the byte array where the message is supposed to start.
	 * @return The size (in bytes) of the message
	 */
	public static int decodeSize(byte b[], int offset) {
		int v_ml = packunpack.unpack32(b,offset);
		int v = (v_ml>>24)&0xff;
		int ml = v_ml & 0x00FFFFFF;
		if(v!=1 || ml<20 || (ml%4)!=0)
			return 4; //will cause decode() to fail
		return ml;
	}
	
	/** The decode status from {@link Message#decode} */
	public enum decode_status {
		/** A complete Diameter message was successfully decoded*/
		decoded,
		/** The buffer appears so far to contain a valid message byte there is not enough bytes for it*/
		not_enough,
		/** The buffer cannot possibly contain a Diameter message*/
		garbage
	}
	
	/**
	 * Decode a message from on-the-wire format.
	 * Implemented as return decode(b,0,b.length)
	 * @param b A byte array which consists of exacly 1 message. Superfluous bytes are not permitted.
	 * @return The result for the decode operation.
	 */
	public decode_status decode(byte b[]) {
		return decode(b,0,b.length);
	}
	
	/**
	 * Decode a message from on-the-wire format.
	 * The message is checked to be in valid format and the VPs to be of
	 * the correct length etc. Invalid/reserved bits are not checked.
	 * @param b A byte array possibly containing a Diameter message
	 * @param offset Offset into the array where decoding should start
	 * @param bytes The bytes to try to decode
	 * @return The result for the decode operation.
	 */
	public decode_status decode(byte b[], int offset, int bytes) {
		if(bytes<1)
			return decode_status.not_enough;
		if(packunpack.unpack8(b,offset)!=1)
			return decode_status.garbage;
		if(bytes<4)
			return decode_status.not_enough;
		int sz = decodeSize(b,offset);
		if((sz&3)!=0)
			return decode_status.garbage;
		if(sz<20)
			return decode_status.garbage;
		if(bytes<20)
			return decode_status.not_enough;
		if(sz==-1)
			return decode_status.garbage;
		if(bytes<sz)
			return decode_status.not_enough;
		
		hdr.decode(b,offset);
		if(hdr.version!=1)
			return decode_status.garbage;
		offset += 20; //skip over header
		int bytes_left = bytes - 20;
		int estimated_avp_count = bytes_left/16;
		ArrayList<AVP> new_avps = new ArrayList<AVP>(estimated_avp_count);
		while(bytes_left>0) {
			if(bytes_left<8)
				return decode_status.garbage;
			int avp_sz = AVP.decodeSize(b,offset,bytes_left);
			if(avp_sz==0)
				return decode_status.garbage;
			if(avp_sz>bytes_left)
				return decode_status.garbage;
			
			AVP new_avp = new AVP();
			if(!new_avp.decode(b,offset,avp_sz))
				return decode_status.garbage;
			new_avps.add(new_avp);
			offset += avp_sz;
			bytes_left -= avp_sz;
		}
		if(bytes_left!=0)
			return decode_status.garbage;
		
		avp = new_avps;
		return decode_status.decoded;
	}
	
	/**Return the number of AVPs in the message*/
	public int size() { return avp.size(); }
	/**Ensure that ther is room for at least he specified number of AVPs*/
	public void ensureCapacity(int minCapacity) { avp.ensureCapacity(minCapacity); }
	/**Gets the AVP at the specified index (0-based)*/
	public AVP get(int index) { return new AVP(avp.get(index)); }
	/**Removes all AVPs from the message*/
	public void clear() { avp.clear(); }
	/**Adds an AVP at the end of the AVP list*/
	public void add(AVP avp) { this.avp.add(avp); }
	/**Inserts an AVP at the specified posistion (0-based)*/
	public void add(int index, AVP avp) { this.avp.add(index,avp); }
	/**Removes the AVP at the specified position (0-based)*/
	public void remove(int index) { avp.remove(index); }
	
	
	private class AVPIterator implements Iterator<AVP> {
		private ListIterator<AVP> i;
		private int code;
		private int vendor_id;
		AVPIterator(ListIterator<AVP> i, int code, int vendor_id) {
			this.i = i;
			this.code = code;
			this.vendor_id = vendor_id;
		}
		public void remove() {
			i.remove();
		}
		public boolean hasNext() {
			while(i.hasNext()) {
				AVP a=i.next();
				if(a.code==code &&
				   (vendor_id==0 || a.vendor_id==vendor_id))
				{
					i.previous();
					return true;
				}
			}
			return false;
		}
		public AVP next() {
			return i.next();
		}
	}

	/**Returns an Iterable for the AVPs*/
	public Iterable<AVP> avps() { return avp; }
	/**Returns an iterator for the AVP list*/
	public Iterator<AVP> iterator() { return avp.iterator(); }
	/**Returns an iterator for the AVPs with the specified code*/
	public Iterator<AVP> iterator(int code) { return iterator(code,0); }
	/**Returns an iterator for the AVPs with the specified code and vendor id*/
	public Iterator<AVP> iterator(int code, int vendor_id) {
		return new AVPIterator(avp.listIterator(),code,vendor_id);
	}
	
	/**
	 * Prepare a response the the supplied request.
	 * Implemented as hdr.prepareResponse(request.hdr);
	 * @see MessageHeader#prepareResponse(MessageHeader)
	 */
	public void prepareResponse(Message request) {
		hdr.prepareResponse(request.hdr);
	}
	
	private class Subset implements Iterable<AVP> {
		Message msg;
		int code;
		int vendor_id;
		Subset(Message msg, int code, int vendor_id) {
			this.msg = msg;
			this.code = code;
			this.vendor_id = vendor_id;
		}
		public Iterator<AVP> iterator() {
			return msg.iterator(code,vendor_id);
		}
	}
	
	/**
	 * Returns a iterable subset of the AVPs.
	 * Implemented as <pre>return subset(code,0);</pre>
	 */
	public Iterable<AVP> subset(int code) {
		return subset(code,0);
	}
	/**
	 * Returns a iterable subset of the AVPs.
	 * This is mainly useful for the new-style foreach statement as in
	 * <pre>
	 * for(AVP avp : message.subset(...something...)) {
	 *    //process the AVP
	 * }
	 * </pre>
	 * @param code The AVP code
	 * @return An iterable subset
	 */
	public Iterable<AVP> subset(int code, int vendor_id) {
		return new Subset(this,code,vendor_id);
	}
	
	/**
	 * Finds an AVP with the specified code.
	 * Implemented as <code>find(code,0);</code>
	 */
	public AVP find(int code) {
		return find(code,0);
	}
	/**
	 * Finds an AVP with the specified code/vendor-id.
	 * Returns an AVP with the specified code (and vendor-id) if any. If
	 * there are no AVPs in this message with the specified code/vendor-id
	 * then null is returned.
	 * It is unspecified which AVP is returned if there are multiple matches.
	 * @param code AVP code
	 * @param vendor_id Vendor-ID. Use 0 to specify none.
	 * @return AP with the specified code/vendor-id. Null if not found.
	 */
	public AVP find(int code, int vendor_id) {
		for(AVP a:avp) {
			if(a.code==code && a.vendor_id==vendor_id)
				return a;
		}
		return null;
	}
	
	int find_first(int code) {
		int i=0;
		for(AVP a:avp) {
			if(a.code==code)
				return i;
			i++;
		}
		return -1;
	}
	int count(int code) {
		int i=0;
		for(AVP a:avp) {
			if(a.code==code)
				i++;
		}
		return i;
	}
}
