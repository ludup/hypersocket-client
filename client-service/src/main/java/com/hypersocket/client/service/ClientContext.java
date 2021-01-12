package com.hypersocket.client.service;

import com.hypersocket.client.HypersocketClient;
import com.hypersocket.client.LocalContext;
import com.hypersocket.client.rmi.Connection;

public interface ClientContext<L extends LocalContext<?>> {

	HypersocketClient<Connection> getClient();

	L getLocalContext();
}
