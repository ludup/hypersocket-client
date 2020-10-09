package com.hypersocket.client.gui.jfx;

import java.io.IOException;
import java.rmi.RemoteException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;
import org.controlsfx.control.action.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.client.ServiceResource;
import com.hypersocket.client.gui.jfx.Bridge.Listener;
import com.hypersocket.client.gui.jfx.Flinger.Direction;
import com.hypersocket.client.gui.jfx.Popup.PositionType;
import com.hypersocket.client.rmi.Connection;
import com.hypersocket.client.rmi.ConnectionStatus;
import com.hypersocket.client.rmi.FavouriteItem;
import com.hypersocket.client.rmi.FavouriteItemService;
import com.hypersocket.client.rmi.GUICallback;
import com.hypersocket.client.rmi.GUICallback.ResourceUpdateType;
import com.hypersocket.client.rmi.Resource;
import com.hypersocket.client.rmi.Resource.Type;
import com.hypersocket.client.rmi.ResourceRealm;
import com.hypersocket.client.rmi.ResourceService;
import com.sshtools.twoslices.Toast;
import com.sshtools.twoslices.ToastType;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.effect.Glow;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;

public class Dock extends AbstractController implements Listener {

	public enum Mode {
		LAUNCHERS, UPDATE, IDLE
	}

	/**
	 * How wide (or high when vertical mode is supported) will the 'tab' be, i.e.
	 * the area where the user hovers over to dock to reveal it
	 */
	static final int AUTOHIDE_TAB_SIZE = 80;

	/*
	 * How height (or wide when vertical mode is supported) will the 'tab' be, i.e.
	 * the area where the user hovers over to dock to reveal it
	 */
	static final int AUTOHIDE_TAB_OPPOSITE_SIZE = 18;

	/* How long the autohide should take to complete (in MS) */

	static final int AUTOHIDE_DURATION = 125;

	/*
	 * How long after the mouse leaves the dock area, will the dock be hidden (in
	 * MS)
	 */
	static final int AUTOHIDE_HIDE_TIME = 2000;

	/*
	 * The initial amount of time after startup before the dock is hidden (in MS)
	 * when autohide is ON.
	 */
	static final int INITIAL_AUTOHIDE_HIDE_TIME = 10000;

	/*
	 * How long (in MS) to keep the dock open after a launch. This prevents autohide
	 * when focus is lost for a number of milliseconds. If focus is regained, the
	 * timer is cleared.
	 */
	static final int LAUNCH_WAIT = 2000;

	/*
	 * How long to keep popup messages visible (in MS)
	 */
	static final double MESSAGE_FADE_TIME = 10000;

	/**
	 * How long (in MS) the mouse must hover over the reveal tab before the dock
	 * will be revealed. If the mouse exits the tab before this time, the reveal
	 * will be cancelled. If the mouse is clicked before leaving the tab, the dock
	 * will be immediatey revealed.
	 */
	private static final double REVEAL_HOVER_TIME = 500;

	static Logger log = LoggerFactory.getLogger(Dock.class);

	private Popup signInPopup;
	private Popup optionsPopup;
	Popup resourceGroupPopup;

	@FXML
	private Button slideLeft;
	@FXML
	private Button slideRight;
	@FXML
	private Button signIn;
	@FXML
	private Button options;
	@FXML
	private Button status;
	@FXML
	private Pane shortcuts;
	@FXML
	private Flinger flinger;
	@FXML
	private Button fileResources;
	@FXML
	private Button browserResources;
	@FXML
	private Button networkResources;
	@FXML
	private Button ssoResources;
	@FXML
	private BorderPane dockContent;
	@FXML
	private StackPane dockStack;
	@FXML
	private Label pull;
	@FXML
	private Button exit;

	private SignIn signInScene;
	private Timeline dockHider;
	private boolean hidden;
	private FadeTransition busyFade;
	private Timeline dockHiderTrigger;
	private Timeline launchWait;
	private Timeline dockRevealer;
	private long yEnd;
	private boolean hiding;
	private ContextMenu contextMenu;
	private Configuration cfg;
	private static Dock instance;
	private Map<ResourceGroupKey, ResourceGroupList> icons = new TreeMap<>();
	private List<ServiceResource> serviceResources = new ArrayList<>();
	// private ResourceGroup resourceGroup;
	private Popup statusPopup;
	private Popup ssoResourcesPopup;
	private Popup browserResourcesPopup;
	private Popup fileResourcesPopup;
	private Popup networkResourcesPopup;
	private Status statusContent;
	private SsoResources ssoResourcesContent;
	private BrowserResources browserResourcesContent;
	private FileResources fileResourcesContent;
	private NetworkResources networkResourcesContent;

	private ChangeListener<Number> sizeChangeListener;
	private ChangeListener<Color> colorChangeListener;
	private ChangeListener<Boolean> borderChangeListener;
	private AbstractController optionsScene;
	private Mode mode = Mode.LAUNCHERS;
	private int appsToUpdate;
	private Update updateScene;
	private ResourceGroup resourceGroup;
	private Set<Long> toggleResourcesForConnection = new HashSet<>();

	public Dock() {
		instance = this;
	}

	public static Dock getInstance() {
		return instance;
	}

	/*
	 * Class methods
	 */

	public void notify(String msg, int type, Action... actions) {
		ToastType toastType = null;
		switch (type) {
		case GUICallback.NOTIFY_WARNING:
			toastType = ToastType.WARNING;
			break;
		case GUICallback.NOTIFY_INFO:
			toastType = ToastType.INFO;
			break;
		case GUICallback.NOTIFY_CONNECT:
		case GUICallback.NOTIFY_DISCONNECT:
			toastType = ToastType.INFO;
			break;
		case GUICallback.NOTIFY_ERROR:
			toastType = ToastType.ERROR;
			break;
		default:
			toastType = ToastType.NONE;
		}
		Toast.toast(toastType, "Hypersocket Client", msg);

	}

	public Mode getMode() {
		return mode;
	}

	public void setMode(Mode mode) {
		if (mode != this.mode) {
			Mode previousMode = this.mode;
			this.mode = mode;
			log.info(String.format("Dock mode is now %s", mode));
			switch (mode) {
			case LAUNCHERS:
				rebuildIcons();
				setAvailable();
				flinger.recentre();
				break;
			case UPDATE:
				try {
					// Starting an update, so hide the all other windows
					hideIfShowing(signInPopup);
					hideIfShowing(optionsPopup);
					hideIfShowing(ssoResourcesPopup);
					hideIfShowing(browserResourcesPopup);
					hideIfShowing(networkResourcesPopup);
					hideIfShowing(fileResourcesPopup);
					hideIfShowing(resourceGroupPopup);
					setAvailable();
					updateScene = (Update) context.openScene(Update.class,
							Configuration.getDefault().isVertical() ? "Vertical" : null);
					Scene scn = updateScene.getScene();
					scn.setFill(new Color(0, 0, 0, 0));

					/*
					 * The update popup will get future update events, but it needs this one to
					 * initialize
					 */
					updateScene.initUpdate(appsToUpdate, previousMode);

					Parent sceneRoot = scn.rootProperty().get();
					scn.setRoot(new Group());

					if (cfg.isVertical())
						((VBox) sceneRoot).minWidthProperty().bind(shortcuts.heightProperty());
					else
						((HBox) sceneRoot).minHeightProperty().bind(shortcuts.widthProperty());

					flinger.getContent().getChildren().clear();
					flinger.getContent().getChildren().add(sceneRoot);
				} catch (IOException ioe) {
					log.error("Failed to load update scene.", ioe);
				}
				break;
			case IDLE:
				flinger.getContent().getChildren().clear();
				setAvailable();
				break;
			default:
				throw new UnsupportedOperationException();
			}
		}
	}

	public void updateResourceGroup(Button source, ResourceGroupList group) {
		if (resourceGroupPopup != null && resourceGroupPopup.isShowing()) {
			positionResourceGroupPopup(source);
			resourceGroup.setResources(group);
		}
	}

	public void hideResourceGroup() {
		hideIfShowing(resourceGroupPopup);
	}

	public boolean arePopupsOpen() {
		return context.isWaitingForExitChoice() || (signInPopup != null && signInPopup.isShowing())
				|| (optionsPopup != null && optionsPopup.isShowing())
				|| (ssoResourcesPopup != null && ssoResourcesPopup.isShowing())
				|| (browserResourcesPopup != null && browserResourcesPopup.isShowing())
				|| (networkResourcesPopup != null && networkResourcesPopup.isShowing())
				|| (fileResourcesPopup != null && fileResourcesPopup.isShowing())
				|| (statusPopup != null && statusPopup.isShowing())
				|| (resourceGroupPopup != null && resourceGroupPopup.isShowing());
	}

	public void onLaunched(Runnable runnable) {
		if (launchWait != null) {
			launchWait.setOnFinished(runnable == null ? null : eh -> runnable.run());
		}
	}

	/*
	 * The following are all events from the {@link Bridge}, and will come in on the
	 * RMI thread.
	 */

	@Override
	public void initUpdate(int apps, Mode mode) {
		this.appsToUpdate = apps;
		Platform.runLater(() -> setMode(Mode.UPDATE));
	}

	@Override
	public void loadResources(Connection connection) {
		rebuildAllLaunchers();
	}

	@Override
	public void finishedConnecting(Connection connection, Exception e) {
		log.info(String.format("New connection finished connected (%s)", connection.toString()));
		Platform.runLater(() -> setAvailable());
	}

	@Override
	public void bridgeEstablished() {
		/*
		 * Only rebuild launchers if the updater is not waiting for the bridge to come
		 * back, as the GUI will be restarted shortly
		 */
		if (updateScene == null || (!updateScene.isAwaitingBridgeEstablish() && !updateScene.isAwaitingGUIRestart())) {

			log.info(String.format("Bridge established, rebuilding all launchers"));
			rebuildAllLaunchers();
			if (context.getBridge().isServiceUpdating()) {
				setMode(Mode.UPDATE);
			} else {
				final StringProperty prop = Configuration.getDefault().temporaryOnStartConnectionProperty();
				String tmp = prop.get();
				if (!StringUtils.isBlank(tmp)) {
					try {
						Connection c = context.getBridge().getConnectionService().getConnection(Long.parseLong(tmp));
						if (c == null)
							throw new Exception("No connection with id of " + tmp);
						log.info(String.format("Using temporary 'on start' connection %d (%s)", c.getId(),
								c.getHostname()));
						context.getBridge().connect(c);
					} catch (Exception e) {
						log.error("Failed to start temporary 'on start' connection.", e);
					}
					prop.set("");
				}
			}
		}
	}

	@Override
	public void bridgeLost() {
		log.info(String.format("Bridge lost, rebuilding all launchers"));
		Platform.runLater(() -> {
			if (updateScene == null || !updateScene.isAwaitingBridgeLoss())
				setMode(Mode.IDLE);
			rebuildAllLaunchers();
		});

		// TODO
		// Stop connecting process if bridge lost during an upgrade
	}

	@Override
	public void disconnected(Connection connection, Exception e) {
		log.info(String.format("Connection disconnected (%s)", connection.toString()));
		rebuildAllLaunchers();
	}

	public boolean isAwaitingLaunch() {
		return launchWait != null && launchWait.getStatus() == javafx.animation.Animation.Status.RUNNING;
	}

	public void showResourceGroup(Button source, ResourceGroupList group) {
		Window parent = this.scene.getWindow();
		if (resourceGroupPopup == null) {
			try {
				resourceGroup = (ResourceGroup) context.openScene(ResourceGroup.class);
			} catch (IOException ioe) {
				throw new RuntimeException(ioe);
			}
			resourceGroupPopup = new Popup(parent, resourceGroup.getScene(), true, PositionType.POSITIONED) {

				@Override
				protected void hideParent(Window parent) {
					hideDock(true);
				}
			};
			resourceGroup.setPopup(resourceGroupPopup);
		}
		positionResourceGroupPopup(source);
		resourceGroup.setResources(group);
		resourceGroupPopup.popup();
	}

	@Override
	public void connecting(Connection connection) {
		Platform.runLater(() -> setAvailable());
	}

	@Override
	public void started(Connection connection) {
		Platform.runLater(() -> setAvailable());
	}

	private void positionResourceGroupPopup(Button source) {
		Point2D sceneCoord = source.localToScreen(0, 0);
		if (cfg.topProperty().get() || cfg.bottomProperty().get())
			resourceGroupPopup.setPosition(sceneCoord.getX());
		else
			resourceGroupPopup.setPosition(sceneCoord.getY());
	}

	// Overrides

	@Override
	public void updateResource(ResourceUpdateType type, Resource resource) {
		switch (type) {
		case CREATE: {
			rebuildResourceIcon(resource.getRealm(), resource);
			rebuildIcons();

			Action[] actions = new Action[0];

			// Find the button so we can launch on clicking the notify
			ResourceGroupKey key = new ResourceGroupKey(resource.getType(), resource.getGroup());
			ResourceGroupList list = icons.get(key);
			if (list != null) {
				ResourceItem rit = list.getItemForResource(resource);
				if (rit != null) {
					LauncherButton lb = getButtonForResourceItem(rit);
					if (lb != null) {
						actions = new Action[] {
								new Action(resources.getString("resources.launch"), new Consumer<ActionEvent>() {
									@Override
									public void accept(ActionEvent t) {
										lb.launch();
									}
								}) };
					}
				}
			}

			notify(MessageFormat.format(resources.getString("resources.created"), resource.getName()),
					GUICallback.NOTIFY_INFO, actions);
			break;
		}
		case DELETE: {
			ResourceGroupKey key = new ResourceGroupKey(resource.getType(), resource.getGroup());
			ResourceGroupList list = icons.get(key);
			if (list != null) {
				ResourceItem rit = list.getItemForResource(resource);
				if (rit != null) {
					list.getItems().remove(rit);
					rebuildIcons();
					deleteLinkedFavouriteItem(resource);
					notify(MessageFormat.format(resources.getString("resources.deleted"), resource.getName()),
							GUICallback.NOTIFY_INFO);
					break;
				}
			}
			break;
		}
		default: {
			ResourceGroupKey key = new ResourceGroupKey(resource.getType(), resource.getGroup());
			ResourceGroupList list = icons.get(key);
			if (list != null) {
				ResourceItem rit = list.getItemForResource(resource);
				if (rit != null) {
					rit.setResource(resource);
					rebuildIcons();
					notify(MessageFormat.format(resources.getString("resources.updated"), resource.getName()),
							GUICallback.NOTIFY_INFO);
					break;
				} else {
					log.warn(String.format("Could not find icon in icon group for resource %s (%s)", resource.getUid(),
							resource.getName()));
				}
			} else {
				log.warn(String.format("Could not find icon group for resource %s (%s)", resource.getUid(),
						resource.getName()));
			}
			break;
		}
		}

		DockOnEventDo.refreshResourcesFavouriteLists();
		showHideResourcesButtonForResourceTypes();
	}

	@Override
	protected void onCleanUp() {
		if (updateScene != null) {
			updateScene.cleanUp();
		}
		if (signInScene != null) {
			signInScene.cleanUp();
		}
		if (optionsScene != null) {
			optionsScene.cleanUp();
		}
		if (statusContent != null) {
			statusContent.cleanUp();
		}
		cfg.sizeProperty().removeListener(sizeChangeListener);
		cfg.colorProperty().removeListener(colorChangeListener);
		cfg.topProperty().removeListener(borderChangeListener);
		cfg.bottomProperty().removeListener(borderChangeListener);
		cfg.leftProperty().removeListener(borderChangeListener);
		cfg.rightProperty().removeListener(borderChangeListener);
	}

	@Override
	protected void onConfigure() {
		cfg = Configuration.getDefault();

		flinger = new Flinger();
		flinger.gapProperty().set(4);
		flinger.directionProperty().setValue(cfg.isVertical() ? Direction.VERTICAL : Direction.HORIZONTAL);
		slideLeft.disableProperty().bind(flinger.leftOrUpDisableProperty());
		slideRight.disableProperty().bind(flinger.rightOrDownDisableProperty());
		shortcuts.getChildren().add(flinger);

		AnchorPane.setTopAnchor(flinger, 0d);
		AnchorPane.setBottomAnchor(flinger, 0d);
		AnchorPane.setLeftAnchor(flinger, 0d);
		AnchorPane.setRightAnchor(flinger, 0d);

		networkResources.setVisible(false);
		ssoResources.setVisible(false);
		browserResources.setVisible(false);
		fileResources.setVisible(false);

		UIHelpers.bindButtonToItsVisibleManagedProperty(networkResources);
		UIHelpers.bindButtonToItsVisibleManagedProperty(ssoResources);
		UIHelpers.bindButtonToItsVisibleManagedProperty(browserResources);
		UIHelpers.bindButtonToItsVisibleManagedProperty(fileResources);

		networkResources.setTooltip(UIHelpers.createDockButtonToolTip(resources.getString("network.toolTip")));
		ssoResources.setTooltip(UIHelpers.createDockButtonToolTip(resources.getString("sso.toolTip")));
		browserResources.setTooltip(UIHelpers.createDockButtonToolTip(resources.getString("web.toolTip")));
		fileResources.setTooltip(UIHelpers.createDockButtonToolTip(resources.getString("files.toolTip")));

		status.setTooltip(UIHelpers.createDockButtonToolTip(status.getTooltip().getText()));
		exit.setTooltip(UIHelpers.createDockButtonToolTip(exit.getTooltip().getText()));
		signIn.setTooltip(UIHelpers.createDockButtonToolTip(signIn.getTooltip().getText()));
		options.setTooltip(UIHelpers.createDockButtonToolTip(options.getTooltip().getText()));

		// Button size changes
		sizeChangeListener = new ChangeListener<Number>() {
			@Override
			public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
				flinger.recentre();
				sizeButtons();
			}
		};
		cfg.sizeProperty().addListener(sizeChangeListener);

		// Colour changes
		colorChangeListener = new ChangeListener<Color>() {
			@Override
			public void changed(ObservableValue<? extends Color> observable, Color oldValue, Color newValue) {
				// styleToolTips();
			}
		};
		cfg.colorProperty().addListener(colorChangeListener);

		// Border changes
		borderChangeListener = new ChangeListener<Boolean>() {

			@Override
			public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
				if (newValue) {
					flinger.directionProperty().setValue(cfg.isVertical() ? Direction.VERTICAL : Direction.HORIZONTAL);
					configurePull();
				}
			}
		};
		cfg.topProperty().addListener(borderChangeListener);
		cfg.bottomProperty().addListener(borderChangeListener);
		cfg.leftProperty().addListener(borderChangeListener);
		cfg.rightProperty().addListener(borderChangeListener);

		dockContent.prefWidthProperty().bind(dockStack.widthProperty());

		// Hide the pull tab initially
		pull.setVisible(false);

		if (context.getBridge().isServiceUpdating()) {
			setMode(Mode.UPDATE);
		}

		rebuildResources();
		rebuildIcons();
		// styleToolTips();
		sizeButtons();
		setAvailable();
		configurePull();
		if (cfg.autoHideProperty().get())
			maybeHideDock(INITIAL_AUTOHIDE_HIDE_TIME);

	}

	private void configurePull() {
		if (cfg.topProperty().get())
			pull.setText(resources.getString("pullTop"));
		else if (cfg.bottomProperty().get())
			pull.setText(resources.getString("pullBottom"));
		else if (cfg.leftProperty().get()) {
			pull.setText(resources.getString("pullBottom"));
			pull.setAlignment(Pos.CENTER_RIGHT);
		} else if (cfg.rightProperty().get()) {
			pull.setText(resources.getString("pullTop"));
			pull.setAlignment(Pos.CENTER_LEFT);
		}
	}

	private void hideIfShowing(Popup popup) {
		if (popup != null && popup.isShowing())
			popup.hide();
	}

	private static String textFill(Color color) {
		return String.format("-fx-text-fill: %s ;", UIHelpers.toHex(color, false));
	}

	private static String background(Color color, boolean opacity) {
		return String.format("-fx-background-color: %s ;", UIHelpers.toHex(color, opacity));
	}

	private void showContextMenu(double x, double y) {
		if (contextMenu != null && contextMenu.isShowing())
			contextMenu.hide();
		contextMenu = new ContextMenu();
		// contextMenu.getStyleClass().add("background");

		Color bg = cfg.colorProperty().getValue();
		Color fg = bg.getBrightness() < 0.5f ? Color.WHITE : Color.BLACK;

		contextMenu.setStyle(background(bg, true));

		contextMenu.setOnHidden(value -> {
			if (cfg.autoHideProperty().get() && !arePopupsOpen())
				maybeHideDock();
		});
		if (!cfg.autoHideProperty().get()) {
			MenuItem hide = new MenuItem(resources.getString("menu.hide"));
			hide.setOnAction(value -> getStage().setIconified(true));
			hide.setStyle(textFill(fg));
			contextMenu.getItems().add(hide);
		}
		MenuItem close = new MenuItem(resources.getString("menu.exit"));
		close.setOnAction(value -> {
			context.confirmExit();
			maybeHideDock();
		});
		close.setStyle(textFill(fg));
		contextMenu.getItems().add(close);
		Point2D loc = new Point2D(x + getStage().getX(), y + getStage().getY());
		contextMenu.show(dockContent, loc.getX(), loc.getY());
	}

	private void rebuildAllLaunchers() {
		log.info("Rebuilding all launchers");
		rebuildResources();
		markFavouritesInIcons();
		showHideResourcesButtonForResourceTypes();
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				if (icons.size() > 0 && mode == Mode.IDLE) {
					setMode(Mode.LAUNCHERS);
				} else if (icons.size() == 0 && mode == Mode.LAUNCHERS) {
					setMode(Mode.IDLE);
				} else {
					if (mode == Mode.LAUNCHERS) {
						rebuildIcons();
					}
					setAvailable();
				}
			}
		});
	}

	private void showHideResourcesButtonForResourceTypes() {
		if (context.getBridge().isConnected() && !context.getBridge().isServiceUpdating()) {
			Set<Type> avaialbleResourceTypes = new HashSet<>();
			Set<ResourceGroupKey> resourceGroupKeys = icons.keySet();
			for (ResourceGroupKey resourceGroupKey : resourceGroupKeys) {
				ResourceGroupList groupList = icons.get(resourceGroupKey);
				if (groupList != null) {
					for (ResourceItem item : groupList.getItems()) {
						Resource resource = item.getResource();
						avaialbleResourceTypes.add(resource.getType());
					}
				}
			}
			ssoResources.setVisible(avaialbleResourceTypes.contains(Type.SSO));
			fileResources.setVisible(avaialbleResourceTypes.contains(Type.FILE));
			browserResources.setVisible(avaialbleResourceTypes.contains(Type.BROWSER));
			networkResources.setVisible(avaialbleResourceTypes.contains(Type.NETWORK));
		}
	}

	private void showStatus(Button source) throws IOException {
		Window parent = this.scene.getWindow();
		if (statusPopup == null) {
			statusContent = (Status) context.openScene(Status.class);
			statusPopup = new Popup(parent, statusContent.getScene(), true, PositionType.DOCKED) {
				@Override
				protected void hideParent(Window parent) {
					hideDock(true);
				}
			};
			statusContent.setPopup(statusPopup);
		}
		statusContent.setResources(serviceResources);
		statusPopup.popup();
	}

	private void openSignInWindow() throws IOException {
		Window parent = this.scene.getWindow();
		if (signInPopup == null) {
			signInScene = (SignIn) context.openScene(SignIn.class);
			signInPopup = new Popup(parent, signInScene.getScene()) {
				@Override
				protected void hideParent(Window parent) {
					hideDock(true);
				}
			};
			signInScene.setPopup(signInPopup);

		}
		signInPopup.popup();
	}

	private void rebuildResources() {

		serviceResources.clear();
		icons.clear();
		log.info("Rebuilding resources");
		if (context.getBridge().isConnected() && !context.getBridge().isServiceUpdating()) {
			ResourceService resourceService = context.getBridge().getResourceService();

			try {
				serviceResources.addAll(resourceService.getServiceResources());
			} catch (Exception e) {
				log.error("Failed to get service resources.", e);
			}

			try {
				for (ResourceRealm resourceRealm : resourceService.getResourceRealms()) {
					for (Resource r : resourceRealm.getResources()) {
						if (r.getType() != Type.ENDPOINT) {
							rebuildResourceIcon(resourceRealm, r);
						}
					}
				}
			} catch (Exception e) {
				log.error("Failed to get resources.", e);
			}
			log.info(String.format("Found %d top level launchers", icons.size()));
		}
	}

	private void rebuildResourceIcon(ResourceRealm resourceRealm, Resource r) {
		ResourceGroupKey igk = new ResourceGroupKey(r.getType(), r.getGroup());
		ResourceGroupList ig = icons.get(igk);
		if (ig == null) {
			ig = new ResourceGroupList(resourceRealm, igk, r.getGroupIcon());
			icons.put(igk, ig);
		}
		ig.getItems().add(new ResourceItem(r, resourceRealm));
	}

	private LauncherButton getButtonForResourceItem(ResourceItem resourceItem) {
		for (Node n : flinger.getContent().getChildren()) {
			if (n instanceof LauncherButton) {
				final LauncherButton launcherButton = (LauncherButton) n;
				if (resourceItem.equals(launcherButton.getResourceItem())) {
					return launcherButton;
				}
			}
		}
		return null;
	}

	public void toggleHideResources(Long connectionId) {
		toggleResourcesForConnection.add(connectionId);
		rebuildIcons();
	}

	public void toggleShowResources(Long connectionId) {
		toggleResourcesForConnection.remove(connectionId);
		rebuildIcons();
	}

	synchronized void rebuildIcons() {

		if (mode != Mode.LAUNCHERS) {
			return;
		}

		context.clearLoadQueue();
		flinger.getContent().getChildren().clear();

		// Type lastType = null;
		for (Map.Entry<ResourceGroupKey, ResourceGroupList> ig : icons.entrySet()) {
			ig.getKey().getType();

			List<ResourceGroupList> groupsAdded = new ArrayList<>();
			for (ResourceItem item : ig.getValue().getItems()) {
				if (!item.getResource().getFavourite()
						|| toggleResourcesForConnection.contains(item.getResource().getConnectionId())) {
					continue;
				}
				ResourceGroupKey gk = new ResourceGroupKey(item.getResource().getType(), item.getResource().getGroup());
				ResourceGroupList groups = icons.get(gk);
				if (groups == null || groups.getItems().size() < 2) {
					flinger.getContent().getChildren().add(new IconButton(resources, item, context, ig.getValue()) {

						@Override
						protected void onFinishLaunch() {
							super.onFinishLaunch();

							if (launchWait != null
									&& launchWait.getStatus() == javafx.animation.Animation.Status.RUNNING)
								launchWait.stop();

							launchWait = new Timeline(new KeyFrame(Duration.millis(Dock.LAUNCH_WAIT)));
							launchWait.play();
						}

					});
				} else {
					if (!groupsAdded.contains(groups)) {
						flinger.getContent().getChildren().add(new GroupButton(resources, context, groups) {

							@Override
							protected void onFinishLaunch() {
								super.onFinishLaunch();

								if (launchWait != null
										&& launchWait.getStatus() == javafx.animation.Animation.Status.RUNNING)
									launchWait.stop();

								launchWait = new Timeline(new KeyFrame(Duration.millis(Dock.LAUNCH_WAIT)));
								launchWait.play();
							}

						});
						groupsAdded.add(groups);
					}
				}
			}

			// lastType = type;
		}

	}

	private void maybeHideDock() {
		maybeHideDock(AUTOHIDE_HIDE_TIME);
	}

	private void maybeHideDock(long time) {
		if (hiding) {
			return;
		}
		if (popOver != null && popOver.isShowing())
			return;
		stopDockHiderTrigger();
		dockHiderTrigger = new Timeline(new KeyFrame(Duration.millis(time), ae -> hideDock(true)));
		dockHiderTrigger.play();
	}

	private void stopDockHiderTrigger() {
		if (dockHiderTrigger != null && dockHiderTrigger.getStatus() == Animation.Status.RUNNING)
			dockHiderTrigger.stop();
	}

	void hideDock(boolean hide) {
		stopDockHiderTrigger();

		if (hide != hidden) {

			/*
			 * If revealing, then don't actually reveal until a delay has passed. The
			 * delayed action will be cancelled if in the mean time the mouse leaves the
			 * dock revealer
			 */
			if (!hide) {
				stopDockRevealerTimer();
				dockRevealer = new Timeline(new KeyFrame(Duration.millis(REVEAL_HOVER_TIME), ae -> changeHidden(hide)));
				dockRevealer.play();
			} else {
				changeHidden(hide);
			}
		}
	}

	private void stopDockRevealerTimer() {
		if (dockRevealer != null && dockRevealer.getStatus() == Animation.Status.RUNNING)
			dockRevealer.stop();
	}

	private void changeHidden(boolean hide) {
		hidePopOver();

		/*
		 * If already hiding, we don't want the mouse event that MIGHT happen when the
		 * resizing dock passes under the mouse (the user wont have moved mouse yet)
		 */
		if (hiding) {
			// TODO check this ...
			return;
		}

		hidden = hide;
		hiding = true;

		pull.setVisible(true);

		dockHider = new Timeline(new KeyFrame(Duration.millis(5), ae -> shiftDock()));
		yEnd = System.currentTimeMillis() + AUTOHIDE_DURATION;
		dockHider.play();
	}

	private void shiftDock() {
		long now = System.currentTimeMillis();
		Rectangle2D cfgBounds = Client.getConfiguredBounds();

		// The bounds to work in
		int boundsSize = cfg.isVertical() ? (int) cfgBounds.getHeight() : (int) cfgBounds.getWidth();

		// Total amount to slide
		int value = cfg.sizeProperty().get() - AUTOHIDE_TAB_OPPOSITE_SIZE;

		// How far along the timeline?
		float fac = Math.min(1f, 1f - ((float) (yEnd - now) / (float) AUTOHIDE_DURATION));

		// The amount of movement so far
		float amt = fac * value;

		// The amount to shrink the width (or height when vertical) of the
		// visible 'bar'
		float barSize = boundsSize * fac;

		// If showing, reverse
		final boolean fhidden = hidden;

		if (!hidden) {
			amt = value - amt;
			barSize = boundsSize - barSize;
			if (!pull.isVisible())
				pull.setVisible(true);
		}

		// Reveal or hide the pull tab
		dockContent.setOpacity(hidden ? 1f - fac : fac);
		pull.setOpacity((hidden ? fac : 1f - fac) * 0.5f);

		Stage stage = getStage();
		if (stage != null) {
			if (cfg.topProperty().get()) {
				getScene().getRoot().translateYProperty().set(-amt);
				stage.setHeight(cfg.sizeProperty().get() - amt + Client.DROP_SHADOW_SIZE);
				stage.setWidth(Math.max(AUTOHIDE_TAB_SIZE, cfgBounds.getWidth() - barSize));
				stage.setX(cfgBounds.getMinX() + ((cfgBounds.getWidth() - stage.getWidth()) / 2f));
			} else if (cfg.bottomProperty().get()) {
				stage.setY(cfgBounds.getMaxY() + amt);
				stage.setHeight(cfg.sizeProperty().get() - amt + Client.DROP_SHADOW_SIZE);
				stage.setWidth(Math.max(AUTOHIDE_TAB_SIZE, cfgBounds.getWidth() - barSize));
				stage.setX(cfgBounds.getMinX() + ((cfgBounds.getWidth() - stage.getWidth()) / 2f));
			} else if (cfg.leftProperty().get()) {
				getScene().getRoot().translateXProperty().set(-amt);
				stage.setWidth(cfg.sizeProperty().get() - amt);
				stage.setHeight(Math.max(AUTOHIDE_TAB_SIZE, cfgBounds.getHeight() - barSize));
				stage.setY(cfgBounds.getMinY() + ((cfgBounds.getHeight() - stage.getHeight()) / 2f));
			} else if (cfg.rightProperty().get()) {
				stage.setX(cfgBounds.getMaxX() + amt - cfg.sizeProperty().get());
				stage.setWidth(cfg.sizeProperty().get() - amt);
				stage.setHeight(Math.max(AUTOHIDE_TAB_SIZE, cfgBounds.getHeight() - barSize));
				stage.setY(cfgBounds.getMinY() + ((cfgBounds.getHeight() - stage.getHeight()) / 2f));
			} else {
				throw new UnsupportedOperationException();
			}
		}

		// The update or the sign in dialog may have been popped, so make sure
		// it is position correctly
		if (signInPopup != null && signInPopup.isShowing()) {
			signInPopup.sizeToScene();
		}

		// If not fully hidden / revealed, play again
		if (now < yEnd) {
			dockHider.playFromStart();
		} else {
			// Defer this as events may still be coming in
			Platform.runLater(new Runnable() {
				@Override
				public void run() {
					if (!fhidden && stage != null) {
						stage.requestFocus();
						pull.setVisible(false);
					}
					hiding = false;
				}
			});
		}
	}

	// private void styleToolTips() {
	// for (Node s : shortcuts.getChildren()) {
	// if (s instanceof ButtonBase) {
	// recreateTooltip((ButtonBase) s);
	// }
	// }
	// recreateTooltip(options);
	// recreateTooltip(signIn);
	// recreateTooltip(exit);
	// recreateTooltip(status);
	// recreateTooltip(fileResources);
	// recreateTooltip(networkResources);
	// recreateTooltip(ssoResources);
	// recreateTooltip(browserResources);
	//
	// }

	// private void recreateTooltip(ButtonBase bb) {
	// if (bb != null && bb.getTooltip() != null)
	// bb.setTooltip(UIHelpers.createDockButtonToolTip(bb.getTooltip()
	// .getText()));
	// }

	private void sizeButtons() {
		UIHelpers.sizeToImage(networkResources);
		UIHelpers.sizeToImage(fileResources);
		UIHelpers.sizeToImage(ssoResources);
		UIHelpers.sizeToImage(browserResources);
		UIHelpers.sizeToImage(slideLeft);
		UIHelpers.sizeToImage(slideRight);
		UIHelpers.sizeToImage(signIn);
		UIHelpers.sizeToImage(exit);
		UIHelpers.sizeToImage(options);
		UIHelpers.sizeToImage(status);
		for (Node n : flinger.getContent().getChildren()) {
			if (n instanceof ButtonBase) {
				UIHelpers.sizeToImage((ButtonBase) n);
			}
		}
		setAvailable();
	}

	private void setAvailable() {
		for (String s : Arrays.asList("statusNotConnected", "statusConnected", "statusError", "statusBusy")) {
			signIn.getStyleClass().remove(s);
		}
		if (context.getBridge().isConnected()) {
			int connected = 0;
			int connecting = 0;
			try {
				List<ConnectionStatus> connections = context.getBridge().getClientService().getStatus();
				for (ConnectionStatus c : connections) {
					log.info(String.format("Connection %s = %s", c.getConnection().getHostname(), c.getStatus()));
					if (c.getStatus() == ConnectionStatus.CONNECTED) {
						connected++;
					} else if (c.getStatus() == ConnectionStatus.CONNECTING) {
						connecting++;
					}
				}
				log.info(String.format("Bridge says %d are connected of %d", connected, connections.size()));
				if (connecting > 0) {
					signIn.getStyleClass().add("statusBusy");
					if (busyFade == null) {
						busyFade = new FadeTransition(Duration.seconds(0.25), signIn);
						signIn.setEffect(new Glow(0.5));
						busyFade.setFromValue(0.5);
						busyFade.setToValue(1.0);
						busyFade.setAutoReverse(true);
						busyFade.setCycleCount(Timeline.INDEFINITE);
						busyFade.play();
					}
				} else if (connected > 0) {
					signIn.getStyleClass().add("statusNotConnected");
					removeBusyGlow();
				} else {
					signIn.getStyleClass().add("statusConnected");
					removeBusyGlow();
					signIn.setEffect(new Glow(0.5));
				}
			} catch (Exception e) {
				log.error("Failed to check connection state.", e);
				signIn.getStyleClass().add("statusError");
				removeBusyGlow();
			}
		} else {
			log.info("Bridge says not connected");
			signIn.getStyleClass().add("statusError");
			removeBusyGlow();
		}
	}

	private void removeBusyGlow() {
		if (busyFade != null) {
			busyFade.stop();
			busyFade = null;
		}
		signIn.setEffect(null);
	}

	private void mouseMovementShow(MouseEvent evt) {
		hideDock(false);
		evt.consume();
	}

	private void mouseMovementHide(MouseEvent evt) {
		maybeHideDock();
		evt.consume();
	}

	@FXML
	private void evtMouseEnter(MouseEvent evt) throws Exception {
		AmIOnDockSensor.INSTANCE.setSensor(true);
		if (cfg.autoHideProperty().get() && cfg.hoverToRevealProperty().get()) {
			mouseMovementShow(evt);
		}
	}

	@FXML
	private void evtMouseExit(MouseEvent evt) throws Exception {
		AmIOnDockSensor.INSTANCE.setSensor(false);
		stopDockRevealerTimer();
		if (cfg.autoHideProperty().get() && cfg.hoverToRevealProperty().get() && !arePopupsOpen()
				&& (contextMenu == null || !contextMenu.isShowing())) {
			mouseMovementHide(evt);
		}
	}

	@FXML
	private void evtMouseClick(MouseEvent evt) throws Exception {
		if (evt.getButton() == MouseButton.PRIMARY && !cfg.hoverToRevealProperty().get()) {
			if (hidden) {
				mouseMovementShow(evt);
			} else {
				stopDockRevealerTimer();
				mouseMovementHide(evt);
			}
			if (contextMenu != null) {
				contextMenu.hide();
			}
		} else if (evt.getButton() == MouseButton.SECONDARY) {
			showContextMenu(evt.getX(), evt.getY());
			evt.consume();
		}
	}

	@FXML
	private void evtExit(ActionEvent evt) throws Exception {
		context.confirmExit();
		maybeHideDock();
	}

	@FXML
	private void evtSlideLeft() {
		flinger.slideLeftOrUp();
	}

	@FXML
	private void evtSlideRight() {
		flinger.slideRightOrDown();
	}

	@FXML
	private void evtRefilter() {
		rebuildIcons();
	}

	@FXML
	private void evtRefilterListSso(ActionEvent evt) throws IOException {
		Object[] values = evtRefilterList(evt, SsoResources.class, ssoResourcesContent, ssoResourcesPopup);
		ssoResourcesContent = (SsoResources) values[0];
		ssoResourcesPopup = (Popup) values[1];
	}

	@FXML
	private void evtRefilterListBrowser(ActionEvent evt) throws IOException {
		Object[] values = evtRefilterList(evt, BrowserResources.class, browserResourcesContent, browserResourcesPopup);
		browserResourcesContent = (BrowserResources) values[0];
		browserResourcesPopup = (Popup) values[1];
	}

	@FXML
	private void evtRefilterListNetwork(ActionEvent evt) throws IOException {
		Object[] values = evtRefilterList(evt, NetworkResources.class, networkResourcesContent, networkResourcesPopup);
		networkResourcesContent = (NetworkResources) values[0];
		networkResourcesPopup = (Popup) values[1];
	}

	@FXML
	private void evtRefilterListFile(ActionEvent evt) throws IOException {
		Object[] values = evtRefilterList(evt, FileResources.class, fileResourcesContent, fileResourcesPopup);
		fileResourcesContent = (FileResources) values[0];
		fileResourcesPopup = (Popup) values[1];
	}

	private Object[] evtRefilterList(ActionEvent evt, Class<? extends AbstractResourceListController> clazz,
			AbstractResourceListController listController, Popup popup) throws IOException {
		Window parent = this.scene.getWindow();
		if (popup == null) {
			listController = (AbstractResourceListController) context.openScene(clazz);
			popup = new Popup(parent, listController.getScene(), true, PositionType.DOCKED_OPPOSITE) {
				@Override
				protected void hideParent(Window parent) {
					hideDock(true);
				}
			};
			listController.setPopup(popup);
		}

		if (context.getBridge().isConnected() && !context.getBridge().isServiceUpdating()) {
			listController.setResources(icons);
		}
		popup.popup();
		return new Object[] { listController, popup };
	}

	@FXML
	private void evtShowPopup(MouseEvent evt) {
		changeHidden(false);
	}

	@FXML
	private void evtOpenSignInWindow(ActionEvent evt) throws Exception {
		openSignInWindow();
	}

	@FXML
	private void evtStatus(ActionEvent evt) throws Exception {
		showStatus((Button) evt.getSource());
	}

	@FXML
	private void evtOpenOptionsWindow(ActionEvent evt) throws Exception {
		Window parent = this.scene.getWindow();
		if (optionsPopup == null) {
			optionsScene = (AbstractController) context.openScene(Options.class);
			optionsPopup = new Popup(parent, optionsScene.getScene()) {

				@Override
				protected void hideParent(Window parent) {
					hideDock(true);
				}

				@Override
				protected boolean isChildFocussed() {
					// HACK!
					//
					// When the custom colour dialog is focused, there doesn't
					// seem to be anyway of determining what the opposite
					// component was the gained the focus. Being as that is
					// the ONLY utility dialog, it should be the one
					
					// Doesnt compile on Java8 + OpenJFX ... Only choice on Linux now
//					for (Window s : Stage.getWindows()) {
//						if (s instanceof Stage) {
//							if (((Stage) s).getStyle() == StageStyle.UTILITY) {
//								return s.isShowing();
//							}
//						}
//					}
					return false;
				}
			};

			((Options) optionsScene).setPopup(optionsPopup);
		}
		optionsPopup.popup();
	}

	private void markFavouritesInIcons() {
		if (context.getBridge().isConnected() && !context.getBridge().isServiceUpdating()) {
			try {
				FavouriteItemService favouriteItemService = context.getBridge().getFavouriteItemService();
				List<FavouriteItem> favouriteItems = favouriteItemService.getFavouriteItems();

				if (favouriteItems != null) {
					Map<String, FavouriteItem> favouriteItemsMap = new HashMap<>();

					for (FavouriteItem favouriteItem : favouriteItems) {
						favouriteItemsMap.put(favouriteItem.getUid(), favouriteItem);
					}

					Set<ResourceGroupKey> resourceGroupKeys = icons.keySet();
					for (ResourceGroupKey resourceGroupKey : resourceGroupKeys) {
						ResourceGroupList groupList = icons.get(resourceGroupKey);
						if (groupList != null) {
							for (ResourceItem item : groupList.getItems()) {
								Resource resource = item.getResource();
								FavouriteItem favouriteItem = favouriteItemsMap.get(resource.getUid());
								if (favouriteItem != null) {
									resource.setFavourite(favouriteItem.getFavourite());
								}
							}
						}
					}

				}
			} catch (RemoteException e) {
				throw new IllegalStateException(e.getMessage(), e);
			}
		}
	}

	public void requestFocus() {
		final Stage stage = getStage();
		if (!stage.isFocused()) {
			// Defer this as events may still be coming in
			Platform.runLater(new Runnable() {
				@Override
				public void run() {
					stage.requestFocus();
				}
			});
		}
	}

	private void deleteLinkedFavouriteItem(Resource resource) {
		try {
			FavouriteItemService favouriteItemService = context.getBridge().getFavouriteItemService();
			FavouriteItem favouriteItem = favouriteItemService.getFavouriteItem(resource.getUid());
			if (favouriteItem != null) {
				favouriteItemService.delete(favouriteItem.getId());
			}
		} catch (RemoteException e) {
			throw new IllegalStateException(e.getMessage(), e);
		}
	}

	public static class DockOnEventDo {

		/**
		 * We need null check at times this code might be called when favourite lists
		 * are not initialized.
		 */
		public static void refreshResourcesFavouriteLists() {
			if (SsoResources.getInstance() != null)
				SsoResources.getInstance().clearList();
			if (BrowserResources.getInstance() != null)
				BrowserResources.getInstance().clearList();
			if (NetworkResources.getInstance() != null)
				NetworkResources.getInstance().clearList();
			if (FileResources.getInstance() != null)
				FileResources.getInstance().clearList();
		}
	}

}
