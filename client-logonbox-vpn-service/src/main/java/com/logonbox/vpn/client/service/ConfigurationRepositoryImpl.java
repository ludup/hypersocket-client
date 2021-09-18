package com.logonbox.vpn.client.service;

import java.util.Objects;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.freedesktop.dbus.exceptions.DBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.common.client.ConfigurationRepository;
import com.logonbox.vpn.common.client.dbus.VPN;

public class ConfigurationRepositoryImpl implements ConfigurationRepository {

	static Logger log = LoggerFactory.getLogger(ConfigurationRepositoryImpl.class);

	private static Preferences NODE = Preferences.userNodeForPackage(ConfigurationRepository.class);

	private LocalContext context;

	public ConfigurationRepositoryImpl(LocalContext context) {
		this.context = context;
	}

	@Override
	public String getValue(String key, String defaultValue) {
		return NODE.get(key, defaultValue);
	}

	@Override
	public String[] getKeys() {
		try {
			return NODE.keys();
		} catch (BackingStoreException e) {
			throw new IllegalStateException("Failed to get keys.", e);
		}
	}

	@Override
	public void setValue(String key, String value) {
		String was = NODE.get(key, null);
		if(!Objects.equals(was, value)) {

			if (value == null) {
				log.info(String.format("Setting '%s' to default value", key));
				NODE.remove(key);
			} else {
				log.info(String.format("Setting '%s' to '%s'", key, value));
				NODE.put(key, value);
			}
			
			try {
				context.sendMessage(new VPN.GlobalConfigChange("/com/logonbox/vpn", key, value));
			} catch (DBusException e) {
				throw new IllegalStateException("Failed to send event.", e);
			}
		}

	}

}
