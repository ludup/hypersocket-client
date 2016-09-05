package com.hypersocket.client.gui.jfx;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.layout.VBox;

public class ResourceGroupController extends AbstractController {
	static Logger LOG = LoggerFactory.getLogger(ResourceGroupController.class);

	@FXML
	private VBox resourceItems;

	@Override
	protected void onInitialize() {
	}

	public void setResources(ResourceGroupList group) {
		resourceItems.getChildren().clear();
		for (ResourceItem item : group.getItems()) {
			ItemButton button = new ItemButton(resources, item, context) {
				@Override
				protected void onBeforeLaunch() {
					Platform.runLater(new Runnable() {

						@Override
						public void run() {
							popup.hide();
						}
					});
				}
			};
			button.setText(item.getResource().getName());
			resourceItems.getChildren().add(button);
		}
	}

}
