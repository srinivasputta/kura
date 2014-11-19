package org.eclipse.kura.core.comm.dio;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.util.Date;
import java.util.Map;

import jdk.dio.DeviceConfig;
import jdk.dio.DeviceManager;
import jdk.dio.uart.UART;
import jdk.dio.uart.UARTConfig;

import org.eclipse.kura.KuraConnectException;
import org.eclipse.kura.KuraException;
import org.eclipse.kura.comm.CommConnection;
import org.eclipse.kura.comm.CommURI;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DioCommConnection implements CommConnection {

	private static final Logger s_logger = LoggerFactory
			.getLogger(DioCommConnection.class);

	private CommURI m_commUri;
	private UART m_UART;

	private InputStream m_InputStream = null;
	private OutputStream m_OutputStream = null;

	@SuppressWarnings("unused")
	private ComponentContext m_context;

	// ----------------------------------------------------------------
	//
	// Activation APIs
	//
	// ----------------------------------------------------------------

	protected void activate(ComponentContext componentContext,
			Map<String, Object> properties) {
		s_logger.info("Activating Device I/O Serial Communication Service...");
		m_context = componentContext;
	}

	protected void deactivate(ComponentContext componentContext) {
		s_logger.info("Deactivating Device I/O Serial Communication Service...");
		m_context = null;
	}

	public void updated(Map<String, Object> properties) {

	}

	// ----------------------------------------------------------------
	//
	// Private Methods
	//
	// ----------------------------------------------------------------

	private void closeStreams() {
		if (m_OutputStream != null) {
			try {
				m_OutputStream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		if (m_InputStream != null) {
			try {
				m_InputStream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private synchronized ByteBuffer getResponse(int timeout) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate(4096);
		Date start = new Date();

		while (m_InputStream.available() < 1
				&& ((new Date()).getTime() - start.getTime()) < timeout) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				// ignore
			}
		}

		while (m_InputStream.available() >= 1) {
			int c = m_InputStream.read();
			buffer.put((byte) c);
		}

		buffer.flip();

		return (buffer.limit() > 0) ? buffer : null;
	}

	private synchronized ByteBuffer getResponse(int timeout, int demark)
			throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate(4096);
		long start = System.currentTimeMillis();

		while ((m_InputStream.available() < 1)
				&& ((System.currentTimeMillis() - start) < timeout)) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
			}
		}

		start = System.currentTimeMillis();
		do {
			if (m_InputStream.available() > 0) {
				start = System.currentTimeMillis();
				int c = m_InputStream.read();
				buffer.put((byte) c);
			}
		} while ((System.currentTimeMillis() - start) < demark);

		buffer.flip();

		return (buffer.limit() > 0) ? buffer : null;
	}

	private String getBytesAsString(byte[] bytes) {
		if (bytes == null) {
			return null;
		} else {
			StringBuffer sb = new StringBuffer();
			for (byte b : bytes) {
				sb.append("0x").append(Integer.toHexString(b)).append(" ");
			}

			return sb.toString();
		}
	}

	// ----------------------------------------------------------------
	//
	// CommConnection Implementation
	//
	// ----------------------------------------------------------------

	@Override
	public CommURI getURI() {
		return m_commUri;
	}

	@Override
	public void setCommURI(CommURI URI) throws KuraException {

		m_commUri = URI;

		closeStreams();

		try {
			init();
		} catch (IOException e) {
			throw new KuraConnectException(e, m_commUri);
		}
	}

	public void init() throws IOException {

		String port = m_commUri.getPort().replace("/dev/", "");
		
		UARTConfig config = new UARTConfig(port,
				DeviceConfig.DEFAULT, m_commUri.getBaudRate(),
				m_commUri.getDataBits(), m_commUri.getParity(),
				m_commUri.getStopBits(), m_commUri.getFlowControl());

		m_UART = (UART) DeviceManager.open(UART.class, config);

	}

	@Override
	public void sendMessage(byte[] message) throws KuraException, IOException {
		if (message != null) {
			s_logger.debug("sendMessage() - " + getBytesAsString(message));

			if (m_OutputStream == null) {
				openOutputStream();
			}

			m_OutputStream.write(message, 0, message.length);
			m_OutputStream.flush();

		} else {
			throw new NullPointerException("Serial message is null");
		}
	}

	@Override
	public byte[] sendCommand(byte[] command, int timeout)
			throws KuraException, IOException {
		if (command != null) {
			s_logger.debug("sendMessage() - " + getBytesAsString(command));

			byte[] dataInBuffer = flushSerialBuffer();
			if (dataInBuffer != null && dataInBuffer.length > 0) {
				s_logger.warn("eating bytes in the serial buffer input stream before sending command: "
						+ getBytesAsString(dataInBuffer));
			}
			m_OutputStream.write(command, 0, command.length);
			m_OutputStream.flush();

			ByteBuffer buffer = getResponse(timeout);
			if (buffer != null) {
				byte[] response = new byte[buffer.limit()];
				buffer.get(response, 0, response.length);
				return response;
			} else {
				return null;
			}
		} else {
			throw new NullPointerException("Serial command is null");
		}
	}

	@Override
	public byte[] sendCommand(byte[] command, int timeout, int demark)
			throws KuraException, IOException {
		if (command != null) {
			s_logger.debug("sendMessage() - " + getBytesAsString(command));

			if (m_OutputStream == null) {
				openOutputStream();
			}
			if (m_InputStream == null) {
				openInputStream();
			}

			byte[] dataInBuffer = flushSerialBuffer();
			if (dataInBuffer != null && dataInBuffer.length > 0) {
				s_logger.warn("eating bytes in the serial buffer input stream before sending command: "
						+ getBytesAsString(dataInBuffer));
			}
			m_OutputStream.write(command, 0, command.length);
			m_OutputStream.flush();

			ByteBuffer buffer = getResponse(timeout, demark);
			if (buffer != null) {
				byte[] response = new byte[buffer.limit()];
				buffer.get(response, 0, response.length);
				return response;
			} else {
				return null;
			}
		} else {
			throw new NullPointerException("Serial command is null");
		}
	}

	@Override
	public byte[] flushSerialBuffer() throws KuraException, IOException {
		ByteBuffer buffer = getResponse(50);
		if (buffer != null) {
			byte[] response = new byte[buffer.limit()];
			buffer.get(response, 0, response.length);
			return response;
		} else {
			return null;
		}
	}

	@Override
	public void close() throws IOException {
		closeStreams();
		if (m_UART.isOpen()) {
			m_UART.close();
		}
	}

	@Override
	public DataInputStream openDataInputStream() throws IOException {
		return new DataInputStream(openInputStream());
	}

	@Override
	public InputStream openInputStream() throws IOException {
		if (m_InputStream == null) {
			m_InputStream = Channels.newInputStream(m_UART);
		}
		return m_InputStream;
	}

	@Override
	public DataOutputStream openDataOutputStream() throws IOException {
		return new DataOutputStream(openOutputStream());
	}

	@Override
	public OutputStream openOutputStream() throws IOException {
		if (m_OutputStream == null) {
			m_OutputStream = Channels.newOutputStream(m_UART);
		}
		return m_OutputStream;
	}

}
