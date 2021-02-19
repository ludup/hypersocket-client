package com.logonbox.vpn.client.service;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.client.HypersocketClient;
import com.hypersocket.client.HypersocketClientListener;
import com.hypersocket.client.HypersocketClientTransport;
import com.logonbox.vpn.common.client.ClientService;
import com.logonbox.vpn.common.client.Connection;
import com.logonbox.vpn.common.client.GUICallback;
import com.logonbox.vpn.common.client.GUIRegistry;

public class ServiceClient extends HypersocketClient<Connection> {

	static Logger log = LoggerFactory.getLogger(ServiceClient.class);

	GUIRegistry guiRegistry;
	ClientService clientService;

	public ServiceClient(HypersocketClientTransport transport, ClientService clientService, Locale currentLocale,
			HypersocketClientListener<Connection> service, Connection connection, GUIRegistry guiRegistry)
			throws IOException {
		super(transport, currentLocale, service);
		this.clientService = clientService;
		this.guiRegistry = guiRegistry;
		setAttachment(connection);
	}

	@Override
	protected void onDisconnect() {
		// Do nothing cause the listener now handles this
	}

	protected void onConnected() {
	}

	@Override
	public void showWarning(String msg) {
		if (guiRegistry.hasGUI()) {
			try {
				guiRegistry.getGUI().notify(msg, GUICallback.NOTIFY_WARNING);
			} catch (RemoteException e) {
				log.error("Failed to show warning", e);
			}
		}
	}

	@Override
	public void showError(String msg) {
		if (guiRegistry.hasGUI()) {
			try {
				guiRegistry.getGUI().notify(msg, GUICallback.NOTIFY_ERROR);
			} catch (RemoteException e) {
				log.error("Failed to show error", e);
			}
		}
	}

	@Override
	protected void onDisconnecting() {
	}

}
