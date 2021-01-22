package com.logonbox.vpn.client.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.client.HypersocketClient;
import com.logonbox.vpn.common.client.GUIRegistry;
import com.logonbox.vpn.common.client.Connection;

public abstract class AbstractServicePlugin
		implements ServicePlugin {

	static Logger log = LoggerFactory.getLogger(AbstractServicePlugin.class);

	protected String[] urls;
	protected HypersocketClient<Connection> serviceClient;
	protected GUIRegistry guiRegistry;
	protected ClientContext context;

	protected AbstractServicePlugin(String... urls) {
		this.urls = urls;
	}

	@Override
	public final void stop() {
		onStop();
	}

	@Override
	public final boolean start(ClientContext context) {

		this.context = context;

		// convenience
		this.serviceClient = context.getClient();
		this.guiRegistry = context.getLocalContext().getGuiRegistry();

		return onStart();
	}

	public abstract boolean onStart();

	public abstract void onStop();

}
