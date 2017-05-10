package com.hypersocket.client.gui.jfx;

import java.net.URI;

import org.apache.commons.lang3.StringUtils;
import org.controlsfx.control.decoration.Decorator;
import org.controlsfx.control.decoration.GraphicDecoration;
import org.controlsfx.control.textfield.CustomPasswordField;
import org.controlsfx.control.textfield.CustomTextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.client.rmi.Connection;
import com.hypersocket.client.rmi.ConnectionService;
import com.hypersocket.client.rmi.GUICallback;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class AddConnection extends AbstractController {

	
	static Logger LOG = LoggerFactory.getLogger(AddConnection.class);
	
	
	@FXML
	private CustomTextField nameInput;
	@FXML
	private CustomTextField serverInput;
	@FXML
	private CustomTextField usernameInput;
	@FXML
	private CustomPasswordField passwordInput;
	@FXML
	private Button add;
	@FXML
	private Button cancel;
	@FXML
	private Label messageLbl;
	
	@Override
	public void onInitialize() {
		messageLbl.setText("");
		Decorator.removeAllDecorations(messageLbl);
		nameInput.setText("");
		serverInput.setText("");
		usernameInput.setText("");
		passwordInput.setText("");
	}
	
	@FXML
	private void evtAdd(ActionEvent evt) {
		try{
			String name = nameInput.getText();
			String server = serverInput.getText();
			String username = usernameInput.getText();
			String password = passwordInput.getText();
			
			if(StringUtils.isBlank(name) || StringUtils.isBlank(server) || StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
				//show error message 
				messageLbl.setText(resources.getString("all.fields.required"));
				if(Decorator.getDecorations(messageLbl) == null) {
					Decorator.addDecoration(messageLbl,
						new GraphicDecoration(createErrorImageNode()));
				}
				return;
			}
			
			ConnectionService connectionService = context.getBridge().getConnectionService();
			Connection connection = connectionService.getConnectionByName(name);
			if(connection != null) {
				//show error message 
				messageLbl.setText(resources.getString("same.name"));
				if(Decorator.getDecorations(messageLbl) == null) {
					Decorator.addDecoration(messageLbl,
						new GraphicDecoration(createErrorImageNode()));
				}
				return;
			}
			
			
			URI uriObj = Util.getURI(server);
			
			connection = context.getBridge().getConnectionService().createNew(uriObj);
			
			connection.setName(name);
			connection.setUsername(username);
			connection.setPassword(password);
			
			connectionService.save(connection);
			
			closePopUp();
			
		}catch (Exception e) {
			Dock.getInstance().notify(resources.getString("connection.add.failure"), GUICallback.NOTIFY_ERROR);
			log.error("Problem in adding connection.", e);
		}
		
	}
	
	@FXML
	private void evtCancel(ActionEvent evt) throws Exception {
		onInitialize();
		closePopUp();
	}

	private void closePopUp() {
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				getStage().hide();
			}
		});
	}
	
	private Node createErrorImageNode() {
		Image image = new Image(getClass().getResource("error.png")
				.toExternalForm());
		ImageView imageView = new ImageView(image);
		imageView.scaleXProperty().set(0.5);
		imageView.scaleYProperty().set(0.5);
		return imageView;
	}
}
