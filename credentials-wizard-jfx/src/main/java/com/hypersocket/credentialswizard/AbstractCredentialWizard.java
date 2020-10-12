package com.hypersocket.credentialswizard;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.security.GeneralSecurityException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker.State;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

public abstract class AbstractCredentialWizard extends Application {

	static final int NO_OPERATION_REQUIRED = 0;
	static final int OPERATION_COMPLETE = 1;
	static final int OPERATION_COMPLETE_INCOMPLETE_PROFILE = 2;
	static final int OPERATION_REQUIRED = 3;
	static final int ERROR = 4;
	static final int CANCELLED = 5;

	@Override
	public void start(final Stage primaryStage) {

		if (!getParameters().getNamed().containsKey("strictSSL")
				|| "false".equalsIgnoreCase(getParameters().getNamed().get("strictSSL"))) {
			installAllTrustingCertificateVerifier();
		}

		StackPane root = new StackPane();
		final ProgressBar progress = new ProgressBar();
		progress.setPrefSize(300, 25);

		WebView webView = new WebView();
		webView.getEngine().setUserAgent("CredentialsWizard/1.0");

		root.getChildren().addAll(webView, progress);

		progress.progressProperty().bind(webView.getEngine().getLoadWorker().progressProperty());

		webView.getEngine().getLoadWorker().stateProperty().addListener(new ChangeListener<State>() {
			@Override
			public void changed(@SuppressWarnings("rawtypes") ObservableValue ov, State oldState, State newState) {
				if (newState == State.SUCCEEDED) {
					// hide progress bar then page is ready
					progress.setVisible(false);
				}
			}
		});

		webView.getEngine().locationProperty().addListener(new ChangeListener<String>() {
			@Override
			public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
				String url = webView.getEngine().locationProperty().getValue();
				int status = onPageChange(stripTrailingSlashes(url));
				switch (status) {
				case NO_OPERATION_REQUIRED:
				case OPERATION_COMPLETE:
				case OPERATION_COMPLETE_INCOMPLETE_PROFILE:
				case ERROR:
					exitWithStatus(status);
					break;
				default:
					if (!primaryStage.isShowing()) {
						Platform.runLater(() -> {
							primaryStage.show();
							primaryStage.toFront();
							webView.getEngine().reload();
						});
					}
					break;
				}
			}
		});

		webView.getEngine().getLoadWorker().exceptionProperty().addListener(new ChangeListener<Throwable>() {
			public void changed(ObservableValue<? extends Throwable> o, Throwable old, final Throwable value) {
				Alert alert = new Alert(AlertType.ERROR);
				alert.setTitle("Error");
				alert.setHeaderText("An error occured loading content.");
				alert.setContentText((value != null) ? webView.getEngine().getLocation() + "\n" + value.getMessage()
						: webView.getEngine().getLocation() + "\nUnexpected error.");
				alert.showAndWait();
			}
		});
		try {
			webView.getEngine().load(getInitialUrl());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		// Configure stage;
		primaryStage.setTitle(getTitle());
		primaryStage.setScene(new Scene(root));
		primaryStage.getScene().getStylesheets().add("style.css");
		primaryStage.setWidth(1024);
		primaryStage.setHeight(768);
		primaryStage.setOnCloseRequest((evt) -> {
			exitWithStatus(CANCELLED);
		});
		primaryStage.show();

	}

	protected abstract String getTitle();

	protected String getInitialUrl() {
		return getParameters().getRaw().get(0);
	}

	protected abstract int onPageChange(String url);

	protected String stripTrailingSlashes(String t) {
		while (t.endsWith("/"))
			t = t.substring(0, t.length() - 1);
		return t;
	}
	
	protected void exitWithStatus(int status) {
		/* The Windows DLL that calls this does not (yet) care about the additional
		 * statuses, so the reduce the status codes 
		 */
		System.exit(status);
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

	protected static void setLogs() throws FileNotFoundException, IOException {
		FileOutputStream fos = new FileOutputStream(
				new File(System.getProperty("credentials.sysOut", "credentials-wizard.out")));
		try {
			FileOutputStream eos = new FileOutputStream(
					new File(System.getProperty("credentials.sysErr", "credentials-wizard.err")));
			System.setOut(new PrintStream(fos));
			System.setErr(new PrintStream(eos));
		} catch (IOException ioe) {
			fos.close();
			throw ioe;
		}
	}

}
