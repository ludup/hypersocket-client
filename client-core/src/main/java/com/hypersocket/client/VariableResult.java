package com.hypersocket.client;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VariableResult extends Result {

	Map<String, String> resource;

	public VariableResult() {
	}

	public Map<String, String> getResource() {
		return resource;
	}

	public void setResource(Map<String, String> resource) {
		this.resource = resource;
	}

}
