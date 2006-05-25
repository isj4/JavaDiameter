package dk.i1.diameter.node;

class ConnectionTimers {
	long last_activity;
	long last_real_activity;
	long last_in_dw;
	boolean dw_outstanding;
	long cfg_watchdog_timer;
	long cfg_idle_close_timeout;
	
	public ConnectionTimers(long watchdog_timer, long idle_close_timeout) {
		last_activity = System.currentTimeMillis();
		last_real_activity = System.currentTimeMillis();
		last_in_dw = System.currentTimeMillis();
		dw_outstanding = false;
		cfg_watchdog_timer = watchdog_timer;
		cfg_idle_close_timeout = idle_close_timeout;
	}
	
	public void markDWR() { //got a DWR
		last_in_dw = System.currentTimeMillis();
	}
	public void markDWA() { //got a DWA	
		last_in_dw = System.currentTimeMillis();
		dw_outstanding = false;
	}
	public void markActivity() { //got something
		last_activity = System.currentTimeMillis();
	}
	public void markCER() { //got a CER
		last_activity = System.currentTimeMillis();
	}
	public void markRealActivity() { //got something non-CER, non-DW
		last_real_activity = last_activity;
	}
	public void markDWR_out() { //sent a DWR
		dw_outstanding = true;
		last_activity = System.currentTimeMillis();
	}
	
	public enum timer_action {
		none,
		disconnect_no_cer,
		disconnect_idle,
		disconnect_no_dw,
		dwr
	}

	public long calcNextTimeout(boolean ready) {
		if(!ready) {
			//when we haven't received a CER or negotiated TLS it will time out
			return last_activity + cfg_watchdog_timer;
		}
		
		long next_watchdog_timeout;

		if(!dw_outstanding)
			next_watchdog_timeout = last_activity + cfg_watchdog_timer; //when to send a DWR
		else
			next_watchdog_timeout = last_activity + cfg_watchdog_timer + cfg_watchdog_timer; //when to kill the connection due to no response

		if(cfg_idle_close_timeout!=0) {
			long idle_timeout;
			idle_timeout = last_real_activity + cfg_idle_close_timeout;
			if(idle_timeout<next_watchdog_timeout)
				return idle_timeout;
		}
		return next_watchdog_timeout;
	}
	
	public timer_action calcAction(boolean ready) {
		long now = System.currentTimeMillis();

		if(!ready &&
		   now >= last_activity + cfg_watchdog_timer)
		{
			return timer_action.disconnect_no_cer;
		}
		
		if(cfg_idle_close_timeout!=0) {
			if(now >= last_real_activity + cfg_idle_close_timeout)
			{
				return timer_action.disconnect_idle;
			}
		}
		
		//section 3.4.1 item 1
		if(now >= last_activity + cfg_watchdog_timer) {
			if(!dw_outstanding) {
				return timer_action.dwr;
			} else {
				if(now >= last_activity + cfg_watchdog_timer + cfg_watchdog_timer) {
					//section 3.4.1 item 3+4
					return timer_action.disconnect_no_dw;
				}
			}
		}
		return timer_action.none;
	}
}
