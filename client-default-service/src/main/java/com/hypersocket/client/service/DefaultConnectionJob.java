package com.hypersocket.client.service;

import java.io.IOException;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypersocket.HypersocketVersion;
import com.hypersocket.Version;
import com.hypersocket.client.CredentialCache;
import com.hypersocket.client.CredentialCache.Credential;
import com.hypersocket.client.DefaultContext;
import com.hypersocket.client.HypersocketClient;
import com.hypersocket.client.HypersocketClientAdapter;
import com.hypersocket.client.HypersocketClientListener;
import com.hypersocket.client.UserCancelledException;
import com.hypersocket.client.rmi.Connection;
import com.hypersocket.json.JsonResponse;
import com.hypersocket.netty.NettyClientTransport;

public class DefaultConnectionJob extends AbstractConnectionJob<DefaultContext> {

	static Logger log = LoggerFactory.getLogger(AbstractConnectionJob.class);

	private String url;
	private Locale locale;

	public DefaultConnectionJob(String url, Locale locale, DefaultContext localContext, Connection connection) {
		super(localContext, connection);
		this.url = url;
		this.locale = locale;
	}

	@Override
	public void run() {

		if (log.isInfoEnabled()) {
			log.info("Connecting to " + url);
		}

		DefaultContext ctx = getLocalContext();
		DefaultClientServiceImpl defaultClientServiceImpl = (DefaultClientServiceImpl) ctx.getClientService();
		HypersocketClientListener<Connection> listener = new HypersocketClientAdapter<Connection>() {
			@Override
			public void disconnected(HypersocketClient<Connection> client, boolean onError) {
				defaultClientServiceImpl.disconnected(connection, client);
				log.info("Client has disconnected, informing GUI");
				ctx.getGuiRegistry().disconnected(connection, onError ? "Error occured during connection." : null);
				if (client.getAttachment().isStayConnected() && onError) {
					try {
						ctx.getClientService().scheduleConnect(connection);
					} catch (RemoteException e1) {
					}
				}
			}
		};

		ServiceClient<?> client = null;
		try {

			client = new ServiceClient<>(new NettyClientTransport(ctx.getBoss(), ctx.getWorker()),
					ctx.getClientService(), locale, listener, ctx.getResourceService(), connection,
					ctx.getGuiRegistry());

			client.connect(connection.getHostname(), connection.getPort(), connection.getPath(), locale);

			if (log.isInfoEnabled()) {
				log.info("Connected to " + url);
			}

			ctx.getGuiRegistry().transportConnected(connection);

			log.info("Awaiting authentication for " + url);
			if (StringUtils.isBlank(connection.getUsername())
					|| !ctx.getConnectionService().hasEncryptedPassword(connection)) {
				// check if we have anything in credential cache
				Credential credential = CredentialCache.getInstance().getCredentials(connection.getHostname());
				if (credential != null) {
					attemptLoginToServer(client, connection, credential.getUsername(), credential.getPassword());
				} else {
					client.login();
				}

			} else {
				attemptLoginToServer(client, connection, connection.getUsername(),
						new String(ctx.getConnectionService().getDecryptedPassword(connection)));
			}

			log.info("Received authentication for " + url);

			// Now get the current version and check against ours.
			String reply = client.getTransport().get("server/version");
			ObjectMapper mapper = new ObjectMapper();

			try {
				JsonResponse json = mapper.readValue(reply, JsonResponse.class);
				if (json.isSuccess()) {
					String[] versionAndSerial = json.getMessage().split(";");
					String version = versionAndSerial[0].trim();
					String serial = versionAndSerial[1].trim();
					updateInfo(client, version);

					/*
					 * Set the transient details. If an update is required it will be performed
					 * shortly by the client service (which will check all connections and update to
					 * the highest one
					 */
					connection.setServerVersion(version);
					connection.setSerial(serial);
					client.addListener(listener);

					if (log.isInfoEnabled()) {
						log.info("Logged into " + url);
					}

					/*
					 * Tell the GUI we are now completely connected. The GUI should NOT yet load any
					 * resources, as we need to check if there are any updates to do firstContext
					 */
					ctx.getGuiRegistry().ready(connection);

					/*
					 * Now check for updates. If there are any, we don't start any plugins for this
					 * connection, and the GUI will not be told to load its resources
					 */
					if (!defaultClientServiceImpl.update(connection, client)) {
						defaultClientServiceImpl.startPlugins(client);
						ctx.getGuiRegistry().loadResources(connection);
					}
					ctx.getClientService().finishedConnecting(connection, client);

				} else {
					throw new Exception("Server refused to supply version. " + json.getMessage());
				}
			} catch (Exception jpe) {
				if (log.isErrorEnabled()) {
					log.error("Failed to parse server version response " + reply, jpe);
				}
				client.disconnect(false);
				ctx.getGuiRegistry().failedToConnect(connection, reply);
				defaultClientServiceImpl.failedToConnect(connection, jpe);
			}

		} catch (Throwable e) {
			if (log.isErrorEnabled()) {
				log.error("Failed to connect " + url, e);
			}
			ctx.getGuiRegistry().failedToConnect(connection, e.getMessage());
			defaultClientServiceImpl.failedToConnect(connection, e);

			if (!(e instanceof UserCancelledException)) {
				if (StringUtils.isNotBlank(connection.getUsername())
						&& StringUtils.isNotBlank(connection.getEncryptedPassword())) {
					if (connection.isStayConnected()) {
						try {
							ctx.getClientService().scheduleConnect(connection);
							return;
						} catch (RemoteException e1) {
						}
					}
				}
			}
		}

	}

	private void attemptLoginToServer(ServiceClient<?> client, Connection connection, String username, String password)
			throws IOException, UnknownHostException, UserCancelledException {
		try {
			client.loginHttp(connection.getRealm(), username, password, true);
		} catch (IOException ioe) {
			if (log.isInfoEnabled()) {
				log.info(String.format("%s error during login", client.getHost()), ioe);
			}
			client.disconnect(true);
			client.connect(connection.getHostname(), connection.getPort(), connection.getPath(), locale);
			client.login();
		}
	}

	private void updateInfo(ServiceClient<?> client, String versionString) {
		Version ourVersion = new Version(HypersocketVersion.getVersion("client-service"));

		// Compare
		Version version = new Version(versionString);
		if (version.compareTo(ourVersion) > 0) {
			log.info(String.format("Updating required, server is version %s, and we are version %s.",
					version.toString(), ourVersion.toString()));
		} else if (version.compareTo(ourVersion) < 0) {
			log.warn(String.format(
					"Client is on a later version than the server. This client is %s, where as the server is %s.",
					ourVersion.toString(), version.toString()));
		} else {
			log.info(String.format("Both server and client are on version %s", version.toString()));
		}
	}

}
