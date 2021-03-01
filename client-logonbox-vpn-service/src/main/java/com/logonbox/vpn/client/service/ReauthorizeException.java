package com.logonbox.vpn.client.service;

import java.io.IOException;

@SuppressWarnings("serial")
public class ReauthorizeException extends IOException {

	public ReauthorizeException(String message) {
		super(message);
	}

}
