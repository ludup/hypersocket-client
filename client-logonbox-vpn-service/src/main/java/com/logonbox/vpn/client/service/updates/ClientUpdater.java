package com.logonbox.vpn.client.service.updates;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.freedesktop.dbus.exceptions.DBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.extensions.AbstractExtensionUpdater;
import com.hypersocket.extensions.ExtensionHelper;
import com.hypersocket.extensions.ExtensionPlace;
import com.hypersocket.extensions.ExtensionTarget;
import com.hypersocket.extensions.ExtensionVersion;
import com.hypersocket.extensions.JsonExtensionPhase;
import com.hypersocket.extensions.JsonExtensionPhaseList;
import com.hypersocket.extensions.JsonExtensionUpdate;
import com.hypersocket.json.utils.TrustModifier;
import com.hypersocket.json.version.HypersocketVersion;
import com.hypersocket.utils.FileUtils;
import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.common.client.dbus.VPN;

public class ClientUpdater extends AbstractExtensionUpdater {
	static Logger log = LoggerFactory.getLogger(ClientUpdater.class);

	private ExtensionTarget target;
	private ExtensionPlace extensionPlace;
	private LocalContext cctx;

	public ClientUpdater(ExtensionPlace extensionPlace, ExtensionTarget target, LocalContext cctx) {
		super();
		this.cctx = cctx;
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
	protected void onExtensionDownloaded(ExtensionVersion def) {
		try {
			cctx.sendMessage(
					new VPN.ExtensionUpdated("/com/logonbox/vpn", extensionPlace.getApp(), def.getExtensionId()));
		} catch (DBusException e) {
			throw new IllegalStateException("Failed to send event.", e);
		}
	}

	@Override
	protected void onUpdateStart(long totalBytesExpected) {
		try {
			cctx.sendMessage(new VPN.UpdateStart("/com/logonbox/vpn", extensionPlace.getApp(), totalBytesExpected));
		} catch (DBusException e) {
			throw new IllegalStateException("Failed to send event.", e);
		}
	}

	@Override
	protected void onUpdateProgress(long sincelastProgress, long totalSoFar, long totalBytesExpected) {
		try {
			cctx.sendMessage(new VPN.UpdateProgress("/com/logonbox/vpn", extensionPlace.getApp(), sincelastProgress,
					totalSoFar, totalBytesExpected));
		} catch (DBusException e) {
			throw new IllegalStateException("Failed to send event.", e);
		}
	}

	@Override
	protected void onUpdateFailure(Throwable e) {
		try {
			StringWriter msg = new StringWriter();
			if (e != null)
				e.printStackTrace(new PrintWriter(msg));
			cctx.sendMessage(new VPN.UpdateFailure("/com/logonbox/vpn", extensionPlace.getApp(), msg.toString()));
		} catch (DBusException ex) {
			throw new IllegalStateException("Failed to send event.", ex);
		}
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
			if (cctx.getClientService().getStatus(null).isEmpty())
				return Collections.emptyMap();
			else {
				JsonExtensionUpdate v = cctx.getClientService().getUpdates();
				return ExtensionHelper.resolveExtensions(true,
						FileUtils.checkEndsWithSlash(AbstractExtensionUpdater.getExtensionStoreRoot())
								+ "api/store/repos2",
						new String[] { "logonbox-vpn-client" }, v.getResource().getLatestVersion(),
						HypersocketVersion.getSerial(), "LogonBox VPN Client", v.getResource().getCustomer(),
						extensionPlace, true, null, getUpdateTargets());
			}
		} else {
			JsonExtensionPhaseList v = cctx.getClientService().getPhases();
			String configuredPhase = cctx.getClientService().getValue("phase", "");
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
					new String[] { "logonbox-vpn-client" }, phase.getVersion(), HypersocketVersion.getSerial(),
					"LogonBox VPN Client", "Public", extensionPlace, true, null, getUpdateTargets());
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
		try {
			cctx.sendMessage(new VPN.UpdateComplete("/com/logonbox/vpn", ExtensionPlace.getDefault().getApp(),
					totalBytesTransfered));
		} catch (DBusException e) {
			throw new IllegalStateException("Failed to send event.", e);
		}
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
