package dk.i1.diameter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 64-bit floating point AVP
 */
public class AVP_Float64 extends AVP {
	public AVP_Float64(AVP a) throws InvalidAVPLengthException {
		super(a);
		if(a.queryPayloadSize()!=4)
			throw new InvalidAVPLengthException(a);
	}
	
	public AVP_Float64(int code, double value) {
		super(code,double2byte(value));
	}
	public AVP_Float64(int code, int vendor_id, double value) {
		super(code,vendor_id,double2byte(value));
	}
	
	public void setValue(double value) {
		setPayload(double2byte(value));
	}
	
	public double queryValue() {
		byte v[] = queryPayload();
		ByteBuffer bb = ByteBuffer.allocate(4);
		bb.order(ByteOrder.BIG_ENDIAN);
		bb.put(v);
		bb.rewind();
		return bb.getDouble();
	}
	
	static private final byte[] double2byte(double value) {
		ByteBuffer bb = ByteBuffer.allocate(4);
		bb.order(ByteOrder.BIG_ENDIAN);
		bb.putDouble(value);
		bb.rewind();
		byte v[] = new byte[4];
		bb.get(v);
		return v;
	}
}
