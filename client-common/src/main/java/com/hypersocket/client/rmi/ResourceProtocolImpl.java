package com.hypersocket.client.rmi;

import java.io.Serializable;

public class ResourceProtocolImpl implements ResourceProtocol, Serializable {

	private static final long serialVersionUID = -3020855022266423615L;

	String name;
	Resource group;
	boolean launchable;
	ResourceLauncher launcher;
	
	public ResourceProtocolImpl() {
	}

	public ResourceProtocolImpl(String name) {
		this.name = name;
	}

	@Override
	public String getProtocol() {
		return name;
	}
	
	public void setResource(Resource group) {
		this.group = group;
	}
	
	public Resource getResource() {
		return group;
	}

	@Override
	public boolean isLaunchable() {
		return launchable;
	}
	
	@Override
	public void setLaunchable(boolean launchable) {
		this.launchable = launchable;
	}
	
	@Override
	public ResourceLauncher getResourceLauncher() {
		return launcher;
	}

	@Override
	public void setResourceLauncher(ResourceLauncher launcher) {
		this.launcher = launcher;
	}

}
