package com.hypersocket.client.cli.commands;

import java.net.URI;
import java.rmi.RemoteException;

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
			if(cli.getCommandLine().getArgList().size() < 4) {
				throw new IllegalArgumentException("saveCredentials requires username and password as additional arguments");
			}
			connection.setUsername(cli.getCommandLine().getArgs()[2]);
			connection.setPassword(cli.getCommandLine().getArgs()[3]);
		}
		
		System.out.println(String.format("Created new connection for %s", uri.getHost()));
		

		int status;
		try {
			status = cli.getClientService().getStatus(connection);
		} catch (RemoteException e1) {
			status = ConnectionStatus.DISCONNECTED;
		}
		if (status == ConnectionStatus.DISCONNECTED) {
			cli.getClientService().connect(connection);
			System.out.println(String.format("Connected to: %s", CLI.getUri(connection)));
//			if(cli.getCommandLine().hasOption('s')) {
				cli.getConnectionService().save(connection);
//				System.out.println(String.format("Saved: %s", CLI.getUri(connection)));
//			}
			cli.exitWhenDone();
		} else {
			System.err.println("Request to connect an already connected or connecting connection "
					+ connection);
		}
	}

	@Override
	public void buildOptions(Options options) {
		options.addOption(new Option("s", "saveCredentials", false, "Save the credentials"));
		options.addOption(new Option("S", "stayConnected", false, "Keep this configuration connected"));
		options.addOption(new Option("c", "connectAtStartup", false, "Connect this configuration when the client starts"));
	}
}