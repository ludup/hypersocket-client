package com.logonbox.vpn.common.client;

import java.io.IOException;

public interface UpdateService {

	boolean isNeedsUpdating();

	boolean isUpdating();

	String[] getPhases();

	String getAvailableVersion();

	void deferUpdate();

	boolean isUpdatesEnabled();

	void checkForUpdate() throws IOException;

	void update() throws IOException;
}
