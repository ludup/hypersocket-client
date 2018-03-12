package com.hypersocket.client.cli.commands;

import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.client.cli.CLI;
import com.hypersocket.client.cli.Command;
import com.hypersocket.client.rmi.Connection;

public class All implements Command {
	static Logger log = LoggerFactory.getLogger(CLI.class);

	@Override
	public void run(CLI cli) throws Exception {
		System.out.println(String.format("%10s %5s %7s %9s %s", "ID", "Stay", "Startup", "Connected", "URL"));
		System.out.println("=============================================================================");
		for (Connection connection : cli.getConnectionService().getConnections()) {
			System.out.println(String.format("%10d %5s %7s %9s %s:%d%s", connection.getId(),
					getBooleanDisplay(connection.isStayConnected()), getBooleanDisplay(connection.isConnectAtStartup()),
					getBooleanDisplay(cli.getClientService().isConnected(connection)), 
					connection.getHostname(), connection.getPort(), connection.getPath()));
		}
	}

	private String getBooleanDisplay(boolean connected) {
		return connected ? "Yes" : "No";
	}

	@Override
	public void buildOptions(Options options) {
	}
}
