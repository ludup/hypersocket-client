package com.hypersocket.client.rmi;

import java.util.List;

public interface ResourceRealm {

	String getName();
	
	List<Resource> getResources();
	
	List<ResourceGroup> getResourceGroups();

	void addResource(Resource res);

	void removeResource(Resource r);

	void addResourceGroup(ResourceGroup group);

	void removeResourceGroup(ResourceGroup group);
}
