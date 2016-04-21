package com.hypersocket.client.service.vpn;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.client.HypersocketClient;
import com.hypersocket.client.NetworkResource;
import com.hypersocket.client.ServiceResource.Status;
import com.hypersocket.client.hosts.AbstractSocketRedirector;
import com.hypersocket.client.hosts.HostsFileManager;
import com.hypersocket.client.hosts.SocketRedirector;
import com.hypersocket.client.rmi.ResourceService;
import com.hypersocket.client.rmi.VPNService;

public class VPNServiceImpl implements VPNService {
	private static Logger log = LoggerFactory.getLogger(VPNServiceImpl.class);

	private Map<HypersocketClient<?>,Map<String, NetworkResource>> localForwards = new HashMap<HypersocketClient<?>,Map<String, NetworkResource>>();
	private Set<NetworkResource> allForwards = new HashSet<NetworkResource>();
	
	private ResourceService resourceService;
	private HostsFileManager mgr;
	private SocketRedirector redirector;

	public VPNServiceImpl(ResourceService resourceService) {
		this.resourceService = resourceService;
	}

	public NetworkResource createURLForwarding(
			HypersocketClient<?> serviceClient, String launchUrl, Long parentId) {

		try {
			URL url = new URL(launchUrl);

			String hostname = url.getHost();

			int port = url.getPort();
			if (port == -1) {
				port = url.getDefaultPort();
			}

			NetworkResource resource = new NetworkResource(parentId, hostname,
					url.getHost(), (int) port, "url");
			boolean started = startLocalForwarding(resource, serviceClient);
			if (started) {
				return resource;
			}

		} catch (MalformedURLException e) {
			log.error("Failed to parse url " + launchUrl, e);
		} catch (IOException e) {
			log.error("Failed to start forwarding for " + launchUrl, e);
		}

		return null;
	}

	public boolean startLocalForwarding(NetworkResource resource,
			HypersocketClient<?> serviceClient) throws IOException {
		boolean started = setupLocalForwarding(serviceClient, resource);

		if (log.isInfoEnabled()) {
			log.info("Local forwarding to " + resource.getHostname() + ":"
					+ resource.getPort() + (started ? " succeeded" : " failed"));
		}

		resource.setServiceStatus(started ? Status.GOOD : Status.BAD);
		resourceService.getServiceResources().add(resource);

		return started;
	}

	synchronized boolean setupLocalForwarding(
			HypersocketClient<?> serviceClient, NetworkResource resource)
			throws IOException {

		if(!localForwards.containsKey(serviceClient)) {
			localForwards.put(serviceClient, new HashMap<String,NetworkResource>());
		}
		
		if (mgr == null) {
			mgr = HostsFileManager.getSystemHostsFile();
		}

		if (redirector == null) {
			redirector = AbstractSocketRedirector.getSystemRedirector();
		}

		String alias = mgr.getAlias(resource.getHostname());

		int actualPort;
		resource.setLocalHostname("127.0.0.1");
		if ((actualPort = serviceClient.getTransport().startLocalForwarding(
				resource.getLocalHostname(), 0, resource)) > 0) {
			try {
				redirector.startRedirecting(alias, resource.getPort(),
						resource.getLocalHostname(), actualPort);
				resource.setLocalPort(actualPort);				
				resource.setAliasInterface(alias);

				localForwards.get(serviceClient).put(resource.getLocalHostname() + ":" + actualPort, resource);
				allForwards.add(resource);
				return true;
			} catch (Exception e) {
				log.error("Failed to redirect local forwarding", e);
				return false;
			}
		} else {
			return false;
		}

	}

	public synchronized void stopAllForwarding(
			HypersocketClient<?> serviceClient) {

		Map<String, NetworkResource> map = localForwards.get(serviceClient);
		if(map == null)
			log.info("No client to stop yet for this connection.");
		else {
			List<NetworkResource> tmp = new ArrayList<NetworkResource>(
					map.values());
			for (NetworkResource resource : tmp) {
				stopLocalForwarding(resource, serviceClient);
			}
		}

		localForwards.remove(serviceClient);
	}

	private boolean isHostnameInUse(String hostname) {
		for (NetworkResource resource : allForwards) {
			if (resource.getHostname().equals(hostname)) {
				return true;
			}
		}
		return false;
	}

	public synchronized void stopLocalForwarding(NetworkResource resource,
			HypersocketClient<?> serviceClient) {
		
		try {
			resourceService.getServiceResources().remove(resource);
		} catch (RemoteException e1) {
			// Accessing locally, shouldn't happen
		}
		String key = "127.0.0.1" + ":" + resource.getLocalPort();
		
		Map<String,NetworkResource> forwards = localForwards.get(serviceClient);
		
		if (forwards.containsKey(key)) {
			log.info(String.format("Stopping local forwarding for %s", key));
			serviceClient.getTransport().stopLocalForwarding("127.0.0.1",
					resource.getLocalPort());
			try {
				redirector.stopRedirecting(resource.getAliasInterface(),
						resource.getPort(), "127.0.0.1",
						resource.getLocalPort());
			} catch (Exception e) {
				log.error("Failed to stop local forwarding redirect", e);
			} finally {
				forwards.remove(key);
				allForwards.remove(resource);

				if (!isHostnameInUse(resource.getHostname())) {
					try {
						log.info(String.format("Removing hosts file entry for %s", resource.getHostname()));
						mgr.removeHostname(resource.getHostname());
					} catch (IOException e) {
					}
				}
			}
		}
	}
}
