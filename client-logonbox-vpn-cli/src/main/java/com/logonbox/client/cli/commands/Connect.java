package com.logonbox.client.cli.commands;

import java.net.URI;
import java.rmi.RemoteException;

import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.client.cli.CLI;
import com.logonbox.vpn.common.client.Connection;
import com.logonbox.vpn.common.client.ConnectionStatus.Type;

public class Connect extends AbstractConnectionCommand {
	static Logger log = LoggerFactory.getLogger(CLI.class);

	@Override
	public void run(CLI cli) throws Exception {
		
		Connection connection= null;
		
		if(cli.getCommandLine().getArgs().length > 1) {
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
	
			connection = cli.getConnectionService().getConnection(uri.getHost());
			
			if(connection==null) {
				connection = cli.getConnectionService().createNew();
				connection.setHostname(uri.getHost());
				connection.setPort(uri.getPort() <= 0 ? 443 : uri.getPort());
				connection.setConnectAtStartup(false);
				String path = uri.getPath();
				if (path.equals("") || path.equals("/")) {
					path = "/hypersocket";
				} else if (path.indexOf('/', 1) > -1) {
					path = path.substring(0, path.indexOf('/', 1));
				}
				connection.setPath(path);

				System.out.println(String.format("Created new connection for %s", uri.getHost()));
			}
		} else if(isSingleConnection(cli)) {
			connection = cli.getConnectionService().getConnections().iterator().next();
		} else {
			throw new IllegalStateException ("Connection information is required");
		}

		Type status;
		try {
			status = cli.getClientService().getStatus(connection);
		} catch (RemoteException e1) {
			status = Type.DISCONNECTED;
		}
		if (status == Type.DISCONNECTED) {
			cli.getClientService().connect(connection);
			System.out.println(String.format("Connecting to: %s", connection.getHostname()));
			
			while(cli.getClientService().getStatus(connection)==Type.CONNECTING) {
				Thread.sleep(500);
			}
			
			status = cli.getClientService().getStatus(connection);
			
			if(status == Type.CONNECTED) {
				System.out.println("Connected");
				if(cli.getCommandLine().hasOption('s')) {
					cli.getConnectionService().save(connection);
				}
			} else {
				cli.getConsole().writer().println(String.format("Failed to connect to %s", connection.getHostname()));
			}
		} else {
			System.out.println("Error: Request to connect an already connected or connecting connection " + connection.getHostname());
		}
	}

	@Override
	public void buildOptions(Options options) {
	}
}
