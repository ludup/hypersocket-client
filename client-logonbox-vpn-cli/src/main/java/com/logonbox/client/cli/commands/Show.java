 package com.logonbox.client.cli.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.client.cli.CLI;
import com.logonbox.vpn.common.client.Connection;

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
	}
}
