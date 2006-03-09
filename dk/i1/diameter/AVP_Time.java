package dk.i1.diameter;
import java.util.Date;

/**
 * A timestamp AVP.
 * AVP_Time contains a second count since 1900. You can get the raw second count
 * using {@link AVP_Unsigned32#queryValue} but this class' methods queryDate()
 * and querySecondsSince1970() are probably more useful in your program.
 * <p>
 * Diameter does not have any base AVPs (RFC3588) with finer granularity than
 * seconds.
 */
public class AVP_Time extends AVP_Unsigned32 {
	private static final int seconds_between_1900_and_1970 = ((70*365)+17)*86400;
	
	public AVP_Time(AVP a) throws InvalidAVPLengthException {
		super(a);
	}
	public AVP_Time(int code, Date value) {
		this(code,0,value);
	}
	public AVP_Time(int code, int vendor_id, Date value) {
		super(code, vendor_id, (int)(value.getTime()/1000+seconds_between_1900_and_1970));
	}
	public AVP_Time(int code, int seconds_since_1970) {
		this(code,0,seconds_since_1970);
	}
	public AVP_Time(int code, int vendor_id, int seconds_since_1970) {
		super(code, vendor_id, seconds_since_1970+seconds_between_1900_and_1970);
	}
	public Date queryDate() {
		return new Date(super.queryValue()-seconds_between_1900_and_1970);
	}
	public int querySecondsSince1970() {
		return super.queryValue()-seconds_between_1900_and_1970;
	}
	public void setValue(Date value) {
		super.setValue((int)(value.getTime()/1000+seconds_between_1900_and_1970));
	}
}
