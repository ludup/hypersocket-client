package com.logonbox.client.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;

public interface ConsoleProvider {
	String readLine(String fmt, Object... args) throws IOException;

	char[] readPassword(String fmt, Object... args) throws IOException;

	Reader reader() throws IOException;

	PrintWriter writer() throws IOException;
}