package com.logonbox.vpn.common.client;

import java.util.List;

import com.hypersocket.client.rmi.Connection;

public interface PeerConfiguration {
	
	String getUsernameHint();
	
	void setUsernameHint(String usernameHint);

	void setId(Long id);

	Long getId();

	String getUserPrivateKey();

	void setUserPrivateKey(String privateKey);

	String getUserPublicKey();

	void setUserPublicKey(String publicKey);

	String getPublicKey();

	void setPublicKey(String Key);

	void setEndpointAddress(String endpointAddress);

	void setEndpointPort(int endpoingPort);

	String getEndpointAddress();

	int getEndpointPort();

	int getMtu();

	void setMtu(int mtu);

	String getAddress();

	void setAddress(String address);

	List<String> getDns();

	void setDns(List<String> dns);

	int getPersistentKeepalive();

	void setPeristentKeepalive(int peristentKeepalive);

	List<String> getAllowedIps();

	void setAllowedIps(List<String> allowedIps);

	Connection getConnection();

	void setConnection(Connection connection);

}
