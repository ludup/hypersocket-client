package com.hypersocket.client.gui.jfx;

import java.util.ResourceBundle;

import javafx.application.Platform;
import javafx.scene.control.OverrunStyle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class LauncherButton extends ImageButton {
	static Logger log = LoggerFactory.getLogger(LauncherButton.class);
	
	private final ResourceItem resourceItem;

	public LauncherButton(ResourceBundle resources, ResourceItem resourceItem,
			Client context) {
		this.resourceItem = resourceItem;
		
		setTextOverrun(OverrunStyle.CLIP);
		setOnAction((event) -> {
			onInitiateLaunch();
			new Thread() {
				public void run() {
					launch();
				}
			}.start();
		});

	}
	
	public ResourceItem getResourceItem() {
		return resourceItem;
	}
	
	protected void onInitiateLaunch() {
		// Called before the launch on the JFX thread
	}
	
	protected void onBeforeLaunch() {
		// Called before the launch on the launch thread
	}
	
	protected void onAfterLaunch() {
		// Called when launch is complete on the launch thread
	}
	
	protected void onFinishLaunch() {
		// Called when launch is complete on the JFX thread
	}

	public void launch() {
		onBeforeLaunch();
		Thread t = new Thread("Launch") {
			public void run() {
				try {
					resourceItem.getResource().getResourceLauncher().launch();
				}
				finally {
					onAfterLaunch();
					Platform.runLater(() -> onFinishLaunch());
				}
				
			}
		};
		t.setDaemon(true);
		t.start();
	}
}
