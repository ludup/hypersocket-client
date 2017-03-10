package com.hypersocket.client.cli.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.client.cli.CLI;
import com.hypersocket.client.rmi.Connection;

public class Show extends AbstractConnectionCommand {
	static Logger log = LoggerFactory.getLogger(CLI.class);

	@Override
	public void run(CLI cli) throws Exception {
		String pattern = getPattern(cli);
		for (Connection c : getConnectionsMatching(pattern, cli)) {
			printConnection(c);
			System.out.println();
		}
	}

	private void printConnection(Connection connection) {
		System.out.println(String.format("Connection: %d", connection.getId()));
		System.out.println(String.format("Host: %s", connection.getHostname()));
		System.out.println(String.format("Port: %d", connection.getPort()));
		System.out.println(String.format("Path: %s", connection.getPath()));
		System.out.println(String.format("Realm: %s", connection.getRealm()));
		System.out.println(String.format("Serial: %s", connection.getSerial()));
		System.out.println(String.format("Server Version: %s", connection.getServerVersion()));
		System.out.println(String.format("Username: %s", connection.getUsername()));
		System.out.println(String.format("Password: %s", connection.getHashedPassword()));
	}
}
