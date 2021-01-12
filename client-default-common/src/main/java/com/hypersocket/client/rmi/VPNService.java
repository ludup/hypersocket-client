package com.hypersocket.client.rmi;

import java.io.IOException;

import com.hypersocket.client.HypersocketClient;
import com.hypersocket.client.NetworkResource;

public interface VPNService {

	NetworkResource createURLForwarding(HypersocketClient<?> serviceClient, String launchUrl, Long parentId);

	boolean startLocalForwarding(NetworkResource resource, HypersocketClient<?> serviceClient) throws IOException;

	void stopAllForwarding(HypersocketClient<?> serviceClient);

	void stopLocalForwarding(NetworkResource resource, HypersocketClient<?> serviceClient);
}
