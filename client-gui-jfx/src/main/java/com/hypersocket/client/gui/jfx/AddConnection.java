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
import com.hypersocket.client.rmi.ConnectionStatus;
import com.hypersocket.client.rmi.GUICallback;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.CheckBox;
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
	private Button edit;
	@FXML
	private Button cancel;
	@FXML
	private Label messageLbl;
	@FXML
	private CheckBox saveCredsCBox;
	@FXML
	private CheckBox conOnstartCBox;
	private Connection currentConnection;
	
	@Override
	public void onInitialize() {
		messageLbl.setText("");
		Decorator.removeAllDecorations(messageLbl);
		nameInput.setText("");
		serverInput.setText("");
		usernameInput.setText("");
		passwordInput.setText("");
		saveCredsCBox.setSelected(false);
		conOnstartCBox.setSelected(false);
		edit.setVisible(false);
		add.setVisible(true);
		
		/*
		 * This is DUMB, but i can't see another way. It stops invisible
		 * components being considered for layout (and so taking up space. You'd
		 * think this might be part of JavaFX, but no ...
		 * 
		 * http://stackoverflow.com/questions/12200195/javafx-hbox-hide-item
		 */
		add.managedProperty().bind(add.visibleProperty());
		edit.managedProperty().bind(edit.visibleProperty());
		cancel.managedProperty().bind(cancel.visibleProperty());
	}
	
	public void setUpEditPage() {
		messageLbl.setText("");
		Decorator.removeAllDecorations(messageLbl);
		nameInput.setText(currentConnection.getName());
		serverInput.setText(Util.getUri(currentConnection));
		usernameInput.setText(currentConnection.getUsername());
		passwordInput.setText(currentConnection.getEncryptedPassword());
		conOnstartCBox.setSelected(currentConnection.isConnectAtStartup());
		saveCredsCBox.setSelected(currentConnection.getId() != null);
		edit.setVisible(true);
		add.setVisible(false);
	}
	
	public Connection getCurrentConnection() {
		return currentConnection;
	}

	public void setCurrentConnection(Connection currentConnection) {
		this.currentConnection = currentConnection;
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
			
			
			URI uriObj = Util.getUri(server);
			
			connection = context.getBridge().getConnectionService().createNew(uriObj);
			
			connection.setName(name);
			connection.setUsername(username);
			connection.setPassword(password);
			
			connection.setConnectAtStartup(conOnstartCBox.isSelected());
			
			if(saveCredsCBox.isSelected()) {
				connectionService.save(connection);
			}
			
			closePopUp();
			
			SignIn.getInstance().addConnection(connection, false);	
		}catch (Exception e) {
			Dock.getInstance().notify(resources.getString("connection.add.failure"), GUICallback.NOTIFY_ERROR);
			log.error("Problem in adding connection.", e);
		}
		
	}
	
	@FXML
	private void evtEdit(ActionEvent evt) {
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
			Connection connection = connectionService.getConnectionByNameWhereIdIsNot(name, currentConnection.getId());
			if(connection != null) {
				//show error message 
				messageLbl.setText(resources.getString("same.name"));
				if(Decorator.getDecorations(messageLbl) == null) {
					Decorator.addDecoration(messageLbl,
						new GraphicDecoration(createErrorImageNode()));
				}
				return;
			}
			
			
			URI uriObj = Util.getUri(server);
			
			connection = context.getBridge().getConnectionService().getConnection(currentConnection.getId());
			context.getBridge().getConnectionService().update(uriObj, connection);
			
			connection.setName(name);
			connection.setUsername(username);
			connection.setPassword(password);
			
			connection.setConnectAtStartup(conOnstartCBox.isSelected());
			
			if(saveCredsCBox.isSelected()) {
				connectionService.save(connection);
			}
			
			int status = context.getBridge().getClientService().getStatus(connection);
			if(ConnectionStatus.CONNECTED == status) {
				context.getBridge().getClientService().disconnect(connection);
				context.getBridge().getClientService().connect(connection);
				SignIn.getInstance().connectionSelected(connection);
			}
			
			closePopUp();
			
			SignIn.getInstance().updateConnectionInList(connection);
			
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
