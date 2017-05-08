package com.hypersocket.client.gui.jfx;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.client.rmi.Resource.Type;

public class NetworkResources extends AbstractResourceListController {
	
	static Logger LOG = LoggerFactory.getLogger(NetworkResources.class);

	private static NetworkResources instance;
	
	public NetworkResources() {
		instance = this;
	}
	
	public static NetworkResources getInstance() {
		return instance;
	}
	
	@Override
	public void setResources(Map<ResourceGroupKey, ResourceGroupList> icons) {
		populateResource(icons, Type.NETWORK);
	}

}
