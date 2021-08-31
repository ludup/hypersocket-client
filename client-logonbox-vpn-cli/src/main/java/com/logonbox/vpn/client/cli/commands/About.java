package com.logonbox.vpn.client.cli.commands;

import java.io.PrintWriter;
import java.util.concurrent.Callable;

import com.hypersocket.json.version.HypersocketVersion;
import com.logonbox.vpn.client.cli.CLIContext;
import com.logonbox.vpn.common.client.Util;
import com.logonbox.vpn.common.client.dbus.VPN;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(name = "about", mixinStandardHelpOptions = true, description = "Show version information.")
public class About implements Callable<Integer> {

	@Spec
	private CommandSpec spec;

	@Override
	public Integer call() throws Exception {
		CLIContext cli = (CLIContext) spec.parent().userObject();
		cli.about();
		PrintWriter writer = cli.getConsole().out();
		if(HypersocketVersion.getVersion("com.logonbox/client-logonbox-vpn-cli").indexOf("-SNAPSHOT") != -1) {

			try {
				VPN vpn = cli.getVPN();
				long vpnFreeMemory = vpn == null ? 0 : vpn.getFreeMemory();
				long vpnMaxMemory = vpn == null ? 0 : vpn.getMaxMemory();
				long vpnUsedMemory = vpnMaxMemory - vpnFreeMemory;
				writer.println();
				writer.println("Service Memory");
				writer.println("==============`");
				writer.println(String.format("Max Memory: %s", Util.toHumanSize(vpnMaxMemory)));
				writer.println(String.format("Free Memory: %s", Util.toHumanSize(vpnFreeMemory)));
				writer.println(String.format("Used Memory: %s", Util.toHumanSize(vpnUsedMemory)));
			}
			catch(IllegalStateException ise) {
				writer.println("Service not available.");
			}
			writer.println();
			long freeMemory = Runtime.getRuntime().freeMemory();
			long maxMemory = Runtime.getRuntime().maxMemory();
			long usedMemory = maxMemory - freeMemory;
			writer.println("CLI Memory");
			writer.println("==========");
			writer.println(String.format("Max Memory: %s", Util.toHumanSize(maxMemory)));
			writer.println(String.format("Free Memory: %s", Util.toHumanSize(freeMemory)));
			writer.println(String.format("Used Memory: %s", Util.toHumanSize(usedMemory)));
			
		}
		
		return 0;
	}
}
