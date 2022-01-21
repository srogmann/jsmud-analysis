package org.rogmann.jsmud.dumps;

import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;

import org.rogmann.jsmud.debugger.JdwpCommand;
import org.rogmann.jsmud.debugger.JdwpCommandSet;

/**
 * Proxy to dump a JDWP-conversation between JVM and debugger.
 */
public class JdwpProxy {

	/**
	 * Main method
	 * @param args listener-port destination-ip destination-port [keepListening-flag]
	 */
	public static void main(String[] args) {
		if (args.length < 3) {
			throw new IllegalArgumentException("Usage: listener-port dest-ip dest-port [keepListening-flag]");
		}
		final int listenerPort = Integer.parseInt(args[0]);
		final String destHost = args[1];
		final int destPort = Integer.parseInt(args[2]);
		final boolean keepListening = (args.length < 4) ? false : Boolean.parseBoolean(args[3]);
		
		final PrintStream psOut = System.out;
		
		final SocketFactory socketFactory = SocketFactory.getDefault();

		psOut.println(String.format("JDWP-Proxy: %d -> %s:%d", Integer.valueOf(listenerPort), destHost, Integer.valueOf(destPort)));
		final ServerSocketFactory serverFactory = ServerSocketFactory.getDefault();
		try (final ServerSocket socketServer = serverFactory.createServerSocket(listenerPort)) {
			int connNo = 0;
			while (keepListening || connNo < 1) {
				psOut.println("Waiting on port " + listenerPort);
				try (final Socket socketClient = socketServer.accept()) {
					connNo++;
					psOut.println(String.format("Accepted connection %d: %s -> %s",
							Integer.valueOf(connNo),
							socketClient.getRemoteSocketAddress(), socketClient.getLocalSocketAddress()));
					try (final Socket socketDest = socketFactory.createSocket(destHost, destPort)) {
						try {
							processRelaying(psOut, socketClient, socketDest);
						}
						catch (IOException e) {
							throw new RuntimeException("IO-error while processing relaying", e);
						}
					}
					catch (IOException e) {
						throw new RuntimeException(String.format("IO-error while creating connection to %s:%d",
								destHost, Integer.valueOf(destPort)), e);
					}
				}
				catch (IOException e) {
					throw new RuntimeException(String.format("IO-error while waiting for connection of port %d",
							Integer.valueOf(listenerPort)), e);
				}
			}
		}
		catch (IOException e) {
			throw new RuntimeException(String.format("Can't listen on port %d", Integer.valueOf(listenerPort)), e);
		}
	}

	/**
	 * Processes a JDWP-stream.
	 * @param psOut display-stream
	 * @param socketClient client-socket
	 * @param socketDest destination-socket
	 * @throws IOException in case of an IO-error
	 */
	public static void processRelaying(final PrintStream psOut, final Socket socketClient, final Socket socketDest)
			throws IOException {
		final AtomicBoolean shouldStop = new AtomicBoolean(false);

		final Map<Integer, JdwpCommandSet> mapReqCmdSet = new ConcurrentHashMap<>();
		final Map<Integer, JdwpCommand> mapReqCmd = new ConcurrentHashMap<>();
		final JdwpParser parserClientDest = new JdwpParser("C->D ", psOut, mapReqCmdSet, mapReqCmd);
		final JdwpParser parserDestClient = new JdwpParser("D->C ", psOut, mapReqCmdSet, mapReqCmd);
		final ConnectionStreamListener listenerClientDest = (buf, offset, len) -> parserClientDest.addPart(buf, offset, len); 
		final ConnectionStreamListener listenerDestClient = (buf, offset, len) -> parserDestClient.addPart(buf, offset, len);
		final BiConsumer<Long, Long> consumerStatClientDest = (num, sum) -> psOut.println(String.format("C->D: %d packets, %d bytes", num, sum));
		final BiConsumer<Long, Long> consumerStatDestClient = (num, sum) -> psOut.println(String.format("D->C: %d packets, %d bytes", num, sum));
		
		final Consumer<IOException> consumerExcClientDest = ioExc -> {
			psOut.println(String.format("IO-error in stream %s -> %s",
					socketClient.getRemoteSocketAddress(), socketDest.getRemoteSocketAddress()));
			ioExc.printStackTrace(psOut);
		};
		final Consumer<IOException> consumerExcDestClient = ioExc -> {
			psOut.println(String.format("IO-error in stream %s -> %s",
					socketDest.getRemoteSocketAddress(), socketClient.getRemoteSocketAddress()));
			ioExc.printStackTrace(psOut);
		};
		@SuppressWarnings("resource")
		final ConnectionStreamProxy proxyClientDest = new ConnectionStreamProxy(socketClient.getInputStream(), socketDest.getOutputStream(),
				listenerClientDest, shouldStop, consumerExcClientDest, consumerStatClientDest);
		@SuppressWarnings("resource")
		final ConnectionStreamProxy proxyDestClient = new ConnectionStreamProxy(socketDest.getInputStream(), socketClient.getOutputStream(),
				listenerDestClient, shouldStop, consumerExcDestClient, consumerStatDestClient);
		psOut.println(String.format("Ready for relaying %s -> %s",
				socketClient.getRemoteSocketAddress(), socketDest.getRemoteSocketAddress()));
		final ExecutorService executor = Executors.newFixedThreadPool(2);
		executor.execute(proxyClientDest);
		executor.execute(proxyDestClient);
		executor.shutdown();
		try {
			boolean isOk = executor.awaitTermination(3600, TimeUnit.SECONDS);
			if (!isOk) {
				psOut.println("Timeout: The connections didn't finish in time.");
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Unexpected interruption", e);
		}
		psOut.println(String.format("Finished for relaying %s -> %s",
				socketClient.getRemoteSocketAddress(), socketDest.getRemoteSocketAddress()));
	}

}
