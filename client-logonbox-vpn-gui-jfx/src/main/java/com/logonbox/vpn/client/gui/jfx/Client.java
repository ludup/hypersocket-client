package com.logonbox.vpn.client.gui.jfx;

import java.awt.SplashScreen;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
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
import javax.net.ssl.X509TrustManager;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.PropertyConfigurator;
import org.kordamp.bootstrapfx.BootstrapFX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.goxr3plus.fxborderlessscene.borderless.BorderlessScene;
import com.logonbox.vpn.client.gui.jfx.MiniHttpServer.DynamicContent;
import com.logonbox.vpn.client.gui.jfx.MiniHttpServer.DynamicContentFactory;
import com.logonbox.vpn.common.client.AbstractDBusClient;
import com.logonbox.vpn.common.client.api.Branding;
import com.logonbox.vpn.common.client.api.BrandingInfo;

import javafx.application.Application;
import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class Client extends Application implements X509TrustManager {

	/**
	 * There seems to be some problem with runtimes later than 11 and loading
	 * resources in the embedded browser from the classpath.
	 * 
	 * This switch activates a work around that opens a local HTTP server and
	 * directs the browser to that instead.
	 * 
	 * Attempts are made to secure this with the use of a private cookie, but this
	 * doesn't always work (not sure why!)
	 * 
	 * NOTE: This is now OFF by default as we are using Java 11. When we switch back
	 * to Java 15 or higher this may need to be revisited unless the underlying bug
	 * has been fixed.
	 */
	static final boolean useLocalHTTPService = System.getProperty("logonbox.vpn.useLocalHTTPService", "false")
			.equals("true");

	/* Security can be turned off for this, useful for debugging */
	static final boolean secureLocalHTTPService = System.getProperty("logonbox.vpn.secureLocalHTTPService", "true")
			.equals("true");

	static final boolean allowBranding = System.getProperty("logonbox.vpn.allowBranding", "true")
			.equals("true");

	/**
	 * Matches the identifier in logonbox VPN server
	 * PeerConfigurationAuthenticationProvider.java
	 */
	static final String DEVICE_IDENTIFIER = "LBVPNDID";
	static final String LOCBCOOKIE = "LOCBCKIE";

	static Logger log = LoggerFactory.getLogger(Client.class);
	static ResourceBundle BUNDLE = ResourceBundle.getBundle(Client.class.getName());
	static UUID localWebServerCookie = UUID.randomUUID();

	private ExecutorService opQueue = Executors.newSingleThreadExecutor();
	private boolean waitingForExitChoice;
	private Stage primaryStage;
	private MiniHttpServer miniHttp;
	private Tray tray;
	private static Client instance;

	public static Client get() {
		return instance;
	}

	@Override
	public void init() throws Exception {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				cleanUp();
			}
		});
		
		Platform.setImplicitExit(false);
		instance = this;
		PropertyConfigurator.configureAndWatch(
				System.getProperty("hypersocket.logConfiguration", "conf" + File.separator + "log4j.properties"));
	}
	
	public AbstractDBusClient getDBus() {
		return Main.getInstance();
	}

	public <C extends AbstractController> C openScene(Class<C> controller) throws IOException {
		return openScene(controller, null);
	}

	@SuppressWarnings("unchecked")
	public <C extends AbstractController> C openScene(Class<C> controller, String fxmlSuffix) throws IOException {
		URL resource = controller
				.getResource(controller.getSimpleName() + (fxmlSuffix == null ? "" : fxmlSuffix) + ".fxml");
		FXMLLoader loader = new FXMLLoader();
		loader.setResources(ResourceBundle.getBundle(controller.getName()));
		Parent root = loader.load(resource.openStream());
		C controllerInst = (C) loader.getController();
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

		installCertificateVerifier();

		if (useLocalHTTPService) {
			miniHttp = new MiniHttpServer(59999, 0, null);
			miniHttp.addContent(new DynamicContentFactory() {
				@Override
				public DynamicContent get(String path, Map<String, List<String>> headers) throws IOException {
					List<String> localCookie = headers.get("Cookie");
					boolean allowed = !secureLocalHTTPService;
					if (!allowed && localCookie != null) {
						for (String ck : localCookie) {
							int idx = ck.indexOf('=');
							String name = ck.substring(0, idx);
							if (name.equals(LOCBCOOKIE)
									&& ck.substring(idx + 1).equals(localWebServerCookie.toString())) {
								allowed = true;
								break;
							}
						}
					}
					if (!allowed)
						throw new IOException("Access denied to " + path);

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
					return new DynamicContent(contentType, conx.getContentLengthLong(), conx.getInputStream(),
							"Set-Cookie", LOCBCOOKIE + "=" + localWebServerCookie + "; Path=/");
				}
			});
			miniHttp.start();
		}

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
		UI fc = openScene(UI.class, null);
		Scene scene = fc.getScene();
		Parent node = scene.getRoot();

		applyColors(null, node);

		/* Anchor to stretch the content across the borderless window */
		AnchorPane anchor = new AnchorPane(node);
		AnchorPane.setBottomAnchor(node, 0d);
		AnchorPane.setLeftAnchor(node, 0d);
		AnchorPane.setTopAnchor(node, 0d);
		AnchorPane.setRightAnchor(node, 0d);

		String[] sizeParts = Main.getInstance().getSize().toLowerCase().split("x");
		BorderlessScene primaryScene = new BorderlessScene(primaryStage, StageStyle.TRANSPARENT, anchor,
				Integer.parseInt(sizeParts[0]), Integer.parseInt(sizeParts[1]));
		if(!Main.getInstance().isNoMove())
			primaryScene.setMoveControl(node);
		primaryScene.setSnapEnabled(false);
		primaryScene.removeDefaultCSS();
		primaryScene.setResizable(!Main.getInstance().isNoMove());
		primaryStage.setAlwaysOnTop(Main.getInstance().isAlwaysOnTop());

		// Finalise and show
		Configuration cfg = Configuration.getDefault();
		int x = cfg.xProperty().get();
		int y = cfg.yProperty().get();
		int h = cfg.hProperty().get();
		int w = cfg.wProperty().get();
		if (h == 0 && w == 0) {
			primaryStage.setWidth(700);
			primaryStage.setHeight(640);
		} else {
			primaryStage.setX(x);
			primaryStage.setY(y);
			primaryStage.setWidth(w);
			primaryStage.setHeight(h);
		}
		primaryStage.setScene(primaryScene);
		primaryStage.show();
		primaryStage.xProperty().addListener((c, o, n) -> cfg.xProperty().set(n.intValue()));
		primaryStage.yProperty().addListener((c, o, n) -> cfg.yProperty().set(n.intValue()));
		primaryStage.widthProperty().addListener((c, o, n) -> cfg.wProperty().set(n.intValue()));
		primaryStage.heightProperty().addListener((c, o, n) -> cfg.hProperty().set(n.intValue()));

		primaryStage.onCloseRequestProperty().set(we -> {
			if (!Main.getInstance().isNoClose())
				confirmExit();
			we.consume();
		});

		if (!Main.getInstance().isNoSystemTray())
			tray = new DorkBoxTray(this);
		
        final SplashScreen splash = SplashScreen.getSplashScreen();
        if (splash != null) {
        	splash.close();
        }
	}

	public Stage getStage() {
		return primaryStage;
	}

	public void confirmExit() {
		int active = 0;
		try {
			active = getDBus().getVPN().getActiveButNonPersistentConnections();
		}
		catch(Exception e) {
			exitApp();
		}
		
		if (active > 0) {
			Alert alert = new Alert(AlertType.CONFIRMATION);
			alert.initModality(Modality.APPLICATION_MODAL);
			alert.initOwner(getStage());
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
					opQueue.execute(() -> {
						getDBus().getVPN().disconnectAll();
						exitApp();
					});
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
		opQueue.shutdown();
		System.exit(0);
	}

	protected void cleanUp() {
		if(tray != null) {
			try {
				tray.close();
			} catch (Exception e) {
			}
		}
		if (miniHttp != null) {
			try {
				miniHttp.close();
			} catch (IOException e) {
			}
		}
	}

	public ExecutorService getOpQueue() {
		return opQueue;
	}

//	public Bridge getBridge() {
//		return bridge;
//	}

	public void clearLoadQueue() {
		opQueue.shutdownNow();
		opQueue = Executors.newSingleThreadExecutor();
	}

	public boolean isWaitingForExitChoice() {
		return waitingForExitChoice;
	}

	protected void installCertificateVerifier() {

		Security.insertProviderAt(new ClientTrustProvider(), 1);
		Security.setProperty("ssl.TrustManagerFactory.algorithm", ClientTrustProvider.TRUST_PROVIDER_ALG);

		try {
			SSLContext sc = SSLContext.getInstance("SSL");
			sc.init(null, new TrustManager[] { this }, new java.security.SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
		} catch (GeneralSecurityException e) {
		}

		// Create all-trusting host name verifier
		HostnameVerifier allHostsValid = new HostnameVerifier() {
			@Override
			public boolean verify(String hostname, SSLSession session) {

				log.debug(String.format("Verify hostname %s: %s", hostname, session));
				if (!"true".equals(System.getProperty("logonbox.vpn.strictSSL", "true"))) {
					log.warn("NOT FOR PRODUCTION USE. All SSL certificates will be trusted regardless of status. This should only be used for testing.");
					return true;
				}
				else
					return false;
			}
		};

		// Install the all-trusting host verifier
		HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
	}

	@Override
	public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		List<String> chainSubjectDN = new ArrayList<>();
		for (X509Certificate c : chain) {
			try {
				if(log.isDebugEnabled())
					log.debug(String.format("Validating: %s", c));
				chainSubjectDN.add(c.getSubjectDN().toString());
				c.checkValidity();
			} catch (CertificateException ce) {
				log.error("Certificate error. " + String.join(" -> ", chainSubjectDN), ce);
				throw ce;
			}
		}
	}

	@Override
	public X509Certificate[] getAcceptedIssuers() {
		X509Certificate[] NO_CERTS = new X509Certificate[0];
		return NO_CERTS;
	}

	public void open() {
		log.info("Open request");
		Platform.runLater(() -> {
			primaryStage.show();
			primaryStage.toFront();
		});
	}

	public void options() {
		open();
		Platform.runLater(() -> UI.getInstance().options());
	}

	public void maybeExit() {
		if (tray == null || !tray.isActive()) {
			confirmExit();
		} else {
			Platform.runLater(() -> primaryStage.hide());
		}
	}

	File getCustomJavaFXCSSFile() {
		File tmpFile;
		if (System.getProperty("hypersocket.bootstrap.distDir") == null)
			tmpFile = new File(new File(System.getProperty("java.io.tmpdir")),
					System.getProperty("user.name") + "-lbvpn-jfx.css");
		else
			tmpFile = new File(new File(System.getProperty("hypersocket.bootstrap.distDir")).getParentFile(), "lbvpn-jfx.css");
		return tmpFile;
	}

	File getCustomLocalWebCSSFile() {
		File tmpFile;
		if (System.getProperty("hypersocket.bootstrap.distDir") == null)
			tmpFile = new File(new File(System.getProperty("java.io.tmpdir")),
					System.getProperty("user.name") + "-lbvpn-web.css");
		else
			tmpFile = new File(new File(System.getProperty("hypersocket.bootstrap.distDir")).getParentFile(), "lbvpn-web.css");
		return tmpFile;
	}

	String getCustomJavaFXCSSResource(Branding branding) {
		StringBuilder bui = new StringBuilder();

		// Get the base colour. All other colours are derived from this
		Color backgroundColour = Color
				.valueOf(branding == null ? BrandingInfo.DEFAULT_BACKGROUND : branding.getResource().getBackground());
		Color foregroundColour = Color
				.valueOf(branding == null ? BrandingInfo.DEFAULT_FOREGROUND : branding.getResource().getForeground());

		if (backgroundColour.getOpacity() == 0) {
			// Prevent total opacity, as mouse events won't be received
			backgroundColour = new Color(backgroundColour.getRed(), backgroundColour.getGreen(),
					backgroundColour.getBlue(), 1f / 255f);
		}

		bui.append("* {\n");

		bui.append("-fx-lbvpn-background: ");
		bui.append(toHex(backgroundColour));
		bui.append(";\n");

		bui.append("-fx-lbvpn-foreground: ");
		bui.append(toHex(foregroundColour));
		bui.append(";\n");

//
		// Highlight
		if (backgroundColour.getSaturation() == 0) {
			// Greyscale, so just use HS blue
			bui.append("-fx-lbvpn-accent: 1e0c51;\n");
			bui.append("-fx-lbvpn-accent2: 0e0041;\n");
		} else {
			// A colour, so choose the next adjacent colour in the HSB colour
			// wheel (45 degrees)
			bui.append("-fx-lbvpn-accent: " + toHex(backgroundColour.deriveColor(45f, 1f, 1f, 1f)) + ";\n");
			bui.append("-fx-lbvpn-accent: " + toHex(backgroundColour.deriveColor(-45f, 1f, 1f, 1f)) + ";\n");
		}

		// End
		bui.append("}\n");

		return bui.toString();

	}

	void applyColors(Branding branding, Parent node) {
		ObservableList<String> ss = node.getStylesheets();

		/* No branding, remove the custom styles if there are any */
		ss.clear();

		/* Create new custom local web styles */
		writeLocalWebCSS(branding);

		/* Create new JavaFX custom styles */
		writeJavaFXCSS(branding);
		File tmpFile = getCustomJavaFXCSSFile();
		if(log.isDebugEnabled())
			log.debug(String.format("Using custom JavaFX stylesheet %s", tmpFile));
		ss.add(0, toUri(tmpFile).toExternalForm());

		node.getStylesheets().add(BootstrapFX.bootstrapFXStylesheet());
		node.getStylesheets().add(Client.class.getResource("bootstrapfx.override.css").toExternalForm());
		node.getStylesheets().add(Client.class.getResource(Client.class.getSimpleName() + ".css").toExternalForm());

	}

	void writeLocalWebCSS(Branding branding) {
		File tmpFile = getCustomLocalWebCSSFile();
		tmpFile.getParentFile().mkdirs();
		String url = toUri(tmpFile).toExternalForm();
		if(log.isDebugEnabled())
			log.debug(String.format("Writing local web style sheet to %s", url));
		String cbg = branding == null ? BrandingInfo.DEFAULT_BACKGROUND : branding.getResource().getBackground();
		String cfg = branding == null ? BrandingInfo.DEFAULT_FOREGROUND : branding.getResource().getForeground();
		String cac = toHex(Color.valueOf(cbg).deriveColor(0, 1, 0.85, 1));
		String cac2 = toHex(Color.valueOf(cbg).deriveColor(0, 1, 1.15, 1));
		try (PrintWriter output = new PrintWriter(new FileWriter(tmpFile))) {
			try (InputStream input = UI.class.getResource("local.css").openStream()) {
				for (String line : IOUtils.readLines(input, "UTF-8")) {
					line = line.replace("${lbvpnBackground}", cbg);
					line = line.replace("${lbvpnForeground}", cfg);
					line = line.replace("${lbvpnAccent}", cac);
					line = line.replace("${lbvpnAccent2}", cac2);
					output.println(line);
				}
			}
		} catch (IOException ioe) {
			throw new IllegalStateException("Failed to load local style sheet template.", ioe);
		}
	}

	void writeJavaFXCSS(Branding branding) {
		try {
			File tmpFile = getCustomJavaFXCSSFile();
			String url = toUri(tmpFile).toExternalForm();
			if(log.isDebugEnabled())
				log.debug(String.format("Writing JavafX style sheet to %s", url));
			PrintWriter pw = new PrintWriter(new FileOutputStream(tmpFile));
			try {
				pw.println(getCustomJavaFXCSSResource(branding));
			} finally {
				pw.close();
			}
		} catch (IOException e) {
			throw new RuntimeException("Could not create custom CSS resource.");
		}
	}

	static URL toUri(File tmpFile) {
		try {
			return tmpFile.toURI().toURL();
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}

	static String toHex(Color color) {
		return toHex(color, -1);
	}

	static String toHex(Color color, boolean opacity) {
		return toHex(color, opacity ? color.getOpacity() : -1);
	}

	static String toHex(Color color, double opacity) {
		if (opacity > -1)
			return String.format("#%02x%02x%02x%02x", (int) (color.getRed() * 255), (int) (color.getGreen() * 255),
					(int) (color.getBlue() * 255), (int) (opacity * 255));
		else
			return String.format("#%02x%02x%02x", (int) (color.getRed() * 255), (int) (color.getGreen() * 255),
					(int) (color.getBlue() * 255));
	}
}
