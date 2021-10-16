package com.logonbox.vpn.client.service.updates;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.freedesktop.dbus.exceptions.DBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.extensions.JsonExtensionPhase;
import com.hypersocket.extensions.JsonExtensionPhaseList;
import com.hypersocket.json.version.Version;
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
//		return FileUtils.checkEndsWithSlash(AbstractExtensionUpdater.getExtensionStoreRoot())
//				+ String.format("api/store/install4j/%s/%s/%s/%s", configuredPhase,
//						HypersocketUtils.csv(REPOS),
//						HypersocketVersion.getSerial(), HypersocketUtils.csv(
//								new ExtensionTarget[] { ExtensionTarget.CLIENT_SERVICE, ExtensionTarget.CLIENT_GUI }));
		
		return "https://logonbox-packages.s3.eu-west-1.amazonaws.com/logonbox-vpn-client/" + configuredPhase + "/updates.xml";
	}

	@Override
	public JsonExtensionPhaseList getPhases() {
		JsonExtensionPhaseList pl = new JsonExtensionPhaseList();
		List<JsonExtensionPhase> l = new ArrayList<>();
		for(String p : new String[] { "nightly", "ea", "stable"} ) {
			JsonExtensionPhase po = new JsonExtensionPhase();
			po.setName(p);
			po.setVersion(p);
			po.setPublicPhase(true);
			l.add(po);
		}
		pl.setResult(l.toArray(new JsonExtensionPhase[0]));
		return pl;
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
			String uurl = buildUpdateUrl(localVersion);
			log.info("Check for updates in " + localVersion + " from " + uurl);
			UpdateDescriptor update = UpdateChecker.getUpdateDescriptor(uurl,
					ApplicationDisplayMode.UNATTENDED);
			best = update.getPossibleUpdateEntry();
			if (best == null) {
				best = update.getEntries()[0];
//				throw new IOException("No latest version available.");
			}
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
			} else {
				UpdateChecker.executeScheduledUpdate(Collections.emptyList(), true, () -> {
					
				});
//				throw new UnsupportedOperationException("Updates not supported by service.");
			}
		}
		return update;

	}

	@Override
	protected void onClearUpdateState() {
	}

}
