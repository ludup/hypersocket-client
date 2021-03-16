package com.logonbox.client.cli.commands;

import org.apache.commons.cli.Options;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.client.cli.CLI;
import com.logonbox.client.cli.Command;
import com.logonbox.vpn.common.client.Connection;
import com.logonbox.vpn.common.client.ConnectionStatus.Type;

public class All implements Command {
	static Logger log = LoggerFactory.getLogger(CLI.class);

	@Override
	public void run(CLI cli) throws Exception {
		System.out.println(String.format("%10s %-15s %5s %7s %9s %s", "ID", "Name", "Stay", "Startup", "Connected", "URL"));
		System.out.println("=============================================================================================");
		for (Connection connection : cli.getConnectionService().getConnections()) {
			System.out.println(String.format("%10d %-15s %5s %7s %9s %s:%d%s", connection.getId(), StringUtils.isBlank(connection.getName()) ? "Default" :  connection.getName(),
					getBooleanDisplay(connection.isStayConnected()), getBooleanDisplay(connection.isConnectAtStartup()),
					getBooleanDisplay(cli.getClientService().getStatus(connection) == Type.CONNECTED), 
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