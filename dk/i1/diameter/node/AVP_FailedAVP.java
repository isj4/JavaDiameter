package dk.i1.diameter.node;

import dk.i1.diameter.*;

class AVP_FailedAVP extends AVP_Grouped {
	private static AVP[] wrap(AVP a) {
		AVP g[] = new AVP[1];
		g[0] = a;
		return g;
	}
	public AVP_FailedAVP(AVP a) {
		super(ProtocolConstants.DI_FAILED_AVP,wrap(a));
	}
}
