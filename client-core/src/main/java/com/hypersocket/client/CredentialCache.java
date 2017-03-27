package com.hypersocket.client;

import java.util.HashMap;
import java.util.Map;

public class CredentialCache {

	Map<String,Credential> cache = new HashMap<String,Credential>();
	static CredentialCache instance = new CredentialCache();
	
	public static CredentialCache getInstance() {
		return instance;
	}
	
	public Credential getCredentials(String host) {
		return cache.get(host);
	}
	
	public void saveCredentials(String host, String username, String password) {
		
		Credential c = new Credential();
		c.username = username;
		c.password = password;
		
		cache.put(host, c);
	}
		
		
	public class Credential {
		String username;
		String password;
		
		public String getPassword() {
			return password;
		}

		public String getUsername() {
			return username;
		}
	}
}
