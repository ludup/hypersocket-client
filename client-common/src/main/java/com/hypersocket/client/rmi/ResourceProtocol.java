package com.hypersocket.client.rmi;

public interface ResourceProtocol extends Launchable {

	String getProtocol();

	Resource getResource();

	void setResource(Resource group);

}
