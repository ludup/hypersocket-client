package com.logonbox.vpn.client.gui.jfx;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.CookieHandler;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import javax.net.ssl.HostnameVerifier;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.http.message.BasicNameValuePair;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusSigHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypersocket.json.AuthenticationRequiredResult;
import com.hypersocket.json.AuthenticationResult;
import com.hypersocket.json.input.InputField;
import com.hypersocket.json.version.HypersocketVersion;
import com.logonbox.vpn.common.client.AbstractDBusClient;
import com.logonbox.vpn.common.client.AbstractDBusClient.BusLifecycleListener;
import com.logonbox.vpn.common.client.AuthenticationCancelledException;
import com.logonbox.vpn.common.client.ConfigurationRepository;
import com.logonbox.vpn.common.client.Connection;
import com.logonbox.vpn.common.client.Connection.Mode;
import com.logonbox.vpn.common.client.ConnectionStatus;
import com.logonbox.vpn.common.client.ConnectionStatus.Type;
import com.logonbox.vpn.common.client.DNSIntegrationMethod;
import com.logonbox.vpn.common.client.ServiceClient;
import com.logonbox.vpn.common.client.Util;
import com.logonbox.vpn.common.client.api.Branding;
import com.logonbox.vpn.common.client.dbus.VPN;
import com.logonbox.vpn.common.client.dbus.VPNConnection;
import com.sshtools.twoslices.Toast;
import com.sshtools.twoslices.ToastType;
import com.sshtools.twoslices.Toaster;
import com.sshtools.twoslices.ToasterFactory;
import com.sshtools.twoslices.ToasterSettings;
import com.sshtools.twoslices.ToasterSettings.SystemTrayIconMode;
import com.sshtools.twoslices.impl.OsXToaster;
//import com.sun.javafx.util.Utils;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Worker.State;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.util.Callback;
import javafx.util.Duration;
import netscape.javascript.JSObject;

public class UI extends AbstractController implements BusLifecycleListener {

	private static final int SPLASH_HEIGHT = 360;
	private static final int SPLASH_WIDTH = 480;

	private static final String DEFAULT_LOCALHOST_ADDR = "http://localhost:59999/";

	public static final class ServiceClientAuthenticator implements ServiceClient.Authenticator {
		private final WebEngine engine;
		private final Semaphore authorizedLock;
		private final Client context;
		private Map<InputField, BasicNameValuePair> results;
		private AuthenticationRequiredResult result;
		private boolean error;

		public ServiceClientAuthenticator(Client context, WebEngine engine, Semaphore authorizedLock) {
			this.engine = engine;
			this.context = context;
			this.authorizedLock = authorizedLock;
		}

		public void cancel() {
			log.info("Cancelling authorization.");
			error = true;
			authorizedLock.release();
		}

		public void submit(JSObject obj) {
			for (InputField field : result.getFormTemplate().getInputFields()) {
				results.put(field, new BasicNameValuePair(field.getResourceKey(),
						memberOrDefault(obj, field.getResourceKey(), String.class, "")));
			}
			authorizedLock.release();
		}

		@Override
		public String getUUID() {
			return context.getDBus().getVPN().getUUID();
		}

		@Override
		public HostnameVerifier getHostnameVerifier() {
			return Main.getInstance().getCertManager();
		}

		@Override
		public void error(JsonNode i18n, AuthenticationResult logonResult) {
			maybeRunLater(() -> {
				if (logonResult.isLastErrorIsResourceKey())
					engine.executeScript("authorizationError('"
							+ StringEscapeUtils.escapeEcmaScript(i18n.get(logonResult.getErrorMsg()).asText()) + "');");
				else
					engine.executeScript("authorizationError('"
							+ StringEscapeUtils.escapeEcmaScript(logonResult.getErrorMsg()) + "');");

			});
		}

		@Override
		public void collect(JsonNode i18n, AuthenticationRequiredResult result,
				Map<InputField, BasicNameValuePair> results) throws IOException {
			this.results = results;
			this.result = result;

			maybeRunLater(() -> {
				JSObject jsobj = (JSObject) engine.executeScript("window");
				jsobj.setMember("remoteBundle", i18n);
				jsobj.setMember("result", result);
				jsobj.setMember("results", results);
				engine.executeScript("collect();");
			});
			try {
				log.info("Waiting for authorize semaphore to be released.");
				authorizedLock.acquire();
			} catch (InterruptedException e) {
				// TODO:
			}

			log.info("Left authorization.");
			if (error)
				throw new AuthenticationCancelledException();
		}

		@Override
		public void authorized() throws IOException {
			log.info("Authorized.");
			maybeRunLater(() -> {
				engine.executeScript("authorized();");
				authorizedLock.release();
			});
		}
	}

	public static final class Register implements Runnable {
		private final VPNConnection selectedConnection;
		private final ServiceClient serviceClient;
		private final UI ui;

		public Register(VPNConnection selectedConnection, ServiceClient serviceClient, UI ui) {
			this.selectedConnection = selectedConnection;
			this.serviceClient = serviceClient;
			this.ui = ui;
		}

		public void run() {
			new Thread() {
				public void run() {
					try {
						serviceClient.register(selectedConnection);
					} catch (AuthenticationCancelledException ae) {
						// Ignore, handled elsewhere
					} catch (IOException | URISyntaxException e) {
						maybeRunLater(() -> ui.showError("Failed to register.", e));
					}
				}
			}.start();
		}
	}

	/**
	 * This object is exposed to the local HTML/Javascript that runs in the browse.
	 */
	public class ServerBridge {

		public void addConnection(JSObject o) throws URISyntaxException {
			Boolean connectAtStartup = (Boolean) o.getMember("connectAtStartup");
			Boolean stayConnected = (Boolean) o.getMember("stayConnected");
			String server = (String) o.getMember("serverUrl");
			if (StringUtils.isBlank(server))
				throw new IllegalArgumentException(bundle.getString("error.invalidUri"));
			UI.this.addConnection(stayConnected, connectAtStartup, server);
		}

		public void authenticate() {
			UI.this.authorize(UI.this.getSelectedConnection());
		}

		public void configure(String usernameHint, String configIniFile) {
			if (LOG.isDebugEnabled())
				LOG.debug(String.format("Connect user: %s, Config: %s", usernameHint, configIniFile));
			UI.this.configure(usernameHint, configIniFile, UI.this.getSelectedConnection());
		}

		public void reset() {
			UI.this.selectPageForState(false, true);
		}

		public void details(long id) {
			VPNConnection connection = context.getDBus().getVPNConnection(id);
			UI.this.connections.getSelectionModel().select(connection);
			setHtmlPage("details.html");
		}

		public void connect() {
			VPNConnection selectedItem = UI.this.getSelectedConnection();
			UI.this.connect(selectedItem == null ? UI.this.connections.getItems().get(0) : selectedItem);
		}

		public void connectTo(VPNConnection connection) {
			UI.this.connect(connection);
		}

		public void disconnectFrom(VPNConnection connection) {
			UI.this.disconnect(connection);
		}

		public void editConnection(JSObject o) {
			Boolean connectAtStartup = (Boolean) o.getMember("connectAtStartup");
			Boolean stayConnected = (Boolean) o.getMember("stayConnected");
			String server = (String) o.getMember("serverUrl");
			String name = (String) o.getMember("name");
			UI.this.editConnection(connectAtStartup, stayConnected, name, server, getSelectedConnection());
		}

		public VPNConnection getConnection() {
			return UI.this.getSelectedConnection();
		}

		public String getOS() {
			return Util.getOS();
		}

		public String getDeviceName() {
			return Util.getDeviceName();
		}

		public String getUserPublicKey() {
			VPNConnection connection = getConnection();
			return connection == null ? null : connection.getUserPublicKey();
		}

		public void join() {
			UI.this.joinNetwork(UI.this.getSelectedConnection());
		}

		public void log(String message) {
			LOG.info("WEB: " + message);
		}

		public void reload() {
			UI.this.initUi(getSelectedConnection());
		}

		public void reveal() {
			UI.this.sidebar.setVisible(true);
			UI.this.connections.requestFocus();
		}

		public void saveOptions(JSObject o) {
			String trayMode = memberOrDefault(o, "trayMode", String.class, null);
			String darkMode = memberOrDefault(o, "darkMode", String.class, null);
			String logLevel = memberOrDefault(o, "logLevel", String.class, null);
			String dnsIntegrationMethod = memberOrDefault(o, "dnsIntegrationMethod", String.class, null);
			String phase = memberOrDefault(o, "phase", String.class, null);
			Boolean automaticUpdates = memberOrDefault(o, "automaticUpdates", Boolean.class, null);
			Boolean ignoreLocalRoutes = memberOrDefault(o, "ignoreLocalRoutes", Boolean.class, null);
			Boolean saveCookies = memberOrDefault(o, "saveCookies", Boolean.class, null);
			UI.this.saveOptions(trayMode, darkMode, phase, automaticUpdates, logLevel, ignoreLocalRoutes,
					dnsIntegrationMethod, saveCookies);
		}

		public void showError(String error) {
			UI.this.showError(error);
		}

		public void unjoin(String reason) {
			UI.this.disconnect(getSelectedConnection(), reason);
		}

		public void update() {
			UI.this.update();
		}

		public void deferUpdate() {
			UI.this.deferUpdate();
		}

		public void cancelUpdate() {
			UI.this.cancelUpdate();
		}

		public void checkForUpdate() {
			UI.this.checkForUpdate();
		}

		public void openURL(String url) {
			Client.get().getHostServices().showDocument(url);
		}

		public String getLastHandshake() {
			VPNConnection connection = getConnection();
			return connection == null ? null
					: DateFormat.getDateTimeInstance().format(new Date(connection.getLastHandshake()));
		}

		public String getUsage() {
			VPNConnection connection = getConnection();
			return connection == null ? null
					: MessageFormat.format(resources.getString("usageDetail"), Util.toHumanSize(connection.getRx()),
							Util.toHumanSize(connection.getTx()));
		}

		public VPNConnection[] getConnections() {
			return context.getDBus().getVPNConnections().toArray(new VPNConnection[0]);
		}
	}

	class BrandingCacheItem {
		Branding branding;
		long loaded = System.currentTimeMillis();

		BrandingCacheItem(Branding branding) {
			this.branding = branding;
		}

		boolean isExpired() {
			return System.currentTimeMillis() > loaded + TimeUnit.MINUTES.toMillis(10);
		}
	}

	final static ResourceBundle bundle = ResourceBundle.getBundle(UI.class.getName());

	@SuppressWarnings("unchecked")
	static <T> T memberOrDefault(JSObject obj, String member, Class<T> clazz, T def) {
		try {
			Object o = obj.getMember(member);
			if (o == null) {
				return null;
			}
			return clazz.isAssignableFrom(o.getClass()) ? (T) o : def;
		} catch (Exception | Error ex) {
			return def;
		}
	}

	static {
		ToasterSettings settings = new ToasterSettings();
		settings.setAppName(bundle.getString("appName"));
		settings.setSystemTrayIconMode(SystemTrayIconMode.HIDDEN);
		ToasterFactory.setSettings(settings);
		if (SystemUtils.IS_OS_MAC_OSX) {
			ToasterFactory.setFactory(new ToasterFactory() {
				@Override
				public Toaster toaster() {
					return new OsXToaster(settings);
				}
			});
		}
	}

	static int DROP_SHADOW_SIZE = 11;

	final static Logger log = LoggerFactory.getLogger(UI.class);

	static Logger LOG = LoggerFactory.getLogger(UI.class);

	private static UI instance;

	public static UI getInstance() {
		return instance;
	}

	private int appsToUpdate;
	private int appsUpdated;
	private Timeline awaitingBridgeEstablish;
	private Timeline awaitingBridgeLoss;
	private boolean awaitingRestart;
	private Branding branding;
	private Map<String, Collection<String>> collections = new HashMap<>();
	private Map<VPNConnection, BrandingCacheItem> brandingCache = new HashMap<>();
	private List<VPNConnection> connecting = new ArrayList<>();
	private boolean deleteOnDisconnect;
	private String htmlPage;
	private String lastErrorMessage;
	private String lastErrorCause;
	private String lastException;
	private ResourceBundle pageBundle;
	private File logoFile;
	private boolean adjustingSelection;
	private Runnable runOnNextLoad;
	private ServerBridge bridge;
	private String disconnectionReason;

	@FXML
	protected ListView<VPNConnection> connections;
	@FXML
	private Hyperlink addConnection;
	@FXML
	private Label messageIcon;
	@FXML
	private Label messageText;
	@FXML
	private Hyperlink options;
	@FXML
	private VBox root;
	@FXML
	private BorderPane sidebar;
	@FXML
	private WebView webView;
	@FXML
	private ImageView titleBarImageView;
	@FXML
	private Hyperlink close;
	@FXML
	private Hyperlink minimize;
	@FXML
	private Hyperlink toggleSidebar;

	public UI() {
		instance = this;
	}

	public void disconnect(VPNConnection sel) {
		disconnect(sel, null);
	}

	public void disconnect(VPNConnection sel, String reason) {
		if (sel == null)
			throw new IllegalArgumentException("No connection given.");
		if (reason == null)
			LOG.info("Requesting disconnect, no reason given");
		else
			LOG.info(String.format("Requesting disconnect, because '%s'", reason));

		context.getOpQueue().execute(() -> {
			try {
				sel.disconnect(StringUtils.defaultIfBlank(reason, ""));
			} catch (Exception e) {
				Platform.runLater(() -> showError("Failed to disconnect.", e));
			}
		});
	}

	public void connect(VPNConnection n) {
		try {
			if (n == null) {
				addConnection();
			} else {
				log.info(String.format("Connect to %s", n.getAddress()));
				if (n != null) {
					Type status = Type.valueOf(n.getStatus());
					log.info(String.format("  current status is %s", status));
					if (status == Type.CONNECTED || status == Type.CONNECTING || status == Type.DISCONNECTING)
						selectPageForState(false, false);
					else if (n.isAuthorized())
						joinNetwork(n);
					else
						authorize(n);
				}
			}
		} catch (Exception e) {
			showError("Failed to connect.", e);
		}
	}

	public void notify(String msg, ToastType toastType) {
		Toast.toast(toastType, resources.getString("appName"), msg);
	}

	private Map<String, Object> beansForOptions() {
		Map<String, Object> beans = new HashMap<>();
		AbstractDBusClient dbus = context.getDBus();
		VPN vpn = dbus.isBusAvailable() ? dbus.getVPN() : null;
		if (vpn == null) {
			beans.put("phases", new String[0]);
			beans.put("phase", "");
			beans.put("automaticUpdates", "true");
			beans.put("ignoreLocalRoutes", "true");
			beans.put("dnsIntegrationMethod", DNSIntegrationMethod.AUTO.name());
		} else {
			try {
				Map<String, String> phases = vpn.getPhases();
				beans.put("phases", phases.keySet().toArray(new String[0]));
			} catch (Exception e) {
				log.warn("Could not get phases.", e);
			}

			/* Configuration stored globally in service */
			beans.put("phase", vpn.getValue(ConfigurationRepository.PHASE, ""));
			beans.put("dnsIntegrationMethod",
					vpn.getValue(ConfigurationRepository.DNS_INTEGRATION_METHOD, DNSIntegrationMethod.AUTO.name()));

			/* Store locally in preference */
			beans.put("automaticUpdates", Boolean.valueOf(vpn.getValue(ConfigurationRepository.AUTOMATIC_UPDATES,
					String.valueOf(ConfigurationRepository.AUTOMATIC_UPDATES_DEFAULT))));
			beans.put("ignoreLocalRoutes",
					Boolean.valueOf(vpn.getValue(ConfigurationRepository.IGNORE_LOCAL_ROUTES, "true")));
		}

		/* Option collections */
		beans.put("trayModes", new String[] { Configuration.TRAY_MODE_AUTO, Configuration.TRAY_MODE_COLOR,
				Configuration.TRAY_MODE_DARK, Configuration.TRAY_MODE_LIGHT, Configuration.TRAY_MODE_OFF });
		beans.put("darkModes", new String[] { Configuration.DARK_MODE_AUTO, Configuration.DARK_MODE_ALWAYS,
				Configuration.DARK_MODE_NEVER });
		beans.put("logLevels",
				new String[] { "", org.apache.log4j.Level.ALL.toString(), org.apache.log4j.Level.TRACE.toString(),
						org.apache.log4j.Level.DEBUG.toString(), org.apache.log4j.Level.INFO.toString(),
						org.apache.log4j.Level.WARN.toString(), org.apache.log4j.Level.ERROR.toString(),
						org.apache.log4j.Level.FATAL.toString(), org.apache.log4j.Level.OFF.toString() });
		beans.put("dnsIntegrationMethods", Arrays.asList(DNSIntegrationMethod.valuesForOs()).stream()
				.map(DNSIntegrationMethod::name).collect(Collectors.toUnmodifiableList()).toArray(new String[0]));

		/* Per-user GUI specific */
		Configuration config = Configuration.getDefault();
		beans.put("trayMode", config.trayModeProperty().get());
		beans.put("darkMode", config.darkModeProperty().get());
		beans.put("logLevel", config.logLevelProperty().get() == null ? "" : config.logLevelProperty().get());
		beans.put("saveCookies", config.saveCookiesProperty().get());

		return beans;
	}

	public void options() {
		connections.getSelectionModel().clearSelection();
		setHtmlPage("options.html");
		sidebar.setVisible(false);
	}

	public void refresh() {
		rebuildConnections(getSelectedConnection());
		selectPageForState(false, true);
	}

	public void reload() {
//		webView.getEngine().reload();
		/* TODO: hrm, why cant we just refresh the page? */
		selectPageForState(false, true);
	}

	@Override
	public void busInitializer(DBusConnection connection) {
		LOG.info("Bridge established.");

		try {
			connection.addSigHandler(VPN.ConnectionAdded.class, new DBusSigHandler<VPN.ConnectionAdded>() {
				@Override
				public void handle(VPN.ConnectionAdded sig) {
					maybeRunLater(() -> {
						try {
							VPNConnection addedConnection = context.getDBus().getVPNConnection(sig.getId());
							try {
								listenConnectionEvents(connection, addedConnection);
							} catch (DBusException e) {
								throw new IllegalStateException("Failed to listen for new connections events.");
							}

							VPNConnection selectedConnection = getSelectedConnection();
							if (selectedConnection == null || selectedConnection.getId() == sig.getId()) {
								adjustingSelection = true;
								try {
									rebuildConnections(selectedConnection);
								} finally {
									adjustingSelection = false;
								}
								VPNConnection newSelectedConnection = getSelectedConnection();
								context.getOpQueue().execute(() -> {
									reloadState(() -> {
										maybeRunLater(() -> {
											reapplyColors();
											reapplyLogo();
											authorize(newSelectedConnection);
										});
									});
								});
							} else {
								rebuildConnections(null);
								selectPageForState(false, false);
							}
						} finally {
							log.info("Connection added");
						}
					});
				}
			});
			connection.addSigHandler(VPN.ConnectionRemoved.class, new DBusSigHandler<VPN.ConnectionRemoved>() {
				@Override
				public void handle(VPN.ConnectionRemoved sig) {
					maybeRunLater(() -> {
						try {
							rebuildConnections(null);
							context.getOpQueue().execute(() -> {
								reloadState(() -> {
									maybeRunLater(() -> {
										reapplyColors();
										reapplyLogo();
										selectPageForState(false, false);
									});
								});
							});
						} finally {
							log.info("Connection deleted");
						}
					});
				}
			});
			connection.addSigHandler(VPN.ConnectionUpdated.class, new DBusSigHandler<VPN.ConnectionUpdated>() {
				@Override
				public void handle(VPN.ConnectionUpdated sig) {
					maybeRunLater(() -> {
						connections.refresh();
						selectPageForState(false, false);
					});
				}
			});

			/* On update available */
			connection.addSigHandler(VPN.UpdateAvailable.class, new DBusSigHandler<VPN.UpdateAvailable>() {
				@Override
				public void handle(VPN.UpdateAvailable sig) {
					maybeRunLater(() -> {
						selectPageForState(false, false);
					});
				}
			});

			/* On update init */
			connection.addSigHandler(VPN.UpdateInit.class, new DBusSigHandler<VPN.UpdateInit>() {
				@Override
				public void handle(VPN.UpdateInit sig) {
					maybeRunLater(() -> {
						if (awaitingRestart)
							throw new IllegalStateException(
									"Cannot initiate updates while waiting to restart the GUI.");
						LOG.info(String.format("Initialising update. Expecting %d apps", sig.getApps()));
						appsToUpdate = sig.getApps();
						appsUpdated = 0;
						selectPageForState(false, false);
					});
				}
			});

			/* On update start */
			connection.addSigHandler(VPN.UpdateStart.class, new DBusSigHandler<VPN.UpdateStart>() {
				@Override
				public void handle(VPN.UpdateStart sig) {
					maybeRunLater(() -> {
						LOG.info(String.format("Starting up of %s, expect %d bytes", sig.getApp(),
								sig.getTotalBytesExpected()));
						String appName = getAppName(sig.getApp());
						Tray tray = Client.get().getTray();
						if (tray != null)
							tray.setProgress(0);
						setUpdateProgress(0, MessageFormat.format(resources.getString("updating"), appName));
					});
				}
			});

			/* On update progress */
			connection.addSigHandler(VPN.UpdateProgress.class, new DBusSigHandler<VPN.UpdateProgress>() {
				@Override
				public void handle(VPN.UpdateProgress sig) {
					maybeRunLater(() -> {
						String appName = getAppName(sig.getApp());
						int pc = (int) (((double) sig.getTotalSoFar() / sig.getTotalBytesExpected()) * 100d);
						Tray tray = Client.get().getTray();
						if (tray != null)
							tray.setProgress(pc);
						setUpdateProgress(pc, MessageFormat.format(resources.getString("updating"), appName));
					});
				}
			});

			/* On update failure */
			connection.addSigHandler(VPN.UpdateComplete.class, new DBusSigHandler<VPN.UpdateComplete>() {
				@Override
				public void handle(VPN.UpdateComplete sig) {
					maybeRunLater(() -> {
						String appName = getAppName(sig.getApp());
						setUpdateProgress(100, MessageFormat.format(resources.getString("updated"), appName));
						appsUpdated++;
						Tray tray = Client.get().getTray();
						if (tray != null)
							tray.setProgress(-1);
						LOG.info(String.format("Update of %s complete, have now updated %d of %d apps", sig.getApp(),
								appsUpdated, appsToUpdate));
					});
				}
			});

			/* On update failure */
			connection.addSigHandler(VPN.UpdateFailure.class, new DBusSigHandler<VPN.UpdateFailure>() {
				@Override
				public void handle(VPN.UpdateFailure sig) {
					maybeRunLater(() -> {
						LOG.info(String.format("Failed to update app %s. %s", sig.getApp(), sig.getMessage()));
						UI.this.notify(sig.getMessage(), ToastType.ERROR);
						Tray tray = Client.get().getTray();
						if (tray != null)
							tray.setAttention(true, false);
						showError(MessageFormat.format(resources.getString("updateFailure"), sig.getApp()),
								sig.getMessage(), sig.getTrace());
					});
				}
			});

			/* On update done */
			connection.addSigHandler(VPN.UpdateDone.class, new DBusSigHandler<VPN.UpdateDone>() {
				@Override
				public void handle(VPN.UpdateDone sig) {
					maybeRunLater(() -> {
						Tray tray = Client.get().getTray();
						if (tray != null)
							tray.setProgress(-1);
						LOG.info(String.format("Update done. Message: %s, Restart: %s", sig.getFailureMessage(),
								sig.isRestart() ? "Yes" : "No"));
						if (StringUtils.isBlank(sig.getFailureMessage())) {
							if (sig.isRestart()) {
								LOG.info(String.format("All apps updated, starting restart process " + Math.random()));
								awaitingBridgeLoss = new Timeline(
										new KeyFrame(Duration.seconds(30), ae -> giveUpWaitingForBridgeStop()));
								awaitingBridgeLoss.play();
							} else {
								LOG.info(String.format("No restart required, continuing"));
								resetState();
							}
						} else {
							setUpdateProgress(100, sig.getFailureMessage());
							resetState();
						}
					});
				}
			});

			/* Listen for events on all existing connections */
			for (VPNConnection vpnConnection : context.getDBus().getVPNConnections()) {
				listenConnectionEvents(connection, vpnConnection);
			}

		} catch (DBusException dbe) {
			throw new IllegalStateException("Failed to configure.", dbe);
		}

		Runnable runnable = new Runnable() {
			public void run() {
				if (awaitingBridgeEstablish != null) {
					// Bridge established as result of update, now restart the
					// client itself
					resetAwaingBridgeEstablish();
					setUpdateProgress(100, resources.getString("guiRestart"));
					new Timeline(new KeyFrame(Duration.seconds(5), ae -> Main.getInstance().restart())).play();
				} else {
					String unprocessedUri = Main.getInstance().getUri();
					if (StringUtils.isNotBlank(unprocessedUri)) {
						connectToUri(unprocessedUri);
					} else {
						initUi(null);
						selectPageForState(Main.getInstance().isConnect(), false);
					}
				}
			}
		};

		/*
		 * If we were waiting for this, it's part of the update process. We don't want
		 * the connection continuing
		 */
		if (awaitingBridgeEstablish != null) {
			awaitingRestart = true;
			maybeRunLater(runnable);
		} else {
			reloadState(() -> maybeRunLater(runnable));
		}
	}

	protected void listenConnectionEvents(DBusConnection bus, VPNConnection connection) throws DBusException {
		/*
		 * Client needs authorizing (first time configuration needed, or connection
		 * failed with existing configuration)
		 */
		bus.addSigHandler(VPNConnection.Authorize.class, connection, new DBusSigHandler<VPNConnection.Authorize>() {
			@Override
			public void handle(VPNConnection.Authorize sig) {
				disconnectionReason = null;
				Mode authMode = Connection.Mode.valueOf(sig.getMode());
				if (authMode.equals(Connection.Mode.CLIENT) || authMode.equals(Connection.Mode.SERVICE)) {
					maybeRunLater(() -> {
						// setHtmlPage(connection.getUri(false) + sig.getUri());
						selectPageForState(false, false);
					});
				} else {
					LOG.info(String.format("This client doest not handle the authorization mode '%s'", sig.getMode()));
				}
			}
		});
		bus.addSigHandler(VPNConnection.Connecting.class, connection, new DBusSigHandler<VPNConnection.Connecting>() {
			@Override
			public void handle(VPNConnection.Connecting sig) {
				disconnectionReason = null;
				maybeRunLater(() -> {
					selectPageForState(false, false);
				});
			}
		});
		bus.addSigHandler(VPNConnection.Connected.class, connection, new DBusSigHandler<VPNConnection.Connected>() {
			@Override
			public void handle(VPNConnection.Connected sig) {
				VPNConnection connection = context.getDBus().getVPNConnection(sig.getId());
				maybeRunLater(() -> {
					UI.this.notify(MessageFormat.format(bundle.getString("connected"), connection.getName(),
							connection.getHostname()), ToastType.INFO);
					connecting.remove(connection);
					connections.refresh();
					if (Main.getInstance().isExitOnConnection()) {
						context.exitApp();
					} else
						selectPageForState(false, false);
				});
			}
		});

		/* Failed to connect */
		bus.addSigHandler(VPNConnection.Failed.class, connection, new DBusSigHandler<VPNConnection.Failed>() {
			@Override
			public void handle(VPNConnection.Failed sig) {
				VPNConnection connection = context.getDBus().getVPNConnection(sig.getId());
				maybeRunLater(() -> {
					String s = sig.getReason();
					log.info(String.format("Failed to connect. %s", s));
					connecting.remove(connection);
					connections.refresh();
					showError("Failed to connect.", s, sig.getTrace());
					UI.this.notify(s, ToastType.ERROR);
				});
			}
		});

		/* Temporarily offline */
		bus.addSigHandler(VPNConnection.TemporarilyOffline.class, connection,
				new DBusSigHandler<VPNConnection.TemporarilyOffline>() {
					@Override
					public void handle(VPNConnection.TemporarilyOffline sig) {
						disconnectionReason = sig.getReason();
						maybeRunLater(() -> {
							log.info("Temporarily offline " + sig.getId());
							connections.refresh();
							selectPageForState(false, false);
						});
					}
				});

		/* Disconnected */
		bus.addSigHandler(VPNConnection.Disconnected.class, connection,
				new DBusSigHandler<VPNConnection.Disconnected>() {
					@Override
					public void handle(VPNConnection.Disconnected sig) {
						String thisDisconnectionReason = sig.getReason();
						maybeRunLater(() -> {
							log.info("Disconnected " + sig.getId() + " (delete " + deleteOnDisconnect + ")");
							VPNConnection connection = null;
							try {
								connection = context.getDBus().getVPNConnection(sig.getId());
								if (StringUtils.isBlank(thisDisconnectionReason))
									UI.this.notify(
											MessageFormat.format(bundle.getString("disconnectedNoReason"),
													connection.getDisplayName(), connection.getHostname()),
											ToastType.INFO);
								else
									UI.this.notify(MessageFormat.format(bundle.getString("disconnected"),
											connection.getDisplayName(), connection.getHostname(), thisDisconnectionReason),
											ToastType.INFO);
							} catch (Exception e) {
								log.error("Failed to get connection, delete not possible.");
							}

							connections.refresh();
							if (connection != null && deleteOnDisconnect) {
								try {
									doDelete(connection);
									initUi(connection);
								} catch (RemoteException e1) {
									log.error("Failed to delete.", e1);
								}
								if (StringUtils.isNotBlank(sig.getReason())) {
									showError("Disconnected. " + sig.getReason());
								} else {
									selectPageForState(false, false);
								}
							} else {
								selectPageForState(false, false);
							}
						});
					}
				});

		/* Disconnecting event */
		bus.addSigHandler(VPNConnection.Disconnecting.class, connection,
				new DBusSigHandler<VPNConnection.Disconnecting>() {
					@Override
					public void handle(VPNConnection.Disconnecting sig) {
						maybeRunLater(() -> {
							log.info("Disconnecting " + bus + " (delete " + deleteOnDisconnect + ")");
							selectPageForState(false, false);
						});
					}
				});
	}

	@Override
	public void busGone() {
		LOG.info("Bridge lost");
		maybeRunLater(() -> {
			if (awaitingBridgeLoss != null) {
				// Bridge lost as result of update, wait for it to come back
				resetAwaingBridgeLoss();
				setUpdateProgress(100, resources.getString("waitingStart"));
				awaitingBridgeEstablish = new Timeline(
						new KeyFrame(Duration.seconds(30), ae -> giveUpWaitingForBridgeEstablish()));
				awaitingBridgeEstablish.play();
			} else {
				connecting.clear();
				rebuildConnections(null);
				initUi(null);
				selectPageForState(false, false);
			}
		});
	}

	protected void joinNetwork(VPNConnection connection) {
		context.getOpQueue().execute(() -> {
			connection.connect();
		});
	}

	protected void addConnection(Boolean stayConnected, Boolean connectAtStartup, String unprocessedUri)
			throws URISyntaxException {
		URI uriObj = Util.getUri(unprocessedUri);
		context.getOpQueue().execute(() -> {
			try {
				context.getDBus().getVPN().createConnection(uriObj.toASCIIString(), connectAtStartup, stayConnected,
						Mode.CLIENT.name());
			} catch (Exception e) {
				showError("Failed to add connection.", e);
			}
		});
	}

	protected void authorize(VPNConnection n) {
		context.getOpQueue().execute(() -> {
			try {
				n.authorize();
			} catch (Exception e) {
				showError("Failed to join VPN.", e);
			}
		});
	}

	protected void configureWebEngine() {
		WebEngine engine = webView.getEngine();
		webView.setContextMenuEnabled(false);

		engine.setUserAgent(
				"LogonBox VPN Client " + HypersocketVersion.getVersion("com.hypersocket/client-logonbox-vpn-gui-jfx"));
		engine.setOnAlert((e) -> {
			Alert alert = new Alert(AlertType.ERROR);
			alert.initModality(Modality.APPLICATION_MODAL);
			alert.initOwner(getStage());
			alert.setTitle("Alert");
			alert.setContentText(e.getData());
			alert.showAndWait();
		});
		engine.setOnError((e) -> {
			LOG.error(String.format("Error in webengine. %s", e.getMessage()), e.getException());
		});

		engine.setOnStatusChanged((e) -> {
			LOG.debug(String.format("Status: %s", e));
		});
		engine.locationProperty().addListener((c, oldLoc, newLoc) -> {
			if (newLoc != null) {
				if ((newLoc.startsWith("http://") || newLoc.startsWith("https://"))
						&& !(newLoc.startsWith(DEFAULT_LOCALHOST_ADDR))) {
					log.info(String.format("This is a remote page (%s), not changing current html page", newLoc));
					htmlPage = null;
				} else {
					String base = newLoc;
					int idx = newLoc.lastIndexOf('?');
					if (idx != -1) {
						base = newLoc.substring(0, idx);
					}
					idx = base.lastIndexOf('/');
					if (idx != -1) {
						base = base.substring(idx + 1);
					}
					if (base.equals(""))
						base = "index.html";
					idx = base.indexOf('#');
					if (idx != -1) {
						base = base.substring(0, idx);
					}
					if (!base.equals("") && !base.equals(htmlPage)) {
						htmlPage = base;
						log.info(String.format("Page changed by user to %s (from browser view)", htmlPage));
					}
				}
				setupPage();
			}
		});
		engine.getLoadWorker().stateProperty().addListener((ov, oldState, newState) -> {
			if (newState == State.SUCCEEDED) {
				processDOM();
				processJavascript();
				if (runOnNextLoad != null) {
					try {
						runOnNextLoad.run();
					} finally {
						runOnNextLoad = null;
					}
				}
			}
		});
		engine.getLoadWorker().exceptionProperty().addListener((o, old, value) -> {
			if (value == null) {
				/* Error cleared */
				return;
			}

			/*
			 * If we are authorizing and get an error from the browser, then it's likely the
			 * target server is offline and can't do the authorization.
			 * 
			 * So cancel the authorize and show the error and allow retry.
			 */
			VPNConnection selectedConnection = getSelectedConnection();
			try {
				if (selectedConnection != null && ConnectionStatus.Type
						.valueOf(selectedConnection.getStatus()) == ConnectionStatus.Type.AUTHORIZING) {
					String reason = value != null ? value.getMessage() : null;
					LOG.info(String.format("Got error while authorizing. Disconnecting now using '%s' as the reason",
							reason));
					context.getOpQueue().execute(() -> {
						try {
							selectedConnection.disconnect(reason);
						} catch (Exception e) {
							Platform.runLater(() -> showError("Failed to disconnect.", e));
						}
					});

					/*
					 * Await the disconnected event that comes back from the service and process it
					 * then
					 */
					return;
				}
			} catch (Exception e) {
				LOG.error("Failed to adjust authorizing state.", e);
			}
			if (!checkForInvalidatedSession(value)) {
				showError("Error.", value);
			}
		});

		engine.setJavaScriptEnabled(true);
	}

	protected boolean checkForInvalidatedSession(Throwable value) {
		/*
		 * This is pretty terrible. JavFX's WebEngine$LoadWorker constructs a new
		 * <strong>Throwable</strong>! with English text as the only way of
		 * distinguishing the actual error! This is ridiculous. Hopefully it will get
		 * fixed, but then this will probably break.
		 */
		if (value != null && value.getMessage() != null) {
			if (value.getMessage().equals("Connection refused by server")) {
				setHtmlPage("offline.html");
				return true;
			}
		}
		return false;
	}

	protected void editConnection(Boolean connectAtStartup, Boolean stayConnected, String name, String server,
			VPNConnection connection) {
		try {
			URI uriObj = Util.getUri(server);

			connection.setName(name);
			connection.setHostname(uriObj.getHost());
			connection
					.setPort(uriObj.getPort() < 1 ? (uriObj.getScheme().equals("https") ? 443 : 80) : uriObj.getPort());
			connection.setConnectAtStartup(connectAtStartup);
			connection.setStayConnected(stayConnected);
			if (!connection.isTransient())
				connection.save();

		} catch (Exception e) {
			showError("Failed to save connection.", e);
		}
	}

	protected VPNConnection getSelectedConnection() {
		if (isNewUI()) {
			/*
			 * New UI, we use the connection that needs the most attention. If / when it
			 * becomes the default, then this will not be necessary.
			 */
			for (VPNConnection connection : connections.getItems()) {
				ConnectionStatus.Type type = Type.valueOf(connection.getStatus());
				if (type == Type.AUTHORIZING) {
					return connection;
				}
			}

			for (VPNConnection connection : connections.getItems()) {
				ConnectionStatus.Type type = Type.valueOf(connection.getStatus());
				if (type == Type.CONNECTING || type == Type.DISCONNECTING) {
					return connection;
				}
			}

			for (VPNConnection connection : connections.getItems()) {
				ConnectionStatus.Type type = Type.valueOf(connection.getStatus());
				if (type == Type.CONNECTED) {
					return connection;
				}
			}

			return connections.getItems().isEmpty() ? null : connections.getItems().get(0);
		} else {
			VPNConnection selectedItem = connections.getSelectionModel().getSelectedItem();
			if (selectedItem == null && connections.getItems().size() > 0)
				selectedItem = connections.getItems().get(0);
			return selectedItem;
		}
	}

	@Override
	protected void onConfigure() {
		super.onConfigure();

		bridge = new ServerBridge();

		setAvailable();

		/*
		 * Setup the connection list
		 */
		Callback<ListView<VPNConnection>, ListCell<VPNConnection>> factory = new Callback<ListView<VPNConnection>, ListCell<VPNConnection>>() {

			@Override
			public ListCell<VPNConnection> call(ListView<VPNConnection> l) {
				return new ListCell<VPNConnection>() {

					@Override
					protected void updateItem(VPNConnection item, boolean empty) {
						super.updateItem(item, empty);
						if (item == null) {
							setText("");
						} else
							setText(item.getDisplayName());
					}
				};
			}
		};
		connections.setOnMouseClicked((e) -> {
			if (e.getClickCount() == 2)
				connect(getSelectedConnection());
		});
		connections.setCellFactory(factory);
		connections.focusedProperty().addListener((e, o, n) -> {
			if (!n && !addConnection.isFocused() && !options.isFocused()) {
				sidebar.setVisible(false);
			}
		});
		connections.getSelectionModel().selectedItemProperty().addListener((e, o, n) -> {
			if (!adjustingSelection && n != null) {
				context.getOpQueue().execute(() -> {
					reloadState(() -> {
						maybeRunLater(() -> {
							reapplyColors();
							reapplyLogo();
							if (htmlPage == null || !htmlPage.equals("details.html"))
								selectPageForState(false, true);
						});
					});
				});
			}
		});

		/* Context menu */
		ContextMenu menu = new ContextMenu();
		MenuItem connect = new MenuItem(bundle.getString("contextMenuConnect"));
		connect.onActionProperty().set((e) -> {
			connect(getSelectedConnection());
		});
		menu.getItems().add(connect);
		MenuItem disconnect = new MenuItem(bundle.getString("contextMenuDisconnect"));
		disconnect.onActionProperty().set((e) -> {
			disconnect(getSelectedConnection());
		});
		menu.getItems().add(disconnect);
		MenuItem add = new MenuItem(bundle.getString("contextMenuEdit"));
		add.onActionProperty().set((e) -> {
			editConnection(getSelectedConnection());
		});
		menu.getItems().add(add);
		MenuItem remove = new MenuItem(bundle.getString("contextMenuRemove"));
		remove.onActionProperty().set((e) -> {
			confirmDelete(getSelectedConnection());
		});
		menu.getItems().add(remove);
		connections.setContextMenu(menu);
		menu.setOnShowing((e) -> {
			VPNConnection selectedConnection = getSelectedConnection();
			if (selectedConnection != null) {
				try {
					ConnectionStatus.Type type = Type.valueOf(selectedConnection.getStatus());
					connect.setDisable(type != ConnectionStatus.Type.DISCONNECTED);
					disconnect.setDisable(
							type != ConnectionStatus.Type.CONNECTED && type != ConnectionStatus.Type.AUTHORIZING);
					add.setDisable(false);
					remove.setDisable(type != ConnectionStatus.Type.DISCONNECTED);
				} catch (Exception e1) {
					connect.setDisable(false);
					disconnect.setDisable(false);
					add.setDisable(false);
					remove.setDisable(false);
					showError("Failed to check state.", e1);
				}
			}
		});

		/* Configure engine */
		configureWebEngine();

		/* Make various components completely hide from layout when made invisible */
		sidebar.managedProperty().bind(sidebar.visibleProperty());

		/* Initial page */
		setHtmlPage("index.html");
		try {
			context.getDBus().addBusLifecycleListener(this);
			context.getDBus().getVPN().ping();
		} catch (Exception e) {
			setHtmlPage("index.html");
		}
	}

	public void setAvailable() {

		minimize.setVisible(!Main.getInstance().isNoMinimize() && Client.get().isMinimizeAllowed()
				&& Client.get().isUndecoratedWindow());
		minimize.setManaged(minimize.isVisible());
		close.setVisible(!Main.getInstance().isNoClose() && Client.get().isUndecoratedWindow());
		close.setManaged(close.isVisible());
		toggleSidebar.setVisible(!Main.getInstance().isNoSidebar() && !isNewUI());
		toggleSidebar.setManaged(toggleSidebar.isVisible());
	}

	@Override
	protected void onInitialize() {
		Font.loadFont(UI.class.getResource("ARLRDBD.TTF").toExternalForm(), 12);
	}

	protected void saveOptions(String trayMode, String darkMode, String phase, Boolean automaticUpdates,
			String logLevel, Boolean ignoreLocalRoutes, String dnsIntegrationMethod, Boolean saveCookies) {
		try {
			/* Local per-user GUI specific configuration */
			Configuration config = Configuration.getDefault();

			if (trayMode != null)
				config.trayModeProperty().set(trayMode);

			if (darkMode != null)
				config.darkModeProperty().set(darkMode);

			if (logLevel != null) {
				config.logLevelProperty().set(logLevel);
				if (logLevel.length() == 0)
					org.apache.log4j.Logger.getRootLogger().setLevel(Main.getInstance().getDefaultLogLevel());
				else
					org.apache.log4j.Logger.getRootLogger().setLevel(org.apache.log4j.Level.toLevel(logLevel));
			}

			if (saveCookies != null) {
				config.saveCookiesProperty().set(saveCookies);
			}

			/* Update configuration stored globally in service */
			VPN vpn = context.getDBus().getVPN();
			boolean checkUpdates = false;
			if (phase != null) {
				if (!vpn.isTrackServerVersion()) {
					String was = vpn.getValue(ConfigurationRepository.PHASE, "");
					if(!Objects.equals(was, phase)) {
						vpn.setValue(ConfigurationRepository.PHASE, phase);
						checkUpdates = true;
					}
				}
			}
			if (automaticUpdates != null) {
				String was = vpn.getValue(ConfigurationRepository.AUTOMATIC_UPDATES, "");
				String now = String.valueOf(automaticUpdates);
				if(!Objects.equals(was, now)) {
					vpn.setValue(ConfigurationRepository.AUTOMATIC_UPDATES, now);
					checkUpdates = true;
				}
			}
			if (ignoreLocalRoutes != null)
				vpn.setValue(ConfigurationRepository.IGNORE_LOCAL_ROUTES, String.valueOf(ignoreLocalRoutes));
			if (logLevel != null) {
				vpn.setValue(ConfigurationRepository.LOG_LEVEL, logLevel);
			}
			if (dnsIntegrationMethod != null) {
				vpn.setValue(ConfigurationRepository.DNS_INTEGRATION_METHOD, dnsIntegrationMethod);
			}

			/* Update selection */
			if (connections.getSelectionModel().isEmpty()) {
				adjustingSelection = true;
				try {
					connections.getSelectionModel().selectFirst();
				} finally {
					adjustingSelection = false;
				}
			}

			if(checkUpdates) {
				new Thread() {
					public void run() {
						// TODO put this back, just for testing update available notification
//						context.getDBus().getVPN().checkForUpdate();
						maybeRunLater(() ->
						selectPageForState(false, true));
					}
				}.start();
			}
			else
				selectPageForState(false, false);

			LOG.info("Saved options");
		} catch (Exception e) {
			showError("Failed to save options.", e);
		}
	}

	protected void setHtmlPage(String htmlPage) {
		setHtmlPage(htmlPage, false);
	}

	protected void setHtmlPage(String htmlPage, boolean force) {
		if (!Objects.equals(htmlPage, this.htmlPage) || force) {
			this.htmlPage = htmlPage;
			LOG.info(String.format("Loading page %s", htmlPage));

			pageBundle = null;
			try {

				CookieHandler cookieHandler = java.net.CookieHandler.getDefault();
				if (htmlPage.startsWith("http://") || htmlPage.startsWith("https://")) {

					/* Set the device UUID cookie for all web access */
					try {
						URI uri = new URI(htmlPage);
						Map<String, List<String>> headers = new LinkedHashMap<String, List<String>>();
						headers.put("Set-Cookie", Arrays.asList(String.format("%s=%s",
								AbstractDBusClient.DEVICE_IDENTIFIER, context.getDBus().getVPN().getUUID())));
						cookieHandler.put(uri.resolve("/"), headers);
					} catch (Exception e) {
						throw new IllegalStateException("Failed to set cookie.", e);
					}
					webView.getEngine().setUserStyleSheetLocation(UI.class.getResource("remote.css").toExternalForm());
					if (htmlPage.contains("?"))
						htmlPage += "&_=" + Math.random();
					else
						htmlPage += "?_=" + Math.random();
					webView.getEngine().load(htmlPage);
				} else {

					/*
					 * The only way I can seem to get this to work is to use a Data URI. Local file
					 * URI does not work (resource also works, but we need a dynamic resource, and I
					 * couldn't get a custom URL handler to work either).
					 */
					File customLocalWebCSSFile = context.getCustomLocalWebCSSFile();
					if (customLocalWebCSSFile.exists()) {
						if (LOG.isDebugEnabled())
							LOG.debug(String.format("Setting user stylesheet at %s", customLocalWebCSSFile));
						byte[] localCss = IOUtils.toByteArray(customLocalWebCSSFile.toURI());
						String datauri = "data:text/css;base64," + Base64.getEncoder().encodeToString(localCss);
						webView.getEngine().setUserStyleSheetLocation(datauri);
					} else {
						if (LOG.isDebugEnabled())
							LOG.debug(String.format("No user stylesheet at %s", customLocalWebCSSFile));
					}

					setupPage();
					String loc = htmlPage;
					if (Client.useLocalHTTPService) {
						// webView.getEngine().load("app://" + htmlPage);
						loc = DEFAULT_LOCALHOST_ADDR + htmlPage;
					} else {
						URL resource = UI.class.getResource(htmlPage);
						if (resource == null)
							throw new FileNotFoundException(String.format("No page named %s.", htmlPage));
						loc = resource.toExternalForm();
					}

					URI uri = new URI(loc);
					Map<String, List<String>> headers = new LinkedHashMap<String, List<String>>();
					headers.put("Set-Cookie", Arrays
							.asList(String.format("%s=%s", Client.LOCBCOOKIE, Client.localWebServerCookie.toString())));
					cookieHandler.put(uri.resolve("/"), headers);

					if (LOG.isDebugEnabled())
						LOG.debug(String.format("Loading location %s", loc));
					webView.getEngine().load(loc);
				}

				sidebar.setVisible(false);
			} catch (Exception e) {
				LOG.error("Failed to set page.", e);
			}
		}
	}

	private void setupPage() {
		if (htmlPage == null || htmlPage.startsWith("http://") || htmlPage.startsWith("https://")) {
			pageBundle = resources;
		} else {
			int idx = htmlPage.lastIndexOf('?');
			if (idx != -1)
				htmlPage = htmlPage.substring(0, idx);
			idx = htmlPage.lastIndexOf('.');
			if (idx == -1) {
				htmlPage = htmlPage + ".html";
				idx = htmlPage.lastIndexOf('.');
			}
			String base = htmlPage.substring(0, idx);
			String res = Client.class.getName();
			idx = res.lastIndexOf('.');
			String resourceName = res.substring(0, idx) + "." + base;
			if (LOG.isDebugEnabled())
				LOG.debug(String.format("Loading bundle %s", resourceName));
			try {
				pageBundle = ResourceBundle.getBundle(resourceName);
			} catch (MissingResourceException mre) {
				// Page doesn't have resources
				LOG.debug(String.format("No resources for %s", resourceName));
				pageBundle = resources;
			}
		}
	}

	private void processJavascript() {
		if (log.isDebugEnabled())
			log.debug("Processing page content");
		String baseHtmlPage = getBaseHtml();
		WebEngine engine = webView.getEngine();
		JSObject jsobj = (JSObject) engine.executeScript("window");
		jsobj.setMember("bridge", bridge);
		VPNConnection selectedConnection = getSelectedConnection();
		jsobj.setMember("connection", selectedConnection);
		jsobj.setMember("pageBundle", pageBundle);

		/* Special pages */
		if ("options.html".equals(baseHtmlPage)) {
			for (Map.Entry<String, Object> beanEn : beansForOptions().entrySet()) {
				log.debug(String.format(" Setting %s to %s", beanEn.getKey(), beanEn.getValue()));
				jsobj.setMember(beanEn.getKey(), beanEn.getValue());
			}
		} else if ("authorize.html".equals(baseHtmlPage)) {
			// TODO release this is page changes
			Semaphore authorizedLock = new Semaphore(1);
			try {
				authorizedLock.acquire();
			} catch (InterruptedException e1) {
				throw new IllegalStateException("Already acquired authorize lock.");
			}

			ServiceClientAuthenticator authenticator = new ServiceClientAuthenticator(context, engine, authorizedLock);
			jsobj.setMember("authenticator", authenticator);
			final ServiceClient serviceClient = new ServiceClient(authenticator);
			jsobj.setMember("serviceClient", serviceClient);
			jsobj.setMember("register", new Register(selectedConnection, serviceClient, this));
		}

		/* Override log. TODO: Not entirely sure this works entirely */
		engine.executeScript("console.log = function(message)\n" + "{\n" + "    bridge.log(message);\n" + "};");

		/* Signal to page we are ready */
		try {
			engine.executeScript("uiReady();");
		} catch (Exception e) {
			log.debug(String.format("Page %s failed to execute uiReady() functions.", baseHtmlPage), e);
		}

	}

	private String getBaseHtml() {
		String htmlPage = this.htmlPage;
		if (htmlPage == null)
			return htmlPage;
		int idx = htmlPage.indexOf('?');
		if (idx != -1) {
			htmlPage = htmlPage.substring(0, idx);
		}
		idx = htmlPage.indexOf('#');
		if (idx != -1) {
			htmlPage = htmlPage.substring(0, idx);
		}
		return htmlPage;
	}

	private void processDOM() {
		boolean busAvailable = context.getDBus().isBusAvailable();
		VPNConnection connection = busAvailable ? getSelectedConnection() : null;
		DOMProcessor processor = new DOMProcessor(busAvailable ? context.getDBus().getVPN() : null,
				connection, collections, lastErrorMessage, lastErrorCause,
				lastException, branding, pageBundle, resources, webView.getEngine().getDocument().getDocumentElement(),
				connection == null ? disconnectionReason : connection.getLastError());
		processor.process();
		collections.clear();
		lastException = null;
		lastErrorMessage = null;
		lastErrorCause = null;
	}

	private void connectToUri(String unprocessedUri) {
		context.getOpQueue().execute(() -> {
			try {
				LOG.info(String.format("Connected to URI %s", unprocessedUri));
				URI uriObj = Util.getUri(unprocessedUri);
				long connectionId = context.getDBus().getVPN().getConnectionIdForURI(uriObj.toASCIIString());
				if (connectionId == -1) {
					if (Main.getInstance().isCreateIfDoesntExist()) {
						/* No existing configuration */
						context.getDBus().getVPN().createConnection(uriObj.toASCIIString(), true, true,
								Mode.CLIENT.name());
					} else {
						showError(MessageFormat.format(bundle.getString("error.uriProvidedDoesntExist"), uriObj));
					}
				} else {
					reloadState(() -> {
						maybeRunLater(() -> {
							initUi(context.getDBus().getVPNConnection(connectionId));
						});
					});
				}

			} catch (Exception e) {
				showError("Failed to add connection.", e);
			}
		});
	}

	private void addConnection() {
		setHtmlPage("addLogonBoxVPN.html");
		sidebar.setVisible(false);
	}

	private void configure(String usernameHint, String configIniFile, VPNConnection config) {
		try {
			LOG.info(String.format("Configuration for %s", usernameHint));
			config.setUsernameHint(usernameHint);
			String error = config.parse(configIniFile);
			if (StringUtils.isNotBlank(error))
				throw new IOException(error);

			config.save();
			selectPageForState(false, false);
			config.authorized();
		} catch (Exception e) {
			showError("Failed to configure connection.", e);
		}
	}

	private boolean confirmDelete(VPNConnection sel) {
		Alert alert = new Alert(AlertType.CONFIRMATION);
		alert.initModality(Modality.APPLICATION_MODAL);
		alert.initOwner(getStage());
		alert.setTitle(resources.getString("delete.confirm.title"));
		alert.setHeaderText(resources.getString("delete.confirm.header"));
		alert.setContentText(MessageFormat.format(resources.getString("delete.confirm.content"), sel.getUri(false)));
		Optional<ButtonType> result = alert.showAndWait();
		if (result.get() == ButtonType.OK) {
			try {
				if (Type.valueOf(sel.getStatus()) == Type.CONNECTED) {
					log.info("Disconnecting deleted connection.");
					deleteOnDisconnect = true;
					doDisconnect(sel);
				} else {
					doDelete(sel);
					initUi(sel);
				}
			} catch (Exception e) {
				log.error("Failed to delete connection.", e);
			}
			return true;
		} else {
			return false;
		}
	}

	private void doDelete(VPNConnection sel) throws RemoteException {
		setHtmlPage("busy.html");
		log.info(String.format("Deleting connection %s", sel));
		sel.delete();
	}

	private void reloadState(Runnable then) {
		/*
		 * Care must be take that this doesn't run on the UI thread, as it may take a
		 * while.
		 */

//		mode = context.getDBus().isBusAvailable() && context.getDBus().getVPN().isUpdating() ? UIState.UPDATE : UIState.NORMAL;
		VPNConnection selectedConnection = getSelectedConnection();
		if (Client.allowBranding) {
			branding = getBranding(selectedConnection);
		}
		File splashFile = getCustomSplashFile();
		if (branding == null) {
			log.info(String.format("Removing branding."));
			if (logoFile != null) {
				logoFile.delete();
				logoFile = null;
			}
			splashFile.delete();
		} else {
			if (log.isDebugEnabled())
				log.debug("Adding custom branding");
			String logo = branding.getLogo();

			/* Create branded splash */
			BufferedImage bim = null;
			Graphics2D graphics = null;
			if (branding.getResource() != null && StringUtils.isNotBlank(branding.getResource().getBackground())) {
				bim = new BufferedImage(SPLASH_WIDTH, SPLASH_HEIGHT, BufferedImage.TYPE_INT_ARGB);
				graphics = (Graphics2D) bim.getGraphics();
				graphics.setColor(java.awt.Color.decode(branding.getResource().getBackground()));
				graphics.fillRect(0, 0, SPLASH_WIDTH, SPLASH_HEIGHT);
			}

			/* Create logo file */
			if (StringUtils.isNotBlank(logo)) {
				File newLogoFile = getCustomLogoFile(selectedConnection);
				try {
					if (!newLogoFile.exists()) {
						log.info(String.format("Attempting to cache logo"));
						URL logoUrl = new URL(logo);
						try (InputStream urlIn = logoUrl.openStream()) {
							try (OutputStream out = new FileOutputStream(newLogoFile)) {
								urlIn.transferTo(out);
							}
						}
						log.info(String.format("Logo cached from %s to %s", logoUrl, newLogoFile.toURI()));
						newLogoFile.deleteOnExit();
					}
					logoFile = newLogoFile;
					branding.setLogo(logoFile.toURI().toString());

					/* Draw the logo on the custom splash */
					if (graphics != null) {
						log.info(String.format("Drawing logo on splash"));
						BufferedImage logoImage = ImageIO.read(logoFile);
						if (logoImage == null)
							throw new Exception(String.format("Failed to load image from %s", logoFile));
						graphics.drawImage(logoImage, (SPLASH_WIDTH - logoImage.getWidth()) / 2,
								(SPLASH_HEIGHT - logoImage.getHeight()) / 2, null);
					}

				} catch (Exception e) {
					log.error(String.format("Failed to cache logo"), e);
					branding.setLogo(null);
				}
			} else if (logoFile != null) {
				logoFile.delete();
				logoFile = null;
			}

			/* Write the splash */
			if (graphics != null) {
				try {
					ImageIO.write(bim, "png", splashFile);
					log.info(String.format("Custom splash written to %s", splashFile));
				} catch (IOException e) {
					log.error(String.format("Failed to write custom splash"), e);
					splashFile.delete();
				}
			}
		}

		then.run();
	}

	private File getCustomSplashFile() {
		File tmpFile;
		String name = "lbvpnc-splash.png";
		if (System.getProperty("hypersocket.bootstrap.distDir") == null)
			tmpFile = new File(new File(System.getProperty("java.io.tmpdir")),
					System.getProperty("user.name") + "-" + name);
		else
			tmpFile = new File(new File(System.getProperty("hypersocket.bootstrap.distDir")).getParentFile(), name);
		return tmpFile;
	}

	private File getCustomLogoFile(VPNConnection connection) {
		File tmpFile;
		String name = "lpvpnclogo-" + (connection == null ? "default" : connection.getId());
		if (System.getProperty("hypersocket.bootstrap.distDir") == null)
			tmpFile = new File(new File(System.getProperty("java.io.tmpdir")),
					System.getProperty("user.name") + "-" + name);
		else
			tmpFile = new File(new File(System.getProperty("hypersocket.bootstrap.distDir")).getParentFile(), name);
		return tmpFile;
	}

	private void reapplyColors() {
		context.applyColors(branding, null);
	}

	private void reapplyLogo() {
		String defaultLogo = UI.class.getResource("logonbox-titlebar-logo.png").toExternalForm();
		if ((branding == null || StringUtils.isBlank(branding.getLogo())
				&& !defaultLogo.equals(titleBarImageView.getImage().getUrl()))) {
			titleBarImageView.setImage(new Image(defaultLogo, true));
		} else if (branding != null && !defaultLogo.equals(branding.getLogo())
				&& !StringUtils.isBlank(branding.getLogo())) {
			titleBarImageView.setImage(new Image(branding.getLogo(), true));
		}
	}

	private void doDisconnect(VPNConnection sel) {
		context.getOpQueue().execute(() -> {
			try {
				sel.disconnect("");
			} catch (Exception e) {
				log.error("Failed to disconnect.", e);
			}
		});
	}

	private void editConnection(VPNConnection connection) {
		setHtmlPage("editConnection.html");
		sidebar.setVisible(false);
	}

	@FXML
	private void evtAddConnection() {
		addConnection();
	}

	@FXML
	private void evtClose() {
		context.maybeExit();
	}

	@FXML
	private void evtMinimize() {
		context.getStage().setIconified(true);
	}

	@FXML
	private void evtOptions() throws Exception {
		options();
	}

	@FXML
	private void evtToggleSidebar() {
		sidebar.setVisible(!sidebar.isVisible());
		if (sidebar.isVisible()) {
			connections.requestFocus();
		}
	}

	private String getAppName(String app) {
		if (resources.containsKey(app)) {
			return resources.getString(app);
		} else {
			return app;
		}
	}

	private void giveUpWaitingForBridgeEstablish() {
		LOG.info("Given up waiting for bridge to start");
		resetAwaingBridgeEstablish();
		notify(resources.getString("givenUpWaitingForBridgeEstablish"), ToastType.ERROR);
		selectPageForState(false, false);
	}

	/*
	 * The following are all events from UI
	 */

	private void giveUpWaitingForBridgeStop() {
		LOG.info("Given up waiting for bridge to stop");
		resetAwaingBridgeLoss();
		notify(resources.getString("givenUpWaitingForBridgeStop"), ToastType.ERROR);
	}

	private void initUi(VPNConnection connection) {
		log.info("Rebuilding URIs");
		try {
			rebuildConnections(connection);
		} catch (Exception e) {
			log.error("Failed to load connections.", e);
		}
		context.applyColors(branding, getScene().getRoot());
		reapplyLogo();
	}

	private void rebuildConnections(final VPNConnection sel) {
		connections.getItems().clear();
		List<VPNConnection> connectionList = context.getDBus().isBusAvailable() ? context.getDBus().getVPNConnections()
				: Collections.emptyList();
		VPNConnection connected = null;
		VPNConnection connecting = null;
		for (VPNConnection s : connectionList) {
			connections.getItems().add(s);
			Type status = Type.valueOf(s.getStatus());
			if (connected == null && status == Type.CONNECTED) {
				connected = s;
			}
			if (connecting == null && status == Type.CONNECTING) {
				connecting = s;
			}
		}
		if (sel != null && connections.getItems().contains(sel))
			connections.getSelectionModel().select(sel);
		else if (connections.getSelectionModel().isEmpty() && !connections.getItems().isEmpty()) {
			/* Prefer connected connection */
			if (connected == null) {
				if (connecting == null)
					connections.getSelectionModel().select(connections.getItems().get(0));
				else
					connections.getSelectionModel().select(connecting);
			} else
				connections.getSelectionModel().select(connected);
		}
	}

	private void resetAwaingBridgeEstablish() {
		if (awaitingBridgeEstablish != null) {
			awaitingBridgeEstablish.stop();
			awaitingBridgeEstablish = null;
		}
	}

	private void resetAwaingBridgeLoss() {
		if (awaitingBridgeLoss != null) {
			awaitingBridgeLoss.stop();
			awaitingBridgeLoss = null;
		}
	}

	private void resetState() {
		resetAwaingBridgeEstablish();
		resetAwaingBridgeLoss();
		appsToUpdate = 0;
		appsUpdated = 0;
	}

	private void selectPageForState(boolean connectIfDisconnected, boolean force) {
		try {
			AbstractDBusClient bridge = context.getDBus();
			boolean busAvailable = bridge.isBusAvailable();
			if (busAvailable && bridge.getVPN().isUpdating()) {
				setHtmlPage("updating.html");
			} else if (busAvailable && bridge.getVPN().isUpdatesEnabled() && bridge.getVPN().isNeedsUpdating()) {
				// An update is available
				log.warn(String.format("Update is available"));
				if (bridge.getVPN().isUpdating()) {
					setHtmlPage("updating.html");
				} else if (Boolean
						.valueOf(context.getDBus().getVPN().getValue(ConfigurationRepository.AUTOMATIC_UPDATES,
								String.valueOf(ConfigurationRepository.AUTOMATIC_UPDATES_DEFAULT)))) {
					update();
				} else
					setHtmlPage("updateAvailable.html");
			} else {
				if (busAvailable && bridge.getVPN().getMissingPackages().length > 0) {
					log.warn(String.format("Missing software packages"));
					collections.put("packages", Arrays.asList(bridge.getVPN().getMissingPackages()));
					setHtmlPage("missingSoftware.html");
				} else {
					if (!busAvailable) {
						/* The bridge is not (yet?) connected */
						setHtmlPage("index.html");
					} else {

						if (isNewUI()) {
							/* Are ANY connections authorizing? */
							for (VPNConnection connection : context.getDBus().getVPNConnections()
									.toArray(new VPNConnection[0])) {
								Type status = Type.valueOf(connection.getStatus());
								if (status == Type.AUTHORIZING) {

									Mode mode = Mode.valueOf(connection.getMode());
									if (mode == Mode.SERVICE) {
										log.info("Authorizing service");
										setHtmlPage("authorize.html");
									} else {
										String authUrl = connection.getAuthorizeUri();
										log.info(String.format("Authorizing to %s", authUrl));
										setHtmlPage(authUrl);
									}

									/* Done */
									return;
								}
							}

							/* Otherwise connections page */
							setHtmlPage("connections.html", true);
						} else {

							VPNConnection sel = getSelectedConnection();

							/* The bridge is connected */
							if (sel == null) {
								if (Main.getInstance().isNoAddWhenNoConnections()) {
									if (Main.getInstance().isConnect()
											|| StringUtils.isNotBlank(Main.getInstance().getUri()))
										setHtmlPage("busy.html");
									else
										setHtmlPage("connected.html");
								} else
									setHtmlPage("addLogonBoxVPN.html");
							} else {

								Type status = Type.valueOf(sel.getStatus());
								if (connectIfDisconnected && status != Type.DISCONNECTED
										&& status != Type.TEMPORARILY_OFFLINE && !sel.isAuthorized()) {
									log.info(String.format("Not authorized, requesting authorize"));
									authorize(sel);
								} else if (status == Type.AUTHORIZING) {
									Mode mode = Mode.valueOf(sel.getMode());
									if (mode == Mode.SERVICE) {
										log.info("Authorizing service");
										setHtmlPage("authorize.html");
									} else {
										String authUrl = sel.getAuthorizeUri();
										log.info(String.format("Authorizing to %s", authUrl));
										setHtmlPage(authUrl);
									}
								} else {
									if (status == Type.CONNECTING || status == Type.AUTHORIZING) {
										/* We have a connection, a peer configuration and are connected! */
										log.info(String.format("Joining"));
										setHtmlPage("joining.html", force);
									} else if (status == Type.TEMPORARILY_OFFLINE) {
										/* We are connected, but the server (peer) appears offline */
										log.info(String.format("Temporarily Offline"));
										setHtmlPage("temporarilyOffline.html", force);
									} else if (status == Type.CONNECTED) {
										/* We have a connection, a peer configuration and are connected! */
										log.info(String.format("Ready, so showing join UI"));
										setHtmlPage("joined.html", force);
									} else if (status == Type.DISCONNECTING) {
										log.info(String.format("Disconnecting, so showing leaving UI"));
										setHtmlPage("leaving.html", force);
									} else {
										log.info("Disconnected, so showing join UI");
										setHtmlPage("join.html", force);
									}
								}
							}
						}
					}
				}
			}
		} catch (Exception e) {
			showError("Failed to set page.", e);
		}

	}

	private void setUpdateProgress(int val, String text) {
		if (!Platform.isFxApplicationThread())
			Platform.runLater(() -> setUpdateProgress(val, text));
		else {
			if (htmlPage.equals("updating.html")) {
				Document document = webView.getEngine().getDocument();
				if (document == null) {
					log.warn(String.format("No document,  ignoring progress update '%s'.", text));
				} else {
					Element progressElement = document.getElementById("progress");
					progressElement.setAttribute("aria-valuenow", String.valueOf(val));
					progressElement.setAttribute("style", "width: " + String.valueOf(val) + "%");
					Element progressText = document.getElementById("progressText");
					progressText.setTextContent(text);
				}
			}
		}
	}

	private void showError(String error) {
		showError(error, (String) null);
	}

	private void showError(String error, String cause) {
		showError(error, cause, (String) null);
	}

	private void showError(String error, Throwable exception) {
		showError(error, null, exception);
	}

	private void showError(String error, String cause, String exception) {
		showError(error, cause, exception, "error.html");
	}

	private void showError(String error, String cause, String exception, String page) {
		maybeRunLater(() -> {
			LOG.error(error, exception);
			lastErrorCause = cause;
			lastErrorMessage = error;
			lastException = exception;
			setHtmlPage(page);
		});
	}

	private void showError(String error, String cause, Throwable exception) {
		maybeRunLater(() -> {
			LOG.error(error, exception);
			if (error == null && exception != null) {
				lastErrorMessage = exception.getMessage();
			} else {
				lastErrorMessage = error;
			}
			if ((cause == null || cause.equals("")) && exception != null && exception.getCause() != null) {
				lastErrorCause = exception.getCause().getMessage();
			} else {
				lastErrorCause = cause;
			}
			if (exception == null) {
				lastException = "";
			} else {
				StringWriter w = new StringWriter();
				exception.printStackTrace(new PrintWriter(w));
				lastException = w.toString();
			}
			setHtmlPage("error.html");
		});
	}

	private void update() {
		new Thread() {
			public void run() {
				context.getDBus().getVPN().update();
			}
		}.start();
	}

	private void checkForUpdate() {
		context.getDBus().getVPN().checkForUpdate();
		if (context.getDBus().getVPN().isNeedsUpdating()) {
			selectPageForState(false, true);
		}
	}

	private void deferUpdate() {
		context.getDBus().getVPN().deferUpdate();
		selectPageForState(false, true);
	}

	private void cancelUpdate() {
		context.getDBus().getVPN().cancelUpdate();
		selectPageForState(false, true);
	}

	private boolean isNewUI() {
		return "true".equals(System.getProperty("logonbox.vpn.newUI", "false"));
	}

	public static void maybeRunLater(Runnable r) {
		if (Platform.isFxApplicationThread())
			r.run();
		else
			Platform.runLater(r);
	}

	public Branding getBranding(VPNConnection connection) {
		ObjectMapper mapper = new ObjectMapper();
		Branding branding = null;
		if (connection != null) {
			try {
				branding = getBrandingForConnection(mapper, connection);
			} catch (IOException ioe) {
				log.info(String.format("Skipping %s:%d because it appears offline.", connection.getHostname(),
						connection.getPort()));
			}
		}
		if (branding == null) {
			for (VPNConnection conx : context.getDBus().getVPNConnections()) {
				try {
					branding = getBrandingForConnection(mapper, conx);
					break;
				} catch (IOException ioe) {
					log.info(String.format("Skipping %s:%d because it appears offline.", conx.getHostname(),
							conx.getPort()));
				}
			}
		}
		return branding;
	}

	protected Branding getBrandingForConnection(ObjectMapper mapper, VPNConnection connection)
			throws UnknownHostException, IOException, JsonProcessingException, JsonMappingException {
		synchronized (brandingCache) {
			BrandingCacheItem item = brandingCache.get(connection);
			if (item != null && item.isExpired()) {
				item = null;
			}
			if (item == null) {
				item = new BrandingCacheItem(branding);
				brandingCache.put(connection, item);
				String uri = connection.getUri(false) + "/api/brand/info";
				log.info(String.format("Retrieving branding from %s", uri));
				URL url = new URL(uri);
				URLConnection urlConnection = url.openConnection();
				urlConnection.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(10));
				urlConnection.setReadTimeout((int) TimeUnit.SECONDS.toMillis(10));
				try (InputStream in = urlConnection.getInputStream()) {
					Branding brandingObj = mapper.readValue(in, Branding.class);
					brandingObj.setLogo("https://" + connection.getHostname() + ":" + connection.getPort()
							+ connection.getPath() + "/api/brand/logo");
					item.branding = brandingObj;
				}
			}
			return item.branding;
		}
	}
}
