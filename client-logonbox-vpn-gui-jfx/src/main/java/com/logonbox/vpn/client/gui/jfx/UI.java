package com.logonbox.vpn.client.gui.jfx;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.rmi.RemoteException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Predicate;

import javax.xml.bind.DatatypeConverter;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.controlsfx.control.action.Action;
import org.ini4j.Ini;
import org.ini4j.Profile.Section;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.hypersocket.HypersocketVersion;
import com.hypersocket.extensions.JsonExtensionPhase;
import com.hypersocket.extensions.JsonExtensionPhaseList;
import com.logonbox.vpn.client.gui.jfx.Bridge.Listener;
import com.logonbox.vpn.common.client.Branding;
import com.logonbox.vpn.common.client.ConfigurationService;
import com.logonbox.vpn.common.client.Connection;
import com.logonbox.vpn.common.client.ConnectionService;
import com.logonbox.vpn.common.client.ConnectionStatus;
import com.logonbox.vpn.common.client.ConnectionStatus.Type;
import com.logonbox.vpn.common.client.GUICallback;
import com.logonbox.vpn.common.client.Keys;
import com.logonbox.vpn.common.client.Keys.KeyPair;
import com.logonbox.vpn.common.client.Util;
import com.sshtools.twoslices.Toast;
import com.sshtools.twoslices.ToastType;
import com.sshtools.twoslices.ToasterFactory;
import com.sshtools.twoslices.ToasterSettings;
import com.sshtools.twoslices.ToasterSettings.SystemTrayIconMode;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.ObservableList;
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
import javafx.util.Callback;
import javafx.util.Duration;
import netscape.javascript.JSObject;

public class UI extends AbstractController implements Listener {

	private static final String DEFAULT_LOCALHOST_ADDR = "http://localhost:59999/";

	/**
	 * This object is exposed to the local HTML/Javascript that runs in the browse.
	 */
	public class ServerBridge {

		public void addConnection(JSObject o) {
			Boolean connectAtStartup = (Boolean) o.getMember("connectAtStartup");
			String server = (String) o.getMember("serverUrl");
			UI.this.addConnection(connectAtStartup, server);
		}

		public void authenticate() {
			UI.this.authorize(UI.this.connections.getSelectionModel().getSelectedItem());
		}

		public void configure(String usernameHint, String configIniFile) {
			LOG.info(String.format("Connect user: %s, Config: %s", usernameHint, configIniFile));
			UI.this.configure(usernameHint, configIniFile, UI.this.connections.getSelectionModel().getSelectedItem());
		}

		public void connect() {
			UI.this.connect(UI.this.connections.getSelectionModel().getSelectedItem());
		}

		public void editConnection(JSObject o) {
			Boolean connectAtStartup = (Boolean) o.getMember("connectAtStartup");
			String server = (String) o.getMember("serverUrl");
			UI.this.editConnection(connectAtStartup, server, getSelectedConnection());
		}

		public Connection getConnection() {
			return UI.this.getSelectedConnection();
		}

		public String getDeviceName() {
			String hostname = SystemUtils.getHostName();
			if (StringUtils.isBlank(hostname)) {
				try {
					hostname = InetAddress.getLocalHost().getHostName();
				} catch (Exception e) {
					hostname = "Unknown Host";
				}
			}
			String os = System.getProperty("os.name");
			if (SystemUtils.IS_OS_WINDOWS) {
				os = "Windows";
			} else if (SystemUtils.IS_OS_LINUX) {
				os = "Linux";
			} else if (SystemUtils.IS_OS_MAC_OSX) {
				os = "Mac OSX";
			}
			return os + " " + hostname;
		}

		public String getUserPublicKey() {
			Connection connection = getConnection();
			return connection == null ? null : connection.getUserPublicKey();
		}

		public void join() {
			UI.this.joinNetwork(UI.this.connections.getSelectionModel().getSelectedItem());
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
			String trayMode = (String) o.getMember("trayMode");
			String phase = (String) o.getMember("phase");
			Boolean automaticUpdates = (Boolean) o.getMember("automaticUpdates");
			UI.this.saveOptions(trayMode, phase, automaticUpdates);
		}

		public void showError(String error) {
			UI.this.showError(error);
		}

		public void unjoin(String reason) {
			disconnect(getSelectedConnection(), reason);
		}

		public void update() {
			UI.this.update();
		}
	}

	final static ResourceBundle bundle = ResourceBundle.getBundle(UI.class.getName());

	static {
		ToasterSettings settings = new ToasterSettings();
		settings.setAppName(bundle.getString("appName"));
		settings.setSystemTrayIconMode(SystemTrayIconMode.HIDDEN);
		ToasterFactory.setSettings(settings);
	}

	static int DROP_SHADOW_SIZE = 11;

	final static Logger log = LoggerFactory.getLogger(UI.class);

	static Logger LOG = LoggerFactory.getLogger(UI.class);
	static final String PRIVATE_KEY_NOT_AVAILABLE = "PRIVATE_KEY_NOT_AVAILABLE";

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
	private List<Connection> connecting = new ArrayList<>();
	private boolean deleteOnDisconnect;
	private String htmlPage;
	private String lastErrorMessage;
	private Throwable lastException;
	private ResourceBundle pageBundle;
	private UIState mode = UIState.NORMAL;

	@FXML
	protected ListView<Connection> connections;
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
	private File logoFile;

	public UI() {
		instance = this;
	}

	@Override
	public void bridgeEstablished() {

		LOG.info("Bridge established.");

		/*
		 * If we were waiting for this, it's part of the update process. We don't want
		 * the connection continuing
		 */
		if (awaitingBridgeEstablish != null) {
			awaitingRestart = true;
		} else {
			reloadState();
		}

		Platform.runLater(() -> {
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
					selectPageForState(false, Main.getInstance().isConnect());
				}
			}
		});
	}

	@Override
	public void bridgeLost() {
		LOG.info("Bridge lost");
		Platform.runLater(new Runnable() {

			@Override
			public void run() {
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
					selectPageForState(true, false);
				}
			}
		});

	}

	public void disconnect(Connection sel) {
		disconnect(sel, null);
	}

	public void disconnect(Connection sel, String reason) {
		if (reason == null)
			LOG.info("Requesting disconnect, no reason given");
		else
			LOG.info(String.format("Requesting disconnect, because '%s'", reason));

		try {
			context.getBridge().getClientService().disconnect(sel, reason);
		} catch (Exception e) {
			showError("Failed to disconnect.", e);
		}
	}

	@Override
	public void disconnected(final Connection connection, Exception e) {
		super.disconnected(connection, e);
		maybeRunLater(() -> {
			log.info("Disconnected " + connection + " (delete " + deleteOnDisconnect + ")");
			if (deleteOnDisconnect) {
				try {
					doDelete(connection);
					initUi(connection);
				} catch (RemoteException e1) {
					log.error("Failed to delete.", e);
				}
			} else {
				rebuildConnections(connection);
			}
			if (e != null) {
				showError("Disconnected.", e);
			} else {
				selectPageForState(true, false);
			}
		});
	}

	@Override
	public void finishedConnecting(final Connection connection, Exception e) {
		Platform.runLater(() -> {
			log.info("Finished connecting " + connection + ". "
					+ (e == null ? "No error" : "Error occured." + e.getMessage()) + " Foreground is ");

			connecting.remove(connection);

			rebuildConnections(connection);
			if (e != null) {
				showError("Failed to connect.", e);
				notify(e.getMessage(), GUICallback.NOTIFY_ERROR);
			} else {
				if (Main.getInstance().isExitOnConnection()) {
					context.exitApp();
				} else
					joinedNetwork();
			}

			// TODO fix when multiple different connections at possible different times

		});
		super.finishedConnecting(connection, e);
	}

	public UIState getMode() {
		return mode;
	}

	@Override
	public void initDone(boolean restart, String errorMessage) {
		if (errorMessage == null) {
			if (restart) {
				LOG.info(String.format("All apps updated, starting restart process " + Math.random()));
				awaitingBridgeLoss = new Timeline(
						new KeyFrame(Duration.seconds(30), ae -> giveUpWaitingForBridgeStop()));
				awaitingBridgeLoss.play();
			} else {
				LOG.info(String.format("No restart required, continuing"));
				resetState();
			}
		} else {
			setUpdateProgress(100, errorMessage);
			resetState();
		}
	}

	@Override
	public void initUpdate(int apps, UIState currentMode) {
		if (awaitingRestart)
			throw new IllegalStateException("Cannot initiate updates while waiting to restart the GUI.");
		this.mode = UIState.UPDATE;
		LOG.info(String.format("Initialising update (currently in mode %s). Expecting %d apps", currentMode, apps));
		appsToUpdate = apps;
		appsUpdated = 0;
		selectPageForState(false, false);
	}

	public void connect(Connection n) {
		try {
			log.info(String.format("Connect to %s", n.getAddress()));
			if (n != null) {
				Type status = context.getBridge().getClientService().getStatus(n);
				log.info(String.format("  current status is %s", status));
				if (status == Type.CONNECTED)
					joinedNetwork();
				else if (n.isAuthorized())
					joinNetwork(n);
				else
					authorize(n);
			}
		} catch (Exception e) {
			showError("Failed to connect.", e);
		}
	}

	public void joinNetwork(Connection connection) {
		setHtmlPage("joining.html");
		try {
			context.getBridge().getClientService().connect(connection);
		} catch (Exception e) {
			showError("Failed to join VPN.", e);
		}
	}

	public void notify(String msg, int type, Action... actions) {
		ToastType toastType = null;
		switch (type) {
		case GUICallback.NOTIFY_WARNING:
			toastType = ToastType.WARNING;
			break;
		case GUICallback.NOTIFY_INFO:
			toastType = ToastType.INFO;
			break;
		case GUICallback.NOTIFY_CONNECT:
		case GUICallback.NOTIFY_DISCONNECT:
			toastType = ToastType.INFO;
			break;
		case GUICallback.NOTIFY_ERROR:
			toastType = ToastType.ERROR;
			break;
		default:
			toastType = ToastType.NONE;
		}
		Toast.toast(toastType, resources.getString("appName"), msg);

	}

	private Map<String, Object> beansForOptions() {
		Map<String, Object> beans = new HashMap<>();
		try {
			JsonExtensionPhaseList phases = context.getBridge().getClientService().getPhases();
			beans.put("phases", phases.getResult() == null ? new JsonExtensionPhase[0] : phases.getResult());
		} catch (Exception e) {
			log.warn("Could not get phases.", e);
			beans.put("phases", new JsonExtensionPhase[0]);
		}
		beans.put("trayModes",
				new String[] { ConfigurationService.TRAY_MODE_AUTO, ConfigurationService.TRAY_MODE_COLOR,
						ConfigurationService.TRAY_MODE_DARK, ConfigurationService.TRAY_MODE_LIGHT,
						ConfigurationService.TRAY_MODE_OFF });
		try {
			beans.put("trayMode", context.getBridge().getConfigurationService().getValue(ConfigurationService.TRAY_MODE,
					ConfigurationService.TRAY_MODE_AUTO));
			beans.put("phase", context.getBridge().getConfigurationService().getValue(ConfigurationService.PHASE, ""));
			beans.put("automaticUpdates", Boolean.valueOf(context.getBridge().getConfigurationService()
					.getValue(ConfigurationService.AUTOMATIC_UPDATES, "true")));
		} catch (Exception e) {
			throw new IllegalStateException("Could not get beans.", e);
		}
		return beans;
	}

	public void options() {
		setHtmlPage("options.html");
		sidebar.setVisible(false);
	}

	public void setMode(UIState mode) {
		this.mode = mode;
		rebuildConnections(getSelectedConnection());
		selectPageForState(false, false);
	}

	@Override
	public boolean showBrowser(Connection connection, String uri) {
		Platform.runLater(() -> setHtmlPage(connection.getUri(false) + uri));
		return true;
	}

	@Override
	public void started(final Connection connection) {
		Platform.runLater(() -> {
			log.info("Started " + connection);
			initUi(connection);
		});
	}

	@Override
	public void startingUpdate(String app, long totalBytesExpected) {
		LOG.info(String.format("Starting up of %s, expect %d bytes", app, totalBytesExpected));
		String appName = getAppName(app);
		setUpdateProgress(0, MessageFormat.format(resources.getString("updating"), appName));
	}

	@Override
	public void updateComplete(String app, long totalBytesTransfered) {
		String appName = getAppName(app);
		setUpdateProgress(100, MessageFormat.format(resources.getString("updated"), appName));
		appsUpdated++;
		LOG.info(
				String.format("Update of %s complete, have now updated %d of %d apps", app, appsUpdated, appsToUpdate));
	}

	@Override
	public void updateFailure(String app, String message) {
		LOG.info(String.format("Failed to update app %s. %s", app, message));
		try {
			context.getBridge().notify(message, GUICallback.NOTIFY_ERROR);
		} catch (RemoteException e) {
			// Not actually remote
		}
		if(StringUtils.isBlank(message))
			showError(MessageFormat.format(resources.getString("updateFailureNoMessage"), app));
		else
			showError(MessageFormat.format(resources.getString("updateFailure"), app, message));
//		resetState();
		//context.getOpQueue().execute(() -> context.getBridge().disconnectAll());
	}

	@Override
	public void updateProgressed(String app, long sincelastProgress, long totalSoFar, long totalBytesExpected) {
		String appName = getAppName(app);
		setUpdateProgress((int) (((double) totalSoFar / totalBytesExpected) * 100d),
				MessageFormat.format(resources.getString("updating"), appName));
	}

	protected void addConnection(Boolean connectAtStartup, String unprocessedUri) {
		try {
			ConnectionService connectionService = context.getBridge().getConnectionService();
			URI uriObj = Util.getUri(unprocessedUri);
			final Connection connection = connectionService.createNew(uriObj);
			if (sameHostPortPathCheck(null, (conId) -> {
				try {
					return connectionService.getConnectionByHostPortAndPath(connection.getHostname(),
							connection.getPort(), connection.getPath()) != null;
				} catch (RemoteException e) {
					throw new IllegalStateException(e.getMessage(), e);
				}
			})) {
				return;
			}
			connection.setName(uriObj.toString());
			connection.setConnectAtStartup(connectAtStartup);

			maybeGenerateKeys(connection);

			Connection connectionSaved = connectionService.add(connection);
			connections.getItems().add(connectionSaved);
			connections.getSelectionModel().select(connectionSaved);
			reloadState();
			reapplyColors();
			reapplyLogo();
			authorize(connectionSaved);

		} catch (Exception e) {
			showError("Failed to add connection.", e);
		}
	}

	protected void maybeGenerateKeys(final Connection connection) {
		/*
		 * If enabled, generate the private key now on the client, to save the server
		 * having to do so (and also storing it).
		 */
		if (Client.generateKeysClientSide) {
			log.info("Generating private key");
			KeyPair key = Keys.genkey();
			connection.setUserPrivateKey(key.getBase64PrivateKey());
			connection.setUserPublicKey(key.getBase64PublicKey());
			log.info(String.format("Public key is %s", connection.getUserPublicKey()));
		}
	}

	protected void authorize(Connection n) {
		try {
			context.getBridge().getClientService().requestAuthorize(n);
		} catch (Exception e) {
			showError("Failed to join VPN.", e);
		}
	}

	protected void configureWebEngine() {
		WebEngine engine = webView.getEngine();
		engine.setUserAgent("LogonBox VPN Client " + HypersocketVersion.getVersion());
		engine.setOnAlert((e) -> {
			Alert alert = new Alert(AlertType.ERROR);
			alert.setTitle("Alert");
			alert.setContentText(e.getData());
			alert.showAndWait();
		});
		engine.setOnError((e) -> {
			LOG.error("Error in webengine.", e);
		});

		engine.setOnStatusChanged((e) -> {
		});
		engine.locationProperty().addListener((c, oldLoc, newLoc) -> {
			if (newLoc != null) {
				if ((newLoc.startsWith("http://") || newLoc.startsWith("https://")) && !(newLoc.startsWith(DEFAULT_LOCALHOST_ADDR))) {
					log.info("This is a remote page, not changing current html page");
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
					if (!base.equals("") && !base.equals(htmlPage)) {
						htmlPage = base;
						log.info(String.format("Page changed by user to %s (likely back button)", htmlPage));
					}
				}
				setupPage();
			}
		});
		engine.getLoadWorker().stateProperty().addListener((ov, oldState, newState) -> {
			if (newState == State.SUCCEEDED) {
				webViewReady(newState);
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
			Connection selectedConnection = getSelectedConnection();
			try {
				if (context.getBridge().getClientService()
						.getStatus(selectedConnection) == ConnectionStatus.Type.AUTHORIZING) {
					String reason = value != null ? value.getMessage() : null;
					LOG.info(String.format("Got error while authorizing. Disconnecting now using '%s' as the reason",
							reason));
					context.getBridge().getClientService().disconnect(selectedConnection, reason);

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

	protected void dataAttributes(Element node, Connection cfg, Connection connection,
			Map<Node, Collection<Node>> newNodes, Set<Node> removeNodes) {

		String errorText = "";
		String exceptionText = "";

		if (lastException != null) {
			StringWriter s = new StringWriter();
			lastException.printStackTrace(new PrintWriter(s, true));
			exceptionText = s.toString();
			if (lastErrorMessage == null) {
				errorText = lastException.getMessage();
			} else {
				errorText = lastErrorMessage + " " + lastException.getMessage();
			}
		} else if (lastErrorMessage != null) {
			errorText = lastErrorMessage;
		}

		String hostname = connection == null ? "" : connection.getHostname();
		String connectionUri = connection == null ? "" : connection.getUri(false);
		String port = connection == null ? "" : Integer.toString(connection.getPort());
		String endpointAddress = cfg == null ? "" : cfg.getEndpointAddress() + ":" + cfg.getEndpointPort();
		String publicKey = cfg == null ? "" : cfg.getPublicKey();
		String userPublicKey = cfg == null ? "" : cfg.getUserPublicKey();
		String address = cfg == null ? "" : cfg.getAddress();
		String usernameHint = cfg == null ? "" : cfg.getUsernameHint();
		String connectAtStartup = cfg == null ? "false" : String.valueOf(cfg.isConnectAtStartup());
		String clientVersion = HypersocketVersion.getVersion();
		String brand = MessageFormat.format(resources.getString("brand"), ( branding == null || branding.getResource() == null || 
				StringUtils.isBlank(branding.getResource().getName()) ? "LogonBox" : branding.getResource().getName() ));

		NamedNodeMap attrs = node.getAttributes();
		for (int i = 0; i < attrs.getLength(); i++) {
			Node attr = attrs.item(i);
			String val = attr.getNodeValue();
			if (attr.getNodeName().startsWith("data-attr-i18n-")) {
				String attrVal = pageBundle == null ? "?" : pageBundle.getString(val);
				node.setAttribute(attr.getNodeName().substring(14), attrVal);
			} else {
				if (attr.getNodeName().equals("data-attr-value")) {
					if (val.equals("server"))
						node.setAttribute(node.getAttribute("data-attr-name"), hostname);
					else if (val.equals("serverUrl"))
						node.setAttribute(node.getAttribute("data-attr-name"), connectionUri);
					else if (val.equals("port"))
						node.setAttribute(node.getAttribute("data-attr-name"), port);
					else if (val.equals("connectAtStartup"))
						node.setAttribute(node.getAttribute("data-attr-name"), connectAtStartup);
					else if (val.equals("address"))
						node.setAttribute(node.getAttribute("data-attr-name"), address);
					else if (val.equals("usernameHint"))
						node.setAttribute(node.getAttribute("data-attr-name"), usernameHint);
					else if (val.equals("endpoint"))
						node.setAttribute(node.getAttribute("data-attr-name"), endpointAddress);
					else if (val.equals("brand"))
						node.setAttribute(node.getAttribute("data-attr-name"), brand);
					else if (val.equals("clientVersion"))
						node.setAttribute(node.getAttribute("data-attr-name"), clientVersion);
					else if (val.equals("publicKey"))
						node.setAttribute(node.getAttribute("data-attr-name"), publicKey);
					else if (val.equals("userPublicKey"))
						node.setAttribute(node.getAttribute("data-attr-name"), userPublicKey);
					else if (val.equals("exception"))
						node.setAttribute(node.getAttribute("data-attr-name"), exceptionText);
					else if (val.equals("errorMessage"))
						node.setAttribute(node.getAttribute("data-attr-name"), errorText);
				} else if (attr.getNodeName().equals("data-i18n")) {
					List<Object> args = new ArrayList<>();
					for (int ai = 0; ai < 9; ai++) {
						String i18nArg = node.getAttribute("data-i18n-" + ai);
						if (i18nArg != null) {
							if (i18nArg.equals("server"))
								args.add(hostname);
							else if (i18nArg.equals("serverUrl"))
								args.add(connectionUri);
							else if (i18nArg.equals("port"))
								args.add(port);
							else if (i18nArg.equals("address"))
								args.add(address);
							else if (i18nArg.equals("usernameHint"))
								args.add(usernameHint);
							else if (i18nArg.equals("endpoint"))
								args.add(endpointAddress);
							else if (i18nArg.equals("clientVersion"))
								args.add(clientVersion);
							else if (i18nArg.equals("brand"))
								args.add(brand);
							else if (i18nArg.equals("publicKey"))
								args.add(publicKey);
							else if (i18nArg.equals("userPublicKey"))
								args.add(userPublicKey);
							else if (i18nArg.equals("exception"))
								args.add(exceptionText);
							else if (i18nArg.equals("errorMessage"))
								args.add(errorText);
						}
					}
					String attrVal;
					try {
						attrVal = pageBundle.getString(val);
					} catch (MissingResourceException mre) {
						attrVal = bundle.getString(val);
					}
					if (!args.isEmpty()) {
						attrVal = MessageFormat.format(attrVal, args.toArray(new Object[0]));
					}
					node.setTextContent(attrVal);
				} else if (attr.getNodeName().equals("data-collection")) {
					Collection<String> collection = collections.get(val);
					if (collection == null)
						log.warn(String.format("No collection named %s", val));
					else {
						List<Node> newCollectionNodes = new ArrayList<>();
						for (String elVal : collection) {
							Node template = node.cloneNode(false);
							template.setTextContent(elVal);
							newCollectionNodes.add(template);
						}
						newNodes.put(node.getParentNode(), newCollectionNodes);
						removeNodes.add(node);
					}
				} else if (attr.getNodeName().equals("data-content")) {
					if (val.equals("server"))
						node.setTextContent(hostname);
					else if (val.equals("serverUrl"))
						node.setTextContent(connectionUri);
					else if (val.equals("port"))
						node.setTextContent(port);
					else if (val.equals("address"))
						node.setTextContent(address);
					else if (val.equals("usernameHint"))
						node.setTextContent(usernameHint);
					else if (val.equals("endpoint"))
						node.setTextContent(endpointAddress);
					else if (val.equals("brand"))
						node.setTextContent(brand);
					else if (val.equals("clientVersion"))
						node.setTextContent(clientVersion);
					else if (val.equals("publicKey"))
						node.setTextContent(publicKey);
					else if (val.equals("userPublicKey"))
						node.setTextContent(userPublicKey);
					else if (val.equals("exception"))
						node.setTextContent(exceptionText);
					else if (val.equals("errorMessage"))
						node.setTextContent(errorText);
				}
			}
		}

		NodeList n = node.getChildNodes();
		for (int i = 0; i < n.getLength(); i++) {
			Node c = n.item(i);
			if (c instanceof Element)
				dataAttributes((Element) c, cfg, connection, newNodes, removeNodes);
		}
	}

	protected void editConnection(Boolean connectAtStartup, String server, Connection connection) {
		try {
			ConnectionService connectionService = context.getBridge().getConnectionService();
			connection.setName(server);
			URI uriObj = Util.getUri(server);
			connection.setHostname(uriObj.getHost());
			connection
					.setPort(uriObj.getPort() < 1 ? (uriObj.getScheme().equals("https") ? 443 : 80) : uriObj.getPort());
			connection.setConnectAtStartup(connectAtStartup);
			Connection connectionSaved = connectionService.save(connection);
			connections.getSelectionModel().select(connectionSaved);
			selectPageForState(false, false);

		} catch (Exception e) {
			showError("Failed to save connection.", e);
		}
	}

	protected Connection getSelectedConnection() {
		Connection selectedItem = connections.getSelectionModel().getSelectedItem();
		if (selectedItem == null && connections.getItems().size() > 0)
			selectedItem = connections.getItems().get(0);
		return selectedItem;
	}

	@Override
	protected void onConfigure() {
		super.onConfigure();

		minimize.setVisible(!Main.getInstance().isNoMinimize());
		minimize.setManaged(minimize.isVisible());
		close.setVisible(!Main.getInstance().isNoClose());
		close.setManaged(close.isVisible());
		toggleSidebar.setVisible(!Main.getInstance().isNoSidebar());
		toggleSidebar.setManaged(toggleSidebar.isVisible());

		initUi(null);
		rebuildConnections(getSelectedConnection());

		/*
		 * Setup the connection list
		 */
		Callback<ListView<Connection>, ListCell<Connection>> factory = new Callback<ListView<Connection>, ListCell<Connection>>() {

			@Override
			public ListCell<Connection> call(ListView<Connection> l) {
				return new ListCell<Connection>() {

					@Override
					protected void updateItem(Connection item, boolean empty) {
						super.updateItem(item, empty);
						if (item == null) {
							setText("");
						} else if (item.getPort() != 443)
							setText(item.getHostname() + ":" + item.getPort());
						else
							setText(item.getHostname());
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
			reloadState();
			reapplyColors();
			reapplyLogo();
			selectPageForState(false, false);
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
			try {
				ConnectionStatus.Type type = context.getBridge().getClientService().getStatus(getSelectedConnection());
				connect.setDisable(type != ConnectionStatus.Type.DISCONNECTED);
				disconnect.setDisable(
						type != ConnectionStatus.Type.CONNECTED && type != ConnectionStatus.Type.AUTHORIZING);
				add.setDisable(false);
				remove.setDisable(type != ConnectionStatus.Type.DISCONNECTED);
			} catch (RemoteException e1) {
				connect.setDisable(false);
				disconnect.setDisable(false);
				add.setDisable(false);
				remove.setDisable(false);
				showError("Failed to check state.", e1);
			}
		});

		/* Configure engine */
		configureWebEngine();

		/* Make various components completely hide from layout when made invisible */
		sidebar.managedProperty().bind(sidebar.visibleProperty());

		/* Initial page */
		if (!context.getBridge().isConnected())
			setHtmlPage("index.html");
	}

	@Override
	protected void onInitialize() {
		Font.loadFont(UI.class.getResource("ARLRDBD.TTF").toExternalForm(), 12);
	}

	protected void saveOptions(String trayMode, String phase, boolean automaticUpdates) {
		try {
			context.getBridge().getConfigurationService().setValue(ConfigurationService.TRAY_MODE, trayMode);
			context.getBridge().getConfigurationService().setValue(ConfigurationService.PHASE, phase);
			context.getBridge().getConfigurationService().setValue(ConfigurationService.AUTOMATIC_UPDATES,
					String.valueOf(automaticUpdates));
			selectPageForState(false, false);
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

				if (htmlPage.startsWith("http://") || htmlPage.startsWith("https://")) {

					/* Set the device UUID cookie for all web access */
					try {
						URI uri = new URI(htmlPage);
						Map<String, List<String>> headers = new LinkedHashMap<String, List<String>>();
						headers.put("Set-Cookie", Arrays.asList(String.format("%s=%s", Client.DEVICE_IDENTIFIER,
								context.getBridge().getClientService().getUUID().toString())));
						java.net.CookieHandler.getDefault().put(uri.resolve("/"), headers);
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
					byte[] localCss = IOUtils.toByteArray(context.getCustomLocalWebCSSFile().toURI());
					String datauri = "data:text/css;base64," + DatatypeConverter.printBase64Binary(localCss);
					webView.getEngine().setUserStyleSheetLocation(datauri);

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
					java.net.CookieHandler.getDefault().put(uri.resolve("/"), headers);

					LOG.info(String.format("Loading location %s", loc));
					webView.getEngine().load(loc);
				}

				sidebar.setVisible(false);
			} catch (Exception e) {
				LOG.error("Failed to set page.", e);
			}
		}
	}

	protected void setupPage() {
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
			LOG.info(String.format("Loading bundle %s", resourceName));
			try {
				pageBundle = ResourceBundle.getBundle(resourceName);
			} catch (MissingResourceException mre) {
				// Page doesn't have resources
				LOG.debug(String.format("No resources for %s", resourceName));
				pageBundle = resources;
			}
		}
	}

	protected void webViewReady(State newState) {
		log.info("Processing page content");
		JSObject jsobj = (JSObject) webView.getEngine().executeScript("window");
		jsobj.setMember("bridge", new ServerBridge());
		jsobj.setMember("configuration", context.getBridge().getConfigurationService());
		if ("options.html".equals(htmlPage)) {
			for (Map.Entry<String, Object> beanEn : beansForOptions().entrySet()) {
				jsobj.setMember(beanEn.getKey(), beanEn.getValue());
			}
		}
		jsobj.setMember("connection", getSelectedConnection());
		jsobj.setMember("pageBundle", pageBundle);
		webView.getEngine()
				.executeScript("console.log = function(message)\n" + "{\n" + "    bridge.log(message);\n" + "};");

		try {
			Connection sel = getSelectedConnection();
			Element rootEl = webView.getEngine().getDocument().getDocumentElement();
			Map<Node, Collection<Node>> newNodes = new HashMap<>();
			Set<Node> removeNodes = new HashSet<>();
			dataAttributes(rootEl, sel, sel, newNodes, removeNodes);
			for (Map.Entry<Node, Collection<Node>> en : newNodes.entrySet()) {
				for (Node n : en.getValue())
					en.getKey().appendChild(n);
			}
			for (Node n : removeNodes)
				n.getParentNode().removeChild(n);

			collections.clear();
			lastException = null;
			lastErrorMessage = null;

		} catch (Exception e) {
			showError("Failed to initialise web view. ", e);
		}
	}

	private void connectToUri(String unprocessedUri) {
		try {
			LOG.info(String.format("Connecting to URI %s", unprocessedUri));
			ConnectionService connectionService = context.getBridge().getConnectionService();
			URI uriObj = Util.getUri(unprocessedUri);
			Connection connection = connectionService.createNew(uriObj);
			Connection conx = connectionService.getConnectionByName(uriObj.toString());
			if (conx == null) {
				if (Main.getInstance().isCreateIfDoesntExist()) {
					/* No existing configuration */
					connection.setName(uriObj.toString());
					connection.setConnectAtStartup(true);
					maybeGenerateKeys(connection);
					Connection connectionSaved = connectionService.add(connection);
					connections.getItems().add(connectionSaved);
					connections.getSelectionModel().select(connectionSaved);
					reloadState();
					reapplyColors();
					reapplyLogo();
					authorize(connectionSaved);
				} else {
					showError(MessageFormat.format(bundle.getString("error.uriProvidedDoesntExist"), uriObj));
				}
			} else {
				reloadState();
				initUi(conx);
			}

		} catch (Exception e) {
			showError("Failed to add connection.", e);
		}
	}

	private void addConnection() {
		setHtmlPage("addLogonBoxVPN.html");
		sidebar.setVisible(false);
	}

	private void configure(String usernameHint, String configIniFile, Connection config) {
		try {
			LOG.info(String.format("Configuration for %s", usernameHint));
			Ini ini = new Ini(new StringReader(configIniFile));
			config.setUsernameHint(usernameHint);

			/* Interface (us) */
			Section interfaceSection = ini.get("Interface");
			config.setAddress(interfaceSection.get("Address"));
			config.setDns(toStringList(interfaceSection, "DNS"));

			String privateKey = interfaceSection.get("PrivateKey");
			if (privateKey != null && config.getUserPrivateKey().length() > 0
					&& !privateKey.equals(PRIVATE_KEY_NOT_AVAILABLE)) {
				/*
				 * TODO private key should be removed from server at this point
				 */
				config.setUserPrivateKey(privateKey);
				config.setUserPublicKey(Keys.pubkey(config.getUserPrivateKey()).getBase64PublicKey());
			} else if (config.getUserPrivateKey() == null || config.getUserPrivateKey().length() == 0) {
				throw new IllegalStateException(
						"Did not receive private key from server, and we didn't generate one on the client. Connection impossible.");
			}

			/* Peer (them) */
			Section peerSection = ini.get("Peer");
			config.setPublicKey(peerSection.get("PublicKey"));
			String[] endpoint = peerSection.get("Endpoint").split(":");
			config.setEndpointAddress(endpoint[0]);
			config.setEndpointPort(Integer.parseInt(endpoint[1]));
			config.setPeristentKeepalive(Integer.parseInt(peerSection.get("PersistentKeepalive")));
			config.setAllowedIps(toStringList(peerSection, "AllowedIPs"));

			setHtmlPage("joining.html");
			context.getBridge().getClientService().authorized(config);

		} catch (Exception e) {
			showError("Failed to configure connection.", e);
		}
	}

	private boolean confirmDelete(Connection sel) {
		Alert alert = new Alert(AlertType.CONFIRMATION);
		alert.setTitle(resources.getString("delete.confirm.title"));
		alert.setHeaderText(resources.getString("delete.confirm.header"));
		alert.setContentText(MessageFormat.format(resources.getString("delete.confirm.content"), Util.getUri(sel)));
		Optional<ButtonType> result = alert.showAndWait();
		if (result.get() == ButtonType.OK) {
			try {
//				if (sel.equals(foregroundConnection)) {
//					foregroundConnection = null;
//				}
				Util.getUri(sel);
				if (context.getBridge().getClientService().getStatus(sel) == Type.CONNECTED) {
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

	private void doDelete(Connection sel) throws RemoteException {
		setHtmlPage("busy.html");
		log.info(String.format("Deleting connection %s", sel));
		context.getBridge().getConnectionService().delete(sel);
		try {
			ObservableList<Connection> items = connections.getItems();
			items.remove(sel);
			Connection newConnections = items.isEmpty() ? null : items.get(0);
			sel = newConnections;
			reloadState();
			reapplyColors();
			reapplyLogo();
			connections.getSelectionModel().clearSelection();
			rebuildConnections(sel);
			selectPageForState(false, false);
		} finally {
			log.info("Connection deleted");
		}
	}

	private void reloadState() {
		try {
			mode = context.getBridge().getClientService().isUpdating() ? UIState.UPDATE : UIState.NORMAL;
			if(Client.allowBranding)
				branding = context.getBridge().getClientService().getBranding(getSelectedConnection());
			if (branding == null) {
				log.info(String.format("Removing branding."));
				if (logoFile != null) {
					logoFile.delete();
					logoFile = null;
				}
			} else {
				log.info(String.format("Adding custom branding"));
				String logo = branding.getLogo();
				if (StringUtils.isNotBlank(logo)) {
					log.info(String.format("Attempting to cache logo"));
					String basename = FilenameUtils.getExtension(logo);
					if (!basename.equals(""))
						basename = "." + basename;
					try {
						File newLogoFile = File.createTempFile("lpvpnc", "logo" + basename);
						newLogoFile.deleteOnExit();
						if (logoFile != null && !newLogoFile.equals(logoFile)) {
							logoFile.delete();
						}
						URL logoUrl = new URL(logo);
						try (InputStream urlIn = logoUrl.openStream()) {
							try (OutputStream out = new FileOutputStream(newLogoFile)) {
								urlIn.transferTo(out);
							}
						}
						logoFile = newLogoFile;
						branding.setLogo(newLogoFile.toURI().toString());
						log.info(String.format("Logo cached from %s to %s", logoUrl, newLogoFile.toURI()));
					} catch (Exception e) {
						log.error(String.format("Failed to cache logo"), e);
						branding.setLogo(null);
					}
				}
			}
		} catch (RemoteException e) {
			throw new IllegalStateException("Failed to retrieve state from local service.", e);
		}
	}

	private void reapplyColors() {
		context.applyColors(branding, getScene().getRoot());
	}

	private void reapplyLogo() {
		String defaultLogo = UI.class.getResource("logonbox-titlebar-logo.png").toExternalForm();
		if ((branding == null || StringUtils.isBlank(branding.getLogo())
				&& !defaultLogo.equals(titleBarImageView.getImage().getUrl()))) {
			titleBarImageView.setImage(new Image(defaultLogo, true));
		} else if (branding != null && !defaultLogo.equals(branding.getLogo()) && !StringUtils.isBlank(branding.getLogo())) {
			titleBarImageView.setImage(new Image(branding.getLogo(), true));
		}
	}

	private void doDisconnect(Connection sel) {
		rebuildConnections(sel);
		new Thread() {
			@Override
			public void run() {
				try {
					context.getBridge().disconnect(sel);
				} catch (Exception e) {
					log.error("Failed to disconnect.", e);
				}
			}
		}.start();
	}

	private void editConnection(Connection connection) {
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

	private List<ConnectionStatus> getConnections() {
		try {
			return context.getBridge().getClientService().getStatus();
		} catch (RemoteException e) {
			throw new IllegalStateException(e.getMessage(), e);
		}
	}

	private void giveUpWaitingForBridgeEstablish() {
		LOG.info("Given up waiting for bridge to start");
		resetAwaingBridgeEstablish();
		try {
			context.getBridge().notify(resources.getString("givenUpWaitingForBridgeEstablish"),
					GUICallback.NOTIFY_ERROR);
		} catch (RemoteException e) {
			// Not actually remote
		}
		UI.getInstance().setMode(UIState.NORMAL);
	}

	/*
	 * The following are all events from UI
	 */

	private void giveUpWaitingForBridgeStop() {
		LOG.info("Given up waiting for bridge to stop");
		resetAwaingBridgeLoss();
		try {
			context.getBridge().notify(resources.getString("givenUpWaitingForBridgeStop"), GUICallback.NOTIFY_ERROR);
		} catch (RemoteException e) {
			// Not actually remote
		}
		UI.getInstance().setMode(UIState.NORMAL);
	}

	private void initUi(Connection connection) {
		log.info("Rebuilding URIs");
		if (context.getBridge().isConnected()) {
			try {
				rebuildConnections(connection);
			} catch (Exception e) {
				log.error("Failed to load connections.", e);
			}
		}
		context.applyColors(branding, getScene().getRoot());
		reapplyLogo();
	}

	private void joinedNetwork() {
		log.info("Joined network");
		setHtmlPage("joined.html");
	}

	private void rebuildConnections(final Connection sel) {
		connections.getItems().clear();
		List<ConnectionStatus> connectionList = context.getBridge().isConnected() ? getConnections() : Collections.emptyList();
		Connection connected = null;
		Connection connecting = null;
		for(ConnectionStatus s : connectionList) {
			connections.getItems().add(s.getConnection());
			if(connected == null && s.getStatus() == Type.CONNECTED) {
				connected = s.getConnection();
			}
			if(connecting == null && s.getStatus() == Type.CONNECTING) {
				connecting = s.getConnection();
			}
		}
		if (sel != null && connections.getItems().contains(sel))
			connections.getSelectionModel().select(sel);
		else if (connections.getSelectionModel().isEmpty() && !connections.getItems().isEmpty()) {
			/* Prefer connected connection */
			if(connected == null) {
				if(connecting == null)
					connections.getSelectionModel().select(connections.getItems().get(0));
				else
					connections.getSelectionModel().select(connecting);
			}
			else
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
		LOG.info(String.format("Reseting update state, returning to mode %s", UIState.NORMAL));
		setMode(UIState.NORMAL);
	}

	private boolean sameHostPortPathCheck(Long conId, Predicate<Long> predicate) {
		if (predicate.test(conId)) {
			// show error message
			return true;
		}
		return false;
	}

	private void selectPageForState(boolean disconnecting, boolean connectIfDisconnected) {
		try {
			Bridge bridge = context.getBridge();
			if (mode == UIState.UPDATE) {
				setHtmlPage("updating.html");
			} else if (bridge.isConnected() && bridge.getClientService().isNeedsUpdating()) {
				/* An update is available */
				log.warn(String.format("Update is available"));
				if (Boolean.valueOf(context.getBridge().getConfigurationService()
						.getValue(ConfigurationService.AUTOMATIC_UPDATES, "true"))) {
					update();
				} else
					setHtmlPage("updateAvailable.html");
			} else {
				if (bridge.isConnected() && bridge.getClientService().getMissingPackages().length > 0) {
					log.warn(String.format("Missing software packages"));
					collections.put("packages", Arrays.asList(bridge.getClientService().getMissingPackages()));
					setHtmlPage("missingSoftware.html");
				} else {
					Connection sel = getSelectedConnection();
					if (sel == null) {
						/* There are no connections at all */
						if (bridge.isConnected()) {
							/* The bridge is connected */
							if (Main.getInstance().isNoAddWhenNoConnections()) {
								if (Main.getInstance().isConnect()
										|| StringUtils.isNotBlank(Main.getInstance().getUri()))
									setHtmlPage("busy.html");
								else
									setHtmlPage("connected.html");
							} else
								setHtmlPage("addLogonBoxVPN.html");
						} else
							/* The bridge is not (yet?) connected */
							setHtmlPage("index.html");
					} else if (connectIfDisconnected && !disconnecting && !sel.isAuthorized()) {
						log.info(String.format("Not authorized, requesting authorize"));
						authorize(sel);
					} else if (context.getBridge().getClientService().getStatus(sel) == Type.CONNECTED) {
						/* We have a connection, a peer configuration and are connected! */
						log.info(String.format("Connected, so showing join UI"));
						setHtmlPage("joined.html");
					} else {
						log.info(String.format("Disconnected, so showing join UI"));
						setHtmlPage("join.html", true);
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
		showError(error, null);
	}

	private void showError(String error, Throwable exception) {
		LOG.error(error, exception);
		lastException = exception;
		lastErrorMessage = error;
		setHtmlPage("error.html");
	}

	private List<String> toStringList(Section section, String key) {
		List<String> n = new ArrayList<>();
		String val = section.get(key, "");
		if (!val.equals("")) {
			for (String a : val.split(",")) {
				n.add(a.trim());
			}
		}
		return n;
	}

	private void update() {
		new Thread("UpdateThread") {
			public void run() {
				try {
					context.getBridge().getClientService().update();
				} catch (Exception e) {
					// Will get error when the service restarts
				}
			}
		}.start();
	}

	public static void maybeRunLater(Runnable r) {
		if (Platform.isFxApplicationThread())
			r.run();
		else
			Platform.runLater(r);
	}

}
