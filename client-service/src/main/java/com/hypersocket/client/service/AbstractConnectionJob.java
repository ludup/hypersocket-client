package com.hypersocket.client.service;

import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.client.LocalContext;
import com.hypersocket.client.rmi.Connection;

public abstract class AbstractConnectionJob<S extends LocalContext<?>> extends TimerTask {

	static Logger log = LoggerFactory.getLogger(AbstractConnectionJob.class);

	protected S localContext;
	protected Connection connection;

	public AbstractConnectionJob(S localContext, Connection connection) {
		this.connection = connection;
		this.localContext = localContext;
	}

	public S getLocalContext() {
		return localContext;
	}

	public Connection getConnection() {
		return connection;
	}
}
