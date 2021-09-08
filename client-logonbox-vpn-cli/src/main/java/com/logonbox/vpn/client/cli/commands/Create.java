package com.logonbox.vpn.client.cli.commands;

import java.net.URI;
import java.util.concurrent.Callable;

import com.logonbox.vpn.client.cli.CLIContext;
import com.logonbox.vpn.client.cli.StateHelper;
import com.logonbox.vpn.common.client.Connection.Mode;
import com.logonbox.vpn.common.client.ConnectionStatus.Type;
import com.logonbox.vpn.common.client.Util;
import com.logonbox.vpn.common.client.dbus.VPNConnection;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(name = "create", mixinStandardHelpOptions = true, description = "Create a new VPN connection.")
public class Create extends AbstractConnectionCommand implements Callable<Integer> {

	@Spec
	private CommandSpec spec;

	@Option(names = { "-b", "--background" }, description = "Connect in the background and return immediately.")
	private boolean background;

	@Option(names = { "-c",
			"--connect-at-startup" }, description = "Have this connection activate when the service starts.", defaultValue = "true")
	private boolean connectAtStartup = true;

	@Option(names = { "-s", "--stay-connected" }, description = "Stay connected.", defaultValue = "true")
	private boolean stayConnected = true;

	@Option(names = { "-n", "--dont-connect-now" }, description = "Just create the connection, don't connect yet.")
	private boolean dontConnectNow;

	@Option(names = { "-m", "--mode" }, description = "The mode of connection.")
	private Mode mode = Mode.CLIENT;

	@Parameters(index = "0", description = "The URI of the server to connect to. Acceptable formats include <server[<port>]> or https://<server[<port>]>[/path].")
	private String uri;

	@Override
	public Integer call() throws Exception {
		URI uriObj = Util.getUri(uri);
		if (!uriObj.getScheme().equals("https")) {
			throw new IllegalArgumentException("Only HTTPS is supported.");
		}

		CLIContext cli = getCLI();

		long connectionId = cli.getVPN().getConnectionIdForURI(uriObj.toASCIIString());
		if (connectionId > 0) {
			if (!cli.isQuiet())
				cli.getConsole().err().println(String.format("Connection for %s already exists", uriObj));
			return 1;
		}

		connectionId = cli.getVPN().createConnection(uriObj.toASCIIString(), connectAtStartup, background, mode.name());
		if (!dontConnectNow) {
			VPNConnection connection = cli.getVPNConnection(connectionId);
			if (background) {
				connection.connect();
			} else {
				try (StateHelper stateHelper = new StateHelper(connection, cli.getBus())) {
					stateHelper.on(Type.AUTHORIZING, (state, mode) -> {
						if (mode.equals(Mode.SERVICE)) {
							cli.getConsole().out().println("Service auth");
						} else {
							throw new UnsupportedOperationException(String.format(
									"This connection requires an authorization type, %s,  which is not currently supported by the CLI tools.",
									mode));
						}
					});
					stateHelper.start(Type.CONNECTING);
					connection.connect();
					Type status = stateHelper.waitForState(Type.DISCONNECTED, Type.CONNECTED);
					if (status == Type.CONNECTED) {
						if (!cli.isQuiet())
							cli.getConsole().out().println("Ready");
						return 0;
					} else {
						if (!cli.isQuiet())
							cli.getConsole().err()
									.println(String.format("Failed to connect to %s", connection.getUri(true)));
						return 1;
					}
				}
			}
		}

		return 0;
	}
}
