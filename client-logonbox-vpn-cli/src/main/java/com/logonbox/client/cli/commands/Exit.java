package com.logonbox.client.cli.commands;

import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.client.cli.CLI;
import com.logonbox.client.cli.Command;

public class Exit implements Command {
	static Logger log = LoggerFactory.getLogger(CLI.class);

	@Override
	public void run(CLI cli) throws Exception {
		cli.exitWhenDone();
		System.out.println("Goodbye!");
	}

	@Override
	public void buildOptions(Options options) {
	}
}
