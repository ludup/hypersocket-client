package com.hypersocket.client.rmi;

public interface Connection {
	
	void setServerVersion(String serverVersion);
	
	String getServerVersion();
	
	void setSerial(String serial);
	
	String getSerial();

	void setPort(Integer port);

	void setConnectAtStartup(boolean connectAtStartup);

	boolean isConnectAtStartup();

	void setStayConnected(boolean stayConnected);

	boolean isStayConnected();

	void setPassword(String hashedPassword);

	String getEncryptedPassword();

	void setUsername(String username);

	String getUsername();

	String getRealm();
	
	void setRealm(String realm);
	
	void setPath(String path);

	String getPath();

	int getPort();

	void setHostname(String hostname);

	public String getHostname();

	Long getId();
	
	String getName();
	
	void setName(String name);

	String getUri(boolean withUsername);

}
