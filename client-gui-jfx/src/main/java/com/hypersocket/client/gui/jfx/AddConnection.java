package com.hypersocket.client.gui.jfx;

import java.net.URI;
import java.rmi.RemoteException;
import java.util.function.Predicate;

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
import com.hypersocket.client.rmi.Util;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;



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
	@FXML
	private StackPane addConnectionStackPane;
	
	private Connection currentConnection;
	
	@Override
	public void onInitialize() {
		messageLbl.setText("");
		Decorator.removeAllDecorations(messageLbl);
		nameInput.setText("");
		serverInput.setText("");
		usernameInput.setText("");
		passwordInput.setText("");
		saveCredsCBox.setSelected(true);
		conOnstartCBox.setSelected(false);
		edit.setVisible(false);
		add.setVisible(true);
		
		UIHelpers.bindButtonToItsVisibleManagedProperty(add);
		UIHelpers.bindButtonToItsVisibleManagedProperty(edit);
		UIHelpers.bindButtonToItsVisibleManagedProperty(cancel);
	}
	
	public void setUpEditPage() {
		messageLbl.setText("");
		Decorator.removeAllDecorations(messageLbl);
		nameInput.setText(currentConnection.getName());
		serverInput.setText(Util.getUri(currentConnection));
		usernameInput.setText(currentConnection.getUsername());
		passwordInput.setText(currentConnection.getEncryptedPassword());
		conOnstartCBox.setSelected(currentConnection.isConnectAtStartup());
		saveCredsCBox.setSelected(true);
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
			
			if(checkEmptyFields(name, server, username)) {
				return;
			}
			
			ConnectionService connectionService = context.getBridge().getConnectionService();
			
			if(sameNameCheck(null, (conId) -> {
				try {
					return connectionService.getConnectionByName(name) != null;
				} catch (RemoteException e) {
					throw new IllegalStateException(e.getMessage(), e);
				}
			})) {
				return;
			}
			
			URI uriObj = Util.getUri(server);
			
			final Connection connection = context.getBridge().getConnectionService().createNew(uriObj);
			
			if(sameHostPortPathCheck(null, (conId) -> {
				try {
					return connectionService.getConnectionByHostPortAndPath(connection.getHostname(), connection.getPort(), connection.getPath()) != null;
				} catch (RemoteException e) {
					throw new IllegalStateException(e.getMessage(), e);
				}
			})) {
				return;
			}
			
			connection.setName(name);
			
			
			connection.setConnectAtStartup(conOnstartCBox.isSelected());
			
			if(saveCredsCBox.isSelected()) {
				connection.setUsername(username);
				connection.setPassword(password);
			} 
			
			context.getBridge().getConnectionService().saveCredentials(connection.getHostname(), username, password);
			
			Connection connectionSaved = connectionService.save(connection);
			
			closePopUp();
			
			SignIn.getInstance().addConnection(connectionSaved, false);	
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
			
			if(checkEmptyFields(name, server, username)) {
				return;
			}
			
			ConnectionService connectionService = context.getBridge().getConnectionService();
			if (sameNameCheck(currentConnection.getId(), (conId) -> {
				try {
					return connectionService.getConnectionByNameWhereIdIsNot(name, currentConnection.getId()) != null;
				} catch (RemoteException e) {
					throw new IllegalStateException(e.getMessage(), e);
				}
			})) {
				return;
			}
					
			URI uriObj = Util.getUri(server);
			
			Connection connectionFromSource = context.getBridge().getConnectionService().getConnection(currentConnection.getId());
			Connection connection = context.getBridge().getConnectionService().update(uriObj, connectionFromSource);
			
			if(sameHostPortPathCheck(currentConnection.getId(), (conId) -> {
				try {
					return connectionService.getConnectionByHostPortAndPathWhereIdIsNot(connection.getHostname(), connection.getPort(),
							connection.getPath(), conId) != null;
				} catch (RemoteException e) {
					throw new IllegalStateException(e.getMessage(), e);
				}
			})) {
				return;
			}
			
			connection.setName(name);
			connection.setConnectAtStartup(conOnstartCBox.isSelected());
			
			if(saveCredsCBox.isSelected()) {
				connection.setUsername(username);
				connection.setPassword(password);
			}
			
			context.getBridge().getConnectionService().saveCredentials(connection.getHostname(), username, password);
			
			Connection connectionSaved = connectionService.save(connection);
			
			int status = context.getBridge().getClientService().getStatus(connectionSaved);
			if(ConnectionStatus.CONNECTED == status) {
				context.getBridge().getClientService().disconnect(connectionSaved);
				context.getBridge().getClientService().connect(connectionSaved);
				SignIn.getInstance().connectionSelected(connectionSaved);
			}
			
			closePopUp();
			
			SignIn.getInstance().updateConnectionInList(connectionSaved);
			
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
	
	private boolean checkEmptyFields(String name, String server, String username) {
		if(StringUtils.isBlank(name) || StringUtils.isBlank(server) 
				|| StringUtils.isBlank(username)) {
			//show error message 
			messageLbl.setText(resources.getString("all.fields.required"));
			checkDecoration();
			return true;
		}
		return false;
	}

	private boolean sameNameCheck(Long conId, Predicate<Long> predicate) {
		if(predicate.test(conId)) {
			//show error message 
			messageLbl.setText(resources.getString("same.name"));
			checkDecoration();
			return true;
		}
		return false;
	}
	
	private boolean sameHostPortPathCheck(Long conId, Predicate<Long> predicate) {
		if(predicate.test(conId)) {
			//show error message 
			messageLbl.setText(resources.getString("same.server"));
			checkDecoration();
			return true;
		}
		return false;
	}

	private void checkDecoration() {
		if(Decorator.getDecorations(messageLbl) == null) {
			Decorator.addDecoration(messageLbl,
				new GraphicDecoration(createErrorImageNode()));
		}
	}
}
