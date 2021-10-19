package com.logonbox.vpn.common.client;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.install4j.api.context.UserCanceledException;
import com.install4j.api.launcher.ApplicationLauncher;
import com.install4j.api.update.ApplicationDisplayMode;
import com.install4j.api.update.UpdateChecker;
import com.install4j.api.update.UpdateDescriptor;
import com.install4j.api.update.UpdateDescriptorEntry;

public class Install4JUpdateServiceImpl implements UpdateService {

	static Logger log = LoggerFactory.getLogger(Install4JUpdateServiceImpl.class);
	private UpdateDescriptorEntry best;
	private AbstractDBusClient context;
	private boolean updating;
	private String availableVersion = "";
	private boolean needsUpdate;

	public Install4JUpdateServiceImpl(AbstractDBusClient context) {
		this.context = context;
		resetAvailableVersion();
	}

	@Override
	public boolean isUpdatesEnabled() {
		return "false".equals(System.getProperty("hypersocket.development.noUpdates", "false"));
	}

	private boolean isNightly(String phase) {
		return phase.startsWith("nightly");
	}

	@Override
	public String[] getPhases() {
		List<String> l = new ArrayList<>();
		for (String p : new String[] { "nightly", "ea", "stable" }) {
			if (!isNightly(p) || (Boolean.getBoolean("logonbox.vpn.updates.nightly")
					|| Boolean.getBoolean("hypersocket.development"))) {
				l.add(p);
			}
		}
		return l.toArray(new String[0]);
	}

	protected String buildUpdateUrl() {
		String configuredPhase = context.getVPN().getValue("phase", "");
		return "https://logonbox-packages.s3.eu-west-1.amazonaws.com/logonbox-vpn-client/" + configuredPhase
				+ "/updates.xml";
	}

	@Override
	public boolean isNeedsUpdating() {
		return needsUpdate;
	}

	@Override
	public boolean isUpdating() {
		return updating;
	}

	@Override
	public String getAvailableVersion() {
		return availableVersion;
	}

	@Override
	public void deferUpdate() {
	}

	@Override
	public void checkForUpdate() throws IOException {
		String uurl = buildUpdateUrl();
		log.info("Check for updates in " + context.getVersion() + " from " + uurl);
		UpdateDescriptor update;
		try {
			update = UpdateChecker.getUpdateDescriptor(uurl, ApplicationDisplayMode.GUI);
			best = update.getPossibleUpdateEntry();
			if (best == null) {
				log.info("No version available.");
				resetAvailableVersion();
			} else {
				availableVersion = best.getNewVersion();
				log.info(availableVersion + " is available.");
				
				/* TODO: This will allow downgrades. */
				if(!availableVersion.equals(context.getVersion())) {
					log.info("Update available.");
					needsUpdate = true;
				}
				else {
					log.info("No update needed.");
					needsUpdate = false;	
				}
			}
		} catch (UserCanceledException e) {
			log.info("Cancelled.");
			resetAvailableVersion();
			throw new InterruptedIOException("Cancelled.");
		} catch(Exception e) {
			log.info("Failed.", e);
			resetAvailableVersion();
			
		}
	}

	protected void resetAvailableVersion() {
		availableVersion = context.getVersion();
		needsUpdate = false;
	}

	@Override
	public void update() throws IOException {
		if (best == null)
			checkForUpdate();
		if (!isNeedsUpdating())
			throw new IOException("Update not needed.");
		ApplicationLauncher.launchApplicationInProcess("2103", null, new ApplicationLauncher.Callback() {
			public void exited(int exitValue) {
				// TODO add your code here (not invoked on event dispatch thread)
			}

			public void prepareShutdown() {
				// TODO add your code here (not invoked on event dispatch thread)
			}
		}, ApplicationLauncher.WindowMode.FRAME, null);
	}

}
