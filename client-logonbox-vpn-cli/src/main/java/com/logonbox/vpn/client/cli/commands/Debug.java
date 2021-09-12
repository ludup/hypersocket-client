package com.logonbox.vpn.client.cli.commands;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Map;
import java.util.concurrent.Callable;

import org.apache.commons.lang3.StringUtils;

import com.logonbox.vpn.client.cli.CLIContext;
import com.logonbox.vpn.client.cli.ConsoleProvider;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(name = "debug", usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Show debug information.")
public class Debug implements Callable<Integer> {

	@Spec
	private CommandSpec spec;

	@Override
	public Integer call() throws Exception {
		CLIContext cli = (CLIContext) spec.parent().userObject();
		ConsoleProvider console = cli.getConsole();
		PrintWriter out = console.out();
		out.println("Environment:");
		for(Map.Entry<String, String> ee : System.getenv().entrySet()) {
			out.println(String.format("  %s=%s", ee.getKey(), StringUtils.abbreviate(ee.getValue(), 40)));
		}
		out.println();
		out.println("System:");
		for(Map.Entry<Object, Object> ee : System.getProperties().entrySet()) {
			out.println(String.format("  %s=%s", ee.getKey(), StringUtils.abbreviate((String)ee.getValue(), 40)));
		}
		out.println();
		out.println("TTY:");
		out.println("   stty: " + cmd("stty", "-g"));
		out.println("   tty: " + cmd("tty"));
		console.flush();
		return 0;
	}
	
	protected String cmd(String... args) throws IOException {
		ProcessBuilder b = new ProcessBuilder(args);
		b.inheritIO();
		b.redirectErrorStream(true);
		Process p = b.start();
		try(ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			try(InputStream in = p.getInputStream()) {
				in.transferTo(out);
			}
			try {
				p.waitFor();
			} catch (InterruptedException e) {
				throw new IOException("Interrupted.", e);
			}
			return new String(out.toByteArray()).trim();
		}
	}
}
