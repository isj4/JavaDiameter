package dk.i1.diameter.session;

/**
 * Authorization time calculator.
 * This utility class keeps track of the authorization-lifetime and
 * authorization-grace-period and calculates when the session must be close or
 * when a re-authorization must be sent.
 */
public class SessionAuthTimers {
	private long latest_auth_time; //absolute, milliseconds
	private long next_reauth_time; //absolute, milliseconds
	private long auth_timeout; //absolute, milliseconds
	
	/**
	 * Updates the calculations based on the supplied values. The method
	 * will try to schedule the re-authorization (if any) 10 seconds before
	 * the session would have to be closed otherwise.
	 * @param auth_time         The time then the authorization succeeded
	 *                          (absolute, milliseconds).
	 *                          Ideally, this should be the time when the
	 *                          users is given service (for the first
	 *                          authorization), and the server's time when
	 *                          the re-authorization succeeds. In most cases
	 *                          System.currentTimeMillis() will do.
	 * @param auth_lifetime     The granted authorization lifetime in
	 *                          relative milliseconds. Use 0 to specify no
	 *                          authorization-lifetime.
	 * @param auth_grace_period The authorization-grace-period in relative
	 *                          milliseconds. Use 0 to specify none.
	 */
	public void updateTimers(long auth_time, long auth_lifetime, long auth_grace_period) {
		latest_auth_time = auth_time;
		if(auth_lifetime!=0) {
			auth_timeout = latest_auth_time + auth_lifetime + auth_grace_period;
			if(auth_grace_period!=0) {
				next_reauth_time = latest_auth_time + auth_lifetime;
			} else {
				//schedule reauth to 10 seconds before timeout. Should be plenty for carrier-grade servers.
				next_reauth_time = Math.max(auth_time+auth_lifetime/2, auth_timeout-10);
			}
		} else {
			next_reauth_time = Long.MAX_VALUE;
			auth_timeout = Long.MAX_VALUE;
		}
	}
	
	/**
	 * Retrieve the calculated time for the next re-authorization.
	 * @return The next re-authorization time, in milliseconds. Will be
	 *         Long.MAX_VALUE if there is none.
	 */
	public long getNextReauthTime() {
		return next_reauth_time;
	}
	/**
	 * Retrieve the maximum timeout of the session after which service must
	 * be denied and the session should be closed.
	 * @return The timeout. Will be Long.MAX_VALUE if there is no timeout.
	 */
	public long getMaxTimeout() {
		return auth_timeout;
	}
}
