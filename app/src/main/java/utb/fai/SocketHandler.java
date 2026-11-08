package utb.fai;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SocketHandler {

    private static final String STOP_MESSAGE = "\u0000STOP\u0000";

    private final Socket mySocket;
    final String clientID;
    private final ActiveHandlers activeHandlers;
    private final Runnable onDisconnected;

    volatile String name;
    final Set<String> rooms =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    final ArrayBlockingQueue<String> messages =
            new ArrayBlockingQueue<>(100);

    private final CountDownLatch startSignal = new CountDownLatch(2);

    final OutputHandler outputHandler = new OutputHandler();
    final InputHandler inputHandler = new InputHandler();

    private volatile boolean inputFinished = false;

    public SocketHandler(
            Socket mySocket,
            ActiveHandlers activeHandlers,
            Runnable onDisconnected) {

        this.mySocket = mySocket;
        this.activeHandlers = activeHandlers;
        this.onDisconnected = onDisconnected;

        clientID = mySocket.getInetAddress().getHostAddress()
                + ":" + mySocket.getPort();
    }

    private void sendMessage(String message) {
        if (!messages.offer(message)) {
            System.err.println(
                    "Message queue full for client: " + clientID
            );
        }
    }

    class OutputHandler implements Runnable {

        @Override
        public void run() {
            try {
                startSignal.countDown();
                startSignal.await();

                Writer writer = new OutputStreamWriter(
                        mySocket.getOutputStream(),
                        StandardCharsets.UTF_8
                );

                while (true) {
                    String message = messages.take();

                    if (STOP_MESSAGE.equals(message)) {
                        break;
                    }

                    writer.write(message);
                    writer.write("\r\n");
                    writer.flush();
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

            } catch (IOException e) {
                if (!mySocket.isClosed()) {
                    System.err.println(
                            "Output error for " + clientID + ": "
                                    + e.getMessage()
                    );
                }
            } finally {
                try {
                    mySocket.close();
                } catch (IOException e) {
                    System.err.println(
                            "Could not close socket: " + e.getMessage()
                    );
                }
            }

            System.out.println(
                    "Output handler finished for " + clientID
            );
        }
    }

    class InputHandler implements Runnable {

        @Override
        public void run() {
            try {
                startSignal.countDown();
                startSignal.await();

                activeHandlers.add(SocketHandler.this);

                sendMessage("You are connected from " + clientID);
                sendMessage("Please enter your name:");
                
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                mySocket.getInputStream(),
                                StandardCharsets.UTF_8
                        )
                );

                // Initial user registration.
                boolean registered = false;

                while (!registered) {
                    String request = reader.readLine();

                    if (request == null) {
                        return;
                    }

                    request = request.trim();

                    if (request.startsWith("#setMyName ")) {
                        request = request.substring(11).trim();
                    }

                    if (activeHandlers.registerName(
                            SocketHandler.this, request)) {

                        activeHandlers.joinRoom(
                                SocketHandler.this, "public"
                        );

                        registered = true;

                        sendMessage("Welcome, " + name + "!");
                        sendMessage("You joined room: public");

                    } else {
                        sendMessage(
                                "Invalid or already used name. "
                                + "Enter another name:"
                        );
                    }
                }

                // Process commands and messages.
                String request;

                while ((request = reader.readLine()) != null) {
                    if (request.equals("/QUIT")) {
                        break;
                    }

                    if (request.isEmpty()) {
                        continue;
                    }

                    handleRequest(request);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

            } catch (IOException e) {
                if (!mySocket.isClosed()) {
                    System.err.println(
                            "Input error for " + clientID + ": "
                                    + e.getMessage()
                    );
                }

            } finally {
                inputFinished = true;

                activeHandlers.remove(SocketHandler.this);

                // Wake up OutputHandler even if the queue is full.
                messages.clear();
                messages.offer(STOP_MESSAGE);

                try {
                    mySocket.close();
                } catch (IOException e) {
                    System.err.println(
                            "Could not close socket: " + e.getMessage()
                    );
                }

                onDisconnected.run();

                System.out.println(
                        "Client disconnected: " + clientID
                );
            }
        }

        private void handleRequest(String request) {

            if (request.startsWith("#setMyName ")) {
                String newName = request.substring(11).trim();

                if (activeHandlers.registerName(
                        SocketHandler.this, newName)) {

                    sendMessage("Your name is now " + name);

                } else {
                    sendMessage(
                            "Invalid or already used name."
                    );
                }

            } else if (request.startsWith("#join ")) {
                String roomName = request.substring(6).trim();

                if (roomName.isEmpty()
                        || roomName.chars()
                        .anyMatch(Character::isWhitespace)) {

                    sendMessage("Invalid room name.");
                    return;
                }

                activeHandlers.joinRoom(
                        SocketHandler.this, roomName
                );

                sendMessage("You joined room: " + roomName);

            } else if (request.startsWith("#leave ")) {
                String roomName = request.substring(7).trim();

                if (roomName.isEmpty()) {
                    sendMessage("Invalid room name.");
                    return;
                }

                if (activeHandlers.leaveRoom(
                        SocketHandler.this, roomName)) {

                    sendMessage("You left room: " + roomName);

                } else {
                    sendMessage("You are not in room: " + roomName);
                }

            } else if (request.equals("#groups")) {

                sendMessage(activeHandlers.getGroups(
                        SocketHandler.this
                ));

            } else if (request.startsWith("#sendPrivate ")) {

                String arguments = request.substring(13).trim();

                int separator = arguments.indexOf(' ');

                if (separator <= 0
                        || separator == arguments.length() - 1) {

                    sendMessage(
                            "Usage: #sendPrivate <name> <message>"
                    );
                    return;
                }

                String targetName =
                        arguments.substring(0, separator);

                String message =
                        arguments.substring(separator + 1).trim();

                if (activeHandlers.sendPrivateMessage(
                        SocketHandler.this, targetName, message)) {

                    sendMessage("Private message sent to " + targetName);

                } else {
                    sendMessage(
                            "User not found or message could not be delivered: "
                                    + targetName
                    );
                }

            } else if (request.startsWith("#")) {

                sendMessage("Unknown command: " + request);

            } else {

                activeHandlers.sendMessageToSharedRooms(
                        SocketHandler.this, request
                );
            }
        }
    }
}
