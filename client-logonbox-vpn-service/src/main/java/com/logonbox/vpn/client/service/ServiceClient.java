package com.logonbox.vpn.client.service;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.client.HypersocketClient;
import com.hypersocket.client.HypersocketClientListener;
import com.hypersocket.client.HypersocketClientTransport;
import com.hypersocket.client.Prompt;
import com.logonbox.vpn.common.client.ClientService;
import com.logonbox.vpn.common.client.GUICallback;
import com.logonbox.vpn.common.client.GUIRegistry;
import com.logonbox.vpn.common.client.Connection;

public class ServiceClient extends HypersocketClient<Connection> {

	static Logger log = LoggerFactory.getLogger(ServiceClient.class);

	GUIRegistry guiRegistry;
	ClientService clientService;
	List<ServicePlugin> plugins = new ArrayList<ServicePlugin>();

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

	// @Override
	protected Map<String, String> showLogin(HypersocketClient<Connection> attached, List<Prompt> prompts, int attempt,
			boolean success) throws IOException {
		if (guiRegistry.hasGUI()) {
			try {
				ResourceBundle rb = attached.getResources();
				return guiRegistry.getGUI().showPrompts(attached.getAttachment(), rb, prompts, attempt, success);
			} catch (RemoteException e) {
				log.error("Failed to show prompts", e);
				disconnect(true);
				throw new IOException(e.getMessage(), e);
			}
		}
		return null;
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

		for (ServicePlugin plugin : plugins) {
			try {
				plugin.stop();
			} catch (Throwable e) {
				log.error("Failed to stop plugin " + plugin.getName(), e);
			}
		}

	}

}
