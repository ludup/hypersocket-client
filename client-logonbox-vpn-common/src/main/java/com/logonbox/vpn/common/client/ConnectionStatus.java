package com.logonbox.vpn.common.client;

public interface ConnectionStatus {

	public enum Type {
		DISCONNECTING,
		DISCONNECTED,
		AUTHORIZING,
		CONNECTING,
		CONNECTED;	
	}
	
	Connection getConnection();
	
	Type getStatus();
}
