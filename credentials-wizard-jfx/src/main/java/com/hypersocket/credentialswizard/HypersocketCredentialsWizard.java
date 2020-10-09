package com.hypersocket.credentialswizard;

import java.io.IOException;

public class HypersocketCredentialsWizard extends AbstractCredentialWizard {

	@Override
	protected String getTitle() {
		return "LogonBox Credentials Wizard";
	}

	@Override
	protected int onPageChange(String url) {

		if (url.equals(stripTrailingSlashes(getInitialUrl()))) {
			return OPERATION_REQUIRED;
		}

		return NO_OPERATION_REQUIRED;
	}

	public static void main(String[] args) throws IOException {
		if (args.length < 1)
			throw new IllegalArgumentException("Must supply the URL of the server");

		setLogs();
		launch(args);
	}
}
