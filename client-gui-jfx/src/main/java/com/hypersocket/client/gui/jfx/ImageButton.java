package com.hypersocket.client.gui.jfx;

import javafx.scene.control.Button;

public class ImageButton extends Button {

	public void sizeToImage() {
		UIHelpers.sizeToImage(this);
	}
	
	public void sizeToImage(double width, double height) {
		UIHelpers.sizeToImage(this, width, height);
	}

	public void setTooltipText(String text) {
		setTooltip(UIHelpers.createDockButtonToolTip(text));
	}
}
