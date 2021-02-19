package com.logonbox.vpn.client.db;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

import com.logonbox.vpn.client.Main;

public class HibernateSessionFactory {

	static SessionFactory factory;
	

	public static SessionFactory getFactory() {
		if (factory == null) {
			synchronized (Main.class) {
				if (factory == null) {
					factory = new Configuration().configure()
							.buildSessionFactory();
				}
			}
		}
		return factory;
	}
	
}
