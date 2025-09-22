import java.util.*;
import java.io.*;
import java.net.*;

public class TCPChatroom {
    private static final int PORT = 2000;
    private static final Map<String, List<ClientHandler>> chatRooms = new HashMap<>();  //store chatrooms

    public static void main(String[] args) {
        System.out.println("Chatroom server started on port " + PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String currentRoom = null;
        private String userName = null;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                userName = in.readLine().trim();
                out.println("Connected...");
                out.println("Welcome to TCPChatroom, " + userName + "!");
                // out.println("Use /create to create a chatroom.");
                // out.println("Use /join  to join a chatroom.");

                String command;
                while ((command = in.readLine()) != null) {
                    String[] parts = command.split(" ", 2);
                    if (parts.length < 2) {
                        out.println("Invalid command. Use /create <room_name> or /join <room_name>.");
                        continue;
                    }

                    String action = parts[0];
                    String roomName = parts[1].trim();

                     //chatroom creation
                    if (action.equalsIgnoreCase("/create")) {
                        if (chatRooms.containsKey(roomName)) {
                            out.println("Room '" + roomName + "' already exists.");
                        } else {
                            createRoom(roomName);
                            joinRoom(roomName);
                        }
                    }
                    
                    //joining an existing
                    else if (action.equalsIgnoreCase("/join")) {
                        if (!chatRooms.containsKey(roomName)) {
                            out.println("Room '" + roomName + "' does not exist.");
                        } else {
                            joinRoom(roomName);
                        }
                    } else {
                        out.println("Unknown command.");
                    }

                    if (currentRoom != null) {
                        break; 
                    }
                }

                String message;
                while ((message = in.readLine()) != null) {
                    if (message.equalsIgnoreCase("/leave")) {
                        leaveRoom();
                        out.println("You have left the chatroom.");
                        break;
                    } else {
                        String[] parts = message.split(" ", 2);
                        if (!(parts[0].charAt(0) == '/')) {
                            broadcastMessage(currentRoom, message, this);
                        } else {
                            if (parts.length < 2) {
                                out.println("Invalid command. Use /create <room_name> or /join <room_name>.");
                                continue;
                            }

                            String action = parts[0];
                            String roomName = parts[1].trim();

                            //chatroom creation
                            if (action.equalsIgnoreCase("/create")) {
                                if (chatRooms.containsKey(roomName)) {
                                    out.println("Room '" + roomName + "' already exists.");
                                } else {
                                    createRoom(roomName);
                                    joinRoom(roomName);
                                }
                            }

                            //joining an existing
                            else if (action.equalsIgnoreCase("/join")) {
                                if (!chatRooms.containsKey(roomName)) {
                                    out.println("Room '" + roomName + "' does not exist.");
                                } else {
                                    joinRoom(roomName);
                                }
                            } else {
                                out.println("Unknown command.");
                            }
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                leaveRoom();
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }


        //creating new chatroom
        private synchronized void createRoom(String roomName) {
            chatRooms.putIfAbsent(roomName, new ArrayList<>());
            out.println("Chatroom '" + roomName + "' created. You have joined it.");
        }

        private synchronized void joinRoom(String roomName) {
            leaveRoom(); 
            chatRooms.get(roomName).add(this);
            currentRoom = roomName;
            out.println("Joined chatroom: " + roomName);
            broadcastSystemMessage(currentRoom, "**" + userName + " has joined chatroom" + "**" );
        }

        private synchronized void leaveRoom() {
            if (currentRoom != null && chatRooms.containsKey(currentRoom)) {
                chatRooms.get(currentRoom).remove(this);
                
                if (!chatRooms.get(currentRoom).isEmpty()) {
                    broadcastSystemMessage(currentRoom, "**" + userName + " has left the chat!**");
                } else {
                    chatRooms.remove(currentRoom);
                }
                currentRoom = null;
            }
        }

        private synchronized void broadcastMessage(String roomName, String message, ClientHandler sender) {
            if (chatRooms.containsKey(roomName)) {
                for (ClientHandler client : chatRooms.get(roomName)) {
                    if (client == sender) continue; 
                    client.out.println(sender.userName + ": " + message);
                }
            }
        }

        private synchronized void broadcastSystemMessage(String roomName, String message) {
            if (chatRooms.containsKey(roomName)) {
                for (ClientHandler client : chatRooms.get(roomName)) {
                    if (client != this) { 
                        client.out.println(message);
                    }
                }
            }
        }
    }
}
