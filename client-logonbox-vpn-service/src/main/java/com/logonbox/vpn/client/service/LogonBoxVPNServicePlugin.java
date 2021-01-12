package com.logonbox.vpn.client.service;

import com.hypersocket.client.service.AbstractServicePlugin;
import com.logonbox.vpn.client.LogonBoxVPNContext;

public abstract class LogonBoxVPNServicePlugin
		extends AbstractServicePlugin<LogonBoxVPNClientContext, LogonBoxVPNContext> {

	protected LogonBoxVPNServicePlugin(String... urls) {
		super(urls);
	}

	protected final void startResources() {
	}

}
