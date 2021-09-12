package com.logonbox.vpn.client.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;

public class BufferedDevice implements ConsoleProvider {
	private final BufferedReader reader;
	private final PrintWriter out;
	private final PrintWriter err;

	public BufferedDevice() {
		this(new BufferedReader(new InputStreamReader(System.in)), new PrintWriter(System.out, true), new PrintWriter(System.err, true));
	}

	public BufferedDevice(BufferedReader reader, PrintWriter out, PrintWriter err) {
		this.reader = reader;
		this.out = out;
		this.err = err;
	}

	@Override
	public String readLine(String fmt, Object... params) throws IOException {
		out.printf(fmt, params);
		out.flush();
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
	public PrintWriter out() throws IOException {
		return out;
	}

	@Override
	public PrintWriter err() throws IOException {
		return err;
	}

	@Override
	public void flush() throws IOException {
		out.flush();
	}

	@Override
	public boolean isAnsi() {
		return false;
	}

	@Override
	public int width() {
		return 80;
	}
}
