/*******************************************************************************
 * Copyright (c) 2013 Hypersocket Limited.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package com.hypersocket.client;

import java.util.ArrayList;
import java.util.List;

public class ResourceGroupsWrapper extends Result {

	List<JsonResourceGroup> resources = new ArrayList<JsonResourceGroup>();
	
	public ResourceGroupsWrapper() {
		
	}

	public List<JsonResourceGroup> getResources() {
		return resources;
	}

	public void setResources(List<JsonResourceGroup> resources) {
		this.resources = resources;
	}
	
	
}
