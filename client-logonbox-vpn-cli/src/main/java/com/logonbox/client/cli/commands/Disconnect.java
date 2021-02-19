package com.logonbox.client.cli.commands;

import java.rmi.RemoteException;

import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.client.cli.CLI;
import com.logonbox.vpn.common.client.Connection;

public class Disconnect extends AbstractConnectionCommand {
	static Logger log = LoggerFactory.getLogger(CLI.class);

	@Override
	public void run(CLI cli) throws Exception {
		
		if(cli.getCommandLine().getArgs().length == 1 && isSingleConnection(cli)) {
			disconnect(cli.getConnectionService().getConnections().iterator().next(), cli);
		} else {
			String pattern = getPattern(cli);
			for (Connection c : getConnectionsMatching(pattern, cli)) {
				if (cli.getClientService().isConnected(c)) {
					disconnect(c, cli);
				} else {
					System.out.println(String.format("%s is not connected.", c.getHostname()));
				}
				
				if (cli.getCommandLine().hasOption('d')) {
					cli.getConnectionService().delete(c);
					System.out.println(String.format("Deleted %s", c.getHostname()));
				}
			}
		}
	}

	private void disconnect(Connection c, CLI cli) throws RemoteException, InterruptedException {
		System.out.println(String.format("Disconnecting from %s", c.getHostname()));
		cli.getClientService().disconnect(c);
		while(cli.getClientService().isConnected(c)) {
			Thread.sleep(500);
		}
		System.out.println("Disconnected");
	}

	@Override
	public void buildOptions(Options options) {
		super.buildOptions(options);
		options.addOption("d", "delete", false, "Permanently delete this connection as well.");
	}

	@SuppressWarnings("unused")
	private void printConnection(Connection connection) {
		System.out.println(String.format("Connection: %d", connection.getId()));
		System.out.println(String.format("Host: %s", connection.getHostname()));
		System.out.println(String.format("Port: %d", connection.getPort()));
		System.out.println(String.format("Path: %s", connection.getPath()));
	}
}
