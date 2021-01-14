package com.logonbox.vpn.client.gui.jfx;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.rmi.RemoteException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;

import org.controlsfx.control.action.Action;
import org.ini4j.Ini;
import org.ini4j.Profile.Section;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.hypersocket.client.Prompt;
import com.hypersocket.client.rmi.Connection;
import com.hypersocket.client.rmi.ConnectionService;
import com.hypersocket.client.rmi.ConnectionStatus;
import com.hypersocket.client.rmi.GUICallback;
import com.hypersocket.client.rmi.Util;
import com.logonbox.vpn.client.gui.jfx.Bridge.Listener;
import com.logonbox.vpn.common.client.PeerConfiguration;
import com.logonbox.vpn.common.client.PeerConfigurationImpl;
import com.logonbox.vpn.common.client.PeerConfigurationService;
import com.sshtools.twoslices.Toast;
import com.sshtools.twoslices.ToastType;

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
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.util.Callback;
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

		public void showError(String error) {
			UI.this.showError(error);
		}

		public void unjoin() {
			disconnect(getSelectedConnection());
		}

		public void join() {
			UI.this.joinNetwork(UI.this.connections.getSelectionModel().getSelectedItem());
		}

		public void authenticate() {
			UI.this.authenticate(UI.this.connections.getSelectionModel().getSelectedItem());
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
	public Map<String, String> showPrompts(final Connection connection, final ResourceBundle promptResources,
			List<Prompt> prompts, int attempts, boolean success) {

		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public void bridgeEstablished() {
		Platform.runLater(() -> {
			initUi(null);
		});
	}

	@Override
	public void bridgeLost() {
		Platform.runLater(() -> {
//			waitingForUpdatesOrResources.clear();
			connecting.clear();
			rebuildConnections(null);
			initUi(null);
		});
	}

	// Overrides

	@Override
	protected void onInitialize() {
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
			if (!n && !addConnection.isFocused()) {
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

		JSObject jsobj = (JSObject) webView.getEngine().executeScript("window");
		jsobj.setMember("bridge", new UIBridge());
		webView.getEngine()
				.executeScript("console.log = function(message)\n" + "{\n" + "    bridge.log(message);\n" + "};");

		try {
			Connection sel = getSelectedConnection();
			PeerConfigurationService srv = context.getBridge().getPeerConfigurationService();
			Element rootEl = webView.getEngine().getDocument().getDocumentElement();
			PeerConfiguration cfg = sel == null ? null : srv.getConfiguration(sel);
			dataAttributes(rootEl, cfg, sel);
			lastException = null;
			lastErrorMessage = null;

		} catch (Exception e) {
			showError("Failed to initialise web view. ", e);
		}
	}

	protected void dataAttributes(Element node, PeerConfiguration cfg, Connection connection) {

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
				dataAttributes((Element) c, cfg, connection);
		}
	}

	protected void connect(Connection n) {
		try {
			if (n != null) {
				PeerConfiguration peerConfiguration = context.getBridge().getPeerConfigurationService()
						.getConfiguration(n);
				if (peerConfiguration == null) {
					setHtmlPage("new.html");
				} else {
					if (context.getBridge().getClientService().isConnected(n))
						joinedNetwork();
					else
						unjoined();
				}
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

	private void unjoined() {
		setHtmlPage("join.html");
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
		setHtmlPage("joined.html");
	}

	protected void authenticate(Connection n) {
		setHtmlPage(n.getUri(false) + "/logonbox-vpn-client/");
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
								Configuration.getDefault().getDeviceUUID().toString())));
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
						loc = UI.class.getResource(htmlPage).toExternalForm();
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

//		if (sameNameCheck(null, (conId) -> {
//			try {
//				return connectionService.getConnectionByName(name) != null;
//			} catch (RemoteException e) {
//				throw new IllegalStateException(e.getMessage(), e);
//			}
//		})) {
//			return;
//		}

			URI uriObj = Util.getUri(server);

			final Connection connection = context.getBridge().getConnectionService().createNew(uriObj);

//		if (sameHostPortPathCheck(null, (conId) -> {
//			try {
//				return connectionService.getConnectionByHostPortAndPath(connection.getHostname(),
//						connection.getPort(), connection.getPath()) != null;
//			} catch (RemoteException e) {
//				throw new IllegalStateException(e.getMessage(), e);
//			}
//		})) {
//			return;
//		}
//
//		if(!nameInput.isDisable())
			connection.setName(server);

			connection.setConnectAtStartup(connectAtStartup);

//		connection.setUsername(username);
//		connection.setPassword(password);

			// context.getBridge().getConnectionService().saveCredentials(connection.getHostname(),
			// username, password);

			Connection connectionSaved = connectionService.save(connection);
			connections.getItems().add(connectionSaved);
			connections.getSelectionModel().select(connectionSaved);
			authenticate(connectionSaved);

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
			Connection sel = getSelectedConnection();
			if (sel == null) {
				/* There are no connections at all */
				if (context.getBridge().isConnected()) {
					/* The bridge is connected */
					setHtmlPage("connected.html");
				} else
					/* The bridge is not (yet?) connected */
					setHtmlPage("index.html");
			} else if (context.getBridge().getClientService().isConnected(sel)) {
				/* We have a connection, a peer configuration and are connected! */
				setHtmlPage("joined.html");
			} else {
				PeerConfiguration peer = context.getBridge().getPeerConfigurationService().getConfiguration(sel);
				if (peer == null)
					/* We have a Connection, but not PeerConfiguration */
					setHtmlPage("new.html");
				else
					/* We have both, but are not currently connected */
					setHtmlPage("join.html");
			}
		} catch (Exception e) {
			showError("Failed to set page.", e);
		}

	}

	private void configure(String usernameHint, String configIniFile, Connection connection) {
		try {
			Ini ini = new Ini(new StringReader(configIniFile));
			PeerConfiguration config = new PeerConfigurationImpl();
			config.setConnection(connection);
			config.setId(connection.getId());
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

			context.getBridge().getPeerConfigurationService().add(config);

			joinNetwork(connection);

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
		context.getBridge().getConnectionService().removeCredentials(sel.getHostname());
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
	private void evtAddConnection() {
		addConnection();
	}

	private void editConnection(Connection connection) {
		setHtmlPage("editConnection.html");
		sidebar.setVisible(false);
	}

	private void addConnection() {
		setHtmlPage("addConnection.html");
		sidebar.setVisible(false);
	}

	private List<Connection> getConnections() {
		try {
			return context.getBridge().getConnectionService().getConnections();
		} catch (RemoteException e) {
			throw new IllegalStateException(e.getMessage(), e);
		}
	}

}
