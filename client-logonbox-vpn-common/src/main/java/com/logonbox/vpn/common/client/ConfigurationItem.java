package com.logonbox.vpn.common.client;

public interface ConfigurationItem {

	public abstract String getName();

	public abstract void setName(String name);

	public abstract String getValue();

	public abstract void setValue(String value);

	public abstract Long getId();

}