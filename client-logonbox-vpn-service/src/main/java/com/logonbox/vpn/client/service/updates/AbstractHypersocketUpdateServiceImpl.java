package com.logonbox.vpn.client.service.updates;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.freedesktop.dbus.exceptions.DBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypersocket.extensions.AbstractExtensionUpdater;
import com.hypersocket.extensions.JsonExtensionPhase;
import com.hypersocket.extensions.JsonExtensionPhaseList;
import com.hypersocket.json.version.HypersocketVersion;
import com.hypersocket.json.version.Version;
import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.client.Main;
import com.logonbox.vpn.common.client.ConfigurationRepository;

public abstract class AbstractHypersocketUpdateServiceImpl implements UpdateService {
	static Logger log = LoggerFactory.getLogger(AbstractHypersocketUpdateServiceImpl.class);

	static final int POLL_RATE = 30;

	private static final int PHASES_TIMEOUT = 3600 * 24;

	/**
	 * NOTE: Currently these must be changed in preparation for every major branch.
	 * Order matters, the first in this list is the one that will be chosen as the
	 * default if no others are set, or if the set one doesn't exist.
	 * 
	 * DO NOT FORGET TO UPDATE options.properties if these are changed.
	 */
	private static final List<String> VALID_PHASES = Arrays.asList("vpn_client_stable_2_4x", "vpn_client_ea_2_4x",
			"nightly2_4x");
	
	private static final long VERSION_CHECK_TIMEOUT = TimeUnit.MINUTES.toMillis(10);

	protected boolean allowUpdateCancel = true;
	protected LocalContext context;
	protected String lastAvailableVersion;
	protected boolean needsUpdate;
	protected boolean updateCancelled;
	protected Object updateLock = new Object();
	protected Thread updateThread;
	protected boolean updating;

	private long lastAvailableVersionRetrieved;
	private JsonExtensionPhaseList phaseList;
	private long phasesLastRetrieved = 0;

	public AbstractHypersocketUpdateServiceImpl(LocalContext context) {
		this.context = context;
	}

	@Override
	public final void cancelUpdate() {
		synchronized (updateLock) {
			if (!isUpdating())
				throw new IllegalStateException("Not updating.");
			if (!allowUpdateCancel)
				throw new IllegalStateException("Cancel is not allowed at this stage of the update.");
			if (updateCancelled)
				log.warn("Update already cancelled, trying to interrupt again.");
			updateCancelled = true;
			updateThread.interrupt();
		}

	}

	@Override
	public final void clearCaches() {
		this.phaseList = null;
	}

	@Override
	public final void clearUpdateState() {
		log.debug("Clearing update state.");
		needsUpdate = false;
		updating = false;
		allowUpdateCancel = true;
		updateCancelled = false;
		onClearUpdateState();
	}

	@Override
	public final String getAvailableVersion() {
		if (lastAvailableVersion == null
				|| lastAvailableVersionRetrieved < System.currentTimeMillis() - VERSION_CHECK_TIMEOUT) {
			lastAvailableVersion = doGetAvailableVersion(getLocalVersion());
			lastAvailableVersionRetrieved = System.currentTimeMillis();
		}
		return lastAvailableVersion;
	}

	@Override
	public final JsonExtensionPhaseList getPhases() {
		JsonExtensionPhaseList l = new JsonExtensionPhaseList();
		if (isTrackServerVersion()) {
			/*
			 * Return an empty phase list, the client should not be showing a phase list if
			 * tracking server version
			 */
			return l;
		} else {
			if (this.phaseList == null || phasesLastRetrieved < System.currentTimeMillis() - (PHASES_TIMEOUT * 1000)) {
				ObjectMapper mapper = new ObjectMapper();
				String extensionStoreRoot = AbstractExtensionUpdater.getExtensionStoreRoot();
				phasesLastRetrieved = System.currentTimeMillis();
				try {
					URL url = new URL(extensionStoreRoot + "api/store/phases");
					URLConnection urlConnection = url.openConnection();
					this.phaseList = l;
					try (InputStream in = urlConnection.getInputStream()) {
						this.phaseList = mapper.readValue(in, JsonExtensionPhaseList.class);
					}
				} catch (IOException ioe) {
					this.phaseList = l;
					throw new IllegalStateException(
							String.format("Failed to get extension phases from %s.", extensionStoreRoot), ioe);
				}

				/* Now filter out the phases we actually want */

				if (!isUseAllCloudPhases()) {
					List<JsonExtensionPhase> pl = new ArrayList<>();
					for (String p : VALID_PHASES) {
						pl.add(this.phaseList.getResultByName(p));
					}
					this.phaseList.setResources(pl.toArray(new JsonExtensionPhase[0]));
				}
			}
			return this.phaseList;
		}
	}

	@Override
	public final boolean isNeedsUpdating() {
		return needsUpdate;
	}

	@Override
	public final boolean isTrackServerVersion() {
		return "true".equalsIgnoreCase(System.getProperty("logonbox.vpn.updates.trackServerVersion", "false"));
	}

	@Override
	public final boolean isUpdating() {
		return updating;
	}

	@Override
	public final void resetUpdateState() {
		lastAvailableVersion = null;
		clearUpdateState();
	}

	@Override
	public final void update(boolean checkOnly) {
		synchronized (updateLock) {
			if (updating)
				throw new IllegalStateException("Already updating.");

			updateThread = Thread.currentThread();
			clearUpdateState();
			updating = true;
		}

		try {
			if (checkOnly)
				log.info("Checking for updates");
			else
				log.info("Getting updates to apply");

			needsUpdate = doUpdate(getLocalVersion(), checkOnly);

		} catch (Exception re) {
			if (log.isDebugEnabled()) {
				log.error("Failed to get GUI extension information. Update aborted.", re);
			} else {
				log.error(
						String.format("Failed to get GUI extension information. Update aborted. %s", re.getMessage()));
			}
		} finally {
			synchronized (updateLock) {
				updating = false;
				updateThread = null;
			}
		}
	}
	
	protected Version getLocalVersion() {
		return new Version(HypersocketVersion.getVersion(Main.ARTIFACT_COORDS));
	}

	protected abstract String doGetAvailableVersion(Version localVersion);

	protected abstract boolean doUpdate(Version localVersion, boolean checkOnly) throws IOException, DBusException;

	protected final int getAttempts() {
		try {
			return Integer.parseInt(System.getProperty("forker.info.attempts"));
		} catch (Exception e) {
			// First start, ignore
			return 0;
		}
	}

	protected final boolean isAutomaticUpdates() {
		boolean automaticUpdates = Boolean
				.valueOf(context.getClientService().getValue(ConfigurationRepository.AUTOMATIC_UPDATES,
						String.valueOf(ConfigurationRepository.AUTOMATIC_UPDATES_DEFAULT)));
		return automaticUpdates;
	}

	protected final boolean isUseAllCloudPhases() {
		return "true".equalsIgnoreCase(System.getProperty("logonbox.vpn.updates.useAllCloudPhases", "false"));
	}

	protected abstract void onClearUpdateState();
}
