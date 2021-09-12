package com.logonbox.vpn.common.client;

import java.io.IOException;

@SuppressWarnings("serial")
public class AuthenticationCancelledException extends IOException {
	
	public AuthenticationCancelledException() {
		super("Authentication cancelled.");
	}
}
