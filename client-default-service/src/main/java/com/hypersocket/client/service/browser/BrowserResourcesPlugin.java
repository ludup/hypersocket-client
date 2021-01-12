package com.hypersocket.client.service.browser;

import java.io.IOException;
import java.net.URLEncoder;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypersocket.client.hosts.HostsFileManager;
import com.hypersocket.client.rmi.BrowserLauncher;
import com.hypersocket.client.rmi.Resource;
import com.hypersocket.client.rmi.Resource.Type;
import com.hypersocket.client.rmi.ResourceImpl;
import com.hypersocket.client.service.DefaultAbstractServicePlugin;

public class BrowserResourcesPlugin extends DefaultAbstractServicePlugin {

	static Logger log = LoggerFactory.getLogger(BrowserResourcesPlugin.class);

	List<JsonBrowserResource> browserResources = new ArrayList<JsonBrowserResource>();

	public BrowserResourcesPlugin() {
		super("browser");
	}

	@Override
	public boolean onStart() {
		return true;
	}

	protected void reloadResources(List<Resource> realmResources) {
		try {
			String json = serviceClient.getTransport().get(
					"browser/myResources");

			ObjectMapper mapper = new ObjectMapper();

			JsonBrowserResourceList list = mapper.readValue(json,
					JsonBrowserResourceList.class);

			Map<String, String> properties = list.getProperties();

			int errors = processBrowserResources(list.getResources(),
					properties.get("authCode"), realmResources);

			if (errors > 0) {
				// Warn
				serviceClient.showWarning(errors
						+ " websites could not be opened.");
			}

		} catch (IOException e) {
			if (log.isErrorEnabled()) {
				log.error("Could not start website resources", e);
			}
		}
	}

	protected int processBrowserResources(JsonBrowserResource[] resources,
			String authCode, List<Resource> realmResources) throws IOException {

		int errors = 0;

		for (JsonBrowserResource resource : resources) {

			try {
				ResourceImpl res = new ResourceImpl("browser-"
						+ String.valueOf(resource.getId()), resource.getName());
	
				res.setGroup(res.getName());
				res.setGroupIcon(resource.getLogo());
				res.setLaunchable(true);
				res.setIcon(resource.getLogo());
				res.setModified(resource.getModifiedDate());
				res.setConnectionId(serviceClient.getAttachment().getId());
	
				if (resource.getType() != null
						&& (resource.getType().equals("BrowserSSOPlugin")
								|| resource.getType().equals("SAMLServiceProvider"))) {
					res.setType(Type.SSO);
				} else {
					res.setType(Type.BROWSER);
				}
	
				String sessionId = serviceClient.getSessionId();
				String url = resource.getLaunchUrl().replace("${basePath}", serviceClient.getBasePath());
				String launchUrl = HostsFileManager.sanitizeURL(url).toExternalForm();;
				
				 if (resource.isRequireVPNAccess()) {
		              vpnService.createURLForwarding(
		                        serviceClient, 
		                        url, 
		                        resource.getId());
		         }
				
				res.setResourceLauncher(new BrowserLauncher(serviceClient
						.getTransport().resolveUrl(
								"attach/" + authCode + "/" + sessionId
										+ "?location="
										+ URLEncoder.encode(launchUrl, "UTF-8"))));
				realmResources.add(res);
			} catch(Throwable t) {
				log.error("error ...............................", t);
				errors++;
			}

		}

		return errors;
	}

	@Override
	public void onStop() {

		if (log.isInfoEnabled()) {
			log.info("Stopping Browser Resources plugin");
		}

		try {
			resourceService.removeResourceRealm(serviceClient.getHost());
		} catch (RemoteException e) {
			log.error(
					"Failed to remove resource realm "
							+ serviceClient.getHost(), e);
		}
	}

	@Override
	public String getName() {
		return "Browser Resources";
	}

	@Override
	protected boolean onCreatedResource(Resource resource) {
		return true;
	}

	@Override
	protected boolean onUpdatedResource(Resource resource) {
		return true;
	}

	@Override
	protected boolean onDeletedResource(Resource resource) {
		return true;
	}
}
