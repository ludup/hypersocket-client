package com.logonbox.vpn.client.cli;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;

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

	public int getHeaderSize() {
		return headerSize;
	}

	public void setHeaderSize(int headerSize) {
		this.headerSize = headerSize;
		updateBar();
	}

	public int getProgressSize() {
		return progressSize;
	}

	public void setProgressSize(int progressSize) {
		this.progressSize = progressSize;
		updateBar();
	}

	public int getMessageSize() {
		return messageSize;
	}

	public void setMessageSize(int messageSize) {
		this.messageSize = messageSize;
		updateBar();
	}

	protected void updateBar() {
		try {
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
			cli.getConsole().out()
					.print(String.format("%-" + headerSize + "s |%s| %-" + messageSize + "s\r",
							header == null ? "" : StringUtils.left(header, headerSize), progress,
							message == null ? "" : StringUtils.left(message, messageSize)));
		} catch (IOException ioe) {
			throw new IllegalStateException("Failed to clear.", ioe);
		}
	}

	public void clear() {
		try {
			cli.getConsole().out().print(String
					.format("%-" + headerSize + "s  %-" + progressSize + "s  %" + messageSize + "s\r", "", "", ""));
		} catch (IOException ioe) {
			throw new IllegalStateException("Failed to clear.", ioe);
		}
	}
}
