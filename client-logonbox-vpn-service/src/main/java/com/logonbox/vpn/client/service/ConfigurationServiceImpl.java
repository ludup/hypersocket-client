package com.logonbox.vpn.client.service;

import java.rmi.RemoteException;
import java.util.prefs.Preferences;

import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.common.client.ConfigurationService;

public class ConfigurationServiceImpl implements ConfigurationService {

	private static Preferences NODE = Preferences.userNodeForPackage(ConfigurationService.class);

	private LocalContext context;

	public ConfigurationServiceImpl(LocalContext context) {
		this.context = context;
	}

	@Override
	public String getValue(String name, String defaultValue) {
		return NODE.get(name, defaultValue);
	}

	@Override
	public void setValue(String name, String value) throws RemoteException {
		NODE.put(name, value);
		context.getGuiRegistry().configurationUpdated(name, value);
		
	}

}
