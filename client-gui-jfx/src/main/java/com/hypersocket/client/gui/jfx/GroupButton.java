package com.hypersocket.client.gui.jfx;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.WeakHashMap;


import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.util.Duration;


import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.hypersocket.client.gui.jfx.Flinger.Direction;

public class GroupButton extends ImageButton {

	static Logger log = LoggerFactory.getLogger(GroupButton.class);

	final static Map<String, Image> iconCache = new WeakHashMap<>();

	private Timeline bouncer = new Timeline();
	private Timeline shrinker = new Timeline();
	private Timeline grower = new Timeline();

	private boolean launching;

	public GroupButton(ResourceBundle resources, Client context,
			ResourceGroupList group) {
		super();
		getStyleClass().add("iconButton");
		setTooltipText(group.getKey().getSubType());
		String typeName = group.getKey().getType().name();
		try {
			String iconName = group.getIcon();

			if (StringUtils.isBlank(iconName)) {
				iconName = "logo://96_autotype_autotype_auto.png";
			}

			if (iconName.startsWith("res://")) {
				// Client specified icon (when retrieving resources)
				final String resourceName = iconName.substring(6);
				URL resource = getClass().getResource(resourceName);
				if (resource == null) {
					setText(resources.getString("resource.icon." + typeName));
					log.warn(String
							.format("Falling back to text based icon for type %s because %s could not be found (%s)",
									typeName, resourceName, group.getKey()
											.getSubType()));
				} else {
					final ImageView imageView = new ImageView(
							resource.toString());
					configureButton(imageView);
					setGraphic(imageView);
				}
			} else {
				// Server specified icon
				String iconPath = iconName;
				if (iconPath.indexOf("/") == -1) {
					iconPath = "fileUpload/file/" + iconName;
				} else {
					if (iconName.startsWith("logo://")) {
						try {
							iconPath = "logo/"
									+ URLEncoder.encode(typeName, "UTF-8")
									+ "/"
									+ URLEncoder.encode(group.getKey()
											.getSubType(), "UTF-8") + "/"
									+ iconName.substring(7);
						} catch (UnsupportedEncodingException e) {
							throw new RuntimeException(e);
						}
					}
				}

				final ImageView imageView = new ImageView(getClass()
						.getResource("ajax-loader.gif").toString());
				configureButton(imageView);
				setGraphic(imageView);

				String cacheKey = group.getKey().getSubType() + "-" + iconPath;
				if (iconCache.containsKey(cacheKey)) {
					imageView.setImage(iconCache.get(cacheKey));
				} else {
					context.getLoadQueue().execute(
							new IconLoader(group.getRealm().getName(),
									cacheKey, imageView, iconPath, context,
									group) {

								@Override
								protected void onImageLoaded() {
									sizeToImage();
								}

							});
				}
			}
		} catch (MissingResourceException mre) {
			setText("%" + typeName);
		}
		sizeToImage();
		//
		// Bouncer for click events
		double bounceSpeed = 50;
		bouncer.setCycleCount(3);
		bouncer.getKeyFrames().addAll(makeKeyFrame(0, 0, 1.0, 1.0),
				makeKeyFrame(bounceSpeed * 2f, 4, 1.0, 1.0),

				makeKeyFrame(bounceSpeed * 2.5f, 4, 1.1, 0.9),
				makeKeyFrame(bounceSpeed * 4.5f, 0, 1.0, 1.0),
				makeKeyFrame(bounceSpeed * 6.5f, -4, 1.0, 1.0),
				makeKeyFrame(bounceSpeed * 8.5f, 0, 1.0, 1.0));
		bouncer.setOnFinished(value -> {
			if (launching) {
				bouncer.play();
			}
		});
		//
		// Grower for hover in
		grower.getKeyFrames().addAll(makeKeyFrame(0, 0, 1.0, 1.0),
				makeKeyFrame(100, 0, 1.2, 1.2));

		// Shrinker for hover out
		shrinker.getKeyFrames().addAll(makeKeyFrame(0, 0, 1.2, 1.2),
				makeKeyFrame(100, 0, 1.0, 1.0));
		
		setOnAction(value -> {
			Flinger flinger = new Flinger();
			flinger.directionProperty().setValue(Direction.VERTICAL);
			for(ResourceItem item : group.getItems()) {
				flinger.getContent()
				.getChildren()
				.add(new IconButton(resources, item, context, group) {

					@Override
					protected void onFinishLaunch() {
						super.onFinishLaunch();
						GroupButton.this.onFinishLaunch();
					}

				});
			}
//			Dock.getInstance().showPopOver("Stuff!", this);
			
			VBox vb = new VBox();
			vb.minHeight(600);
			vb.prefHeight(600);
//			vb.getChildren().add(flinger);
			
			Dock.getInstance().showPopOver(flinger, this);
//			Dock.getInstance().showResourceGroup(this, group);
		});

		// Monitor mouse activity
		setOnMouseEntered(value -> {
//			Dock.getInstance().updateResourceGroup(this, group);
//			Dock.getInstance().showPopOver("Stuff!", this);
			grower.play();
		});
		setOnMouseExited(value -> {
			shrinker.play();
		});
	}

	protected void onInitiateLaunch() {
		launching = true;
		bouncer.play();
	}

	protected void onFinishLaunch() {
		launching = false;
	}

	private KeyFrame makeKeyFrame(double d, double y, double sx, double sy) {
		return new KeyFrame(Duration.millis(d), new KeyValue(
				translateYProperty(), y), new KeyValue(scaleXProperty(), sx),
				new KeyValue(scaleYProperty(), sy));
	}

	private void configureButton(final ImageView imageView) {
		imageView.setFitHeight(32);
		imageView.setFitWidth(32);
		imageView.setPreserveRatio(true);
		imageView.getStyleClass().add("launcherIcon");
	}

}
