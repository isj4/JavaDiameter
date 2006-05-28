package dk.i1.diameter.node;
import java.util.*;
import dk.i1.diameter.ProtocolConstants;

/**
 * A bag of supported/allowed applications.
 * A Capability instance is used by the {@link Node} class to announce this node's
 * capabilities in terms of supported/allowed authentication/authorization/accounting
 * applications, vendor-specific applications and vendor-IDs. It is also used by the
 * Node to catch messages that belong to applications that have not been announced or
 * is being sent to peers that do not support it.
 * <p>
 * An application is set of message commands with defined semantics. Examples are
 * NASREQ (mostly dial-up sessions), MOBILEIP (mobile IPv4/IPv6 and asociated
 * roaming), EAP (extensible authentication). Each aplication can be used for
 * authentication/authorization, accounting, or both.
 * Each diameter message identifies which application it belongs to. Messages
 * belonging to applications that have not been negotiated are rejected.
 * Special rules applies to the "common" application (low-level diameter control
 * message) and peers that announce the "relay" application.
 * <p>The announced vendor-IDs can be used for implementing tweaks in areas not
 * fully specified in the application specification.
 * <p>
 * {@link dk.i1.diameter.ProtocolConstants} contains definitions for some of the standard applications (DIAMETER_APPLICATION_....)
 * <p>
 * A hypothetical node that supports NASREQ and EAP could create the capability
 * set like this:
 * <pre>
Capability cap = new Capability();
cap.addSupportedVendor(<i>a vendor-id</i>);
cap.addSupportedVendor(<i>another vendor-id</i>);
cap.addAuthApp(ProtocolConstants.DIAMETER_APPLICATION_NASREQ);
cap.addAcctApp(ProtocolConstants.DIAMETER_APPLICATION_NASREQ);
cap.addAuthApp(ProtocolConstants.DIAMETER_APPLICATION_EAP);
 * </pre>
 * A hypothetical node that only supports a vendor-specific accounting
 * extension could create the capability set like this:
 * <pre>
static final int our_vendor_id = ...;
static final int our_application_id = ...;
Capability cap = new Capability();
cap.addSupportedVendor(our_vendor_id);
cap.addVendorAcctApp(our_vendor_id,our_application_id);
</pre>
 */
public class Capability {
	static class VendorApplication {
		public int vendor_id;
		public int application_id;
		public VendorApplication(int vendor_id, int application_id) {
			this.vendor_id = vendor_id;
			this.application_id = application_id;
		}
		public int hashCode() {
			return vendor_id + application_id;
		}
		public boolean equals(Object obj) {
			return ((VendorApplication)obj).vendor_id == vendor_id &&
			       ((VendorApplication)obj).application_id == application_id;
		}
	}
	
	Set<Integer> supported_vendor;
	Set<Integer> auth_app;
	Set<Integer> acct_app;
	Set<VendorApplication> auth_vendor;
	Set<VendorApplication> acct_vendor;
	
	/**Constructor.
	 * The instance is initialized to be empty.
	 */
	public Capability() {
		supported_vendor = new HashSet<Integer>();
		auth_app = new HashSet<Integer>();
		acct_app = new HashSet<Integer>();
		auth_vendor = new HashSet<VendorApplication>();
		acct_vendor = new HashSet<VendorApplication>();
	}
	/**Copy-Constructor (deep copy).
	 */
	public Capability(Capability c) {
		supported_vendor = new HashSet<Integer>();
		for(Integer i:c.supported_vendor)
			supported_vendor.add(i);
		auth_app = new HashSet<Integer>();
		for(Integer i:c.auth_app)
			auth_app.add(i);
		acct_app = new HashSet<Integer>();
		for(Integer i:c.acct_app)
			acct_app.add(i);
		auth_vendor = new HashSet<VendorApplication>();
		for(VendorApplication va:c.auth_vendor)
			auth_vendor.add(va);
		acct_vendor = new HashSet<VendorApplication>();
		for(VendorApplication va:c.acct_vendor)
			acct_vendor.add(va);
	}
	
	/**Returns if the specified vendor ID is supported*/
	public boolean isSupportedVendor(int vendor_id) {
		return supported_vendor.contains(vendor_id);
	}
	/**
	 * Returns if the specified application is an allowed auth-application.
	 * If the application "relay" is listen, then the auth-application is always allowed.
	 */
	public boolean isAllowedAuthApp(int app) {
		return auth_app.contains(app) ||
		       auth_app.contains(ProtocolConstants.DIAMETER_APPLICATION_RELAY);
	}
	/**
	 * Returns if the specified application is an allowed auth-application.
	 * If the application "relay" is listen, then the auth-application is always allowed.
	 */
	public boolean isAllowedAcctApp(int app) {
		return acct_app.contains(app) ||
		       acct_app.contains(ProtocolConstants.DIAMETER_APPLICATION_RELAY);
	}
	/**
	 * Returns if the specified vendor-specific application is an allowed auth-application.
	 */
	public boolean isAllowedAuthApp(int vendor_id, int app) {
		return auth_vendor.contains(new VendorApplication(vendor_id,app));
	}
	/**
	 * Returns if the specified vendor-specific application is an allowed auth-application.
	 */
	public boolean isAllowedAcctApp(int vendor_id, int app) {
		return acct_vendor.contains(new VendorApplication(vendor_id,app));
	}
	
	public void addSupportedVendor(int vendor_id) {
		supported_vendor.add(vendor_id);
	}
	public void addAuthApp(int app) {
		auth_app.add(app);
	}
	public void addAcctApp(int app) {
		acct_app.add(app);
	}
	public void addVendorAuthApp(int vendor_id, int app) {
		auth_vendor.add(new VendorApplication(vendor_id,app));
	}
	public void addVendorAcctApp(int vendor_id, int app) {
		acct_vendor.add(new VendorApplication(vendor_id,app));
	}
	
	/**
	 * Returns if no applications are allowed/supported
	 */
	public boolean isEmpty() {
		return auth_app.isEmpty() &&
		       acct_app.isEmpty() &&
		       auth_vendor.isEmpty() &&
		       acct_vendor.isEmpty();
	}
	
	/**
	 * Create a capability intersection.
	 */
	static Capability calculateIntersection(Capability us, Capability peer) {
		//assumption: we are not a relay
		Capability c = new Capability();
		for(Integer vendor_id : peer.supported_vendor) {
			if(us.isSupportedVendor(vendor_id))
				c.addSupportedVendor(vendor_id);
		}
		
		for(Integer app : peer.auth_app) {
			if(app==ProtocolConstants.DIAMETER_APPLICATION_RELAY ||
			   us.auth_app.contains(app) ||
			   us.auth_app.contains(ProtocolConstants.DIAMETER_APPLICATION_RELAY))
				c.addAuthApp(app);
		}
		for(Integer app : peer.acct_app) {
			if(app==ProtocolConstants.DIAMETER_APPLICATION_RELAY ||
			   us.acct_app.contains(app) ||
			   us.acct_app.contains(ProtocolConstants.DIAMETER_APPLICATION_RELAY))
				c.addAcctApp(app);
		}
		for(VendorApplication va : peer.auth_vendor) {
			//relay app is not well-defined for vendor-app
			if(us.isAllowedAuthApp(va.vendor_id,va.application_id))
				c.addVendorAuthApp(va.vendor_id,va.application_id);
		}
		for(VendorApplication va : peer.acct_vendor) {
			//relay app is not well-defined for vendor-app
			if(us.isAllowedAcctApp(va.vendor_id,va.application_id))
				c.addVendorAcctApp(va.vendor_id,va.application_id);
		}
		return c;
	}
}
