package com.logonbox.vpn.client.cli;

import java.text.MessageFormat;
import java.util.ResourceBundle;


public class Updater {

	final static ResourceBundle bundle = ResourceBundle.getBundle(Updater.class.getName());

	private boolean cancelled;
	private ProgressBar progressBar;
	private boolean quiet;

	public Updater(CLI cli) {
		progressBar = new ProgressBar(cli);
		progressBar.setHeader(bundle.getString("client.update.updates"));
		progressBar.setMessage(bundle.getString("client.update.checkingForUpdates"));
	}

	public boolean isCancelled() {
		return cancelled;
	}
	public void awaitingRestart() {
		progressBar.setMessage(bundle.getString("client.update.awaitingRestart"));
		progressBar.setVal(progressBar.getMax());
		
	}

	public void awaitingNewService() {
		progressBar.setMessage(bundle.getString("client.update.awaitingServiceStart"));
		progressBar.setVal(progressBar.getMax());
	}

	public void done() {
		progressBar.setMessage(bundle.getString("client.update.completed"));
		progressBar.setVal(progressBar.getMax());
	}

	public void complete(String app) {
		progressBar.clear();
		System.out.println(MessageFormat.format(bundle.getString("client.update.completedAppUpdate"), app));
	}

	public void failure(String app, String failureMessage) {
		progressBar.clear();
		if (app == null) {
			System.out.println(MessageFormat.format(bundle.getString("client.update.failedUpdates"), failureMessage));
		} else {
			System.out.println(MessageFormat.format(bundle.getString("client.update.failedAppUpdate"), app, failureMessage));
		}
	}

	public void start(String app, long totalBytesExpected) {
		progressBar.setMessage(MessageFormat.format(bundle.getString("client.update.startingAppUpdate"), app));
		progressBar.setMax((int) totalBytesExpected);
		progressBar.setVal(0);
	}

	public void progress(String app, long sincelastProgress, long totalSoFar) {
		progressBar.setVal((int) totalSoFar);
	}

	public void close() {
		progressBar.clear();
	}

	public void show() {
		progressBar.setVal(progressBar.getMin());
	}


}
