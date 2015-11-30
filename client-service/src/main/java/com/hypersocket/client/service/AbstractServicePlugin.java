package com.hypersocket.client.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.client.HypersocketClient;
import com.hypersocket.client.rmi.GUICallback.ResourceUpdateType;
import com.hypersocket.client.rmi.GUIRegistry;
import com.hypersocket.client.rmi.Resource;
import com.hypersocket.client.rmi.ResourceRealm;
import com.hypersocket.client.rmi.ResourceService;
import com.hypersocket.client.service.vpn.VPNServiceImpl;

public abstract class AbstractServicePlugin implements ServicePlugin {

	static Logger log = LoggerFactory.getLogger(AbstractServicePlugin.class);

	static ScheduledExecutorService fingerprintChecker = Executors
			.newScheduledThreadPool(1);

	protected String url;
	protected String fingerprint;
	protected HypersocketClient<?> serviceClient;
	protected ResourceService resourceService;
	protected GUIRegistry guiRegistry;
	protected List<Resource> realmResources = new ArrayList<Resource>();
	protected ResourceRealm resourceRealm;
	protected ScheduledFuture<?> checkTask;
	protected ClientContext context;
	protected VPNServiceImpl vpnService;

	protected AbstractServicePlugin(String url) {
		this.url = url;
	}

	public boolean isUpdateNeeded() throws IOException {
		String json = serviceClient.getTransport().get(
				String.format("%s/fingerprint", url));

		JSONParser parser = new JSONParser();
		try {
			JSONObject result = (JSONObject) parser.parse(json);
			if (Boolean.TRUE.equals(result.get("success"))) {
				String latestFingerprint = (String) result.get("message");
				if (!Objects.equals(latestFingerprint, fingerprint)) {
					fingerprint = latestFingerprint;
					return true;
				}
			}
		} catch (ParseException e) {
			throw new IOException("Failed to parse response.", e);
		}

		return false;
	}

	@Override
	public final void stop() {
		if (checkTask != null) {
			checkTask.cancel(true);
		}
		onStop();
	}

	@Override
	public final boolean start(ClientContext context) {

		this.context = context;
		
		// convenience
		this.vpnService = context.getVPNService();
		this.serviceClient = context.getClient();
		this.resourceService = context.getResourceService();
		this.guiRegistry = context.getGUI();

		if (log.isInfoEnabled()) {
			log.info("Starting Resources for " + getClass());
		}

		startResources();

		checkTask = fingerprintChecker.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				processResourceUpdates();
			}

		}, 5, 5, TimeUnit.SECONDS);

		return onStart();
	}

	protected abstract void reloadResources(List<Resource> realmResources);

	protected abstract boolean onCreatedResource(Resource resource);

	protected abstract boolean onUpdatedResource(Resource resource);

	protected abstract boolean onDeletedResource(Resource resource);

	public abstract boolean onStart();

	public abstract void onStop();

	protected final void startResources() {
		try {
			resourceRealm = resourceService.getResourceRealm(serviceClient
					.getHost());
			realmResources.clear();
			reloadResources(realmResources);
			for (Resource ri : realmResources) {
				if (onCreatedResource(ri)) {
					resourceRealm.addResource(ri);
				}
			}
		} catch (IOException e) {
			if (log.isErrorEnabled()) {
				log.error("Could not start website resources", e);
			}
		}
	}

	protected int processResourceList(String json, ResourceMapper mapper,
			String resourceName) throws IOException {
		try {
			JSONParser parser = new JSONParser();

			JSONObject result = (JSONObject) parser.parse(json);

			if (log.isDebugEnabled()) {
				log.debug(result.toJSONString());
			}

			JSONArray fields = (JSONArray) result.get("resources");

			if (fields.size() == 0) {
				if (log.isInfoEnabled()) {
					log.info("There are no " + resourceName + " to start");
				}
				return 0;
			}

			int totalResources = 0;
			int totalErrors = 0;

			@SuppressWarnings("unchecked")
			Iterator<JSONObject> it = (Iterator<JSONObject>) fields.iterator();
			while (it.hasNext()) {
				if (!mapper.processResource(it.next())) {
					totalErrors++;
				}
				totalResources++;
			}

			if (totalErrors == totalResources) {
				// We could not start any resources
				throw new IOException("No resources could be started!");
			}

			return totalErrors;

		} catch (ParseException e) {
			throw new IOException("Failed to parse network resources json", e);
		}
	}

	private void processResourceUpdates() {
		try {
			if (log.isDebugEnabled()) {
				log.debug("Checking if updated needed (" + fingerprint + "/"
						+ AbstractServicePlugin.this.getClass());
			}

			if (isUpdateNeeded()) {
				log.info("Resource update needed for for "
						+ AbstractServicePlugin.this.getClass());

				// Load the new resources
				List<Resource> newResources = new ArrayList<Resource>();
				reloadResources(newResources);

				/*
				 * Build a list of existing resource 'UID'. These should be
				 * stable over the lifetime of a resource, so may be used as an
				 * equality test for existing resources to detected which are
				 * added, removed or changed
				 */
				Set<String> existingResourceUIDList = new HashSet<String>();
				Set<String> newResourceUIDList = new HashSet<String>();
				Map<String, Resource> resourceIds = new HashMap<String, Resource>();
				getResourceUIDList(existingResourceUIDList, realmResources);
				getResourceUIDList(newResourceUIDList, newResources);

				// Look for deleted resources
				for (Resource r : new ArrayList<Resource>(realmResources)) {
					resourceIds.put(r.getUid(), r);
					if (!newResourceUIDList.contains(r.getUid())) {
						log.info(String.format(
								"Found a deleted resource (%s) '%s'",
								r.getUid(), r.getName()));
						if (onDeletedResource(r)) {
							realmResources.remove(r);
							resourceRealm.removeResource(r);
							guiRegistry.updateResource(
									ResourceUpdateType.DELETE, r);
						}
					}
				}

				// Look for new resources
				for (Resource r : newResources) {
					if (!existingResourceUIDList.contains(r.getUid())) {
						log.info(String.format(
								"Found a new resource (%s) '%s'", r.getUid(),
								r.getName()));
						if (onCreatedResource(r)) {
							realmResources.add(r);
							resourceRealm.addResource(r);
							guiRegistry.updateResource(
									ResourceUpdateType.CREATE, r);
						}
					}
				}

				// Look for new updated
				for (Resource r : newResources) {
					Resource current = resourceIds.get(r.getUid());
					if (current != null) {
						if (!Objects.equals(current.getModified(),
								r.getModified())) {
							log.info(String.format(
									"Found an update resource (%s) '%s'",
									r.getUid(), r.getName()));
							if (onUpdatedResource(r)) {
								realmResources.set(
										realmResources.indexOf(current), r);
								guiRegistry.updateResource(
										ResourceUpdateType.UPDATE, r);
							}
						}
					}
				}

			}
		} catch (IOException ioe) {
			log.error("Failed to check if resource update neede for "
					+ AbstractServicePlugin.this.getClass(), ioe);
		}
	}

	private void getResourceUIDList(Set<String> existingResources,
			List<Resource> resourceList) {
		for (Resource r : resourceList) {
			if (existingResources.contains(r.getUid())) {
				log.warn(String
						.format("More than one resource with a UID of %s was found. This should not happen",
								r.getUid()));
			} else {
				existingResources.add(r.getUid());
			}
		}
	}
}
