package com.hypersocket.client.service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import com.hypersocket.client.LocalContext;
import com.hypersocket.client.rmi.Connection;
import com.hypersocket.client.rmi.GUIRegistry;
import com.hypersocket.client.rmi.Resource;

public abstract class AbstractServicePlugin<C extends ClientContext<L>, L extends LocalContext<?>>
		implements ServicePlugin<C> {

	static Logger log = LoggerFactory.getLogger(AbstractServicePlugin.class);

	static ScheduledExecutorService fingerprintChecker = Executors.newScheduledThreadPool(1);

	protected String[] urls;
	protected Map<String, String> fingerprints = new HashMap<>();
	protected HypersocketClient<Connection> serviceClient;
	protected GUIRegistry guiRegistry;
	protected ScheduledFuture<?> checkTask;
	protected C context;

	protected AbstractServicePlugin(String... urls) {
		this.urls = urls;
	}

	public boolean isUpdateNeeded() throws IOException {
		for (String url : urls) {
			String json = serviceClient.getTransport().get(String.format("%s/fingerprint", url));
			String fingerprint = fingerprints.get(url);
			JSONParser parser = new JSONParser();
			try {
				JSONObject result = (JSONObject) parser.parse(json);
				if (Boolean.TRUE.equals(result.get("success"))) {
					String latestFingerprint = (String) result.get("message");
					if (!Objects.equals(latestFingerprint, fingerprint)) {
						fingerprints.put(url, latestFingerprint);
						return true;
					}
				}
			} catch (ParseException e) {
				throw new IOException("Failed to parse response.", e);
			}
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
	public final boolean start(C context) {

		this.context = context;

		// convenience
		this.serviceClient = context.getClient();
		this.guiRegistry = context.getLocalContext().getGuiRegistry();

		if (log.isInfoEnabled()) {
			log.info("Starting Resources for " + getClass());
		}

		startResources();

		checkTask = fingerprintChecker.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				processResourceUpdates();
			}

		}, 60, 60, TimeUnit.SECONDS);

		return onStart();
	}

	protected abstract void reloadResources(List<Resource> realmResources);

	protected abstract boolean onCreatedResource(Resource resource);

	protected abstract boolean onUpdatedResource(Resource resource);

	protected abstract boolean onDeletedResource(Resource resource);

	public abstract boolean onStart();

	public abstract void onStop();

	protected abstract void startResources();

	protected int processResourceList(String json, ResourceMapper mapper, String resourceName) throws IOException {
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

	protected abstract void processResourceUpdates();
}
