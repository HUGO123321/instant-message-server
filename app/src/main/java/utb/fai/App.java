package utb.fai;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class App {

    private static final int DEFAULT_PORT = 33000;
    private static final int DEFAULT_MAX_CONNECTIONS = 2;

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        int maxConnections = DEFAULT_MAX_CONNECTIONS;

        if (args.length > 0 && args[0].equals("--help")) {
            System.out.println("Usage: Server [PORT] [MAX_CONNECTIONS]");
            System.out.println("Default port: " + DEFAULT_PORT);
            System.out.println("Default maximum connections: "
                    + DEFAULT_MAX_CONNECTIONS);
            return;
        }

        if (args.length > 2) {
            System.err.println("Error: Too many arguments.");
            return;
        }

        try {
            if (args.length >= 1) {
                port = Integer.parseInt(args[0]);
            }

            if (args.length >= 2) {
                maxConnections = Integer.parseInt(args[1]);
            }
        } catch (NumberFormatException e) {
            System.err.println("Error: Arguments must be integers.");
            return;
        }

        if (port < 1 || port > 65535) {
            System.err.println("Error: Port must be between 1 and 65535.");
            return;
        }

        if (maxConnections < 1) {
            System.err.println("Error: Maximum connections must be positive.");
            return;
        }

        ActiveHandlers activeHandlers = new ActiveHandlers();
        Semaphore connectionSlots = new Semaphore(maxConnections);
        ExecutorService pool =
                Executors.newFixedThreadPool(maxConnections * 2);

        System.out.printf(
                "IM server listening on port %d, maximum connections=%d...%n",
                port, maxConnections
        );

        try (ServerSocket serverSocket = new ServerSocket(port)) {

            while (!Thread.currentThread().isInterrupted()) {
                Socket clientSocket = serverSocket.accept();
                clientSocket.setKeepAlive(true);

                if (!connectionSlots.tryAcquire()) {
                    try (Socket rejected = clientSocket;
                         PrintWriter writer = new PrintWriter(
                                 rejected.getOutputStream(), true)) {
                        writer.println("Server is full. Try again later.");
                    } catch (IOException e) {
                        System.err.println(
                                "Could not reject client: " + e.getMessage()
                        );
                    }
                    continue;
                }

                System.out.println(
                        "Client connected: "
                                + clientSocket.getRemoteSocketAddress()
                );

                SocketHandler handler = new SocketHandler(
                        clientSocket,
                        activeHandlers,
                        connectionSlots::release
                );

                pool.execute(handler.inputHandler);
                pool.execute(handler.outputHandler);
            }

        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        } finally {
            pool.shutdownNow();

            try {
                if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.err.println("Some server tasks did not terminate.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
