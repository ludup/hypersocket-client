package com.hypersocket.client.gui.jfx;

import java.rmi.RemoteException;
import java.text.MessageFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.client.gui.jfx.Dock.Mode;
import com.hypersocket.client.rmi.Connection;
import com.hypersocket.client.rmi.GUICallback;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.util.Duration;

public class Update extends AbstractController {
	static Logger LOG = LoggerFactory.getLogger(Update.class);

	@FXML
	private ProgressBar progress;
	@FXML
	private Label message;

	private Timeline awaitingBridgeLoss;
	private Timeline awaitingBridgeEstablish;
	private int appsToUpdate;
	private int appsUpdated;
	private Connection updatingConnection;
	private Mode currentMode = Mode.IDLE;

	private boolean awaitingRestart;

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
	public void initUpdate(int apps, Mode currentMode) {
		if(awaitingRestart)
			throw new IllegalStateException("Cannot initiate updates while waiting to restart the GUI..");
		
		super.initUpdate(apps, currentMode);
		this.currentMode = currentMode;
		LOG.info(String.format("Initialising update (currently in mode %s). Expecting %d apps", currentMode, apps));
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
		
		/* If we were waiting for this, it's part of the update process. We don't want
		 * the connection continuing 
		 */
		if (awaitingBridgeEstablish != null) {
			awaitingRestart = true;
		}
		
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				if (awaitingBridgeEstablish != null) {
					/* If the connection that originated the update was known, then try to reconnect to it when
					 * the client GUI starts again 
					 */
					if(updatingConnection != null) {
						LOG.info(String.format("Will use connection %d (%s) when next starting", updatingConnection.getId(), updatingConnection.getHostname())); 
						Configuration.getDefault().temporaryOnStartConnectionProperty().set(String.valueOf(updatingConnection.getId()));
					}
					else
						LOG.info(String.format("Updating connection is not known, might not start connected to anything (unless some profiles are 'stay connected')", updatingConnection.getId(), updatingConnection.getHostname()));
					
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
						.format("All apps updated, starting restart process " + Math.random()));
				try {
					throw new Exception();
				}
				catch(Exception e) {
					LOG.error("TRACE" , e);
				}
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
		LOG.info(String.format("Reseting update state, returning to mode %s", currentMode));
		Dock.getInstance().setMode(currentMode);
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
	}

	public boolean isAwaitingBridgeLoss() {
		return awaitingBridgeLoss != null;
	}

	public boolean isAwaitingGUIRestart() {
		return awaitingRestart;
	}

	public boolean isAwaitingBridgeEstablish() {
		return awaitingBridgeEstablish != null;
	}
}
