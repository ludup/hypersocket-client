package com.hypersocket.client.service;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.client.CredentialCache;
import com.hypersocket.client.CredentialCache.Credential;
import com.hypersocket.client.HypersocketClient;
import com.hypersocket.client.HypersocketClientListener;
import com.hypersocket.client.HypersocketClientTransport;
import com.hypersocket.client.Prompt;
import com.hypersocket.client.rmi.ClientService;
import com.hypersocket.client.rmi.Connection;
import com.hypersocket.client.rmi.GUICallback;
import com.hypersocket.client.rmi.GUIRegistry;
import com.hypersocket.client.rmi.ResourceService;

public class ServiceClient extends HypersocketClient<Connection> {

	static Logger log = LoggerFactory.getLogger(ServiceClient.class);

	ResourceService resourceService;
	GUIRegistry guiRegistry;
	ClientService clientService; 
	List<ServicePlugin> plugins = new ArrayList<ServicePlugin>();

	protected ServiceClient(HypersocketClientTransport transport,
			ClientService clientService,
			Locale currentLocale, HypersocketClientListener<Connection> service,
			ResourceService resourceService, Connection connection,
			GUIRegistry guiRegistry) throws IOException {
		super(transport, currentLocale, service);
		this.resourceService = resourceService;
		this.clientService = clientService;
		this.guiRegistry = guiRegistry;
		setAttachment(connection);
	}

	@Override
	protected void onDisconnect() {
		// Do nothing cause the listener now handles this
	}

	// @Override
	protected Map<String, String> showLogin(HypersocketClient<Connection> attached, List<Prompt> prompts, int attempt, boolean success)
			throws IOException {
		if (guiRegistry.hasGUI()) {
			try {
				ResourceBundle rb = attached.getResources();
				return guiRegistry.getGUI().showPrompts(attached.getAttachment(), rb, prompts, attempt, success);
			} catch (RemoteException e) {
				log.error("Failed to show prompts", e);
				disconnect(true);
				throw new IOException(e);
			}
		}
		return null;
	}

	protected void onConnected() {

		Credential creds = CredentialCache.getInstance().getCredentials(getTransport().getHost());
		if(creds!=null) {
			getAttachment().setUsername(creds.getUsername());
			getAttachment().setHashedPassword(creds.getPassword());
			try {
				clientService.save(getAttachment());
			} catch (RemoteException e) {
			}
		}
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
