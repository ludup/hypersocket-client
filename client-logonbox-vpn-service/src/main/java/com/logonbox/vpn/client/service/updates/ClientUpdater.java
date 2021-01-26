package com.logonbox.vpn.client.service.updates;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.HypersocketVersion;
import com.hypersocket.extensions.AbstractExtensionUpdater;
import com.hypersocket.extensions.ExtensionHelper;
import com.hypersocket.extensions.ExtensionPlace;
import com.hypersocket.extensions.ExtensionTarget;
import com.hypersocket.extensions.ExtensionVersion;
import com.hypersocket.extensions.JsonExtensionPhase;
import com.hypersocket.extensions.JsonExtensionPhaseList;
import com.hypersocket.extensions.JsonExtensionUpdate;
import com.hypersocket.utils.FileUtils;
import com.hypersocket.utils.TrustModifier;
import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.common.client.GUIRegistry;

public class ClientUpdater extends AbstractExtensionUpdater {
	static Logger log = LoggerFactory.getLogger(ClientUpdater.class);

	private GUIRegistry gui;
	private ExtensionTarget target;
	private ExtensionPlace extensionPlace;
	private LocalContext cctx;

	public ClientUpdater(GUIRegistry gui, ExtensionPlace extensionPlace, ExtensionTarget target, LocalContext cctx) {
		super();
		this.cctx = cctx;
		this.gui = gui;
		this.extensionPlace = extensionPlace;
		this.target = target;
	}

	@Override
	protected InputStream downloadFromUrl(URL url) throws IOException {
		URLConnection con = url.openConnection();
		// TODO make this configurable or have a way to prompt user to verify
		// certificate
		TrustModifier.relaxHostChecking(con);
		return con.getInputStream();
	}

	@Override
	protected void onUpdateStart(long totalBytesExpected) {
		gui.onUpdateStart(extensionPlace.getApp(), totalBytesExpected);
	}

	@Override
	protected void onUpdateProgress(long sincelastProgress, long totalSoFar, long totalBytesExpected) {
		gui.onUpdateProgress(extensionPlace.getApp(), sincelastProgress, totalSoFar, totalBytesExpected);
	}

	@Override
	protected void onUpdateFailure(Throwable e) {
		gui.onUpdateFailure(extensionPlace.getApp(), e);
	}

	@Override
	public ExtensionPlace getExtensionPlace() {
		return extensionPlace;
	}

	@Override
	public ExtensionTarget[] getUpdateTargets() {
		return new ExtensionTarget[] { target };
	}

	@Override
	public String getVersion() {
		return HypersocketVersion.getVersion();
	}

	@Override
	protected Map<String, ExtensionVersion> onResolveExtensions(String version) throws IOException {
		if (cctx.getClientService().isTrackServerVersion()) {
			JsonExtensionUpdate v = cctx.getClientService().getUpdates();
			return ExtensionHelper.resolveExtensions(true,
					FileUtils.checkEndsWithSlash(AbstractExtensionUpdater.getExtensionStoreRoot()) + "api/store/repos2",
					v.getResource().getRepos(), v.getResource().getLatestVersion(), HypersocketVersion.getSerial(),
					"Hypersocket Client", v.getResource().getCustomer(), extensionPlace, true, null,
					getUpdateTargets());
		} else {
			JsonExtensionPhaseList v = cctx.getClientService().getPhases();
			String configuredPhase = cctx.getConfigurationService().getValue("phase", "");
			JsonExtensionPhase phase = null;
			if (!configuredPhase.equals("")) {
				phase = v.getResultByName(configuredPhase);
			}
			if (phase == null) {
				phase = v.getFirstResult();
			}
			if (phase == null) {
				throw new IOException("No extension phases discovered, updates will not be possible.");
			}

			return ExtensionHelper.resolveExtensions(true,
					FileUtils.checkEndsWithSlash(AbstractExtensionUpdater.getExtensionStoreRoot()) + "api/store/repos2",
					new String[] { "hypersocket-client" }, phase.getVersion(), HypersocketVersion.getSerial(),
					"Hypersocket Client", "Public", extensionPlace, true, null, getUpdateTargets());
		}
	}

	@Override
	protected void onExtensionUpdateComplete(ExtensionVersion def) {

	}

	@Override
	public boolean hasLocalRepository(String version) {
		return false;
	}

	@Override
	public File getLocalRepositoryFile() {
		return null;
	}

	@Override
	protected void onUpdateComplete(long totalBytesTransfered, int totalUpdates) {
		gui.onUpdateComplete(extensionPlace.getApp(), totalBytesTransfered);
	}

	@Override
	public Set<String> getNewFeatures() {
		return Collections.<String>emptySet();
	}

	@Override
	protected boolean getInstallMandatoryExtensions() {
		return "true".equals(System.getProperty("logonbox.vpn.updater.installMandatoryExtensions", "true"));
	}
}
