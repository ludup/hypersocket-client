package com.logonbox.vpn.client.service;

import com.logonbox.vpn.common.client.Connection;
import com.logonbox.vpn.common.client.ConnectionStatus;
import com.logonbox.vpn.common.client.StatusDetail;

public class ConnectionStatusImpl implements ConnectionStatus {

	private Type status;
	private Connection connection;
	private StatusDetail detail;
	private String authorizeUri;

	public ConnectionStatusImpl(Connection connection, StatusDetail detail, Type status, String authorizeUri) {
		this.connection = connection;
		this.status = status;
		this.detail = detail;
		this.authorizeUri = authorizeUri;
	}

	@Override
	public String getAuthorizeUri() {
		return authorizeUri;
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
