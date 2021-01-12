package com.logonbox.vpn.client.gui.jfx;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.goxr3plus.fxborderlessscene.borderless.BorderlessScene;
import com.logonbox.vpn.client.gui.jfx.MiniHttpServer.DynamicContent;
import com.logonbox.vpn.client.gui.jfx.MiniHttpServer.DynamicContentFactory;
import com.logonbox.vpn.client.gui.jfx.fontawesome.AwesomeIcons;

import javafx.application.Application;
import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class Client extends Application {

	/**
	 * Matches the identifier in logonbox VPN server
	 * PeerConfigurationAuthenticationProvider.java
	 */
	static final String DEVICE_IDENTIFIER = "LBVPNDID";
	static final String LOCBCOOKIE = "LOCBCKIE";

	static Logger log = LoggerFactory.getLogger(Client.class);
	static ResourceBundle BUNDLE = ResourceBundle.getBundle(Client.class.getName());
	static UUID localWebServerCookie = UUID.randomUUID();

	private Bridge bridge;

	private static Object barrier = new Object();
	private ExecutorService loadQueue = Executors.newSingleThreadExecutor();
	private boolean waitingForExitChoice;

	private Stage primaryStage;
	private MiniHttpServer miniHttp;

	public static void initialize() throws InterruptedException {
		Thread t = new Thread("JavaFX Init Thread") {
			@Override
			public void run() {
				Application.launch(Client.class, new String[0]);
			}
		};

		synchronized (barrier) {
			t.setDaemon(true);
			t.start();
			barrier.wait();
		}
	}

	public FramedController openScene(Class<? extends Initializable> controller) throws IOException {
		return openScene(controller, null);
	}

	public FramedController openScene(Class<? extends Initializable> controller, String fxmlSuffix) throws IOException {
		URL resource = controller
				.getResource(controller.getSimpleName() + (fxmlSuffix == null ? "" : fxmlSuffix) + ".fxml");
		FXMLLoader loader = new FXMLLoader();
		loader.setResources(ResourceBundle.getBundle(controller.getName()));
		Parent root = loader.load(resource.openStream());
		FramedController controllerInst = (FramedController) loader.getController();
		if (controllerInst == null) {
			throw new IOException("Controller not found. Check controller in FXML");
		}
		Scene scene = new Scene(root);
		controllerInst.configure(scene, this);
		return controllerInst;
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		this.primaryStage = primaryStage;

		if (!getParameters().getNamed().containsKey("strictSSL")
				|| "false".equalsIgnoreCase(getParameters().getNamed().get("strictSSL"))) {
			installAllTrustingCertificateVerifier();
		}

		miniHttp = new MiniHttpServer(59999, 0, null);
		miniHttp.addContent(new DynamicContentFactory() {
			@Override
			public DynamicContent get(String path, Map<String, List<String>> headers) throws IOException {
				List<String> localCookie = headers.get("Cookie");
				boolean allowed = false;
				if (localCookie != null) {
					for (String ck : localCookie) {
						int idx = ck.indexOf('=');
						String name = ck.substring(0, idx);
						if (name.equals(LOCBCOOKIE) && ck.substring(idx + 1).equals(localWebServerCookie.toString())) {
							allowed = true;
							break;
						}
					}
				}
//				if (!allowed)
//					throw new IOException("Access denied to " + path);
				System.out.println("WARNING: Letting all requests through, no security. MUST BE FIXED");

				String pathNoParms = path;
				int idx = pathNoParms.indexOf('?');
				if (idx != -1) {
					pathNoParms = pathNoParms.substring(0, idx);
				}
				URL res = Client.class.getResource(pathNoParms.substring(1));
				if (res == null) {
					System.out.println("404 " + path);
					throw new FileNotFoundException(path);
				}
				URLConnection conx = res.openConnection();
				String contentType = conx.getContentType();
				if (pathNoParms.endsWith(".js")) {
					contentType = "text/javascript";
				} else if (pathNoParms.endsWith(".css")) {
					contentType = "text/css";
				} else if (pathNoParms.endsWith(".html")) {
					contentType = "text/html";
				} else if (pathNoParms.endsWith(".woff")) {
					contentType = "font/woff";
				} else if (pathNoParms.endsWith(".ttf")) {
					contentType = "font/ttf";
				} else if (pathNoParms.endsWith(".svg")) {
					contentType = "image/svg+xml";
				} else if (pathNoParms.endsWith(".bmp")) {
					contentType = "image/bmp";
				} else if (pathNoParms.endsWith(".png")) {
					contentType = "image/png";
				} else if (pathNoParms.endsWith(".gif")) {
					contentType = "image/gif";
				} else if (pathNoParms.endsWith(".jpeg") || pathNoParms.endsWith(".jpe")) {
					contentType = "image/jpg";
				}
				return new DynamicContent(contentType, conx.getContentLengthLong(), conx.getInputStream(), "Set-Cookie",
						LOCBCOOKIE + "=" + localWebServerCookie + "; Path=/");
			}
		});
		miniHttp.start();

//		setUserAgentStylesheet(STYLESHEET_MODENA);

		synchronized (barrier) {
			barrier.notify();
		}

		// Bridges to the common client network code
		bridge = new Bridge();

		// Setup the window
		if (Platform.isSupported(ConditionalFeature.TRANSPARENT_WINDOW)) {
			primaryStage.initStyle(StageStyle.TRANSPARENT);
		} else {
			primaryStage.initStyle(StageStyle.UNDECORATED);
		}
		primaryStage.setTitle(BUNDLE.getString("title"));
		primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("logonbox-icon256x256.png")));
		primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("logonbox-icon128x128.png")));
		primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("logonbox-icon64x64.png")));
		primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("logonbox-icon48x48.png")));
		primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("logonbox-icon32x32.png")));

		// Open the actual scene
		FramedController fc = openScene(UI.class, null);
		Scene scene = fc.getScene();
		Parent node = scene.getRoot();

		node.getStylesheets().add(Client.class.getResource(Client.class.getSimpleName() + ".css").toExternalForm());
		AwesomeIcons.install(node);

		/* Anchor to stretch the content across the borderless window */
		AnchorPane anchor = new AnchorPane(node);
		AnchorPane.setBottomAnchor(node, 0d);
		AnchorPane.setLeftAnchor(node, 0d);
		AnchorPane.setTopAnchor(node, 0d);
		AnchorPane.setRightAnchor(node, 0d);

		BorderlessScene primaryScene = new BorderlessScene(primaryStage, StageStyle.TRANSPARENT, anchor, 460, 200);
		primaryScene.setMoveControl(node);
		primaryScene.setSnapEnabled(false);
		primaryScene.setResizable(true);

		// Finalise and show
		primaryStage.setScene(primaryScene);
		primaryStage.show();

		primaryStage.onCloseRequestProperty().set(we -> {
			confirmExit();
			we.consume();
		});

		bridge.start();
	}

	public Stage getStage() {
		return primaryStage;
	}

	public void confirmExit() {

		/*
		 * LDP - I don't want to be prompted to disconnect if I have set the connection
		 * to stay connected.
		 */
		int active = bridge.getActiveButNonPersistentConnections();

		if (active > 0) {
			Alert alert = new Alert(AlertType.CONFIRMATION);
			alert.setTitle(BUNDLE.getString("exit.confirm.title"));
			alert.setHeaderText(BUNDLE.getString("exit.confirm.header"));
			alert.setContentText(BUNDLE.getString("exit.confirm.content"));

			ButtonType disconnect = new ButtonType(BUNDLE.getString("exit.confirm.disconnect"));
			ButtonType stayConnected = new ButtonType(BUNDLE.getString("exit.confirm.stayConnected"));
			ButtonType cancel = new ButtonType(BUNDLE.getString("exit.confirm.cancel"), ButtonData.CANCEL_CLOSE);

			alert.getButtonTypes().setAll(disconnect, stayConnected, cancel);
			waitingForExitChoice = true;
			try {
				Optional<ButtonType> result = alert.showAndWait();

				if (result.get() == disconnect) {
					new Thread() {
						public void run() {
							bridge.disconnectAll();
							exitApp();
						}
					}.start();
				} else if (result.get() == stayConnected) {
					exitApp();
				}
			} finally {
				waitingForExitChoice = false;
			}
		} else {
			exitApp();
		}
	}

	protected void exitApp() {
		try {
			miniHttp.close();
		} catch (IOException e) {
		}
		System.exit(0);
	}

	public ExecutorService getLoadQueue() {
		return loadQueue;
	}

	public Bridge getBridge() {
		return bridge;
	}

	public void clearLoadQueue() {
		loadQueue.shutdownNow();
		loadQueue = Executors.newSingleThreadExecutor();
	}

	public boolean isWaitingForExitChoice() {
		return waitingForExitChoice;
	}

	protected void installAllTrustingCertificateVerifier() {
		NaiveTrustProvider.setAlwaysTrust(true);

		try {
			SSLContext sc = SSLContext.getInstance("SSL");
			sc.init(null, new TrustManager[] { new NaiveTrustManager() }, new java.security.SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
		} catch (GeneralSecurityException e) {
		}

		// Create all-trusting host name verifier
		HostnameVerifier allHostsValid = new HostnameVerifier() {
			public boolean verify(String hostname, SSLSession session) {
				return true;
			}
		};

		// Install the all-trusting host verifier
		HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
	}

}
