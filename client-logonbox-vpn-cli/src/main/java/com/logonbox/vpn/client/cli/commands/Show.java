package com.logonbox.vpn.client.cli.commands;

import java.io.PrintWriter;

import org.apache.commons.lang3.StringUtils;

import com.logonbox.vpn.client.cli.CLIContext;
import com.logonbox.vpn.common.client.dbus.VPNConnection;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "show", mixinStandardHelpOptions = true, description = "Show connection details.")
public class Show extends AbstractConnectionCommand {

	@Parameters(description = "Show connection details.")
	private String[] names;

	@Override
	public Integer call() throws Exception {
		CLIContext cli = getCLI();
		String pattern = getPattern(cli, names);
		for (VPNConnection c : getConnectionsMatching(pattern, cli)) {
			printConnection(cli.getConsole().out(), c);
			cli.getConsole().out().println();
		}
		return 0;
	}

	private void printConnection(PrintWriter writer, VPNConnection connection) {
		writer.println(String.format("Basic"));
		writer.println(String.format(" Connection: %d", connection.getId()));
		writer.println(String.format(" Name: %s", connection.getName()));
		writer.println(String.format(" Uri: %s", connection.getUri(true)));
		writer.println(String.format(" Status: %s", connection.getStatus()));
		writer.println(String.format(" Transient: %s", connection.isTransient() ? "Yes" : "No"));
		writer.println(String.format(" Authorized: %s", connection.isAuthorized() ? "Yes" : "No"));
		writer.println(String.format(" Shared: %s", connection.isShared() ? "Yes" : "No"));
		writer.println(String.format(" Owner: %s", connection.getOwner()));
		if(connection.isAuthorized()) {
			writer.println(String.format("VPN"));
			writer.println(String.format(" Address: %s", connection.getAddress()));
			writer.println(String.format(" Endpoint: %s:%d", connection.getEndpointAddress(), connection.getEndpointPort()));
			writer.println(String.format(" DNS: %s", String.join(",", connection.getDns())));
			if(!connection.isRouteAll())
				writer.println(String.format(" Allowed IPs: %s", String.join(",", connection.getAllowedIps())));
			writer.println(String.format(" Route All: %s", connection.isRouteAll() ? "Yes" : "No"));
			writer.println(String.format(" User Public Key: %s", connection.getUserPublicKey()));
			writer.println(String.format(" Server Public Key: %s", connection.getPublicKey()));
			writer.println(String.format(" MTU: %s", connection.getMtu()));
			writer.println(String.format(" Persistent Keep Alive: %s", connection.getPersistentKeepalive()));
			if(StringUtils.isNotBlank(connection.getPreUp()))
				writer.println(String.format(" Pre-Up: %s", connection.getPreUp()));
			if(StringUtils.isNotBlank(connection.getPostUp()))
				writer.println(String.format(" Post-Up: %s", connection.getPostUp()));
			if(StringUtils.isNotBlank(connection.getPreDown()))
				writer.println(String.format(" Pre-Down: %s", connection.getPreDown()));
			if(StringUtils.isNotBlank(connection.getPostDown()))
				writer.println(String.format(" Post-Down: %s", connection.getPostDown()));
		}
	}
}
