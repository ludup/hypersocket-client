package com.logonbox.vpn.client.service;

import com.hypersocket.client.HypersocketClient;
import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.common.client.Connection;

public interface ClientContext {

	HypersocketClient<Connection> getClient();

	LocalContext getLocalContext();
}
