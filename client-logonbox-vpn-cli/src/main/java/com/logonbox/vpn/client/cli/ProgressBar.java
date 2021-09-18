package com.logonbox.vpn.client.cli;

import static org.fusesource.jansi.Ansi.ansi;

import org.apache.commons.lang3.StringUtils;
import org.fusesource.jansi.Ansi.Erase;

public class ProgressBar {

	private int min;
	private int max = 100;
	private int val;
	private String header;
	private String message;
	private int headerSize = 15;
	private int progressSize = 20;
	private int messageSize = 40;
	private CLI cli;

	public ProgressBar(CLI cli) {
		this.cli = cli;
	}

	public int getMin() {
		return min;
	}

	public void setMin(int min) {
		this.min = min;
		updateBar();
	}

	public int getMax() {
		return max;
	}

	public void setMax(int max) {
		this.max = max;
		updateBar();
	}

	public int getVal() {
		return val;
	}

	public void setVal(int val) {
		this.val = val;
		updateBar();
	}

	public String getHeader() {
		return header;
	}

	public void setHeader(String header) {
		this.header = header;
		updateBar();
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
		updateBar();
	}

	protected void updateBar() {
		try {
			calcParts();
			int v = Math.min(max, Math.max(min, val));
			int pc = (int) (((float) (v - min) / (float) (max - min)) * (float) progressSize);
			StringBuilder progress = new StringBuilder();
			for (int i = 0; i < progressSize; i++) {
				if (i < pc) {
					progress.append("*");
				} else {
					progress.append("-");
				}
			}
			ConsoleProvider console = cli.getConsole();
			String hdr = String.format("%-" + headerSize + "s |%s| %-" + messageSize + "s",
					header == null ? "" : StringUtils.left(header, headerSize), progress,
					message == null ? "" : StringUtils.left(message, messageSize));
			if (console.isAnsi()) {
				console.out().print(ansi().cursorToColumn(0).append(hdr).eraseLine(Erase.FORWARD).cursorToColumn(0));
			} else {
				console.out().print(hdr + "\r");
			}
			console.flush();
		} catch (Exception ioe) {
			throw new IllegalStateException("Failed to clear.", ioe);
		}
	}

	public void clear() {
		try {
			calcParts();
			ConsoleProvider console = cli.getConsole();
			if (console.isAnsi()) {
				console.out().print(ansi().eraseLine());
			} else {
				console.out().print(String
						.format("%-" + headerSize + "s  %-" + progressSize + "s  %" + messageSize + "s\r", "", "", ""));
			}
			console.flush();
		} catch (Exception ioe) {
			throw new IllegalStateException("Failed to clear.", ioe);
		}
	}

	private void calcParts() {
		int rem = cli.getConsole().width() - 5;
		headerSize = (int) ((float) rem / 5f);
		progressSize = (int) ((float) rem / 3.75f);
		messageSize = (int) ((float) rem / 1.875);
	}
}
