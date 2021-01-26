package com.logonbox.vpn.client.gui.jfx;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.URL;
import java.rmi.RemoteException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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

import com.hypersocket.extensions.JsonExtensionPhase;
import com.hypersocket.extensions.JsonExtensionPhaseList;
import com.logonbox.vpn.client.gui.jfx.Bridge.Listener;
import com.logonbox.vpn.common.client.ConfigurationService;
import com.logonbox.vpn.common.client.Connection;
import com.logonbox.vpn.common.client.ConnectionService;
import com.logonbox.vpn.common.client.ConnectionStatus;
import com.logonbox.vpn.common.client.GUICallback;
import com.logonbox.vpn.common.client.Util;
import com.sshtools.twoslices.Toast;
import com.sshtools.twoslices.ToastType;

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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.util.Callback;
import javafx.util.Duration;
import netscape.javascript.JSObject;

/**
 * Controller for the "Sign In" window, where connections are managed and
 * credentials prompted for.
 */
public class UI extends AbstractController implements Listener {

	static Logger LOG = LoggerFactory.getLogger(UI.class);

	/**
	 * This object is exposed to the local HTML/Javascript that runs in the browse.
	 */
	public class UIBridge {

		public void reload() {
			UI.this.initUi(getSelectedConnection());
		}

		public void showError(String error) {
			UI.this.showError(error);
		}

		public void unjoin() {
			disconnect(getSelectedConnection());
		}

		public void join() {
			UI.this.joinNetwork(UI.this.connections.getSelectionModel().getSelectedItem());
		}

		public void update() {
			UI.this.update();
		}

		public void authenticate() {
			UI.this.authorize(UI.this.connections.getSelectionModel().getSelectedItem());
		}

		public void saveOptions(String phase, boolean automaticUpdates) {
			UI.this.saveOptions(phase, automaticUpdates);
		}

		public void configure(String usernameHint, String configIniFile) {
			UI.this.configure(usernameHint, configIniFile, UI.this.connections.getSelectionModel().getSelectedItem());
		}

		public void addConnection(JSObject o) {
			Boolean connectAtStartup = (Boolean) o.getMember("connectOnStartup");
			String server = (String) o.getMember("serverUrl");
			UI.this.addConnection(connectAtStartup, server);
		}

		public void editConnection(JSObject o) {
			Boolean connectAtStartup = (Boolean) o.getMember("connectOnStartup");
			String server = (String) o.getMember("serverUrl");
			UI.this.editConnection(connectAtStartup, server, getSelectedConnection());
		}

		public void reveal() {
			UI.this.sidebar.setVisible(true);
			UI.this.connections.requestFocus();
		}

		public void log(String message) {
			LOG.info("WEB: " + message);
		}
	}

	final static Logger log = LoggerFactory.getLogger(UI.class);
	final static ResourceBundle bundle = ResourceBundle.getBundle(UI.class.getName());

	private static UI instance;

	@FXML
	private VBox root;
	@FXML
	private Label messageText;
	@FXML
	private Label messageIcon;
	@FXML
	protected ListView<Connection> connections;

	@FXML
	private Hyperlink addConnection;
	@FXML
	private Hyperlink options;
	@FXML
	private WebView webView;
	@FXML
	private BorderPane sidebar;

	private List<Connection> disconnecting = new ArrayList<>();
	private List<Connection> connecting = new ArrayList<>();
	private boolean deleteOnDisconnect;
	private String htmlPage;
	private ResourceBundle pageBundle;
	private String lastErrorMessage;
	private Throwable lastException;
	private Map<String, Collection<String>> collections = new HashMap<>();
	private Map<String, Object> beans = new HashMap<>();
	private UIState mode = UIState.NORMAL;
	private boolean awaitingRestart;
	private int appsToUpdate;
	private int appsUpdated;
	private Timeline awaitingBridgeEstablish;
	private Timeline awaitingBridgeLoss;

	public UI() {
		instance = this;
	}

	public static UI getInstance() {
		return instance;
	}

	/*
	 * Class methods
	 */

	/*
	 * The following are all events from the {@link Bridge}, and will come in on the
	 * RMI thread.
	 */
	@Override
	public void disconnecting(Connection connection) {
		super.disconnecting(connection);
	}

	@Override
	public void disconnected(final Connection connection, Exception e) {
		super.disconnected(connection, e);
		Platform.runLater(() -> {
			log.info("Disconnected " + connection + " (delete " + deleteOnDisconnect + ")");
			if (disconnecting.contains(connection)) {
				disconnecting.remove(connection);
				rebuildConnections(connection);
			}
//			if (Objects.equals(connection, foregroundConnection)) {
//				log.info("Clearing foreground connection");
//				foregroundConnection = null;
//			}

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
			selectPageForState();
		});
	}

	@Override
	public void started(final Connection connection) {
		Platform.runLater(() -> {
			log.info("Started " + connection);
//			waitingForUpdatesOrResources.remove(connection);
			initUi(connection);
		});
	}

	@Override
	public void finishedConnecting(final Connection connection, Exception e) {
		Platform.runLater(() -> {
			log.info("Finished connecting " + connection + ". "
					+ (e == null ? "No error" : "Error occured." + e.getMessage()) + " Foreground is "
			/* + foregroundConnection */);

//			if (e != null) {
//				waitingForUpdatesOrResources.remove(connection);
//			}

			connecting.remove(connection);

//			if (Objects.equals(connection, foregroundConnection)) {
//				foregroundConnection = null;
//			}

			rebuildConnections(connection);
			if (e != null) {
				showError("Failed to connect.", e);
				notify(e.getMessage(), GUICallback.NOTIFY_ERROR);
			} else
				joinedNetwork();

			// TODO fix when multiple different connections at possible different times

		});
		super.finishedConnecting(connection, e);
	}

	@Override
	public boolean showBrowser(Connection connection, String uri) {
		Platform.runLater(() -> setHtmlPage(connection.getUri(false) + uri));
		return true;
	}

	@Override
	public void bridgeEstablished() {

		/*
		 * If we were waiting for this, it's part of the update process. We don't want
		 * the connection continuing
		 */
		if (awaitingBridgeEstablish != null) {
			awaitingRestart = true;
		}

		Platform.runLater(() -> {
			if (awaitingBridgeEstablish != null) {
				// Bridge established as result of update, now restart the
				// client itself
				resetAwaingBridgeEstablish();
				setUpdateProgress(100, resources.getString("guiRestart"));
				new Timeline(new KeyFrame(Duration.seconds(5), ae -> Main.getInstance().restart())).play();
			} else {
				try {
					mode = context.getBridge().getClientService().isUpdating() ? UIState.UPDATE : UIState.NORMAL;
				} catch (RemoteException e) {
					throw new IllegalStateException("Impossible!");
				}
				initUi(null);
			}
		});
	}

	@Override
	public void bridgeLost() {

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
				}
			}
		});

	}

	@Override
	public void initUpdate(int apps, UIState currentMode) {
		this.mode = UIState.UPDATE;
		if (awaitingRestart)
			throw new IllegalStateException("Cannot initiate updates while waiting to restart the GUI..");

		LOG.info(String.format("Initialising update (currently in mode %s). Expecting %d apps", currentMode, apps));
		appsToUpdate = apps;
		appsUpdated = 0;
		selectPageForState();
	}

	@Override
	public void startingUpdate(String app, long totalBytesExpected) {
		LOG.info(String.format("Starting up of %s, expect %d bytes", app, totalBytesExpected));
		String appName = getAppName(app);
		setUpdateProgress(0, MessageFormat.format(resources.getString("updating"), appName));
	}

	@Override
	public void updateProgressed(String app, long sincelastProgress, long totalSoFar, long totalBytesExpected) {
		String appName = getAppName(app);
		setUpdateProgress((int) (((double) totalSoFar / totalBytesExpected) * 100d),
				MessageFormat.format(resources.getString("updating"), appName));
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
		resetState();
		try {
			context.getBridge().notify(message, GUICallback.NOTIFY_ERROR);
		} catch (RemoteException e) {
			// Not actually remote
		}
		context.getBridge().disconnectAll();
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
				resetState();
			}
		} else {
			setUpdateProgress(100, errorMessage);
			resetState();
		}
	}

	public void setMode(UIState mode) {
		this.mode = mode;
		selectPageForState();
	}

	public UIState getMode() {
		return mode;
	}

	// Overrides

	@Override
	protected void onInitialize() {
		Font.loadFont(UI.class.getResource("ARLRDBD.TTF").toExternalForm(), 12);
	}

	@Override
	protected void onConfigure() {
		super.onConfigure();
		initUi(null);
		rebuildConnections(null);

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
			boolean connected = false;
			try {
				connected = context.getBridge().getClientService().isConnected(getSelectedConnection());
				connect.setDisable(connected);
				disconnect.setDisable(!connected);
				add.setDisable(connected);
				remove.setDisable(connected);
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
//		spinner.managedProperty().bind(spinner.visibleProperty());
		sidebar.managedProperty().bind(sidebar.visibleProperty());

	}

	protected Connection getSelectedConnection() {
		Connection selectedItem = connections.getSelectionModel().getSelectedItem();
		if (selectedItem == null && connections.getItems().size() > 0)
			selectedItem = connections.getItems().get(0);
		return selectedItem;
	}

	protected void saveOptions(String phase, boolean automaticUpdates) {
		try {
			context.getBridge().getConfigurationService().setValue(ConfigurationService.PHASE, phase);
			context.getBridge().getConfigurationService().setValue(ConfigurationService.AUTOMATIC_UPDATES,
					String.valueOf(automaticUpdates));
			selectPageForState();
		} catch (Exception e) {
			showError("Failed to save options.", e);
		}
	}

	protected void configureWebEngine() {
		WebEngine engine = webView.getEngine();
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
			String base = newLoc;
			int idx = newLoc.lastIndexOf('/');
			if (idx != -1) {
				base = newLoc.substring(idx + 1);
			}
			if (!base.equals("") && !base.equals(htmlPage)) {
				htmlPage = base;
				setupPage();
				log.info(String.format("Page changed by user to %s (likely back button)", htmlPage));
			}
		});
		engine.getLoadWorker().stateProperty().addListener((ov, oldState, newState) -> {
			if (newState == State.SUCCEEDED) {
				webViewReady(newState);
			}
		});
		engine.getLoadWorker().exceptionProperty().addListener((o, old, value) -> {
			showError("Exception during page load.", value);
		});

		engine.setJavaScriptEnabled(true);
	}

	protected void webViewReady(State newState) {

		log.info("Processing page content");
		JSObject jsobj = (JSObject) webView.getEngine().executeScript("window");
		jsobj.setMember("bridge", new UIBridge());
		jsobj.setMember("configuration", context.getBridge().getConfigurationService());
		for (Map.Entry<String, Object> beanEn : beans.entrySet()) {
			jsobj.setMember(beanEn.getKey(), beanEn.getValue());
		}
		beans.clear();
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
		String address = cfg == null ? "" : cfg.getAddress();
		String usernameHint = cfg == null ? "" : cfg.getUsernameHint();

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
					else if (val.equals("address"))
						node.setAttribute(node.getAttribute("data-attr-name"), address);
					else if (val.equals("usernameHint"))
						node.setAttribute(node.getAttribute("data-attr-name"), usernameHint);
					else if (val.equals("endpoint"))
						node.setAttribute(node.getAttribute("data-attr-name"), endpointAddress);
					else if (val.equals("publicKey"))
						node.setAttribute(node.getAttribute("data-attr-name"), publicKey);
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
							else if (i18nArg.equals("publicKey"))
								args.add(publicKey);
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
					else if (val.equals("publicKey"))
						node.setTextContent(publicKey);
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

	protected void connect(Connection n) {
		try {
			if (n != null) {
				if (context.getBridge().getClientService().isConnected(n))
					joinedNetwork();
				else
					selectPageForState();
			}
		} catch (Exception e) {
			showError("Failed to connect.", e);
		}
	}

	private void showError(String error, Throwable exception) {
		LOG.error(error, exception);
		lastException = exception;
		lastErrorMessage = error;
		setHtmlPage("error.html");
	}

	private void showError(String error) {
		showError(error, null);
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

	private void joinNetwork(Connection connection) {
		setHtmlPage("joining.html");
		try {
			context.getBridge().getClientService().connect(connection);
		} catch (Exception e) {
			showError("Failed to join VPN.", e);
		}
	}

	private void joinedNetwork() {
		log.info("Joined network");
		setHtmlPage("joined.html");
	}

	protected void authorize(Connection n) {
		try {
			context.getBridge().getClientService().requestAuthorize(n);
		} catch (Exception e) {
			showError("Failed to join VPN.", e);
		}
	}

	protected void setHtmlPage(String htmlPage) {
		if (!Objects.equals(htmlPage, this.htmlPage)) {
			this.htmlPage = htmlPage;
			LOG.info(String.format("Loading page %s", htmlPage));
			pageBundle = null;
			try {

				if (htmlPage.startsWith("http://") || htmlPage.startsWith("https://")) {

					/* Set the device UUID cookie for all web access */
					try {
						/*
						 * TODO: replace this with public key when key generation is moved to client
						 * side
						 */
						URI uri = new URI(htmlPage);
						Map<String, List<String>> headers = new LinkedHashMap<String, List<String>>();
						headers.put("Set-Cookie", Arrays.asList(String.format("%s=%s", Client.DEVICE_IDENTIFIER,
								context.getBridge().getClientService().getUUID().toString())));
						java.net.CookieHandler.getDefault().put(uri.resolve("/"), headers);
					} catch (Exception e) {
						throw new IllegalStateException("Failed to set cookie.", e);
					}
					webView.getEngine().setUserStyleSheetLocation(UI.class.getResource("remote.css").toExternalForm());
					webView.getEngine().load(htmlPage);
				} else {
					webView.getEngine().setUserStyleSheetLocation(UI.class.getResource("local.css").toExternalForm());

					// webView.getEngine().load(getClass().getResource(htmlPage).toExternalForm());

					setupPage();
					String loc = htmlPage;
					if (Client.useLocalHTTPService) {
						// webView.getEngine().load("app://" + htmlPage);
						loc = "http://localhost:59999/" + htmlPage;
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

					webView.getEngine().load(loc);
				}

				sidebar.setVisible(false);
			} catch (Exception e) {
				LOG.error("Failed to set page.", e);
			}
		}
	}

	protected void setupPage() {
		int idx = htmlPage.lastIndexOf('.');
		String base = htmlPage.substring(0, idx);
		String res = Client.class.getName();
		idx = res.lastIndexOf('.');
		String resourceName = res.substring(0, idx) + "." + base;
		LOG.info(String.format("Loading bundle %s", resourceName));
		try {
			pageBundle = ResourceBundle.getBundle(resourceName);
		} catch (MissingResourceException mre) {
			// Page doesn't have resources
			mre.printStackTrace();
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
		Toast.toast(toastType, "Hypersocket Client", msg);

	}

	protected void addConnection(Boolean connectAtStartup, String server) {
		try {
			ConnectionService connectionService = context.getBridge().getConnectionService();

			if (sameNameCheck(null, (conId) -> {
				try {
					return connectionService.getConnectionByName(server) != null;
				} catch (RemoteException e) {
					throw new IllegalStateException(e.getMessage(), e);
				}
			})) {
				return;
			}

			URI uriObj = Util.getUri(server);

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
			connection.setName(server);
			connection.setConnectAtStartup(connectAtStartup);

			Connection connectionSaved = connectionService.add(connection);
			connections.getItems().add(connectionSaved);
			connections.getSelectionModel().select(connectionSaved);
			authorize(connectionSaved);

		} catch (Exception e) {
			showError("Failed to add connection.", e);
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
			selectPageForState();

		} catch (Exception e) {
			showError("Failed to save connection.", e);
		}
	}

	private void selectPageForState() {
		try {
			Bridge bridge = context.getBridge();
			if (mode == UIState.UPDATE) {
				setHtmlPage("updating.html");
			} else if (bridge.isConnected() && bridge.getClientService().isNeedsUpdating()) {
				/* An update is available */
				if (Boolean.valueOf(context.getBridge().getConfigurationService()
						.getValue(ConfigurationService.AUTOMATIC_UPDATES, "true"))) {
					update();
				} else
					setHtmlPage("updateAvailable.html");
			} else {
				if (bridge.isConnected() && bridge.getClientService().getMissingPackages().length > 0) {
					collections.put("packages", Arrays.asList(bridge.getClientService().getMissingPackages()));
					setHtmlPage("missingSoftware.html");
				} else {
					Connection sel = getSelectedConnection();
					if (sel == null) {
						/* There are no connections at all */
						if (bridge.isConnected()) {
							/* The bridge is connected */
							setHtmlPage("addLogonBoxVPN.html");
						} else
							/* The bridge is not (yet?) connected */
							setHtmlPage("index.html");
					} else if (!sel.isAuthorized()) {
						authorize(sel);
					} else if (bridge.getClientService().isConnected(sel)) {
						/* We have a connection, a peer configuration and are connected! */
						setHtmlPage("joined.html");
					} else {
						log.info(String.format("Connected, so showing joined UI"));
						setHtmlPage("join.html");
					}
				}
			}
		} catch (Exception e) {
			showError("Failed to set page.", e);
		}

	}

	private void configure(String usernameHint, String configIniFile, Connection config) {
		try {
			Ini ini = new Ini(new StringReader(configIniFile));
			config.setUsernameHint(usernameHint);

			/* Interface (us) */
			Section interfaceSection = ini.get("Interface");
			config.setAddress(interfaceSection.get("Address"));
			config.setDns(toStringList(interfaceSection, "DNS"));

			/* TODO private key should be removed from server at this point */
			config.setUserPrivateKey(interfaceSection.get("PrivateKey"));

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

	private void initUi(Connection connection) {
		log.info("Rebuilding URIs");

		if (context.getBridge().isConnected()) {
			try {
				List<ConnectionStatus> connections = context.getBridge().getClientService().getStatus();

				// Look for new connections
				for (ConnectionStatus c : connections) {
					Connection conx = c.getConnection();
					rebuildConnections(conx);
				}
			} catch (Exception e) {
				log.error("Failed to load connections.", e);
			}
		}
		selectPageForState();
	}

	private void disconnect(Connection sel) {
		try {
			context.getBridge().getClientService().disconnect(sel);
		} catch (Exception e) {
			showError("Failed to disconnect.", e);
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
				if (context.getBridge().getClientService().isConnected(sel)) {
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
		log.info(String.format("Deleting connection %s", sel));
		context.getBridge().getConnectionService().delete(sel);
		try {
			ObservableList<Connection> items = connections.getItems();
			items.remove(sel);
			Connection newConnections = items.isEmpty() ? null : items.get(0);
			sel = newConnections;
			rebuildConnections(sel);
		} finally {
			log.info("Connection deleted");
		}
	}

	private void doDisconnect(Connection sel) {

		if (disconnecting.contains(sel)) {
			throw new IllegalStateException("Already disconnecting " + sel);
		}
		disconnecting.add(sel);

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

	private boolean sameNameCheck(Long conId, Predicate<Long> predicate) {
		if (predicate.test(conId)) {
			// show error message
			return true;
		}
		return false;
	}

	private boolean sameHostPortPathCheck(Long conId, Predicate<Long> predicate) {
		if (predicate.test(conId)) {
			// show error message
			return true;
		}
		return false;
	}

//	private void setUserDetails(Connection connection) {
//
//		// These will be collected during prompts and maybe saved
////		promptedUsername = null;
////		promptedPassword = null;
//
//		int status;
//		try {
//			status = context.getBridge().getClientService().getStatus(connection);
//		} catch (RemoteException e1) {
//			status = ConnectionStatus.DISCONNECTED;
//		}
//		if (status == ConnectionStatus.DISCONNECTED) {
//			connecting.add(connection);
//			waitingForUpdatesOrResources.add(connection);
//			setAvailable(connection);
//			new Thread() {
//				@Override
//				public void run() {
//					try {
//						context.getBridge().connect(connection);
//						log.info(String.format("Connected to %s", Util.getUri(connection)));
//					} catch (Exception e) {
//						foregroundConnection = null;
//						log.error("Failed to connect.", e);
//						Platform.runLater(new Runnable() {
//							@Override
//							public void run() {
//								getInstance().notify(e.getMessage(), GUICallback.NOTIFY_ERROR);
//							}
//						});
//					} finally {
//						Platform.runLater(new Runnable() {
//							@Override
//							public void run() {
//								setAvailable(connection);
//							}
//						});
//					}
//				}
//			}.start();
//		} else {
//			log.warn("Request to connect an already connected or connecting connection " + connection);
//		}
//	}
//
	private void rebuildConnections(final Connection sel) {
		if (Platform.isFxApplicationThread()) {
			connections.getItems().clear();
			if (context.getBridge().isConnected()) {
				connections.getItems().addAll(getConnections());
			}
			if (sel != null)
				connections.getSelectionModel().select(sel);
		} else
			Platform.runLater(() -> rebuildConnections(sel));

	}

	/*
	 * The following are all events from UI
	 */

	@FXML
	private void evtClose() {
		context.confirmExit();
	}

	@FXML
	private void evtMinimize() {
		context.getStage().setIconified(true);
	}

	@FXML
	private void evtToggleSidebar() {
		sidebar.setVisible(!sidebar.isVisible());
		if (sidebar.isVisible()) {
			connections.requestFocus();
		}
	}

	@FXML
	private void evtOptions() throws Exception {
		try {
			JsonExtensionPhaseList phases = context.getBridge().getClientService().getPhases();
			beans.put("phases", phases.getResult() == null ? new JsonExtensionPhase[0] : phases.getResult());
		} catch (Exception e) {
			log.warn("Could not get phases.", e);
		}
		beans.put("phase", context.getBridge().getConfigurationService().getValue(ConfigurationService.PHASE, ""));
		beans.put("automaticUpdates", Boolean.valueOf(context.getBridge().getConfigurationService()
				.getValue(ConfigurationService.AUTOMATIC_UPDATES, "true")));
		setHtmlPage("options.html");
		sidebar.setVisible(false);
	}

	@FXML
	private void evtAddConnection() {
		addConnection();
	}

	private void editConnection(Connection connection) {
		setHtmlPage("editConnection.html");
		sidebar.setVisible(false);
	}

	private void addConnection() {
		setHtmlPage("addLogonBoxVPN.html");
		sidebar.setVisible(false);
	}

	private List<Connection> getConnections() {
		try {
			return context.getBridge().getConnectionService().getConnections();
		} catch (RemoteException e) {
			throw new IllegalStateException(e.getMessage(), e);
		}
	}

	private String getAppName(String app) {
		if (resources.containsKey(app)) {
			return resources.getString(app);
		} else {
			return app;
		}
	}

	private void resetState() {
		resetAwaingBridgeEstablish();
		resetAwaingBridgeLoss();
		appsToUpdate = 0;
		appsUpdated = 0;
		LOG.info(String.format("Reseting update state, returning to mode %s", UIState.NORMAL));
		UI.getInstance().setMode(UIState.NORMAL);
	}

	private void resetAwaingBridgeLoss() {
		if (awaitingBridgeLoss != null) {
			awaitingBridgeLoss.stop();
			awaitingBridgeLoss = null;
		}
	}

	private void resetAwaingBridgeEstablish() {
		if (awaitingBridgeEstablish != null) {
			awaitingBridgeEstablish.stop();
			awaitingBridgeEstablish = null;
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

}
