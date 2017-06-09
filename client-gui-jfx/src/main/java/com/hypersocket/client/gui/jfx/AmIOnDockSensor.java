package com.hypersocket.client.gui.jfx;

import java.util.concurrent.atomic.AtomicBoolean;

public class AmIOnDockSensor {

	public static final AmIOnDockSensor INSTANCE = new AmIOnDockSensor();
	
	private final AtomicBoolean sensor = new AtomicBoolean();
	
	private AmIOnDockSensor() {}
	
	public boolean isOnDock() {
		return sensor.get();
	}
	
	public void setSensor(boolean value) {
		sensor.set(value);
	}
}
