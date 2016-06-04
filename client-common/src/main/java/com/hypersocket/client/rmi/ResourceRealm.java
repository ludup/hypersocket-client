package com.hypersocket.client.rmi;

import java.util.List;

public interface ResourceRealm {

	String getName();
	
	List<Resource> getResources();

	void addResource(Resource res);

	void removeResource(Resource r);

}
