package com.logonbox.vpn.client.gui.jfx;

import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.stage.Stage;

public interface FramedController extends Initializable {

	Scene getScene();

	void configure(Scene scene, Client jfxhsClient);

	void cleanUp();

	Stage getStage();
}
