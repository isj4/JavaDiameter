package dk.i1.diameter;
import java.lang.reflect.Array;

/**
 * AVP with UTF-8 string payload.
 */
public class AVP_UTF8String extends AVP {
	public AVP_UTF8String(AVP a) {
		super(a);
	}
	public AVP_UTF8String(int code, String value) {
		super(code,string2byte(value));
	}
	public AVP_UTF8String(int code, int vendor_id, String value) {
		super(code,vendor_id,string2byte(value));
	}
	public String queryValue() {
		try {
			return new String(queryPayload(),"UTF-8");
		} catch(java.io.UnsupportedEncodingException e) {
			return null;
		}
	}
	public void setValue(String value) {
		setPayload(string2byte(value));
	}
	
	static private final byte[] string2byte(String value) {
		try {
			return value.getBytes("UTF-8");
		} catch(java.io.UnsupportedEncodingException e) {
			//never happens
		}
		return null;
	}
}
