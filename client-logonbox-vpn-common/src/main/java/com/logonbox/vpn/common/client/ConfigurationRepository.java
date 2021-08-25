package com.logonbox.vpn.common.client;

public interface ConfigurationRepository {

	public final static String LOG_LEVEL = "logLevel";
	public final static String IGNORE_LOCAL_ROUTES = "ignoreLocalRoutes";
	public final static String DNS_INTEGRATION_METHOD = "dnsIntegrationMethod";
	public final static String AUTOMATIC_UPDATES = "automaticUpdates";
	public final static String PHASE = "phase";

	String getValue(String name, String defaultValue);
	
	void setValue(String name, String value); 
}
