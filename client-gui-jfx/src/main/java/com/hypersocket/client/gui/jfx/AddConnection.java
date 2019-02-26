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
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
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
	private Label usernameLbl;
	@FXML
	private Label passwordLbl;
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
		saveCredsCBox.setSelected(false);
		conOnstartCBox.setSelected(false);
		edit.setVisible(false);
		add.setVisible(true);

		UIHelpers.bindButtonToItsVisibleManagedProperty(add);
		UIHelpers.bindButtonToItsVisibleManagedProperty(edit);
		UIHelpers.bindButtonToItsVisibleManagedProperty(cancel);

		nameInput.textProperty().addListener((e) -> setAvailable());
		serverInput.textProperty().addListener((e) -> setAvailable());
		usernameInput.textProperty().addListener((e) -> setAvailable());
		passwordInput.textProperty().addListener((e) -> setAvailable());

		setUpDrag();
		setAvailable();
	}

	public void setUpEditPage() {
		messageLbl.setText("");
		Decorator.removeAllDecorations(messageLbl);
		nameInput.setText(currentConnection.getName());
		serverInput.setText(Util.getUri(currentConnection));
		usernameInput.setText(currentConnection.getUsername());
		passwordInput.setText(currentConnection.getEncryptedPassword());
		conOnstartCBox.setSelected(currentConnection.isConnectAtStartup());
		saveCredsCBox.setSelected(StringUtils.isNotBlank(currentConnection.getUsername()) || StringUtils.isNotBlank(currentConnection.getEncryptedPassword()));
		edit.setVisible(true);
		add.setVisible(false);
	}

	public Connection getCurrentConnection() {
		return currentConnection;
	}

	public void setCurrentConnection(Connection currentConnection) {
		this.currentConnection = currentConnection;
	}
	
	private void setAvailable() {
		saveCredsCBox.setSelected(usernameInput.getText().length() > 0 | passwordInput.getText().length() > 0);

		if(saveCredsCBox.isSelected()) {
			usernameLbl.setText(resources.getString("usernameReq"));
			passwordLbl.setText(resources.getString("passwordReq"));
		}
		else {
			usernameLbl.setText(resources.getString("username"));
			passwordLbl.setText(resources.getString("password"));
		}
		
		if (checkEmptyFields(nameInput.getText(), serverInput.getText(), saveCredsCBox.isSelected(), usernameInput.getText(), passwordInput.getText())) {
			edit.setDisable(true);
			add.setDisable(true);
			return;
		}
		else {
			edit.setDisable(false);
			add.setDisable(false);
		}
	}

	@FXML
	private void evtAdd(ActionEvent evt) {
		try {
			String name = nameInput.getText();
			String server = serverInput.getText();
			String username = usernameInput.getText();
			String password = passwordInput.getText();

			if (checkEmptyFields(name, server, saveCredsCBox.isSelected(), username, password)) {
				return;
			}

			ConnectionService connectionService = context.getBridge().getConnectionService();

			if (sameNameCheck(null, (conId) -> {
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

			if (sameHostPortPathCheck(null, (conId) -> {
				try {
					return connectionService.getConnectionByHostPortAndPath(connection.getHostname(),
							connection.getPort(), connection.getPath()) != null;
				} catch (RemoteException e) {
					throw new IllegalStateException(e.getMessage(), e);
				}
			})) {
				return;
			}

			connection.setName(name);

			connection.setConnectAtStartup(conOnstartCBox.isSelected());

			if (saveCredsCBox.isSelected()) {
				connection.setUsername(username);
				connection.setPassword(password);
			}

			context.getBridge().getConnectionService().saveCredentials(connection.getHostname(), username, password);

			Connection connectionSaved = connectionService.save(connection);

			closePopUp();

			SignIn.getInstance().addConnection(connectionSaved, false);
		} catch (Exception e) {
			Dock.getInstance().notify(resources.getString("connection.add.failure"), GUICallback.NOTIFY_ERROR);
			log.error("Problem in adding connection.", e);
		}

	}

	@FXML
	private void evtEdit(ActionEvent evt) {
		try {
			String name = nameInput.getText();
			String server = serverInput.getText();
			String username = usernameInput.getText();
			String password = passwordInput.getText();

			if (checkEmptyFields(name, server, saveCredsCBox.isSelected(), username, password)) {
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

			Connection connectionFromSource = context.getBridge().getConnectionService()
					.getConnection(currentConnection.getId());
			Connection connection = context.getBridge().getConnectionService().update(uriObj, connectionFromSource);

			if (sameHostPortPathCheck(currentConnection.getId(), (conId) -> {
				try {
					return connectionService.getConnectionByHostPortAndPathWhereIdIsNot(connection.getHostname(),
							connection.getPort(), connection.getPath(), conId) != null;
				} catch (RemoteException e) {
					throw new IllegalStateException(e.getMessage(), e);
				}
			})) {
				return;
			}

			connection.setName(name);
			connection.setConnectAtStartup(conOnstartCBox.isSelected());

			if (saveCredsCBox.isSelected()) {
				connection.setUsername(username);
				connection.setPassword(password);
			} else {
				connection.setUsername("");
				connection.setPassword("");
			}

			context.getBridge().getConnectionService().saveCredentials(connection.getHostname(), username, password);

			Connection connectionSaved = connectionService.save(connection);

			int status = context.getBridge().getClientService().getStatus(connectionSaved);
			if (ConnectionStatus.CONNECTED == status) {
				context.getBridge().getClientService().disconnect(connectionSaved);
				context.getBridge().getClientService().connect(connectionSaved);
				SignIn.getInstance().connectionSelected(connectionSaved);
			}

			closePopUp();

			SignIn.getInstance().updateConnectionInList(connectionSaved);

		} catch (Exception e) {
			Dock.getInstance().notify(resources.getString("connection.edit.failure"), GUICallback.NOTIFY_ERROR);
			log.error("Problem in editing connection.", e);
		}
	}

	@FXML
	private void evtCancel(ActionEvent evt) throws Exception {
		onInitialize();
		closePopUp();
	}

	@FXML
	private void evtSaveCred(ActionEvent evt) throws Exception {
		if (!saveCredsCBox.isSelected()) {
			passwordInput.clear();
			usernameInput.clear();
			usernameInput.requestFocus();
		}
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
		Image image = new Image(getClass().getResource("error.png").toExternalForm());
		ImageView imageView = new ImageView(image);
		imageView.scaleXProperty().set(0.5);
		imageView.scaleYProperty().set(0.5);
		return imageView;
	}

	private boolean checkEmptyFields(String name, String server, boolean save, String username, String password) {
		if (StringUtils.isBlank(name) || StringUtils.isBlank(server)
				|| (save && (StringUtils.isBlank(username) || StringUtils.isBlank(password)))) {
			// show error message
			messageLbl.setText(resources.getString("all.fields.required"));
			checkDecoration();
			return true;
		}
		messageLbl.setText("");
		Decorator.removeAllDecorations(messageLbl);
		return false;
	}

	private boolean sameNameCheck(Long conId, Predicate<Long> predicate) {
		if (predicate.test(conId)) {
			// show error message
			messageLbl.setText(resources.getString("same.name"));
			checkDecoration();
			return true;
		}
		return false;
	}

	private boolean sameHostPortPathCheck(Long conId, Predicate<Long> predicate) {
		if (predicate.test(conId)) {
			// show error message
			messageLbl.setText(resources.getString("same.server"));
			checkDecoration();
			return true;
		}
		return false;
	}

	private void checkDecoration() {
		if (Decorator.getDecorations(messageLbl) == null) {
			Decorator.addDecoration(messageLbl, new GraphicDecoration(createErrorImageNode()));
		}
	}

	public void setUpDrag() {
		// allow the clock background to be used to drag the clock around.
		final Delta dragDelta = new Delta();

		addConnectionStackPane.setOnMousePressed(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent mouseEvent) {
				// record a delta distance for the drag and drop operation.
				dragDelta.x = getStage().getX() - mouseEvent.getScreenX();
				dragDelta.y = getStage().getY() - mouseEvent.getScreenY();
				scene.setCursor(Cursor.MOVE);
			}
		});

		addConnectionStackPane.setOnMouseReleased(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent mouseEvent) {
				scene.setCursor(Cursor.HAND);
			}
		});

		addConnectionStackPane.setOnMouseDragged(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent mouseEvent) {
				getStage().setX(mouseEvent.getScreenX() + dragDelta.x);
				getStage().setY(mouseEvent.getScreenY() + dragDelta.y);
			}
		});

		addConnectionStackPane.setOnMouseEntered(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent mouseEvent) {
				if (!mouseEvent.isPrimaryButtonDown()) {
					scene.setCursor(Cursor.HAND);
				}
			}
		});

		addConnectionStackPane.setOnMouseExited(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent mouseEvent) {
				if (!mouseEvent.isPrimaryButtonDown()) {
					scene.setCursor(Cursor.DEFAULT);
				}
			}
		});
	}

	// records relative x and y co-ordinates.
	private class Delta {
		double x, y;
	}
}
