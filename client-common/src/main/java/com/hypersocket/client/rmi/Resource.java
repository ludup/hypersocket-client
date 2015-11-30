package com.hypersocket.client.rmi;

import java.util.Calendar;
import java.util.List;

public interface Resource extends Launchable {
	
	public enum Type {
		FILE, NETWORK, BROWSER, SSO
	}
	
	String getUid();
	
	String getIcon();
	
	String getColour();
	
	String getGroup();
	
	Type getType();

	String getHostname();

	List<ResourceProtocol> getProtocols();

	void setResourceRealm(ResourceRealm realm);
	
	ResourceRealm getRealm();

	String getName();
	
	Calendar getModified();

}
