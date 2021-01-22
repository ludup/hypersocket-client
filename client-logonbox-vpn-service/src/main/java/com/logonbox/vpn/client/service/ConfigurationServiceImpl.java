package com.logonbox.vpn.client.service;

import java.rmi.RemoteException;
import java.util.List;

import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.criterion.Restrictions;

import com.logonbox.vpn.client.db.HibernateSessionFactory;
import com.logonbox.vpn.common.client.ConfigurationItem;
import com.logonbox.vpn.common.client.ConfigurationItemImpl;
import com.logonbox.vpn.common.client.ConfigurationService;

public class ConfigurationServiceImpl implements ConfigurationService {

	Session session;
	public ConfigurationServiceImpl() {
		session = HibernateSessionFactory.getFactory().openSession();
	}

	@Override
	public String getValue(String name, String defaultValue) {
		ConfigurationItem item = getItem(name);
		if(item==null) {
			return defaultValue;
		} else {
			return item.getValue();
		}
	}
	
	protected ConfigurationItem getItem(String name) {
		
		Criteria crit = session.createCriteria(ConfigurationItem.class);
		crit.add(Restrictions.eq("name", name));
		return (ConfigurationItem)crit.uniqueResult();
	}

	@Override
	public void setValue(String name, String value) throws RemoteException {
		
		Transaction trans = session.beginTransaction();
		
		ConfigurationItem item = getItem(name);
		if(item==null) {
			item = new ConfigurationItemImpl();
			item.setName(name);
		}
		
		item.setValue(value);
		session.save(item);
		session.flush();
		
		trans.commit();
		
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<ConfigurationItem> getConfigurationItems()
			throws RemoteException {
		
		Criteria crit = session.createCriteria(ConfigurationItemImpl.class);
		return (List<ConfigurationItem>)crit.list();
	}

}
