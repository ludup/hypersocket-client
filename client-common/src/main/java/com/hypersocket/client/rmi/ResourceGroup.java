package com.hypersocket.client.rmi;

public interface ResourceGroup {

	String getName();
	
	String getLogo();

	void setResourceRealm(ResourceRealm object);

	ResourceRealm getRealm();
	
}
