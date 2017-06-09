package com.hypersocket.client.gui.jfx;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.client.ServiceResource;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class Status extends AbstractController {
	static Logger LOG = LoggerFactory.getLogger(Status.class);

	@FXML
	private VBox statusItems;
	
	@FXML
	private StackPane statusStackPane;

	@Override
	protected void onInitialize() {
		statusItems.focusTraversableProperty().set(true);
	}

	public void setResources(List<ServiceResource> group) {
		statusItems.getChildren().clear();
		for (ServiceResource item : group) {
			HBox hb = new HBox();
			hb.getStyleClass().add("item");

			// Icon
			Label status = new Label();
			status.setText(resources.getString("status.icon"));
			status.getStyleClass().add("icon");
			hb.getChildren().add(status);
			switch(item.getServiceStatus()) {
			case GOOD:
//			      Effect glow = new Glow(0.5);
//			      status.setEffect(glow);
			      status.setStyle("-fx-text-fill: green;");
			      break;
			case BAD:
//			      glow = new Glow(0.5);
//			      status.setEffect(glow);
			      status.setStyle("-fx-text-fill: red;");
			      break;      
			default:
				status.setOpacity(0.5f);
				break;
			}
			 

			// Text
			Label label = new Label();
			label.setText(item.getServiceDescription());
			label.setTooltip(new Tooltip(item.getFullServiceDescription()));
			hb.getChildren().add(label);
			
			//
			statusItems.getChildren().add(hb);
		}
	}

}
