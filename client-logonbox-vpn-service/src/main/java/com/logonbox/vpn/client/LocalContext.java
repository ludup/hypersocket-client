package com.logonbox.vpn.client;

import java.util.concurrent.ExecutorService;

import com.logonbox.vpn.client.wireguard.PlatformService;
import com.logonbox.vpn.common.client.ClientService;
import com.logonbox.vpn.common.client.ConfigurationService;
import com.logonbox.vpn.common.client.GUIRegistry;
import com.logonbox.vpn.common.client.ConnectionService;

public interface LocalContext {

	PlatformService<?> getPlatformService();
	
	ClientService getClientService();

	ExecutorService getWorker();

	ExecutorService getBoss();

	GUIRegistry getGuiRegistry();

	Runnable getRestartCallback();
	
	ConnectionService getConnectionService();

	ConfigurationService getConfigurationService();
}
