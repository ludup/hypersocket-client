package com.hypersocket.client.gui.jfx;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.rmi.RemoteException;

import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class IconLoader implements Runnable {
	private final ImageView imageView;
	private final String iconPath;
	private final String cacheKey;
	private final Client context;
	private final ResourceGroupList group;
	private final String host;

	public IconLoader(String host, String cacheKey, ImageView imageView, String iconPath, Client context, ResourceGroupList group) {
		this.imageView = imageView;
		this.iconPath = iconPath;
		this.cacheKey = cacheKey;
		this.host = host;
		this.context = context;
		this.group = group;
	}
	
	protected void onImageLoaded() {
		
	}

	@Override
	public void run() {
		try {
			byte[] arr = context
					.getBridge()
					.getClientService()
					.getBlob(host, iconPath, 10000);
			Image img = new Image(new ByteArrayInputStream(
					arr));
			IconButton.iconCache.put(cacheKey, img);
			Platform.runLater(new Runnable() {
				@Override
				public void run() {
					imageView.setImage(img);
					onImageLoaded();
				}
			});
		} catch (RemoteException re) {

//			String subType = Dock.getSubType(group);
//			String imgPath = String.format("types/%s.png",
//					subType);
//			URL resource = getClass().getResource(imgPath);
//			if (resource == null) {
//				IconButton.log.error("Failed to load icon.", re);
//			} else {
//				try {
//					setImageFromResource(imageView,
//							resource);
//				} catch (IOException ioe) {
//					IconButton.log.error("Failed to load icon.", ioe);
//				}
//			}
		}
	}

	private void setImageFromResource(
			final ImageView imageView, URL resource)
			throws IOException {
		InputStream openStream = resource.openStream();
		try {
			Image img = new Image(openStream);
			Platform.runLater(new Runnable() {
				@Override
				public void run() {
					imageView.setImage(img);
					onImageLoaded();
				}
			});
		} finally {
			openStream.close();
		}
	}
}