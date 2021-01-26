package com.logonbox.vpn.common.client;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ConfigurationService extends Remote {

	public final static String AUTOMATIC_UPDATES = "automaticUpdates";
	public final static String PHASE = "phase";

	String getValue(String name, String defaultValue) throws RemoteException;
	
	void setValue(String name, String value) throws RemoteException; 
}
