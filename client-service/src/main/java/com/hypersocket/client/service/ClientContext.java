package com.hypersocket.client.service;

import com.hypersocket.client.HypersocketClient;
import com.hypersocket.client.rmi.GUIRegistry;
import com.hypersocket.client.rmi.ResourceService;
import com.hypersocket.client.service.vpn.VPNServiceImpl;

public interface ClientContext {

	ResourceService getResourceService();
	
	VPNServiceImpl getVPNService();
	
	HypersocketClient<?> getClient();
	
	GUIRegistry getGUI();
}
