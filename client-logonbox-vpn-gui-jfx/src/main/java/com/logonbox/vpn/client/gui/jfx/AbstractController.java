package com.logonbox.vpn.client.gui.jfx;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.extensions.ExtensionDefinition;
import com.logonbox.vpn.client.gui.jfx.Bridge.Listener;
import com.logonbox.vpn.common.client.Connection;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;

public class AbstractController implements FramedController, Listener {
	static Logger log = LoggerFactory.getLogger(AbstractController.class);

	protected Client context;
	protected ResourceBundle resources;
	protected URL location;
	protected Scene scene;

	@Override
	public final void initialize(URL location, ResourceBundle resources) {
		this.location = location;
		this.resources = resources;
		onInitialize();
	}

	@Override
	public final void cleanUp() {
		context.getBridge().removeListener(this);
		onCleanUp();
	}

	@Override
	public final void configure(Scene scene, Client context) {
		this.scene = scene;
		this.context = context;
		onConfigure();
		context.getBridge().addListener(this);
	}

	public Stage getStage() {
		return (Stage) scene.getWindow();
	}

	@Override
	public void bridgeEstablished() {
	}

	@Override
	public void bridgeLost() {
	}

	@Override
	public void ping() {
	}

	protected void onConfigure() {
	}

	protected void onCleanUp() {
	}

	protected void onInitialize() {
	}

	@Override
	public void connecting(Connection connection) {
	}

	@Override
	public void started(Connection connection) {
	}

	@Override
	public void finishedConnecting(Connection connection, Exception e) {
	}

	@Override
	public void disconnecting(Connection connection) {
	}

	@Override
	public void disconnected(Connection connection, Exception e) {
	}

	@Override
	public Scene getScene() {
		return scene;
	}

	@Override
	public void startingUpdate(String app, long totalBytesExpected) {
	}

	@Override
	public void updateProgressed(String app, long sincelastProgress, long totalSoFar, long totalBytesExpected) {
	}

	@Override
	public void updateComplete(String app, long totalBytesTransfered) {
	}

	@Override
	public void updateFailure(String app, String message) {
	}

	@Override
	public void extensionUpdateComplete(String app, ExtensionDefinition def) {
	}

	@Override
	public void initUpdate(int apps, UIState currentMode) {
	}

	@Override
	public void initDone(boolean restart, String errorMessage) {
	}

	protected void walkTree(Object node, Consumer<Object> visitor) {
		if (node == null) {
			return;
		}
		visitor.accept(node);
		if (node instanceof TabPane) {
			((TabPane) node).getTabs().forEach(n -> walkTree(n, visitor));
		} else if (node instanceof Tab) {
			walkTree(((Tab) node).getContent(), visitor);
			walkTree(((Tab) node).getGraphic(), visitor);
		} else if (node instanceof Parent) {
			((Parent) node).getChildrenUnmodifiable().forEach(n -> walkTree(n, visitor));
		}
	}

	@Override
	public boolean showBrowser(Connection connection, String uri) {
		// TODO
		return false;
	}
}
