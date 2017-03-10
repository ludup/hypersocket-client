package com.hypersocket.client.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;

public class BufferedDevice implements ConsoleProvider {
	private final BufferedReader reader;
	private final PrintWriter writer;

	public BufferedDevice() {
		this(new BufferedReader(new InputStreamReader(System.in)), new PrintWriter(System.out));
	}

	public BufferedDevice(BufferedReader reader, PrintWriter writer) {
		this.reader = reader;
		this.writer = writer;
	}

	@Override
	public String readLine(String fmt, Object... params) throws IOException {
		writer.printf(fmt, params);
		writer.flush();
		try {
			return reader.readLine();
		} catch (IOException e) {
			throw new IllegalStateException();
		}
	}

	@Override
	public char[] readPassword(String fmt, Object... params) throws IOException {
		return readLine(fmt, params).toCharArray();
	}

	@Override
	public Reader reader() throws IOException {
		return reader;
	}

	@Override
	public PrintWriter writer() throws IOException {
		return writer;
	}
}
