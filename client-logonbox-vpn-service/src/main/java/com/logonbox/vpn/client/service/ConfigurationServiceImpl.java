package com.logonbox.vpn.client.service;

import java.rmi.RemoteException;
import java.util.prefs.Preferences;

import org.hibernate.Session;

import com.logonbox.vpn.client.db.HibernateSessionFactory;
import com.logonbox.vpn.common.client.ConfigurationService;

public class ConfigurationServiceImpl implements ConfigurationService {

	private static Preferences NODE = Preferences.userNodeForPackage(ConfigurationService.class);

	Session session;

	public ConfigurationServiceImpl() {
		session = HibernateSessionFactory.getFactory().openSession();
	}

	@Override
	public String getValue(String name, String defaultValue) {
		return NODE.get(name, defaultValue);
	}

	@Override
	public void setValue(String name, String value) throws RemoteException {
		NODE.put(name, value);
	}

}
