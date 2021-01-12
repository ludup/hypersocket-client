package com.hypersocket.client.rmi;

import com.hypersocket.client.ServiceResource;

public interface VPNResource {
	public enum Status {
		GOOD, BAD, UNKNOWN
	}
	
	public ServiceResource getOriginator();

	public Status getServiceStatus();

	public String getServiceDescription();
}
