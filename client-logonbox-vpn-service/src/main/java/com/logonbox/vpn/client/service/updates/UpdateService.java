package com.logonbox.vpn.client.service.updates;

import com.hypersocket.extensions.JsonExtensionPhaseList;
import com.logonbox.vpn.common.client.dbus.VPNFrontEnd;

public interface UpdateService {

	boolean isNeedsUpdating() ;
	
	boolean isUpdating() ;

	JsonExtensionPhaseList getPhases() ;
	
	boolean isTrackServerVersion();

	void update(boolean checkOnly) ;

	String getAvailableVersion();

	void cancelUpdate();

	void clearUpdateState();

	void updateFrontEnd(VPNFrontEnd frontEnd);

	void clearCaches();

	void resetUpdateState();
}
