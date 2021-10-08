package com.logonbox.vpn.client.service.updates;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.freedesktop.dbus.exceptions.DBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypersocket.extensions.ExtensionPlace;
import com.hypersocket.extensions.ExtensionTarget;
import com.hypersocket.extensions.JsonExtensionPhase;
import com.hypersocket.extensions.JsonExtensionPhaseList;
import com.hypersocket.extensions.JsonExtensionUpdate;
import com.hypersocket.json.version.Version;
import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.common.client.Connection;
import com.logonbox.vpn.common.client.dbus.VPN;
import com.logonbox.vpn.common.client.dbus.VPNFrontEnd;

public class HypersocketUpdateServiceImpl extends AbstractHypersocketUpdateServiceImpl implements UpdateService {

	static Logger log = LoggerFactory.getLogger(HypersocketUpdateServiceImpl.class);

	private Set<ExtensionTarget> appsToUpdate = new LinkedHashSet<>();
	private boolean guiNeedsSeparateUpdate;

	public HypersocketUpdateServiceImpl(LocalContext context) {
		super(context);
	}

	public JsonExtensionUpdate getUpdates() {
		log.info("Finding highest version from all connections.");
		ObjectMapper mapper = new ObjectMapper();
		/* Find the server with the highest version */
		Version highestVersion = null;
		JsonExtensionUpdate highestVersionUpdate = null;
		Connection highestVersionConnection = null;
		for (Connection connection : context.getClientService().getConnections(null)) {
			try {
				URL url = new URL(connection.getUri(false) + "/api/extensions/checkVersion");
				if (log.isDebugEnabled())
					log.info(String.format("Trying %s.", url));
				URLConnection urlConnection = url.openConnection();
				urlConnection.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(10));
				urlConnection.setReadTimeout((int) TimeUnit.SECONDS.toMillis(10));
				try (InputStream in = urlConnection.getInputStream()) {
					JsonExtensionUpdate extensionUpdate = mapper.readValue(in, JsonExtensionUpdate.class);
					Version version = new Version(extensionUpdate.getResource().getCurrentVersion());
					if (highestVersion == null || version.compareTo(highestVersion) > 0) {
						highestVersion = version;
						highestVersionUpdate = extensionUpdate;
						highestVersionConnection = connection;
					}
				}
			} catch (IOException ioe) {
				if (log.isDebugEnabled())
					log.info(String.format("Skipping %s:%d because it appears offline.", connection.getHostname(),
							connection.getPort()), ioe);
				else
					log.info(String.format("Skipping %s:%d because it appears offline.", connection.getHostname(),
							connection.getPort()));
			}
		}
		if (highestVersionUpdate == null) {
			throw new IllegalStateException("Failed to get most recent version from any servers.");
		}
		log.info(String.format("Highest version available is %s, from server %s", highestVersion,
				highestVersionConnection.getDisplayName()));
		return highestVersionUpdate;
	}

	@Override
	protected void onClearUpdateState() {
		appsToUpdate.clear();
	}

	@Override
	protected boolean doUpdate(Version localVersion, boolean checkOnly) {

		int updates = 0;
		guiNeedsSeparateUpdate = true;

		Collection<VPNFrontEnd> frontEnds = context.getFrontEnds();
		List<ClientUpdater> updaters = new ArrayList<>();

		/*
		 * For the client service, we use the local 'extension place'
		 */
		appsToUpdate.add(ExtensionTarget.CLIENT_SERVICE);
		ExtensionPlace defaultExt = ExtensionPlace.getDefault();
		defaultExt.setDownloadAllExtensions(true);
		updaters.add(new ClientUpdater(defaultExt, ExtensionTarget.CLIENT_SERVICE, context));

		/*
		 * For the GUI (and CLI), we get the extension place remotely, as the clients
		 * themselves are best placed to know what extensions it has and where they
		 * stored.
		 *
		 * However, it's possible the GUI or CLI is not yet running, so we only do this
		 * if it is available. If this happens we may need to update it as well when it
		 * eventually starts
		 */
		for (VPNFrontEnd fe : frontEnds) {
			if (!fe.isUpdated()) {
				guiNeedsSeparateUpdate = false;
				appsToUpdate.add(fe.getTarget());
				updaters.add(new ClientUpdater(fe.getPlace(), fe.getTarget(), context));
			}
		}

		try {
			if (!checkOnly) {
				context.sendMessage(new VPN.UpdateInit("/com/logonbox/vpn", appsToUpdate.size()));
			}

			for (ClientUpdater update : updaters) {
				if (updateCancelled)
					throw new IOException("Cancelled by user.");
				if ((checkOnly && update.checkForUpdates()) || (!checkOnly && update.update())) {
					updates++;
					log.info(String.format("    %s (%s) - needs update", update.getExtensionPlace().getApp(),
							update.getExtensionPlace().getDir()));
				} else
					log.info(String.format("    %s (%s) - no updates", update.getExtensionPlace().getApp(),
							update.getExtensionPlace().getDir()));
			}

			if (updates > 0) {
				/* Make sure available version is updated and ready for the events */
				lastAvailableVersion = null;
				getAvailableVersion();
			}

			if (!checkOnly) {
				if (updates > 0) {
					log.info("Applying updates");

					/*
					 * If when we started the update, the GUI wasn't attached, but it is now, then
					 * instead of restarting immediately, try to update any client extensions too
					 */
					if (guiNeedsSeparateUpdate && !frontEnds.isEmpty()) {
						appsToUpdate.clear();
						for (VPNFrontEnd fe : frontEnds) {
							if (!fe.isUpdated()) {
								appsToUpdate.add(fe.getTarget());
								updaters.add(new ClientUpdater(fe.getPlace(), fe.getTarget(), context));
							}
						}
						if (appsToUpdate.isEmpty()) {
							allowUpdateCancel = false;
							/* Still nothing else to update, we are done */
							context.sendMessage(new VPN.UpdateDone("/com/logonbox/vpn", true, ""));
							log.info("Update complete, restarting.");
							/* Delay restart to let signals be sent */
							context.getClientService().getTimer().schedule(() -> context.getClientService().restart(),
									5, TimeUnit.SECONDS);
						} else {
							context.sendMessage(new VPN.UpdateInit("/com/logonbox/vpn", appsToUpdate.size()));
							int updated = 0;
							for (ClientUpdater update : updaters) {
								if (updateCancelled)
									throw new IOException("Cancelled by user.");
								if (update.update())
									updated++;
							}
							context.sendMessage(new VPN.UpdateDone("/com/logonbox/vpn", updated > 0, ""));
							if (updated > 0) {
								log.info("Update complete, restarting.");
								/* Delay restart to let signals be sent */
								allowUpdateCancel = false;
								context.getClientService().getTimer()
										.schedule(() -> context.getClientService().restart(), 5, TimeUnit.SECONDS);
							}
						}
					} else {
						allowUpdateCancel = false;
						context.sendMessage(new VPN.UpdateDone("/com/logonbox/vpn", true, ""));
						log.info("Update complete, restarting.");
						/* Delay restart to let signals be sent */
						context.getClientService().getTimer().schedule(() -> context.getClientService().restart(), 5,
								TimeUnit.SECONDS);
					}
				} else {
					context.sendMessage(new VPN.UpdateDone("/com/logonbox/vpn", false, "Nothing to update."));
				}
			} else {
				if (updates > 0) {
					context.sendMessage(new VPN.UpdateAvailable("/com/logonbox/vpn"));
				}
			}

		} catch (IOException | DBusException e) {
			if (log.isDebugEnabled()) {
				log.error("Failed to execute update job.", e);
			} else {
				log.warn(String.format("Failed to execute update job. %s", e.getMessage()));
			}
			return false;
		}

		return updates > 0;
	}

	@Override
	public void updateFrontEnd(VPNFrontEnd frontEnd) {
		if (frontEnd.isInteractive()) {
			if (guiNeedsSeparateUpdate) {
				/*
				 * If the client hasn't supplied the extensions it is using, then we can't do
				 * any updates. It is probably running outside of Forker, so isn't supplied the
				 * list
				 */
				if (frontEnd.getPlace().getBootstrapArchives().isEmpty()) {
					log.warn(String.format(
							"Front-end %s did not supply its list of extensions. Probably running in a development environment. Skipping updates.",
							frontEnd.getPlace().getApp()));
					appsToUpdate.clear();
				} else if (Boolean.getBoolean("logonbox.automaticUpdates")) {

					/* Do the separate GUI update */
					appsToUpdate.add(frontEnd.getTarget());
					ClientUpdater guiJob = new ClientUpdater(frontEnd.getPlace(), frontEnd.getTarget(), context);

					try {
						context.sendMessage(new VPN.UpdateInit("/com/logonbox/vpn", appsToUpdate.size()));
						try {
							boolean atLeastOneUpdate = guiJob.update();
							if (atLeastOneUpdate)
								log.info("Update complete, at least one found so restarting.");
							else
								log.info("No updates available.");
							context.sendMessage(new VPN.UpdateDone("/com/logonbox/vpn", atLeastOneUpdate, null));
						} catch (IOException e) {
							if (log.isDebugEnabled())
								log.error("Failed to update GUI.", e);
							else
								log.error(String.format("Failed to update GUI. %s", e.getMessage()));
							context.sendMessage(new VPN.UpdateDone("/com/logonbox/vpn", false, e.getMessage()));
						}
					} catch (Exception re) {
						log.error("GUI refused to update, ignoring.", re);
						try {
							context.sendMessage(new VPN.UpdateDone("/com/logonbox/vpn", false, null));
						} catch (DBusException e) {
							throw new IllegalStateException("Failed to send message.", e);
						}
					}
				}
			} else if (updating) {
				/*
				 * If we register while an update is taking place, try to make the client catch
				 * up and show the update progress window
				 */
				try {
					context.sendMessage(new VPN.UpdateInit("/com/logonbox/vpn", appsToUpdate.size()));
				} catch (DBusException e) {
					throw new IllegalStateException("Failed to send event.", e);
				}
			} else if (isAutomaticUpdates() && getAttempts() == 0) {
				/*
				 * Otherwise if automatic updates are enabled, start one now
				 */
				update(false);
			}
		}
	}

	@Override
	protected String doGetAvailableVersion(Version localVersion) {
		if (isTrackServerVersion()) {
			if (context.getClientService().getStatus(null).isEmpty()) {
				/*
				 * We don't have any servers configured, so no version can yet be known
				 */
				return "";
			} else {
				/*
				 * We have the version of the server we are connecting to, check if there are
				 * any updates for this version
				 */
				try {
					JsonExtensionUpdate v = getUpdates();
					Version remoteVersion = new Version(v.getResource().getCurrentVersion());
					if (remoteVersion.compareTo(localVersion) < 1)
						return "";
					else
						return v.getResource().getCurrentVersion();
				} catch (IllegalStateException ise) {
					return "";
				}
			}
		} else {
			JsonExtensionPhaseList v = getPhases();
			String configuredPhase = context.getClientService().getValue("phase", "");
			JsonExtensionPhase phase = null;
			if (!configuredPhase.equals("")) {
				phase = v.getResultByName(configuredPhase);
			}
			if (phase == null) {
				phase = v.getFirstResult();
			}
			if (phase == null) {
				return "";
			}

			Version remoteVersion = new Version(phase.getVersion());
			if (remoteVersion.compareTo(localVersion) < 0) {
				System.out.println(phase.getVersion() + " is less than " + localVersion);
				return "";
			} else
				return phase.getVersion();
		}
	}
}
