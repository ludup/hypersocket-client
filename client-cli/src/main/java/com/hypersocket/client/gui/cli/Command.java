package com.hypersocket.client.gui.cli;

import org.apache.commons.cli.Options;

public interface Command {

	void buildOptions(Options options);
	void run(CLI cli) throws Exception;
}
