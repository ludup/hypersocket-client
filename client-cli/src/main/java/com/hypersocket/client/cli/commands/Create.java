package com.hypersocket.client.cli.commands;

import java.net.URI;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.client.cli.CLI;
import com.hypersocket.client.cli.Command;
import com.hypersocket.client.rmi.Connection;
import com.hypersocket.client.rmi.ConnectionStatus;

public class Create implements Command {
	static Logger log = LoggerFactory.getLogger(CLI.class);

	@Override
	public void run(CLI cli) throws Exception {
			
		if(cli.getCommandLine().getArgList().size() <= 1) {
			System.err.println("Error: You must provide at least a URL for the connection!");
			return;
		}
		
		String realUri = cli.getCommandLine().getArgs()[1];
		if (!realUri.startsWith("https://")) {
			if (realUri.indexOf("://") != -1) {
				throw new IllegalArgumentException("Only HTTPS is supported.");
			}
			realUri = "https://" + realUri;
		}
		URI uri = new URI(realUri);
		if (!uri.getScheme().equals("https")) {
			throw new IllegalArgumentException("Only HTTPS is supported.");
		}

		Connection connection = cli.getConnectionService().getConnection(uri.getHost());
		
		if(connection!=null) {
			throw new IllegalArgumentException(String.format("Connection for %s already exists", uri.getHost()));
		}
		
		connection = cli.getConnectionService().createNew();
		connection.setHostname(uri.getHost());
		connection.setPort(uri.getPort() <= 0 ? 443 : uri.getPort());
		connection.setConnectAtStartup(cli.getCommandLine().hasOption("c"));
		connection.setStayConnected(cli.getCommandLine().hasOption("S"));
		
		String path = uri.getPath();
		if (path.equals("") || path.equals("/")) {
			path = "/hypersocket";
		} else if (path.indexOf('/', 1) > -1) {
			path = path.substring(0, path.indexOf('/', 1));
		}
		connection.setPath(path);

		// Prompt for authentication
		if(cli.getCommandLine().hasOption("s")) {
			if(cli.getCommandLine().getArgList().size() >= 4) {
				connection.setUsername(cli.getCommandLine().getArgs()[2]);
				connection.setPassword(cli.getCommandLine().getArgs()[3]);
			}
		}
		
		cli.getClientService().connect(cli.getConnectionService().save(connection));
		
		int status;
		do {
			Thread.sleep(500);
			status = cli.getClientService().getStatus(connection);
		} while(status==ConnectionStatus.CONNECTING);
		
		if(status==ConnectionStatus.DISCONNECTED) {
			System.out.println(String.format("Error: Failed to authenticate to %s", connection.getHostname()));
			cli.getConnectionService().delete(connection);
		} else {
			System.out.println(String.format("Created %s", connection.getHostname()));
			cli.getClientService().disconnect(connection);
		}
	}

	@Override
	public void buildOptions(Options options) {
		options.addOption(new Option("s", "saveCredentials", false, "Save the credentials"));
		options.addOption(new Option("S", "stayConnected", false, "Keep this configuration connected"));
		options.addOption(new Option("c", "connectAtStartup", false, "Connect this configuration when the client starts"));
	}
}
