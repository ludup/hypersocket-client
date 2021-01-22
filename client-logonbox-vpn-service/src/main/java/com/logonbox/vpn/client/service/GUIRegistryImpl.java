package com.logonbox.vpn.client.service;

import java.rmi.RemoteException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.extensions.ExtensionDefinition;
import com.logonbox.vpn.common.client.CancelledException;
import com.logonbox.vpn.common.client.GUICallback;
import com.logonbox.vpn.common.client.GUIRegistry;
import com.logonbox.vpn.common.client.Connection;

/**
 * A facade for the {@link GUIRegistryImpl} instance that should be used
 * 'service side'. It wraps remote callbacks, only calling if the RMI connection
 * hasn't been lost, and sometimes doing to exception handling as well so other
 * service side code does not have to do this.
 */
public class GUIRegistryImpl implements GUIRegistry {

	static Logger log = LoggerFactory.getLogger(GUIRegistryImpl.class);

	private GUICallback gui;
	private final Object lock = new Object();
	private boolean guiAttached;
	private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

	private ScheduledFuture<?> pingTask;

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.hypersocket.client.service.IGuiRegistry#hasGUI()
	 */
	@Override
	public boolean hasGUI() {
		return gui != null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.hypersocket.client.service.IGuiRegistry#getGUI()
	 */
	@Override
	public GUICallback getGUI() {
		return gui;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.hypersocket.client.service.IGuiRegistry#registerGUI(com.hypersocket.
	 * client.rmi.GUICallback)
	 */
	@Override
	public void registerGUI(GUICallback gui) throws RemoteException {
		if (this.gui != null)
			throw new IllegalStateException(
					"CLI interactive mode cannot be executed at the same time as the Desktop Ribbon or another instance of the CLI in interactive mode");

		synchronized (lock) {
			pingTask = scheduler.scheduleWithFixedDelay(new Runnable() {
				@Override
				public void run() {
					synchronized (lock) {
						if (GUIRegistryImpl.this.gui != null)
							try {
								GUIRegistryImpl.this.gui.ping();
							} catch (RemoteException e) {
								// Gone without informing us
								try {
									unregisterGUI(GUIRegistryImpl.this.gui, false);
								} catch (RemoteException e1) {
									GUIRegistryImpl.this.gui = null;
								}
							}
					}
				}
			}, 10, 10, TimeUnit.SECONDS);
			this.gui = gui;
			guiAttached = true;
			gui.registered();
			if (log.isInfoEnabled()) {
				log.info("Registered GUI");
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.hypersocket.client.service.IGuiRegistry#unregisterGUI(com.hypersocket.
	 * client.rmi.GUICallback)
	 */
	@Override
	public void unregisterGUI(GUICallback gui, boolean callback) throws RemoteException {
		if (log.isInfoEnabled()) {
			log.info("Unregistering GUI");
		}
		synchronized (lock) {
			if (gui == null)
				throw new IllegalStateException("Not registered " + gui);
			if (pingTask != null) {
				if (log.isInfoEnabled()) {
					log.info("Cancelling ping task");
				}
				pingTask.cancel(false);
				pingTask = null;
			}
			this.gui = null;
			guiAttached = false;
			if (callback) {
				try {
					if (log.isInfoEnabled()) {
						log.info("Informing GUI they are now unregistered.");
					}
					gui.unregistered();
				} catch (Exception e) {
					// Might EOF
				}
			}
			if (log.isInfoEnabled()) {
				log.info("Unregistered GUI");
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.hypersocket.client.service.IGuiRegistry#started(com.hypersocket.client.
	 * rmi.Connection)
	 */
	@Override
	public void started(Connection connection) {
		synchronized (lock) {
			try {
				if (gui != null && guiAttached) {
					log.info("Informing GUI " + connection + " to start");
					gui.started(connection);
				}
			} catch (RemoteException re) {
				log.error("Failed to inform GUI of readyness.", re);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.hypersocket.client.service.IGuiRegistry#ready(com.hypersocket.client.rmi.
	 * Connection)
	 */
	@Override
	public void ready(Connection connection) {
		synchronized (lock) {
			try {
				if (gui != null && guiAttached) {
					log.info("Informing GUI " + connection + " is ready");
					gui.ready(connection);
				}
			} catch (RemoteException re) {
				log.error("Failed to inform GUI of readyness.", re);
			}
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.hypersocket.client.service.IGuiRegistry#failedToConnect(com.hypersocket.
	 * client.rmi.Connection, java.lang.String)
	 */
	@Override
	public void failedToConnect(Connection connection, String reply) {
		synchronized (lock) {
			try {
				if (gui != null && guiAttached) {
					gui.failedToConnect(connection, "Could not connect. " + reply);
				}
			} catch (RemoteException re) {
				log.error("Failed to inform GUI of connection failure.", re);
			}
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.hypersocket.client.service.IGuiRegistry#disconnected(com.hypersocket.
	 * client.rmi.Connection, java.lang.String)
	 */
	@Override
	public void disconnected(Connection connection, String message) {
		synchronized (lock) {
			try {
				if (gui != null && guiAttached) {
					gui.disconnected(connection, "Disconnected. " + message);
				}
			} catch (RemoteException re) {
				log.error("Failed to inform GUI of disconnection.", re);
			}
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.hypersocket.client.service.IGuiRegistry#transportConnected(com.
	 * hypersocket.client.rmi.Connection)
	 */
	@Override
	public void transportConnected(Connection connection) {
		synchronized (lock) {
			try {
				if (gui != null && guiAttached) {
					gui.transportConnected(connection);
				}
			} catch (RemoteException re) {
				log.error("Failed to inform GUI of transport connection.", re);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.hypersocket.client.service.IGuiRegistry#notify(java.lang.String,
	 * int)
	 */
	@Override
	public void notify(String msg, int type) {
		synchronized (lock) {
			try {
				if (gui != null && guiAttached) {
					gui.notify(msg, type);
				}
			} catch (RemoteException re) {
				log.error("Failed to inform GUI of transport connection.", re);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.hypersocket.client.service.IGuiRegistry#onExtensionUpdateComplete(java.
	 * lang.String, com.hypersocket.extensions.ExtensionDefinition)
	 */
	@Override
	public void onExtensionUpdateComplete(String app, ExtensionDefinition def) {
		synchronized (lock) {
			try {
				if (gui != null && guiAttached) {
					gui.onExtensionUpdateComplete(app, def);
				}
			} catch (RemoteException ex) {
				failed(app, ex);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.hypersocket.client.service.IGuiRegistry#onUpdateProgress(java.lang.
	 * String, long, long, long)
	 */
	@Override
	public void onUpdateProgress(String app, long sincelastProgress, long totalSoFar, long totalBytesExpected) {
		synchronized (lock) {
			try {
				if (gui != null && guiAttached) {
					gui.onUpdateProgress(app, sincelastProgress, totalSoFar, totalBytesExpected);
				}
			} catch (RemoteException ex) {
				failed(app, ex);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.hypersocket.client.service.IGuiRegistry#onUpdateStart(java.lang.String,
	 * long)
	 */
	@Override
	public void onUpdateStart(String app, long totalBytesExpected, Connection connection) {
		synchronized (lock) {
			try {
				if (gui != null && guiAttached) {
					gui.onUpdateStart(app, totalBytesExpected, connection);
				}
			} catch (RemoteException ex) {
				failed(app, ex);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.hypersocket.client.service.IGuiRegistry#onUpdateInit(int)
	 */
	@Override
	public void onUpdateInit(int apps) throws RemoteException {
		synchronized (lock) {
			if (gui != null && guiAttached) {
				gui.onUpdateInit(apps);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.hypersocket.client.service.IGuiRegistry#onUpdateComplete(java.lang.
	 * String, long)
	 */
	@Override
	public void onUpdateComplete(String app, long totalBytesTransfered) {
		synchronized (lock) {
			try {
				if (gui != null && guiAttached) {
					gui.onUpdateComplete(totalBytesTransfered, app);
				}
			} catch (RemoteException ex) {
				failed(app, ex);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.hypersocket.client.service.IGuiRegistry#onUpdateFailure(java.lang.String,
	 * java.lang.Throwable)
	 */
	@Override
	public void onUpdateFailure(String app, Throwable e) {
		synchronized (lock) {
			try {
				if (gui != null && guiAttached) {
					gui.onUpdateFailure(app, e == null ? "Update failed. No reason supplied." : e.getMessage());
				}
			} catch (RemoteException ex) {
				failed(app, ex);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.hypersocket.client.service.IGuiRegistry#onUpdateDone(java.lang.String)
	 */
	@Override
	public void onUpdateDone(boolean restart, String failureMessage) throws RemoteException {
		synchronized (lock) {
			if (gui != null && guiAttached) {
				gui.onUpdateDone(restart, failureMessage);
			}
		}
	}

	private void failed(String app, RemoteException ex) {
		if (ex.getCause() instanceof CancelledException) {
			try {
				gui.onUpdateFailure(app, "Cancelled by user.");
			} catch (RemoteException ex2) {
				log.error("Failed to inform GUI of cancelled update.", ex2);
			}
		} else {
			log.error("Failed to inform GUI of update state change.", ex);
		}
		guiAttached = false;
	}

}
