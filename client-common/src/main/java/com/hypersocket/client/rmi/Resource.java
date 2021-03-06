package com.hypersocket.client.rmi;

import java.util.Calendar;
import java.util.List;

public interface Resource extends Launchable {
	
	public enum Type {
		FILE, NETWORK, BROWSER, SSO, ENDPOINT
	}
	
	String getUid();
	
	String getIcon();
	
	String getColour();
	
	String getGroup();
	
	String getGroupIcon();
	
	Type getType();

	String getHostname();

	List<ResourceProtocol> getProtocols();

	void setResourceRealm(ResourceRealm realm);
	
	ResourceRealm getRealm();

	String getName();
	
	Calendar getModified();

}
