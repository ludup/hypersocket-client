package com.hypersocket.client.gui.jfx;

import java.rmi.RemoteException;
import java.text.MessageFormat;
import java.util.logging.Logger;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.ImageView;
import javafx.util.Duration;

import com.hypersocket.client.gui.jfx.Dock.Mode;
import com.hypersocket.client.rmi.Connection;
import com.hypersocket.client.rmi.GUICallback;

public class Update extends AbstractController {
	final static Logger LOG = Logger.getLogger(Update.class.getName());

	@FXML
	private ProgressBar progress;
	@FXML
	private Label message;
	@FXML
	private ImageView icon;

	private Timeline awaitingBridgeLoss;
	private Timeline awaitingBridgeEstablish;
	private int appsToUpdate;
	private int appsUpdated;
	private Connection updatingConnection;

	@Override
	protected void onInitialize() {
	}

	@Override
	protected void onConfigure() {
		Configuration cfg = Configuration.getDefault();
		cfg.sizeProperty().addListener(new ChangeListener<Number>() {
			@Override
			public void changed(ObservableValue<? extends Number> observable,
					Number oldValue, Number newValue) {
				sizeButtons();
			}
		});
		sizeButtons();
	}

	@Override
	public void initUpdate(int apps) {
		super.initUpdate(apps);
		LOG.info(String.format("Initialising update. Expecting %d apps", apps));
		this.message.textProperty().set(resources.getString("init"));
		appsToUpdate = apps;
		appsUpdated = 0;
	}

	@Override
	public void startingUpdate(String app, long totalBytesExpected, Connection connection) {
		LOG.info(String.format("Starting up of %s, expect %d bytes", app,
				totalBytesExpected));
		updatingConnection = connection;
		String appName = getAppName(app);
		this.message.textProperty().set(MessageFormat.format(resources.getString("updating"), appName));
		progress.progressProperty().setValue(0);
	}

	@Override
	public void updateProgressed(String app, long sincelastProgress,
			long totalSoFar, long totalBytesExpected) {
		String appName = getAppName(app);
		this.message.textProperty().set(MessageFormat.format(resources.getString("updating"), appName));
		progress.progressProperty().setValue(
				(double) totalSoFar / totalBytesExpected);
	}

	@Override
	public void bridgeEstablished() {
		super.bridgeEstablished();
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				if (awaitingBridgeEstablish != null) {
					/* If the connection that originated the update was known, then try to reconnect to it when
					 * the client GUI starts again 
					 */
					if(updatingConnection != null) {
						Configuration.getDefault().temporaryOnStartConnectionProperty().set(String.valueOf(updatingConnection.getId()));
					}
					
					// Bridge established as result of update, now restart the
					// client itself
					resetAwaingBridgeEstablish();
					message.textProperty().set(
							resources.getString("guiRestart"));
					new Timeline(new KeyFrame(Duration.seconds(5), ae -> Main
							.getInstance().restart())).play();
				}
			}
		});
	}

	@Override
	public void bridgeLost() {
		super.bridgeLost();
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				if (awaitingBridgeLoss != null) {
					// Bridge lost as result of update, wait for it to come back
					resetAwaingBridgeLoss();
					message.textProperty().set(
							resources.getString("waitingStart"));
					awaitingBridgeEstablish = new Timeline(new KeyFrame(
							Duration.seconds(30),
							ae -> giveUpWaitingForBridgeEstablish()));
					awaitingBridgeEstablish.play();
				}
			}
		});
	}

	@Override
	public void updateComplete(String app, long totalBytesTransfered) {
		String appName = getAppName(app);
		progress.progressProperty().setValue(1);
		this.message.textProperty().set(MessageFormat.format(resources.getString("updated"), appName));
		appsUpdated++;
		LOG.info(String.format(
				"Update of %s complete, have now updated %d of %d apps", app,
				appsUpdated, appsToUpdate));
	}

	private String getAppName(String app) {
		if(resources.containsKey(app)) {
			return resources.getString(app);
		} else {
			return app;
		}
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
			if(restart) {
				LOG.info(String
						.format("All apps updated, starting restart process"));
				awaitingBridgeLoss = new Timeline(new KeyFrame(
						Duration.seconds(30), ae -> giveUpWaitingForBridgeStop()));
				awaitingBridgeLoss.play();
			} else {
				resetState();
			}
		} else {
			this.message.textProperty().set(errorMessage);
			progress.progressProperty().setValue(1);
			resetState();
		}
	}

	private void resetState() {
		resetAwaingBridgeEstablish();
		resetAwaingBridgeLoss();
		appsToUpdate = 0;
		appsUpdated = 0;
		Dock.getInstance().setMode(Mode.IDLE);
	}

	private void giveUpWaitingForBridgeEstablish() {
		LOG.info("Given up waiting for bridge to start");
		resetAwaingBridgeEstablish();
		try {
			context.getBridge().notify(
					resources.getString("givenUpWaitingForBridgeEstablish"),
					GUICallback.NOTIFY_ERROR);
		} catch (RemoteException e) {
			// Not actually remote
		}
		Dock.getInstance().setMode(Mode.IDLE);
	}

	private void giveUpWaitingForBridgeStop() {
		LOG.info("Given up waiting for bridge to stop");
		resetAwaingBridgeLoss();
		try {
			context.getBridge().notify(
					resources.getString("givenUpWaitingForBridgeStop"),
					GUICallback.NOTIFY_ERROR);
		} catch (RemoteException e) {
			// Not actually remote
		}
		Dock.getInstance().setMode(Mode.IDLE);
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

	private void sizeButtons() {
		int sz = Configuration.getDefault().sizeProperty().get();
		int df = sz / 8;
		sz -= df;
		icon.setFitWidth(sz - df);
		icon.setFitHeight(sz - df);
	}
}
