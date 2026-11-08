package utb.fai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ActiveHandlers {

    private final Set<SocketHandler> activeHandlers = new HashSet<>();
    private final Map<String, SocketHandler> usersByName = new HashMap<>();
    private final Map<String, Set<SocketHandler>> rooms = new HashMap<>();

    public synchronized boolean add(SocketHandler handler) {
        return activeHandlers.add(handler);
    }

    public synchronized boolean remove(SocketHandler handler) {
        activeHandlers.remove(handler);

        if (handler.name != null) {
            usersByName.remove(handler.name, handler);
        }

        for (Set<SocketHandler> roomHandlers : rooms.values()) {
            roomHandlers.remove(handler);
        }

        handler.rooms.clear();
        return true;
    }

    public synchronized boolean registerName(
            SocketHandler handler, String newName) {

        if (newName == null || newName.isEmpty()
                || newName.chars().anyMatch(Character::isWhitespace)) {
            return false;
        }

        SocketHandler existing = usersByName.get(newName);

        if (existing != null && existing != handler) {
            return false;
        }

        if (handler.name != null) {
            usersByName.remove(handler.name, handler);
        }

        handler.name = newName;
        usersByName.put(newName, handler);

        return true;
    }

    public synchronized void joinRoom(
            SocketHandler handler, String roomName) {

        if (roomName == null || roomName.isEmpty()
                || roomName.chars().anyMatch(Character::isWhitespace)) {
            return;
        }

        rooms.computeIfAbsent(roomName, key -> new HashSet<>())
                .add(handler);

        handler.rooms.add(roomName);
    }

    public synchronized boolean leaveRoom(
            SocketHandler handler, String roomName) {

        Set<SocketHandler> roomHandlers = rooms.get(roomName);

        if (roomHandlers == null
                || !roomHandlers.remove(handler)) {
            return false;
        }

        handler.rooms.remove(roomName);

        if (roomHandlers.isEmpty()) {
            rooms.remove(roomName);
        }

        return true;
    }

    public synchronized void sendMessageToSharedRooms(
            SocketHandler sender, String message) {

        Set<SocketHandler> recipients = new HashSet<>();

        for (String roomName : sender.rooms) {
            Set<SocketHandler> roomHandlers = rooms.get(roomName);

            if (roomHandlers != null) {
                recipients.addAll(roomHandlers);
            }
        }

        recipients.remove(sender);

        String formattedMessage =
                "[" + sender.name + "] >> " + message;

        for (SocketHandler recipient : recipients) {
            if (!recipient.messages.offer(formattedMessage)) {
                System.err.println(
                        "Message queue full for client: "
                                + recipient.clientID
                );
            }
        }
    }

    public synchronized boolean sendPrivateMessage(
            SocketHandler sender,
            String targetName,
            String message) {

        SocketHandler target = usersByName.get(targetName);

        if (target == null || target == sender) {
            return false;
        }

        String formattedMessage =
                "[" + sender.name + "] >> " + message;

        if (!target.messages.offer(formattedMessage)) {
            System.err.println(
                    "Message queue full for client: " + target.clientID
            );
            return false;
        }

        return true;
    }

    public synchronized String getGroups(SocketHandler handler) {
        List<String> sortedRooms = new ArrayList<>(handler.rooms);
        Collections.sort(sortedRooms);

        return String.join(",", sortedRooms);
    }

    public synchronized boolean isRegistered(String name) {
        return usersByName.containsKey(name);
    }
}
