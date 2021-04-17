package com.logonbox.vpn.common.client;

public interface StatusDetail {

	StatusDetail EMPTY = new StatusDetail() {

		@Override
		public long getRx() {
			return 0;
		}

		@Override
		public long getTxBps() {
			return 0;
		}

		@Override
		public long getRxBps() {
			return 0;
		}

		@Override
		public long getTx() {
			return 0;
		}

		@Override
		public long getLastHandshake() {
			return 0;
		}

	};

	long getTx();

	long getRx();

	long getTxBps();

	long getRxBps();

	long getLastHandshake();
}
