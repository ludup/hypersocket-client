package com.logonbox.vpn.client;

import com.hypersocket.client.LocalContext;
import com.logonbox.vpn.client.wireguard.PlatformService;
import com.logonbox.vpn.common.client.LogonBoxVPNClientService;
import com.logonbox.vpn.common.client.PeerConfigurationService;

public interface LogonBoxVPNContext extends LocalContext<LogonBoxVPNClientService> {

	PeerConfigurationService getPeerConfigurationService();

	PlatformService getPlatformService();
}
