package com.logonbox.vpn.client.service.updates;

import java.io.IOException;

import org.freedesktop.dbus.exceptions.DBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.extensions.AbstractExtensionUpdater;
import com.hypersocket.extensions.ExtensionTarget;
import com.hypersocket.json.version.HypersocketVersion;
import com.hypersocket.json.version.Version;
import com.hypersocket.utils.FileUtils;
import com.hypersocket.utils.HypersocketUtils;
import com.install4j.api.context.UserCanceledException;
import com.install4j.api.update.ApplicationDisplayMode;
import com.install4j.api.update.UpdateChecker;
import com.install4j.api.update.UpdateDescriptor;
import com.install4j.api.update.UpdateDescriptorEntry;
import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.common.client.dbus.VPN;
import com.logonbox.vpn.common.client.dbus.VPNFrontEnd;

public class Install4JUpdateServiceImpl extends AbstractHypersocketUpdateServiceImpl implements UpdateService {


	private static final String[] REPOS = new String[] { "logonbox-vpn-client-packages" };
	
	static Logger log = LoggerFactory.getLogger(Install4JUpdateServiceImpl.class);
	private UpdateDescriptorEntry best;

	public Install4JUpdateServiceImpl(LocalContext context) {
		super(context);
	}

	@Override
	public void updateFrontEnd(VPNFrontEnd frontEnd) {
		/* Nothing to do? */
	}

	protected String buildUpdateUrl(Version version) {
		String configuredPhase = context.getClientService().getValue("phase", "");
		return FileUtils.checkEndsWithSlash(AbstractExtensionUpdater.getExtensionStoreRoot())
				+ String.format("api/store/install4j/%s/%s/%s/%s", configuredPhase,
						HypersocketUtils.csv(REPOS),
						HypersocketVersion.getSerial(), HypersocketUtils.csv(
								new ExtensionTarget[] { ExtensionTarget.CLIENT_SERVICE, ExtensionTarget.CLIENT_GUI }));
	}

	@Override
	protected String doGetAvailableVersion(Version localVersion) {
		try {
			if (best == null) {
				doGetLatest(localVersion);
			}
			return best.getNewVersion();
		} catch (IOException ioe) {
			return "";
		} catch (UserCanceledException e) {
			throw new IllegalStateException("Cancelled.", e);
		}
	}

	protected void doGetLatest(Version localVersion) throws UserCanceledException, IOException {
		if (best == null) {
			UpdateDescriptor update = UpdateChecker.getUpdateDescriptor(buildUpdateUrl(localVersion),
					ApplicationDisplayMode.UNATTENDED);
			best = update.getPossibleUpdateEntry();
			if (best == null)
				throw new IOException("No latest version available.");
		}
	}

	@Override
	protected boolean doUpdate(Version localVersion, boolean checkOnly) throws IOException, DBusException {

		best = null;
		try {
			doGetLatest(localVersion);
		} catch (UserCanceledException e) {
			throw new IllegalStateException("Cancelled.", e);
		}

		Version remoteVersion = new Version(best.getNewVersion());
		log.info(String.format("Latest version is %s", remoteVersion));

		boolean update = remoteVersion.compareTo(localVersion) > 0;
		if (update) {
			if (checkOnly) {
				context.sendMessage(new VPN.UpdateAvailable("/com/logonbox/vpn"));
			} else
				throw new UnsupportedOperationException("Updates not supported by service.");
		}
		return update;

	}

	@Override
	protected void onClearUpdateState() {
	}

}
