package com.logonbox.client.cli;

import org.apache.commons.cli.Options;

public interface Command {

	void buildOptions(Options options);
	void run(CLI cli) throws Exception;
}
