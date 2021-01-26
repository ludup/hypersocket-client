package com.logonbox.client.cli.commands;

import java.net.URI;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.client.cli.CLI;
import com.logonbox.client.cli.Command;
import com.logonbox.vpn.common.client.Connection;
import com.logonbox.vpn.common.client.ConnectionStatus.Type;

public class Create implements Command {
	static Logger log = LoggerFactory.getLogger(CLI.class);

	@Override
	public void run(CLI cli) throws Exception {

		if (cli.getCommandLine().getArgList().size() <= 1) {
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

		if (connection != null) {
			throw new IllegalArgumentException(String.format("Connection for %s already exists", uri.getHost()));
		}

		connection = cli.getConnectionService().createNew();
		connection.setHostname(uri.getHost());
		connection.setPort(uri.getPort() <= 0 ? 443 : uri.getPort());
		connection.setConnectAtStartup(cli.getCommandLine().hasOption("c"));
		connection.setStayConnected(cli.getCommandLine().hasOption("S"));

		String path = uri.getPath();
		if (path != null && path.indexOf('/', 1) > -1) {
			path = path.substring(0, path.indexOf('/', 1));
		}
		connection.setPath(path);

		// Prompt for authentication
		if (cli.getCommandLine().getArgList().size() >= 3) {
			connection.setName(cli.getCommandLine().getArgs()[2]);
		}

		cli.getClientService().connect(cli.getConnectionService().save(connection));

		if (!cli.getCommandLine().hasOption("b")) {
			Type status;
			do {
				Thread.sleep(500);
				status = cli.getClientService().getStatus(connection);
			} while (status == Type.CONNECTING);

			if (status == Type.DISCONNECTED) {
				System.out.println(String.format("Error: Failed to authenticate to %s", connection.getHostname()));
				cli.getConnectionService().delete(connection);
			} else {
				System.out.println(String.format("Created %s", connection.getHostname()));
				cli.getClientService().disconnect(connection);
			}
		}
	}

	@Override
	public void buildOptions(Options options) {
		options.addOption(new Option("b", "background", false, "Connect in the background and return immediately"));
		options.addOption(new Option("S", "stayConnected", false, "Keep this configuration connected"));
		options.addOption(
				new Option("c", "connectAtStartup", false, "Connect this configuration when the client starts"));
	}
}
