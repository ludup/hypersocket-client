package com.hypersocket.credentialswizard;

import java.io.FileNotFoundException;
import java.io.IOException;

public class NAMCredentialsWizard extends AbstractCredentialWizard {

	@Override
	protected String getTitle() {
		return "Nervepoint Credentials Wizard";
	}

	@Override
	protected int onPageChange(String url) {
		/*
		 * If we get redirected noop, this means there is no setup to
		 * do, so exit the VM. This should be picked up by the caller to
		 * act accordingly.
		 */
		if (url.indexOf("rpc/noop") != -1) {
			return NO_OPERATION_REQUIRED;
		}

		/* When we set 'complete=true' in the URL, the job completed */
		if (url.indexOf("complete=true") != -1) {
			return url.indexOf("problems=0") == -1 ? OPERATION_COMPLETE_INCOMPLETE_PROFILE : OPERATION_COMPLETE;
		}

		/*
		 * If we see any other page than userSetup.html or runJob.html
		 * then assume something went wrong
		 */
		if (url.indexOf("userSetup.html") != -1 || url.indexOf("runJob.html") != -1
				|| url.indexOf(".html") != -1) {
			return OPERATION_REQUIRED;
		}

		return ERROR;
	}

	public static void main(String[] args) throws FileNotFoundException, IOException {
		if (args.length < 1)
			throw new IllegalArgumentException(
					"Must supply the URL of the server");

		setLogs();
		launch(args);
	}
}
