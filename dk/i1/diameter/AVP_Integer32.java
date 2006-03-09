package dk.i1.diameter;

/**
 * 32-bit signed integer AVP.
 */
public class AVP_Integer32 extends AVP {
	public AVP_Integer32(AVP a) throws InvalidAVPLengthException {
		super(a);
		if(a.queryPayloadSize()!=4)
			throw new InvalidAVPLengthException(a);
	}
	public AVP_Integer32(int code, int value) {
		super(code,int2byte(value));
	}
	public AVP_Integer32(int code, int vendor_id, int value) {
		super(code,vendor_id,int2byte(value));
	}
	public int queryValue() {
		return packunpack.unpack32(payload,0);
	}
	public void setValue(int value) {
		packunpack.pack32(payload,0,value);
	}
	
	static private final byte[] int2byte(int value) {
		byte[] v=new byte[4];
		packunpack.pack32(v,0,value);
		return v;
	}
}
