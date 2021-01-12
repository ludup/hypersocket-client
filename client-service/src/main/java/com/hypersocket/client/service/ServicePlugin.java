package com.hypersocket.client.service;


public interface ServicePlugin<C extends ClientContext> {

	boolean start(C context);

	void stop();

	String getName();

}
