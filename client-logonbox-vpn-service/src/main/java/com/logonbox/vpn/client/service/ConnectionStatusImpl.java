package com.logonbox.vpn.client.service;

import com.logonbox.vpn.common.client.Connection;
import com.logonbox.vpn.common.client.ConnectionStatus;
import com.logonbox.vpn.common.client.StatusDetail;

public class ConnectionStatusImpl implements ConnectionStatus {

	private Type status;
	private Connection connection;
	private StatusDetail detail;

	public ConnectionStatusImpl(Connection connection, StatusDetail detail, Type status) {
		this.connection = connection;
		this.status = status;
		this.detail = detail;
	}

	@Override
	public Connection getConnection() {
		return connection;
	}

	@Override
	public Type getStatus() {
		return status;
	}

	@Override
	public StatusDetail getDetail() {
		return detail;
	}

}
