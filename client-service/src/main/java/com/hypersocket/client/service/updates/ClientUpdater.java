package com.hypersocket.client.service.updates;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypersocket.HypersocketVersion;
import com.hypersocket.client.HypersocketClient;
import com.hypersocket.client.rmi.Connection;
import com.hypersocket.client.rmi.GUIRegistry;
import com.hypersocket.extensions.AbstractExtensionUpdater;
import com.hypersocket.extensions.ExtensionHelper;
import com.hypersocket.extensions.ExtensionPlace;
import com.hypersocket.extensions.ExtensionTarget;
import com.hypersocket.extensions.ExtensionVersion;
import com.hypersocket.extensions.JsonExtensionUpdate;
import com.hypersocket.utils.TrustModifier;

public class ClientUpdater extends AbstractExtensionUpdater {
	static Logger log = LoggerFactory.getLogger(ClientUpdater.class);

	private GUIRegistry gui;
	private HypersocketClient<Connection> hypersocketClient;
	private ExtensionTarget target;
	private ExtensionPlace extensionPlace;
	private Connection connection; // Will probably be used again

	public ClientUpdater(GUIRegistry gui, Connection connection,
			HypersocketClient<Connection> hypersocketClient,
			ExtensionPlace extensionPlace, ExtensionTarget target) {
		super();
		this.connection = connection;
		this.gui = gui;
		this.hypersocketClient = hypersocketClient;
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
		gui.onUpdateStart(extensionPlace.getApp(), totalBytesExpected, connection);
	}

	@Override
	protected void onUpdateProgress(long sincelastProgress, long totalSoFar, long totalBytesExpected) {
		gui.onUpdateProgress(extensionPlace.getApp(), sincelastProgress,
				totalSoFar,totalBytesExpected);
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
		
		ObjectMapper mapper = new ObjectMapper();
		String update = hypersocketClient.getTransport().get("extensions/checkVersion");
		JsonExtensionUpdate v =  mapper.readValue(update, JsonExtensionUpdate.class);
		
		return ExtensionHelper
				.resolveExtensions(
						true,
						System.getProperty("hypersocket.archivesURL",
								"https://updates2.hypersocket.com/hypersocket/api/store/repos"),
						v.getResource().getRepos(),
						v.getResource().getLatestVersion(),
						HypersocketVersion.getSerial(),
						"Hypersocket Client",
						v.getCustomer(),
						extensionPlace,
						true,
						getUpdateTargets());
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

}
