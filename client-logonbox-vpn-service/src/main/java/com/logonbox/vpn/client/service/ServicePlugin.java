package com.logonbox.vpn.client.service;


public interface ServicePlugin {

	boolean start(ClientContext context);

	void stop();

	String getName();

}
