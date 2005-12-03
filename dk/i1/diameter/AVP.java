package dk.i1.diameter;

/**
 * A Diameter AVP.
 * See RFC3588 section 4 for details.
 * An AVP consists of a code, some flags, an optional vendor ID, and a payload.
 * <p>
 * The payload is not checked for correct size and content until you try to
 * construct one of its subclasses from it. Eg
 * <pre>
 * AVP avp = ...
 * try {
 *     int application_id = new AVP_Unsigned32(avp).queryValue());
 *     ...
 * } catch({@link InvalidAVPLengthException} ex) {
 *     ..
 * }
 * </pre>
 * @see ProtocolConstants
 */
public class AVP {
	byte payload[];
	
	/**The AVP code*/
	public int code;
	/**The flags except the vendor flag*/
	private int flags;
	/**The vendor ID. Assigning directly to this has the delayed effect of of setting/unsetting the vendor flag bit*/
	public int vendor_id;
	
	private static final int avp_flag_vendor        = 0x80;
	private static final int avp_flag_mandatory     = 0x40;
	private static final int avp_flag_private       = 0x20;
	
	/** Default constructor
	 * The code and vendor_id are initialized to 0, no flags are set, and the payload is null.
	 */
	public AVP() {
	}
	/** Copy constructor (deep copy)*/
	public AVP(AVP a) {
		payload = new byte[a.payload.length];
		System.arraycopy(a.payload,0, payload,0, a.payload.length);
		code = a.code;
		flags = a.flags;
		vendor_id = a.vendor_id;
	}
	/**
	 * Create AVP with code and payload
	 * @param code
	 * @param payload
	 */
	public AVP(int code, byte payload[]) {
		this(code,0,payload);
	}
	/**
	 * Create AVP with code and payload
	 * @param code
	 * @param vendor_id
	 * @param payload
	 */
	public AVP(int code, int vendor_id, byte payload[]) {
		this.code = code;
		this.vendor_id = vendor_id;
		this.payload = payload;
	}
	
	static final int decodeSize(byte [] b, int offset, int bytes) {
		if(bytes<8)
			return 0; //garbage
		int flags_and_length = packunpack.unpack32(b,offset+4);
		int flags_ = (flags_and_length>>24)&0xff;
		int length = flags_and_length&0x00FFFFFF;
		int padded_length = (length+3)&~3;
		if((flags_&avp_flag_vendor)!=0) {
			if(length<12)
				return 0; //garbage
		} else {
			if(length<8)
				return 0; //garbage
		}
		return padded_length;
	}
	
	boolean decode(byte [] b, int offset, int bytes) {
		if(bytes<8) return false;
		int i=0;
		code = packunpack.unpack32(b,offset+i);
		i += 4;
		int flags_and_length = packunpack.unpack32(b,offset+i);
		i += 4;
		flags = (flags_and_length>>24)&0xff;
		int length = flags_and_length&0x00FFFFFF;
		int padded_length = (length+3)&~3;
		if(bytes!=padded_length) return false;
		length -= 8;
		if((flags&avp_flag_vendor)!=0) {
			if(length<4) return false;
			vendor_id = packunpack.unpack32(b,offset+i);
			i += 4;
			length -= 4;
		}
		setPayload(b,offset+i,length);
		i += length;
		return true;
	}
	
	int encodeSize() {
		int sz = 4 + 4;
		if(vendor_id!=0)
			sz += 4;
		sz+= (payload.length+3)&~3;
		return sz;
	}
	
	int encode(byte b[], int offset) {
		int sz = 4 + 4;
		if(vendor_id!=0)
			sz += 4;
		sz += payload.length;
		
		int f=flags;
		if(vendor_id!=0)
			f |= avp_flag_vendor;
		else
			f &= ~avp_flag_vendor;

		int i=0;
		
		packunpack.pack32(b,offset+i, code);
		i += 4;
		packunpack.pack32(b,offset+i, sz | (f<<24));
		i += 4;
		if(vendor_id!=0) {
			packunpack.pack32(b,offset+i, vendor_id);
			i += 4;
		}
		
		System.arraycopy(payload,0, b, offset+i, payload.length);
		
		return encodeSize();
	}
	byte[] encode() {
		int sz = 4 + 4;
		if(vendor_id!=0)
			sz += 4;
		sz += payload.length;
		
		int f=flags;
		if(vendor_id!=0)
			f |= avp_flag_vendor;
		else
			f &= ~avp_flag_vendor;

		byte[] b = new byte[encodeSize()];
		
		int i=0;
		
		packunpack.pack32(b,i, code);
		i += 4;
		packunpack.pack32(b,i, sz | (f<<24));
		i += 4;
		if(vendor_id!=0) {
			packunpack.pack32(b,i, vendor_id);
			i += 4;
		}
		
		System.arraycopy(payload,0, b,i, payload.length);
		
		return b;
	}
	
	byte[] queryPayload() {
		byte tmp[] = new byte[payload.length];
		System.arraycopy(payload,0, tmp,0, payload.length);
		return tmp;
	}
	
	int queryPayloadSize() { return payload.length; }
	
	void setPayload(byte[] payload_) {
		setPayload(payload_,0,payload_.length);
	}
	
	void setPayload(byte[] b, int from, int count) {
		byte[] new_payload = new byte[count];
		System.arraycopy(b,from, new_payload,0, count);
		payload = new_payload;
	}
	
	/**Returns if the AVP is vendor-specific (has non-zero vendor_id)*/
	public boolean isVendorSpecific()  { return vendor_id!=0; }
	/**Returns if the mandatory (M) flag is set*/
	public boolean isMandatory()  { return (flags&avp_flag_mandatory)!=0; }
	/**Returns if the private (P) flag is set*/
	public boolean isPrivate() { return (flags&avp_flag_private)!=0; }
	/**Sets/unsets the mandatory (M) flag*/
	public void setMandatory(boolean b) {
		if(b) flags |= avp_flag_mandatory;
		else flags &= ~avp_flag_mandatory;
	}
	/**Sets/unsets the private (P) flag*/
	public void setPrivate(boolean b) {
		if(b) flags |= avp_flag_private;
		else flags &= ~avp_flag_private;
	}
}
