package com.hypersocket.client.gui.cli;

import com.hypersocket.client.i18n.I18N;

public class Updater {

	private boolean cancelled;
	private ProgressBar progressBar;

	public Updater() {
		progressBar = new ProgressBar();
		progressBar.setHeader(I18N.getResource("client.update.updates"));
		progressBar.setMessage(I18N.getResource("client.update.checkingForUpdates"));
	}

	public boolean isCancelled() {
		return cancelled;
	}

	public void awaitingNewService() {
		progressBar.setMessage(I18N.getResource("client.update.awaitingServiceStart"));
		progressBar.setVal(progressBar.getMax());
	}

	public void done() {
		progressBar.setMessage(I18N.getResource("client.update.completed"));
		progressBar.setVal(progressBar.getMax());
	}

	public void complete(String app) {
		progressBar.clear();
		System.out.println(String.format(I18N.getResource("client.update.completedAppUpdate", app)));
	}

	public void failure(String app, String failureMessage) {
		progressBar.clear();
		if (app == null) {
			System.out.println(String.format(I18N.getResource("client.update.failedUpdates", failureMessage)));
		} else {
			System.out.println(String.format(I18N.getResource("client.update.failedAppUpdate", app, failureMessage)));
		}
	}

	public void start(String app, long totalBytesExpected) {
		progressBar.setMessage(I18N.getResource("client.update.startingAppUpdate", app));
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
