/*******************************************************************************
 * Copyright (c) 2013 LogonBox Limited.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package com.hypersocket.client.cli;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

import com.hypersocket.client.Prompt;

public class Logon {

	List<Prompt> prompts;
	Map<String, String> hidden = new HashMap<String, String>();
	Map<String, String> results = new HashMap<String, String>();
	CLI cli;

	public Logon(List<Prompt> prompts, CLI cli) {
		this.prompts = prompts;
		this.cli = cli;
	}

	public void show() throws IOException {
		ConsoleProvider c = cli.getConsole();
		for (Prompt prompt : prompts) {
			switch (prompt.getType()) {
			case TEXT: {
				String v = null;
				if (StringUtils.isNotBlank(prompt.getDefaultValue())) {
					v = c.readLine("%s (RETURN for default value of '%s'): ", prompt.getLabel(),
							prompt.getDefaultValue());
					if (v.isEmpty())
						v = prompt.getDefaultValue();
				} else {
					v = c.readLine("%s:", prompt.getLabel(), prompt.getDefaultValue());
				}
				if (v == null) {
					// Ctrl+D or stdin lost for some reason
					return;
				}
				results.put(prompt.getResourceKey(), v);
				break;
			}
			case PASSWORD: {
				String v = null;
				if (StringUtils.isNotBlank(prompt.getDefaultValue())) {
					char[] pw = c.readPassword("%s (RETURN for default value '%s'): ", prompt.getLabel(),
							prompt.getDefaultValue());
					if (pw != null)
						if (pw.length == 0)
							v = prompt.getDefaultValue();
						else
							v = new String(pw);
				} else {
					char[] pw = c.readPassword("%s: ", prompt.getLabel(), prompt.getDefaultValue());
					if (pw != null)
						v = new String(pw);
				}

				if (v == null) {
					// Ctrl+D or stdin lost for some reason
					return;
				}

				results.put(prompt.getResourceKey(), v);
				break;
			}
			default:
				System.err.println(
						String.format("Unhandled type '%s' for '%s'", prompt.getType(), prompt.getResourceKey()));
				break;
			}
		}
	}

	public Map<String, String> getResults() {
		return results;
	}

}
