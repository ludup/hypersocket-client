package com.logonbox.vpn.common.client;

public interface ConnectionStatus {

	public enum Type {
		DISCONNECTING,
		DISCONNECTED,
		AUTHORIZING,
		CONNECTING,
		CONNECTED;	
	}
	
	StatusDetail getDetail();
	
	Connection getConnection();
	
	Type getStatus();
}
