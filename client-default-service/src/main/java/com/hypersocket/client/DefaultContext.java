package com.hypersocket.client;

import com.hypersocket.client.rmi.DefaultClientService;
import com.hypersocket.client.rmi.VPNService;

public interface DefaultContext extends LocalContext<DefaultClientService> {
	VPNService getVPNService();
}
