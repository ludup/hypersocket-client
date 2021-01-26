package com.logonbox.vpn.common.client;

import java.io.Serializable;

public class ConnectionStatusImpl implements ConnectionStatus, Serializable {

	private static final long serialVersionUID = 296406363321007200L;

	Type status;
	Connection connection;
	
	public ConnectionStatusImpl(Connection connection, Type status) {
		this.connection = connection;
		this.status = status;
	}
	
	@Override
	public Connection getConnection() {
		return connection;
	}

	@Override
	public Type getStatus() {
		return status;
	}

}
