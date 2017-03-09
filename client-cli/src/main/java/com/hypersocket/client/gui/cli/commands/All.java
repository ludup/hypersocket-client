package com.hypersocket.client.gui.cli.commands;

import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.client.gui.cli.CLI;
import com.hypersocket.client.gui.cli.Command;
import com.hypersocket.client.rmi.Connection;

public class All implements Command {
	static Logger log = LoggerFactory.getLogger(CLI.class);

	@Override
	public void run(CLI cli) throws Exception {
		System.out.println(String.format("%10s %5s %5s %5s %s", "ID", "Stay", "Start", "Status", "URL"));
		System.out.println("=============================================================================");
		for (Connection connection : cli.getConnectionService().getConnections()) {
			System.out.println(String.format("%10d %5s %5s %5s %s:%d%s", connection.getId(),
					connection.isStayConnected(), connection.isConnectAtStartup(),
					cli.getClientService().isConnected(connection), 
					connection.getHostname(), connection.getPort(), connection.getPath()));
		}
	}

	@Override
	public void buildOptions(Options options) {
	}
}
