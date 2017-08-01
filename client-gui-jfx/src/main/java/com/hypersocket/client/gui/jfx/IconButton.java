package com.hypersocket.client.gui.jfx;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.WeakHashMap;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.util.Duration;

public class IconButton extends LauncherButton {

	static Logger log = LoggerFactory.getLogger(IconButton.class);

	final static Map<String, Image> iconCache = new WeakHashMap<>();

	private Timeline bouncer = new Timeline();
	private Timeline shrinker = new Timeline();
	private Timeline grower = new Timeline();

	private boolean launching;

	public IconButton(ResourceBundle resources, ResourceItem resourceItem,
			Client context, ResourceGroupList group) {
		this(resources, resourceItem, context, group, -1, -1);
	}
	public IconButton(ResourceBundle resources, ResourceItem resourceItem,
			Client context, ResourceGroupList group, double width, double height) {
		super(resources, resourceItem, context);
		getStyleClass().add("iconButton");
		setTooltipText(resourceItem.getResource().getName());
		String typeName = resourceItem.getResource().getType().name();
		try {
			String iconName = resourceItem.getResource().getIcon();
			
			if (StringUtils.isBlank(iconName)) {
				iconName = "logo://96_autotype_autotype_auto.png";
			}

			if(iconName.startsWith("res://")){
				// Client specified icon (when retrieving resources)
				final String resourceName = iconName.substring(6);
				URL resource = getClass().getResource(resourceName);
				if (resource == null) {
					setText(resources.getString("resource.icon." + typeName));
					log.warn(String.format(
							"Falling back to text based icon for type %s because %s could not be found (%s)",
							typeName,  resourceName, resourceItem.getResource().getName()));
				} else {
					final ImageView imageView = new ImageView(
							resource.toString());
					configureButton(imageView);
					setGraphic(imageView);
				}
			}
			else {
				// Server specified icon
				String iconPath = iconName;
				if(iconPath.indexOf("/") == -1) {
						iconPath = "files/download/" + iconName;
				}
				else {
					if(iconName.startsWith("logo://")) {
						try {
							iconPath = "logo/" + URLEncoder.encode(typeName, "UTF-8") + "/" + URLEncoder.encode(resourceItem.getResource().getName(), "UTF-8") + "/" + iconName.substring(7);
						} catch (UnsupportedEncodingException e) {
							throw new RuntimeException(e);
						}
					}
				}
				
				final ImageView imageView = new ImageView(getClass()
						.getResource("ajax-loader.gif").toString());
				configureButton(imageView);
				setGraphic(imageView);

				String cacheKey = resourceItem.getResourceRealm().getName()
						+ "-" + iconPath;
				if (iconCache.containsKey(cacheKey)) {
					imageView.setImage(iconCache.get(cacheKey));
				} else {
					context.getLoadQueue().execute(new IconLoader(resourceItem.getResourceRealm()
							.getName(), cacheKey, imageView, iconPath, context, group) {

						@Override
						protected void onImageLoaded() {
							if(width < 0 && height < 0) {
								sizeToImage();
							} else {
								sizeToImage(width, height);
							}
							
						}
						
					});
				}
			}
		} catch (MissingResourceException mre) {
			setText("%" + typeName);
		}
		
		if(width < 0 && height < 0) {
			sizeToImage();
		} else {
			sizeToImage(width, height);
		}

		// Bouncer for click events
		double bounceSpeed = 50;
		bouncer.setCycleCount(3);
		bouncer.getKeyFrames().addAll(
				makeKeyFrame(0, 0, 1.0, 1.0),
				makeKeyFrame(bounceSpeed * 2f, 4, 1.0, 1.0),
				
				makeKeyFrame(bounceSpeed * 2.5f, 4, 1.1, 0.9),
				makeKeyFrame(bounceSpeed * 4.5f, 0, 1.0, 1.0),
				makeKeyFrame(bounceSpeed * 6.5f, -4, 1.0, 1.0),
				makeKeyFrame(bounceSpeed * 8.5f, 0, 1.0, 1.0)
				);
		bouncer.setOnFinished(value -> {
			if(launching) {
				bouncer.play();
			}
		});
		
		// Grower for hover in
		grower.getKeyFrames().addAll(makeKeyFrame(0, 0, 1.0, 1.0), makeKeyFrame(100, 0, 1.2, 1.2));

		// Shrinker for hover out
		shrinker.getKeyFrames().addAll(makeKeyFrame(0, 0, 1.2, 1.2), makeKeyFrame(100, 0, 1.0, 1.0));

		// Monitor mouse activity
		setOnMouseEntered(value -> {
			grower.play();
		});
		setOnMouseExited(value -> {
			shrinker.play();
		});
	}
	@Override
	protected void onInitiateLaunch() {
		launching = true;
		bouncer.play();
	}
	
	@Override
	protected void onFinishLaunch() {
		launching = false;
	}
	
	private KeyFrame makeKeyFrame(double d, double y, double sx, double sy) {
		return new KeyFrame(Duration.millis(d), 
				new KeyValue(translateYProperty(),y), 
				new KeyValue(scaleXProperty(), sx),
				new KeyValue(scaleYProperty(), sy));
	}

	private void configureButton(final ImageView imageView) {
		/*imageView.setFitHeight(32);
		imageView.setFitWidth(32);
		imageView.setPreserveRatio(true);
		imageView.setSmooth(true);
		imageView.getStyleClass().add("launcherIcon");*/
	}
}
