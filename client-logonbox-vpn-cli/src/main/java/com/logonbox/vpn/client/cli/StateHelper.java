package com.logonbox.vpn.client.cli;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusSigHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.vpn.common.client.Connection.Mode;
import com.logonbox.vpn.common.client.ConnectionStatus.Type;
import com.logonbox.vpn.common.client.dbus.VPNConnection;
import com.logonbox.vpn.common.client.dbus.VPNConnection.Authorize;
import com.logonbox.vpn.common.client.dbus.VPNConnection.Connected;
import com.logonbox.vpn.common.client.dbus.VPNConnection.Connecting;
import com.logonbox.vpn.common.client.dbus.VPNConnection.Disconnected;
import com.logonbox.vpn.common.client.dbus.VPNConnection.Disconnecting;
import com.logonbox.vpn.common.client.dbus.VPNConnection.Failed;
import com.logonbox.vpn.common.client.dbus.VPNConnection.TemporarilyOffline;

public class StateHelper implements Closeable {
	static Logger log = LoggerFactory.getLogger(StateHelper.class);
	
	public interface StateChange {
		void state(Type state, Mode mode) throws Exception;
	}

	private DBusConnection bus;
	private VPNConnection connection;
	private Type currentState;
	private DBusSigHandler<Authorize> authorizeSigHandler;
	private DBusSigHandler<Connected> startedSigHandler;
	private DBusSigHandler<Connecting> joiningSigHandler;
	private DBusSigHandler<Failed> failedSigHandler;
	private DBusSigHandler<Disconnected> disconnectedSigHandler;
	private DBusSigHandler<Disconnecting> disconnectingSigHandler;
	private DBusSigHandler<TemporarilyOffline> temporarilyOfflineSigHandler;
	private Object lock = new Object();
	private boolean interrupt;
	private Map<Type, StateChange> onState = new HashMap<>();
	private Exception error;

	public StateHelper(VPNConnection connection, DBusConnection bus) throws DBusException {
		this.connection = connection;
		this.bus = bus;
		currentState = Type.valueOf(connection.getStatus());
		bus.addSigHandler(VPNConnection.Disconnecting.class, connection,
				disconnectingSigHandler = new DBusSigHandler<VPNConnection.Disconnecting>() {
					@Override
					public void handle(VPNConnection.Disconnecting sig) {
						stateChange();
					}
				});
		bus.addSigHandler(VPNConnection.Disconnected.class, connection,
				disconnectedSigHandler = new DBusSigHandler<VPNConnection.Disconnected>() {
					@Override
					public void handle(VPNConnection.Disconnected sig) {
						stateChange();
					}
				});
		bus.addSigHandler(VPNConnection.Failed.class, connection,
				failedSigHandler = new DBusSigHandler<VPNConnection.Failed>() {
					@Override
					public void handle(VPNConnection.Failed sig) {
						stateChange();
					}
				});
		bus.addSigHandler(VPNConnection.Connecting.class, connection,
				joiningSigHandler = new DBusSigHandler<VPNConnection.Connecting>() {
					@Override
					public void handle(VPNConnection.Connecting sig) {
						stateChange();
					}
				});
		bus.addSigHandler(VPNConnection.Connected.class, connection,
				startedSigHandler = new DBusSigHandler<VPNConnection.Connected>() {
					@Override
					public void handle(VPNConnection.Connected sig) {
						stateChange();
					}
				});
		bus.addSigHandler(VPNConnection.Authorize.class, connection,
				authorizeSigHandler = new DBusSigHandler<VPNConnection.Authorize>() {
					@Override
					public void handle(VPNConnection.Authorize sig) {
						stateChange();
					}
				});
		bus.addSigHandler(VPNConnection.Failed.class, connection,
				failedSigHandler = new DBusSigHandler<VPNConnection.Failed>() {
					@Override
					public void handle(VPNConnection.Failed sig) {
						stateChange();
					}
				});
		bus.addSigHandler(VPNConnection.TemporarilyOffline.class, connection,
				temporarilyOfflineSigHandler = new DBusSigHandler<VPNConnection.TemporarilyOffline>() {
					@Override
					public void handle(VPNConnection.TemporarilyOffline sig) {
						stateChange();
					}
				});
	}

	public Type waitForStateNot(Type... state) throws InterruptedException {
		return waitForStateNot(-1, state);
	}

	public Type waitForStateNot(long timeout, Type... state) throws InterruptedException {
		return waitForStateNot(timeout, TimeUnit.MILLISECONDS, state);
	}

	public Type waitForStateNot(long timeout, TimeUnit unit, Type... state) throws InterruptedException {
		try {
			if (timeout < 0) {
				while (isState(state) && !interrupt) {
					synchronized (lock) {
						lock.wait();
					}
				}
			} else {
				long ms = unit.toMillis(timeout);
				while (isState(state) && ms > 0 && !interrupt) {
					long started = System.currentTimeMillis();
					synchronized (lock) {
						lock.wait(unit.toMillis(ms));
					}
					ms -= System.currentTimeMillis() - started;
				}
				if (ms <= 0)
					throw new InterruptedException("Timeout.");
			}
			return currentState;
		} finally {
			interrupt = false;
		}
	}

	public Type waitForState(Type... state) throws InterruptedException {
		return waitForState(-1, state);
	}

	public Type waitForState(long timeout, Type... state) throws InterruptedException {
		return waitForState(timeout, TimeUnit.MILLISECONDS, state);
	}

	public Type waitForState(long timeout, TimeUnit unit, Type... state) throws InterruptedException {
		try {
			if (timeout < 0) {
				while (!isState(state) && !interrupt) {
					synchronized (lock) {
						lock.wait();
					}
				}
			} else {
				long ms = unit.toMillis(timeout);
				while (!isState(state) && timeout > 0 && !interrupt) {
					long started = System.currentTimeMillis();
					synchronized (lock) {
						lock.wait(ms);
					}
					ms -= System.currentTimeMillis() - started;
				}
				if (ms < 0)
					throw new InterruptedException("Timeout.");
			}
			if(error != null) {
				if(error instanceof RuntimeException)
					throw (RuntimeException)error;
				else
					throw new IllegalStateException(error);
			}
			return currentState;
		}

		finally {
			error = null;
			interrupt = false;
		}
	}

	@Override
	public void close() throws IOException {
		try {
			onState.clear();
			bus.removeSigHandler(VPNConnection.Authorize.class, authorizeSigHandler);
			bus.removeSigHandler(VPNConnection.Connected.class, startedSigHandler);
			bus.removeSigHandler(VPNConnection.Connecting.class, joiningSigHandler);
			bus.removeSigHandler(VPNConnection.Failed.class, failedSigHandler);
			bus.removeSigHandler(VPNConnection.Disconnected.class, disconnectedSigHandler);
			bus.removeSigHandler(VPNConnection.Disconnecting.class, disconnectingSigHandler);
			bus.removeSigHandler(VPNConnection.TemporarilyOffline.class, temporarilyOfflineSigHandler);
		} catch (DBusException dbe) {
			throw new IOException("Failed to remove signal handlers.", dbe);
		}
	}

	boolean isState(Type... state) {
		for (Type s : state)
			if (s == currentState)
				return true;
		return false;
	}

	void stateChange() {
		synchronized (lock) {
			Type newState = Type.valueOf(connection.getStatus());
			if(!Objects.equals(currentState, newState)) { 
				log.info(String.format("State change from %s to %s", currentState, newState));
				currentState = newState;
				try {
					if(onState.containsKey(currentState))  {
						try {
							onState.get(currentState).state(currentState, Mode.valueOf(connection.getMode()));
						} catch (Exception e) {
							error = e;
							interrupt = true;
							log.debug("Failed state change.", e);
						}
					}
				}
				finally {
					lock.notifyAll();
				}
			}
		}
	}

	public void start(Type state) {
		currentState = state;
		error = null;
		interrupt = false;
	}
	
	public void on(Type type, StateChange run) {
		synchronized (lock) {
			onState.put(type, run);
		}
	}

	public void interrupt() {
		synchronized (lock) {
			interrupt = true;
			lock.notifyAll();
		}
	}
}
