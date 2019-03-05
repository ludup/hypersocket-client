package com.hypersocket.client.gui.jfx;

import java.io.IOException;
import java.net.URL;
import java.rmi.RemoteException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.Semaphore;

import org.apache.commons.lang3.StringUtils;
import org.controlsfx.control.textfield.CustomPasswordField;
import org.controlsfx.control.textfield.CustomTextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.client.Option;
import com.hypersocket.client.Prompt;
import com.hypersocket.client.gui.jfx.Bridge.Listener;
import com.hypersocket.client.gui.jfx.Dock.DockOnEventDo;
import com.hypersocket.client.gui.jfx.Popup.PositionType;
import com.hypersocket.client.rmi.BrowserLauncher;
import com.hypersocket.client.rmi.Connection;
import com.hypersocket.client.rmi.ConnectionStatus;
import com.hypersocket.client.rmi.GUICallback;
import com.hypersocket.client.rmi.Util;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Controller for the "Sign In" window, where connections are managed and
 * credentials prompted for.
 */
public class SignIn extends AbstractController implements Listener {
	
	static Logger log = LoggerFactory.getLogger(SignIn.class);
	
	private static SignIn instance;
	
	public SignIn() {
		instance = this;
	}
	
	public static SignIn getInstance() {
		return instance;
	}
	
	private enum ButtonNature {
		CONNECT, EDIT, REVEAL, DISCONNECT, DELETE
	}
	
	@FXML
	private BorderPane credentialsUI;
	@FXML
	private BorderPane promptUI;
	@FXML
	private Button login;
	@FXML
	private ProgressIndicator spinner;
	@FXML
	private HBox progressUI;
	@FXML
	private VBox container;
	@FXML
	private VBox root;
	@FXML
	private Label messageText;
	@FXML
	private Label messageIcon;
	@FXML
	protected VBox connections;
	@FXML
	protected Label connectionsMessage;
	@FXML
	private Button addConnection;
	@FXML
	private StackPane connectionsStackPane;
	
	private Connection foregroundConnection;
	private Semaphore promptSemaphore = new Semaphore(1);
	private boolean abortPrompt;
	private boolean promptsAvailable;
//	private String promptedUsername;
//	private char[] promptedPassword;
	private Map<Prompt, Control> promptNodes = new LinkedHashMap<>();
	private final Map<String, String> promptValues = new LinkedHashMap<>();
	private List<Connection> disconnecting = new ArrayList<>();
	private List<Connection> connecting = new ArrayList<>();
	private List<Connection> waitingForUpdatesOrResources = new ArrayList<>();
	private boolean deleteOnDisconnect;
	private Popup addConnectionPopUp;
	private AddConnection addConnectionContent;
	private Set<Long> savedConnectionsIdCache = new HashSet<>();

	private Connection currentConnection;
	
	/*
	 * Class methods
	 */

	/*
	 * The following are all events from the {@link Bridge}, and will come in on
	 * the RMI thread.
	 */
	@Override
	public void disconnecting(Connection connection) {
		abortPrompts(connection);
		super.disconnecting(connection);
	}
	
	@Override
	public void disconnected(final Connection connection, Exception e) {
		super.disconnected(connection, e);
		Platform.runLater(() -> {
			log.info("Disconnected " + connection + " (delete "
					+ deleteOnDisconnect + ")");
			if (disconnecting.contains(connection)) {
				disconnecting.remove(connection);
				setAvailable(connection);
				sizeAndPosition();
			}
			if (Objects.equals(connection, foregroundConnection)) {
				log.info("Clearing foreground connection");
				foregroundConnection = null;
			}
			
			if (deleteOnDisconnect) {
				try {
					doDelete(connection);
					initUi(connection);
				} catch (RemoteException e1) {
					log.error("Failed to delete.", e);
				}
			} else {
				setAvailable(connection);
			}
			
			DockOnEventDo.refreshResourcesFavouriteLists();
		});
	}

	@Override
	public void started(final Connection connection) {
		Platform.runLater(() -> {
			log.info("Started " + connection);
			waitingForUpdatesOrResources.remove(connection);
			initUi(connection);
		});
	}

	@Override
	public void finishedConnecting(final Connection connection, Exception e) {
		Platform.runLater(() -> {
			log.info("Finished connecting "
					+ connection
					+ ". "
					+ (e == null ? "No error" : "Error occured."
							+ e.getMessage()) + " Foreground is "
					+ foregroundConnection);

			if (e != null) {
				waitingForUpdatesOrResources.remove(connection);
			}

			connecting.remove(connection);

			if (Objects.equals(connection, foregroundConnection)) {
				foregroundConnection = null;
			}
			
			setAvailable(connection);
			if (e != null) {
				abortPrompts(connection);
				log.error("Failed to connect.", e);
				Dock.getInstance().notify(e.getMessage(),
						GUICallback.NOTIFY_ERROR);
			}
			
			DockOnEventDo.refreshResourcesFavouriteLists();
		});
		super.finishedConnecting(connection, e);
	}

	@Override
	public Map<String, String> showPrompts(final Connection connection, final ResourceBundle promptResources, List<Prompt> prompts, int attempts,
			boolean success) {

		try {
			currentConnection = connection;
			abortPrompt = false;
			promptSemaphore.acquire();
			Platform.runLater(new Runnable() {

				@Override
				public void run() {
					clearCredentials();
					VBox vbox = new VBox();
					int idx = 0;
					for (Prompt p : prompts) {
						switch (p.getType()) {
							case TEXT:
								addLabel(vbox, p);
								CustomTextField txt = new CustomTextField();
								txt.setText(p.getDefaultValue());
								if (!success) {
									txt.setLeft(createErrorImageNode());
								}
								vbox.getChildren().add(txt);
								txt.getStyleClass().add("input");
								addToolTip(p, txt);
								txt.setOnAction((event) -> {
									focusNextPrompt(txt);
								});
								promptNodes.put(p, txt);
								break;
							case HIDDEN:
								promptValues.put(p.getResourceKey(),
										p.getDefaultValue());
								break;
							case PASSWORD:
								addLabel(vbox, p);
								CustomPasswordField pw = new CustomPasswordField();
								if (!success) {
									pw.setLeft(createErrorImageNode());
								}
								vbox.getChildren().add(pw);
								pw.getStyleClass().add("input");
								addToolTip(p, pw);
								promptNodes.put(p, pw);
								pw.setOnAction((event) -> {
									focusNextPrompt(pw);
								});
								break;
							case SELECT:
								addLabel(vbox, p);
								ComboBox<String> cb = new ComboBox<>();
								for (Option o : p.getOptions()) {
									cb.itemsProperty().get().add(o.getName());
								}
								cb.getStyleClass().add("input");
								addToolTip(p, cb);
								vbox.getChildren().add(cb);
								promptNodes.put(p, cb);
								if (idx == 0) {
									cb.requestFocus();
								}
								break;
							case A:
								addLabel(vbox, p);
								String aLabel = getValueText(promptResources, p);
								Hyperlink h = new Hyperlink(aLabel);
								h.onActionProperty().set((val) -> {
									String urlStr = p.getDefaultValue();
									try {
										new URL(urlStr);
										new BrowserLauncher(urlStr).launch();
									}
									catch(Exception e) {
										new BrowserLauncher(Util.getUri(connection) + "/" + urlStr).launch();
									}
								});
								h.getStyleClass().add("input");
								vbox.getChildren().add(h);
								promptNodes.put(p, h);
								break;
							case P:
								Label l = new Label(getValueText(promptResources, p));
								l.setWrapText(true);
								vbox.getChildren().add(l);
								break;

							case CHECKBOX:
								CheckBox checkBox = new CheckBox();
								String label = getLabelText(promptResources, p);
								checkBox.setText(label);
								checkBox.setSelected(Boolean.parseBoolean(p.getDefaultValue()));
								addToolTip(p, checkBox);
								vbox.getChildren().add(checkBox);
								promptNodes.put(p, checkBox);
								break;

						}
						idx++;
					}
					
					credentialsUI.setCenter(vbox);
					promptsAvailable = true;
					setAvailable(connection);

					Platform.runLater(new Runnable() {

						@Override
						public void run() {
							focusNextPrompt(null);
						}
					});
				}

				private void addToolTip(Prompt p, Control control) {
					if(StringUtils.isNotEmpty(p.getInfoKey())) {
                        String toolTip = getI18NText(promptResources, p.getInfoKey(), "");
                        if(StringUtils.isNotEmpty(toolTip)) {
							control.setTooltip(new Tooltip(toolTip));
                        }
                    }
				}

				private void addLabel(VBox vbox, Prompt p) {
					Label l = new Label(getLabelText(promptResources, p));
					vbox.getChildren().add(l);
				}
			});
			promptSemaphore.acquire();
			promptSemaphore.release();
			promptsAvailable = false;
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			currentConnection = null;
		}
		if (abortPrompt) {
			log.info("Returning nothing from prompt, was aborted.");
			return null;
		} else {
//			if (promptValues.containsKey("username"))
//				promptedUsername = promptValues.get("username");
//			if (promptValues.containsKey("password"))
//				promptedPassword = promptValues.get("password").toCharArray();

			return promptValues;
		}
	}

	private String getLabelText(final ResourceBundle promptResources, Prompt p) {
		return getI18NText(promptResources, p.getResourceKey() + ".label", "");
	}

	private String getValueText(final ResourceBundle promptResources, Prompt p) {
		return getI18NText(promptResources, p.getResourceKey(), p.getDefaultValue());
	}
	
	private String getI18NText(final ResourceBundle promptResources, String key, String defaultStr) {
		String aLabel;
		try {
			aLabel = promptResources.getString(key);
		}
		catch(Exception e) {
			try {
				aLabel = resources.getString(key);
			} catch (Exception e2) {
				aLabel = defaultStr;
			}
		}
		return aLabel;
	}

	@Override
	public void bridgeEstablished() {
		Platform.runLater(() -> {
			abortPrompts(null);
			initUi(null);
		});
	}

	@Override
	public void bridgeLost() {
		Platform.runLater(() -> {
			waitingForUpdatesOrResources.clear();
			connecting.clear();
			abortPrompts(null);
			initUi(null);
		});
	}

	// Overrides

	
	@Override
	protected void onInitialize() {
		installTooltip(addConnection, new Tooltip(resources.getString("addConnection.icon.tooltip")));
	}
	
	@Override
	protected void onConfigure() {
		super.onConfigure();
		initUi(null);

		
		promptUI.managedProperty().bind(promptUI.visibleProperty());
		progressUI.managedProperty().bind(progressUI.visibleProperty());
		
		setAvailable(null);

	}

	// Private methods

	private void focusNextPrompt(Control c) {
		
		boolean found = c == null;
		for (Map.Entry<Prompt, Control> en : promptNodes.entrySet()) {
			if (!found && Objects.equals(en.getValue(), c)) {
				found = true;
			} else if (found) {
				log.info("Will now focus " + en.getValue());
				en.getValue().requestFocus();
				return;
			}
		}

		// Get to the end? treat this as submit of form
		log.info("Action on last prompt, submitting");
		evtLogin(null);
	}

	public void connectionSelected(Connection selectedConnection) {
		hidePopOver();
		abortPrompts(selectedConnection);
		// A connection with the URI already exists, is it our foreground
		// connection?

		log.info(String.format("Selected connection exists for %s (%s:%d)",
				selectedConnection.getHostname(), selectedConnection.getHostname(),
				selectedConnection.getPort()));

		if (selectedConnection.equals(foregroundConnection)) {
			try {
				if (context.getBridge().getClientService()
						.isConnected(foregroundConnection)) {

					// Already connected, don't do anything

					foregroundConnection = selectedConnection;
					log.info("Already connected, won't try to connect.");
					return;
				}
			} catch (Exception e) {
				log.warn("Failed to test if already connected.", e);
			}
		} else {

			// The foreground connection is this new connection

			foregroundConnection = selectedConnection;
		}

		setUserDetails(selectedConnection);
		setAvailable(selectedConnection);
	}

	private void abortPrompts(Connection connection) {
		// If prompts are waiting, cancel them
		if (!promptSemaphore.tryAcquire()) {
			// Prompts are waiting
			abortPrompt = true;
		}
		// Will release this acquire if successful, or the waiting one
		// if not
		promptSemaphore.release();

		// Update UI
		Runnable r = new Runnable() {
			@Override
			public void run() {
				clearCredentials();
				setAvailable(connection);
			}
		};
		if (Platform.isFxApplicationThread())
			r.run();
		else
			Platform.runLater(r);
	}

	private void clearCredentials() {
		credentialsUI.getChildren().clear();
		promptNodes.clear();
		promptValues.clear();
	}

	private void initUi(Connection connection) {
		try {
			log.info("Rebuilding URIs");

			if (context.getBridge().isConnected()) {
				try {
					List<ConnectionStatus> connections = context.getBridge()
							.getClientService().getStatus();

					// Look for new connections
					for (ConnectionStatus c : connections) {
						Connection conx = c.getConnection();
						setAvailable(conx);
					}
				} catch (Exception e) {
					log.error("Failed to load connections.", e);
				}
			}
		} finally {
		}
	}

	private boolean confirmDelete(Connection sel) {
		popup.setDismiss(false);
		try {
			Alert alert = new Alert(AlertType.CONFIRMATION);
			alert.setTitle(resources.getString("delete.confirm.title"));
			alert.setHeaderText(resources.getString("delete.confirm.header"));
			alert.setContentText(MessageFormat.format(
					resources.getString("delete.confirm.content"), Util.getUri(sel)));
			Optional<ButtonType> result = alert.showAndWait();
			if (result.get() == ButtonType.OK) {
				try {
					if (sel.equals(foregroundConnection)) {
						abortPrompts(sel);
					}

					if (sel.equals(foregroundConnection)) {
						foregroundConnection = null;
					}
					Util.getUri(sel);
					if (context.getBridge().getClientService().isConnected(sel)) {
						log.info("Disconnecting deleted connection.");
						deleteOnDisconnect = true;
						doDisconnect(sel);
					} else {
						doDelete(sel);
						initUi(sel);
					}
				} catch (Exception e) {
					log.error("Failed to delete connection.", e);
				}
				return true;
			} else {
				return false;
			}
		} finally {
			popup.setDismiss(true);
		}
	}

	private void doDelete(Connection sel) throws RemoteException {
		log.info(String.format("Deleting connection %s", sel));
		context.getBridge().getConnectionService().delete(sel);
		context.getBridge().getConnectionService().removeCredentials(sel.getHostname());
		try {
			removeBorderPaneFromConnectionList(sel);
			DockOnEventDo.refreshResourcesFavouriteLists();
			setAvailable(sel);
		} finally {
			log.info("Connection deleted");
		}
	}

	private void doDisconnect(Connection sel) {

		if (disconnecting.contains(sel)) {
			throw new IllegalStateException("Already disconnecting " + sel);
		}
		disconnecting.add(sel);

		setAvailable(sel);
		new Thread() {
			@Override
			public void run() {
				try {
					context.getBridge().disconnect(sel);
				} catch (Exception e) {
					log.error("Failed to disconnect.", e);
				}
			}
		}.start();
	}

	private void sizeAndPosition() {
		Stage stage = getStage();
		if (stage != null) {
			stage.sizeToScene();
			adjustPopUISize();
		}
	}

	private void setUserDetails(Connection connection) {

		// These will be collected during prompts and maybe saved
//		promptedUsername = null;
//		promptedPassword = null;

		int status;
		try {
			status = context.getBridge().getClientService()
					.getStatus(connection);
		} catch (RemoteException e1) {
			status = ConnectionStatus.DISCONNECTED;
		}
		if (status == ConnectionStatus.DISCONNECTED) {
			connecting.add(connection);
			waitingForUpdatesOrResources.add(connection);
			setAvailable(connection);
			new Thread() {
				@Override
				public void run() {
					try {
						context.getBridge().connect(connection);
						log.info(String.format("Connected to %s",
								Util.getUri(connection)));
					} catch (Exception e) {
						foregroundConnection = null;
						log.error("Failed to connect.", e);
						Platform.runLater(new Runnable() {
							@Override
							public void run() {
								Dock.getInstance().notify(e.getMessage(),
										GUICallback.NOTIFY_ERROR);
							}
						});
					} finally {
						Platform.runLater(new Runnable() {
							@Override
							public void run() {
								setAvailable(connection);
							}
						});
					}
				}
			}.start();
		} else {
			log.warn("Request to connect an already connected or connecting connection "
					+ connection);
		}
	}

	private void setAvailable(final Connection sel) {

		Runnable runnable = new Runnable() {
			@Override
			public void run() {
				if (context.getBridge().isConnected()) {
					List<Connection> connections = getConnections();
					Long selectedId = sel == null ? 0 : sel.getId();
					for (Connection connection : connections) {
						if(savedConnectionsIdCache.contains(connection.getId())) {
							continue;
						}
						addConnection(connection, selectedId.equals(connection.getId()));
					}
					
					connectionsMessage.setVisible(connections.isEmpty());
					
					boolean showSpinner = (!waitingForUpdatesOrResources.isEmpty()
							|| !connecting.isEmpty() || !disconnecting.isEmpty()) && !promptsAvailable;;
					boolean selectionConnected = false;
					try {
						selectionConnected = sel != null
								&& context.getBridge().getClientService()
										.isConnected(sel);
					} catch (Exception e) {
						log.warn("Failed to test if connected. Assuming not.",
								e);
					}
				
					if(sel != null) {
						boolean busy =	waitingForUpdatesOrResources.contains(sel) && !promptsAvailable;
							
						Long conId = getConnectionId(sel);
						
						Button connect = (Button) getButton(ButtonNature.CONNECT, conId);
						Button disConnect = (Button) getButton(ButtonNature.DISCONNECT, conId);
						Button delete = (Button) getButton(ButtonNature.DELETE, conId);
						Button edit = (Button) getButton(ButtonNature.EDIT, conId);
						ToggleButton reveal = (ToggleButton) getButton(ButtonNature.REVEAL, conId); 
						
						delete.setVisible(sel != null && !selectionConnected
								&& sel.getId() != null);
						delete.setDisable(disconnecting.contains(sel) || busy);
						
						edit.setVisible(sel != null && !selectionConnected
								&& sel.getId() != null);
						edit.setDisable(connecting.contains(sel) || disconnecting.contains(sel) || busy);
						
						reveal.setVisible(selectionConnected);
						reveal.setDisable(connecting.contains(sel) || disconnecting.contains(sel) || busy);
						
						connect.setVisible(!selectionConnected);
						connect.setDisable(busy);
						
						disConnect.setVisible(selectionConnected);
					}
					
					promptUI.setVisible(disconnecting.isEmpty() && promptsAvailable);
					progressUI.setVisible(showSpinner);
					login.setDisable(selectionConnected);

				} else {
					progressUI.setVisible(false);
					promptUI.setVisible(false);
					login.setDisable(true);
				}
				rebuildContainer();
				sizeAndPosition();
			}
		};
		if (Platform.isFxApplicationThread())
			runnable.run();
		else
			Platform.runLater(runnable);

	}

	private void rebuildContainer() {
		container.getChildren().clear();
		if (credentialsUI.isVisible()) {
			container.getChildren().add(credentialsUI);
		}
		/*if (progressUI.isVisible()) {
			container.getChildren().add(progressUI);
		}*/
		if (promptUI.isVisible()) {
			container.getChildren().add(promptUI);
		}
	}

	private Node createErrorImageNode() {
		Image image = new Image(getClass().getResource("error.png")
				.toExternalForm());
		ImageView imageView = new ImageView(image);
		imageView.scaleXProperty().set(0.5);
		imageView.scaleYProperty().set(0.5);
		return imageView;
	}

	/*
	 * The following are all events from UI
	 */

	@SuppressWarnings("unchecked")
	@FXML
	private void evtLogin(ActionEvent evt) {
		try {
			for (Map.Entry<Prompt, Control> en : promptNodes.entrySet()) {
				if (en.getValue() instanceof TextField) {
					promptValues.put(en.getKey().getResourceKey(),
							((TextField) en.getValue()).getText());
				} else if (en.getValue() instanceof PasswordField) {
					promptValues.put(en.getKey().getResourceKey(),
							((PasswordField) en.getValue()).getText());
				} else if (en.getValue() instanceof ComboBox) {
					promptValues.put(en.getKey().getResourceKey(),
							((ComboBox<String>) en.getValue()).getValue());
				} else if (en.getValue() instanceof CheckBox) {
					promptValues.put(en.getKey().getResourceKey(),
							Boolean.toString(((CheckBox) en.getValue()).isSelected()));
				}
			}
			if(currentConnection != null)
				promptValues.put("saveCredentials", String.valueOf(Configuration.getDefault().isSaveCredentials(currentConnection)));
			if (log.isDebugEnabled()) {
				log.debug("Sending prompt values ..");
				for (Map.Entry<String, String> en : promptValues.entrySet()) {
					log.debug(en.getKey() + " = " + en.getValue());
				}
			}
			credentialsUI.getChildren().clear();
			promptsAvailable = false;
			promptSemaphore.release();
			adjustPopUISize();
		} catch (Exception e) {
			log.error("Failed to login.", e);
		}
	}

	@FXML
	private void evtHideTooltipPopover(MouseEvent evt) {
		hidePopOver();
	}
	
	@FXML
	private void evtAddConnection(ActionEvent evt) throws IOException {
		setUpAddConnectionPopUp();
		addConnectionContent.setCurrentConnection(null);
		addConnectionPopUp.popup();
		
	}
	
	public void addConnection(Connection connection, boolean isConnected) {
		
		savedConnectionsIdCache.add(connection.getId());

		Long extractedConnectionId = getConnectionId(connection);
		
		BorderPane connectionBorderPane = new BorderPane();
		connectionBorderPane.setId(String.format("connection_border_pane_%d",  extractedConnectionId));
		connectionBorderPane.setPadding(new Insets(1, 0, 1, 0));
		
		Label nameLabel = new Label();
		nameLabel.setId(String.format("label_%d", extractedConnectionId));
		nameLabel.setText(StringUtils.isBlank(connection.getName()) ? resources.getString("connection.default") : connection.getName()) ;
		nameLabel.getStyleClass().add("info-lg");
		nameLabel.setWrapText(true);
		nameLabel.setPrefWidth(120);
		connectionBorderPane.setLeft(nameLabel);
		
		HBox connectionButtonBox = new HBox();
		connectionButtonBox.setId(String.format("connection_button_box_%d", extractedConnectionId));
		connectionButtonBox.setSpacing(5);
		connectionBorderPane.setRight(connectionButtonBox);
		
		final Button connect = new Button();
		connect.setId(String.format("connect_%d", extractedConnectionId));
		connect.getStyleClass().add("connection-button");
		connect.setText(resources.getString("connect.icon"));
		installTooltip(connect, new Tooltip(resources.getString("connect.tooltip")));
		connect.setOnAction(evt -> {try {
			evtConnect(evt);
		} catch (Exception e) {
			throw new IllegalStateException(e.getMessage(), e);
		}});
		connect.setVisible(false);
		connect.setUserData(extractedConnectionId);
		connectionButtonBox.getChildren().add(connect);
		
		
		
		Button disConnect = new Button();
		disConnect.setId(String.format("disConnect_%d", extractedConnectionId));
		disConnect.getStyleClass().add("connection-button");
		disConnect.setText(resources.getString("disconnect.icon"));
		installTooltip(disConnect, new Tooltip(resources.getString("disconnect.tooltip")));
		disConnect.setOnAction(evt -> {try {
			evtDisconnect(evt);
		} catch (Exception e) {
			throw new IllegalStateException(e.getMessage(), e);
		}});
		disConnect.setVisible(false);
		disConnect.setUserData(extractedConnectionId);
		connectionButtonBox.getChildren().add(disConnect);
		
		if(isConnected) {
			disConnect.setVisible(true);
		} else {
			connect.setVisible(true);
		}
		
		Button edit = new Button();
		edit.setId(String.format("edit_%d", extractedConnectionId));
		edit.getStyleClass().add("connection-button");
		edit.setText(resources.getString("edit.icon"));
		installTooltip(edit, new Tooltip(resources.getString("edit.tooltip")));
		edit.setOnAction(evt -> {try {
			evtEdit(evt);
		} catch (Exception e) {
			throw new IllegalStateException(e.getMessage(), e);
		}});
		edit.setUserData(extractedConnectionId);
		connectionButtonBox.getChildren().add(edit);
		
		ToggleButton reveal = new ToggleButton();
		reveal.setId(String.format("reveal_%d", extractedConnectionId));
		reveal.getStyleClass().add("connection-button");
		reveal.setText(resources.getString("reveal.icon"));
		installTooltip(reveal, new Tooltip(resources.getString("reveal.tooltip")));
		reveal.setOnAction(evt -> {try {
			evtReveal(evt);
		} catch (Exception e) {
			throw new IllegalStateException(e.getMessage(), e);
		}});
		reveal.setVisible(false);
		reveal.setUserData(extractedConnectionId);
		connectionButtonBox.getChildren().add(reveal);
		
		Button delete = new Button();
		delete.setId(String.format("delete_%d", extractedConnectionId));
		delete.getStyleClass().add("connection-button");
		delete.setText(resources.getString("delete.icon"));
		installTooltip(delete, new Tooltip(resources.getString("delete.tooltip")));
		delete.setOnAction(evt -> {try {
			evtDelete(evt);
		} catch (Exception e) {
			throw new IllegalStateException(e.getMessage(), e);
		}});
		delete.setUserData(extractedConnectionId);
		connectionButtonBox.getChildren().add(delete);
		
		//adding empty element to fill space in center, else left and right sides get very close to each other
		HBox center = new HBox();
		center.setPrefWidth(70);
		connectionBorderPane.setCenter(center);
		
		UIHelpers.bindButtonToItsVisibleManagedProperty(disConnect);
		UIHelpers.bindButtonToItsVisibleManagedProperty(connect);
		UIHelpers.bindButtonToItsVisibleManagedProperty(edit);
		UIHelpers.bindButtonToItsVisibleManagedProperty(reveal);
		UIHelpers.bindButtonToItsVisibleManagedProperty(delete);
		
		connections.getChildren().add(connectionBorderPane);
		
		//remove no connections message
		connectionsMessage.setVisible(false);
	}
	
	public void updateConnectionInList(Connection con) {
		Long conId = getConnectionId(con);
		Label nameLabel = getConnectionLabel(conId);
		nameLabel.setText(StringUtils.isBlank(con.getName()) ? resources.getString("connection.default") : con.getName());
	}
	
	public void refreshConnectionList() {
		savedConnectionsIdCache.clear();
		connections.getChildren().clear();
		setAvailable(null);
	}
	
	public AddConnection getAddConnectionContent() {
		return addConnectionContent;
	}
	
	private void installTooltip(ButtonBase button, Tooltip tooltip) {
		button.setOnMouseEntered(evt -> {
			tooltip.show(button, evt.getScreenX() + 10, evt.getScreenY() + 10);
		});
		button.setOnMouseExited(evt -> {
			tooltip.hide();
		});
	}

	private List<Connection> getConnections() {
		try {
			return context.getBridge().getConnectionService().getConnections();
		} catch (RemoteException e) {
			throw new IllegalStateException(e.getMessage(), e);
		}
	}
	
	private ButtonBase getButton(ButtonNature buttonType, Long id) {
		String tag = null;
		if(ButtonNature.CONNECT.equals(buttonType)) {
			tag = String.format("#connect_%d", id);
		} else if (ButtonNature.DISCONNECT.equals(buttonType)) {
			tag = String.format("#disConnect_%d", id);
		} else if(ButtonNature.DELETE.equals(buttonType)) {
			tag = String.format("#delete_%d", id);
		} else if(ButtonNature.EDIT.equals(buttonType)) {
			tag = String.format("#edit_%d", id);
		} else if (ButtonNature.REVEAL.equals(buttonType)) {
			tag = String.format("#reveal_%d", id);
		}
		return (ButtonBase) connections.lookup(tag);
	}
	
	private BorderPane getConnectionBorderPane(Long id) {
		return (BorderPane) connections.lookup(String.format("#connection_border_pane_%d", id));
	}
	
	private Label getConnectionLabel(Long id) {
		return (Label) connections.lookup(String.format("#label_%d", id));
	}
	
	private void removeBorderPaneFromConnectionList(Connection con) {
		Long conId = getConnectionId(con);
		BorderPane borderPane = getConnectionBorderPane(conId);
		connections.getChildren().remove(borderPane);
	}
	
	private Connection getConnectionFromButton(Button button) {
		Long id = (Long) button.getUserData();
		if(id == null) {
			return null;
		}
		
		try {
			return context.getBridge().getConnectionService().getConnection(id);
		} catch (RemoteException e) {
			log.error("Problem in getting connection refrence from service.", e);
			return null;
		}
	}
	
	private Long getConnectionId(Connection con) {
		if(con.getId() == null) {
			throw new IllegalStateException("con id cannot be null");
		}
		return con.getId();
	}
	
	private void evtConnect(ActionEvent evt) throws Exception {
		Long id = (Long) ((Button) evt.getSource()).getUserData();
		if(id != null) {
			Connection connection = context.getBridge().getConnectionService().getConnection(id);
			connectionSelected(connection);
		}
	}

	private void evtDelete(ActionEvent evt) throws Exception {
		Connection sel = getConnectionFromButton((Button) evt.getSource());
		if (sel != null && sel.getId() != null) {
			confirmDelete(sel);
			adjustPopUISize();
		}
	}
	
	private void evtReveal(ActionEvent evt) throws Exception {
		ToggleButton toggleButton = (ToggleButton) evt.getSource();
		Long connectionId = (Long) toggleButton.getUserData();
		if(connectionId == null) {
			throw new IllegalStateException("Could not find connection id from button.");
		}
		
		boolean hide = toggleButton.isSelected();
		if(hide) {
			toggleButton.setText(resources.getString("reveal.hide.icon"));
			Dock.getInstance().toggleHideResources(connectionId);
		} else {
			toggleButton.setText(resources.getString("reveal.icon"));
			Dock.getInstance().toggleShowResources(connectionId);
		}
	}

	private void evtDisconnect(ActionEvent evt) throws Exception {
		Connection sel = getConnectionFromButton((Button) evt.getSource());
		if (sel != null) {
			doDisconnect(sel);
		}
	}
	
	private void evtEdit(ActionEvent evt) throws Exception {
		Connection sel = getConnectionFromButton((Button) evt.getSource());
		setUpAddConnectionPopUp();
		addConnectionContent.setCurrentConnection(sel);
		addConnectionPopUp.popup();
	}

	private void setUpAddConnectionPopUp() throws IOException {
		Window parent = Dock.getInstance().getScene().getWindow();
		if (addConnectionPopUp == null) {
			addConnectionContent = (AddConnection) context.openScene(AddConnection.class);
			addConnectionPopUp = new Popup(parent, addConnectionContent.getScene(), false, PositionType.CENTER) {
				@Override
				public void popup() {
					if(addConnectionContent.getCurrentConnection() != null) {
						addConnectionContent.setUpEditPage();
					} else {
						addConnectionContent.onInitialize();
					}
					super.popup();
				}
			};
			
			addConnectionContent.setPopup(popup);
		}
	}
	
	private void adjustPopUISize() {
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				popup.sizeToScene();
			}
		});
	}
	
}
