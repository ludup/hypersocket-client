package com.hypersocket.client.gui.jfx;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.client.rmi.Resource.Type;

public class SsoResources extends AbstractResourceListController {
	
	static Logger LOG = LoggerFactory.getLogger(SsoResources.class);

	private static SsoResources instance;
	
	public SsoResources() {
		instance = this;
	}
	
	public static SsoResources getInstance() {
		return instance;
	}
	
	@Override
	public void setResources(Map<ResourceGroupKey, ResourceGroupList> icons) {
		populateResource(icons, Type.SSO);
	}

}
