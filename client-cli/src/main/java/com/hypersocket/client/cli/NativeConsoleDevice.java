package com.hypersocket.client.cli;

import java.io.Console;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;

public class NativeConsoleDevice implements ConsoleProvider {
	private final Console console;
	
	public NativeConsoleDevice() {
		this(System.console());
	}

	public NativeConsoleDevice(Console console) {
		if(console == null)
			throw new IllegalArgumentException("No console.");
		this.console = console;
	}


	@Override
	public Reader reader() throws IOException {
		return console.reader();
	}

	@Override
	public String readLine(String fmt, Object... args) throws IOException {
		return console.readLine(fmt, args);
	}

	@Override
	public char[] readPassword(String fmt, Object... args) throws IOException {
		return console.readPassword(fmt, args);
	}

	@Override
	public PrintWriter writer() throws IOException {
		return console.writer();
	}
}