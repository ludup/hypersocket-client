package com.logonbox.vpn.client.service;

public abstract class LogonBoxVPNServicePlugin
		extends AbstractServicePlugin {

	protected LogonBoxVPNServicePlugin(String... urls) {
		super(urls);
	}

	protected final void startResources() {
	}

}
