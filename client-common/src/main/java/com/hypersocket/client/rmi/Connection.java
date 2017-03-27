package com.hypersocket.client.rmi;

public interface Connection {
	
	public void setServerVersion(String serverVersion);
	
	public String getServerVersion();
	
	public void setSerial(String serial);
	
	public String getSerial();

	public void setPort(Integer port);

	public void setConnectAtStartup(boolean connectAtStartup);

	public boolean isConnectAtStartup();

	public void setStayConnected(boolean stayConnected);

	public boolean isStayConnected();

	public void setHashedPassword(String hashedPassword);

	public String getPassword();

	public void setUsername(String username);

	public String getUsername();

	public String getRealm();
	
	public void setRealm(String realm);
	
	public void setPath(String path);

	public String getPath();

	public int getPort();

	public void setHostname(String hostname);

	public String getHostname();

	Long getId();

}
