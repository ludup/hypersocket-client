package com.logonbox.vpn.client.service;

import java.rmi.RemoteException;
import java.util.Objects;
import java.util.prefs.Preferences;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.common.client.ConfigurationService;

public class ConfigurationServiceImpl implements ConfigurationService {

	static Logger log = LoggerFactory.getLogger(ConfigurationServiceImpl.class);

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
		String was = NODE.get(name, null);
		if(!Objects.equals(was, value)) {

			if (value == null) {
				log.info(String.format("Setting '%s' to default value", name));
				NODE.remove(name);
			} else {
				log.info(String.format("Setting '%s' to '%s'", name, value));
				NODE.put(name, value);
			}
			
			context.getGuiRegistry().configurationUpdated(name, value);
		}

	}

}
