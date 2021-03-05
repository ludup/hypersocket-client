package com.logonbox.vpn.client.wireguard.windows;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.forker.client.impl.jna.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;

public class WindowsWireGuardNamedPipe implements Closeable, Runnable {

	final static Logger LOG = LoggerFactory.getLogger(WindowsWireGuardNamedPipe.class);

	private static final int MAX_BUFFER_SIZE = 1024;
	private HANDLE hNamedPipe;
	private String pipeName;
	private Thread thread;
	private boolean run = true;
	private long rx;
	private long tx;
	private ByteBuffer buffer;
	private BufferedReader in;
	private IntByReference lpNumberOfBytesWritten = new IntByReference(0);
	private OutputStream out;

	public WindowsWireGuardNamedPipe(String name) throws IOException {

		/*
		 * Try to open up the named pipe to the WireGuard DLL. NOTE: This MUST be
		 * compiled to our own path so as not to interfere with the official client.
		 */
		// based on
		// https://msdn.microsoft.com/en-us/library/windows/desktop/aa365588(v=vs.85).aspx
		// \\.\pipe\ProtectedPrefix\Administrators\LogonBoxVPN\` + tunnelName
		pipeName = "\\\\.\\pipe\\ProtectedPrefix\\Administrators\\LogonBoxVPN\\" + name;
		LOG.info(String.format("Opening named pipe %s", pipeName));
		// based on
		// https://msdn.microsoft.com/en-us/library/windows/desktop/aa365592(v=vs.85).aspx
		assertCallSucceeded("WaitNamedPipe",
				Kernel32.INSTANCE.WaitNamedPipe(pipeName, (int) TimeUnit.SECONDS.toMillis(15L)));
		LOG.info("Connected to server");

		hNamedPipe = assertValidHandle("CreateNamedPipe",
				Kernel32.INSTANCE.CreateFile(pipeName, WinNT.GENERIC_READ | WinNT.GENERIC_WRITE, 0, // no sharing
						null, // default security attributes
						WinNT.OPEN_EXISTING, // opens existing pipe
						0, // default attributes
						null // no template file
				));

		IntByReference lpMode = new IntByReference(WinBase.PIPE_READMODE_MESSAGE);
		assertCallSucceeded("SetNamedPipeHandleState",
				Kernel32.INSTANCE.SetNamedPipeHandleState(hNamedPipe, lpMode, null, null));
		LOG.info(String.format("Opened named pipe %s", pipeName));

		buffer = ByteBuffer.allocate(MAX_BUFFER_SIZE);
		in = new BufferedReader(new InputStreamReader(new NamedPipedInputStream()));
		out = new BufferedOutputStream(new NamedPipeOutputStream());

		LOG.info("Await client connection");
		assertCallSucceeded("ConnectNamedPipe", Kernel32.INSTANCE.ConnectNamedPipe(hNamedPipe, null));
		LOG.info("Client connected");

		thread = new Thread(this, "WireGuardPipeMonitor" + name);
		thread.setDaemon(true);
		thread.start();
	}

	public long getRx() {
		return rx;
	}

	public long getTx() {
		return tx;
	}

	@Override
	public void run() {
		try {
			while (run) {
				out.write("get=1\n\n".getBytes("UTF-8"));
				out.flush();
				String line = in.readLine();
				if (line == null)
					break;
				line = line.trim();
				if (line.length() == 0)
					break;
				if (line.startsWith("rx_bytes=")) {
					rx += Long.parseLong(line.substring(9));
				} else if (line.startsWith("tx_bytes=")) {
					tx += Long.parseLong(line.substring(9));
				} else
					LOG.warn(String.format("Unknown response: %s", line));
			}
		} catch (Exception e) {
			if (run) {
				LOG.error("Pipe read failed.", e);
			}
		}
	}

	@Override
	public void close() throws IOException {
		run = false;
		if (thread != null) {
			thread.interrupt();
		}
		assertCallSucceeded("Named pipe handle close", Kernel32.INSTANCE.CloseHandle(hNamedPipe));
		LOG.info(String.format("Closed named pipe %s", pipeName));
	}

	class NamedPipeOutputStream extends OutputStream {

		@Override
		public void write(int b) throws IOException {
			buffer.rewind();
			buffer.put((byte) b);
			assertCallSucceeded("WriteFile",
					Kernel32.INSTANCE.WriteFile(hNamedPipe, buffer, 1, lpNumberOfBytesWritten, null));
			LOG.info(String.format("Sent 1 byte of client data - length=%d", lpNumberOfBytesWritten.getValue()));
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			buffer.rewind();
			buffer.put(b, off, len);
			assertCallSucceeded("WriteFile",
					Kernel32.INSTANCE.WriteFile(hNamedPipe, buffer, len, lpNumberOfBytesWritten, null));
			LOG.info(String.format("Sent %d bytes of client data - length=%d", len, lpNumberOfBytesWritten.getValue()));
		}

		@Override
		public void flush() throws IOException {
			// Flush the pipe to allow the client to read the pipe's contents before
			// disconnecting
			assertCallSucceeded("FlushFileBuffers", Kernel32.INSTANCE.FlushFileBuffers(hNamedPipe));
			LOG.info("Flushing to pipe");
		}

	}

	class NamedPipedInputStream extends InputStream {

		boolean closed;

		NamedPipedInputStream() {
		}

		@Override
		public void close() throws IOException {
			closed = true;
		}

		@Override
		public int read() throws IOException {
			if (closed)
				throw new IOException("Closed");

			assertCallSucceeded("ReadFile",
					Kernel32.INSTANCE.ReadFile(hNamedPipe, buffer, 1, lpNumberOfBytesWritten, null));

			while (true) {
				int readSize = lpNumberOfBytesWritten.getValue();
				LOG.info("Received client data - length=" + readSize);
				if (readSize > 0)
					return readSize;
			}

//			return readSize;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			assertCallSucceeded("ReadFile",
					Kernel32.INSTANCE.ReadFile(hNamedPipe, buffer, len, lpNumberOfBytesWritten, null));

			while (true) {
				int readSize = lpNumberOfBytesWritten.getValue();
				LOG.info("Received client data - length=" + readSize);
				buffer.get(b, off, readSize);
				if (readSize > 0)
					return readSize;
			}
//			return readSize;
		}

	}

	static final void assertCallSucceeded(String message, int result) throws IOException {
		assertCallSucceeded(message, result == 0);
	}

	static final void assertCallSucceeded(String message, boolean result) throws IOException {
		if (result) {
			return;
		}

		int hr = Kernel32.INSTANCE.GetLastError();
		if (hr == WinError.ERROR_SUCCESS) {
			throw new IOException(message + " failed with unknown reason code");
		} else {
			throw new IOException(message + " failed: hr=" + hr + " - 0x" + Integer.toHexString(hr));
		}
	}

	static final HANDLE assertValidHandle(String message, HANDLE handle) throws IOException {
		if ((handle == null) || WinBase.INVALID_HANDLE_VALUE.equals(handle)) {
			int hr = Kernel32.INSTANCE.GetLastError();
			if (hr == WinError.ERROR_SUCCESS) {
				throw new IOException(message + " failed with unknown reason code");
			} else {
				throw new IOException(message + " failed: hr=" + hr + " - 0x" + Integer.toHexString(hr));
			}
		}

		return handle;
	}
}
