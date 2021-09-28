package com.logonbox.vpn.common.client;

public interface ConnectionStatus {

	public enum Type {
		DISCONNECTING,
		DISCONNECTED,
		TEMPORARILY_OFFLINE,
		AUTHORIZING,
		CONNECTING,
		CONNECTED;	
	}
	
	StatusDetail getDetail();
	
	Connection getConnection();
	
	Type getStatus();
	
	String getAuthorizeUri();
}
