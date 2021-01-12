package com.hypersocket.client.db;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

import com.hypersocket.client.AbstractMain;

public class HibernateSessionFactory {

	static SessionFactory factory;
	

	public static SessionFactory getFactory() {
		if (factory == null) {
			synchronized (AbstractMain.class) {
				if (factory == null) {
					factory = new Configuration().configure()
							.buildSessionFactory();
				}
			}
		}
		return factory;
	}
	
}
