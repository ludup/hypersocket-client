package com.logonbox.vpn.client.gui.jfx;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;

public class AbstractController implements FramedController {
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
		onCleanUp();
	}

	@Override
	public final void configure(Scene scene, Client context) {
		this.scene = scene;
		this.context = context;
		onConfigure();
	}

	public Stage getStage() {
		return (Stage) scene.getWindow();
	}

	protected void onConfigure() {
	}

	protected void onCleanUp() {
	}

	protected void onInitialize() {
	}

	@Override
	public Scene getScene() {
		return scene;
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

}
