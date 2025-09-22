import javax.swing.*;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.locks.*;

public class TCPServer implements Runnable {

    private final Socket tcpClientSocket;
    private final String client;
    public static final ArrayList<String> clients = new ArrayList<>();
    private boolean shutdown;
    private String currentRoom;

    private static final Lock messageLock = new ReentrantLock();
    private static final Lock settingslock = new ReentrantLock();
    public static ArrayList<Socket> sockets = new ArrayList<>();
    public static HashMap<String, String> commands = new HashMap<>();
    public static ArrayList<String> settings = new ArrayList<>();
    public static ArrayList<String> settingValues = new ArrayList<>();
    public static ArrayList<String> bannedWords = new ArrayList<>();

    public static String nameVal = "Default";
    public static String textVal = "Default";
    public static boolean censorVal = false;

    public static Socket socketInSettings;
    public static String clientInSettings;
    public static ConcurrentQueue<String> settingsQueue = new ConcurrentQueue<>();

    private static final Map<String, List<Socket>> chatRooms = new HashMap<>();  //store chatrooms

    public TCPServer(Socket tcpClientSocket, String client) {
        this.tcpClientSocket = tcpClientSocket;
        this.client = client;
        this.currentRoom = "Main";
        this.shutdown = false;

        commands.put("/help", "Shows all commands that are available to users.");
        commands.put("/whisper", "Allows user to message another privately with the structure: /<command> <name> <message>.");
        commands.put("/members", "Shows list of all online members on the server.");
        commands.put("/inroom", "Shows list of all members in current room.");
        commands.put("/settings", "Shows all settings that can be changed.");
        commands.put("/create", "Creates a new chatroom with specified name.");
        commands.put("/join", "Join a specified chatroom");
        commands.put("/leave", "Leave current chatroom");
        commands.put("/rooms", "Lists all existing chatrooms.");
        commands.put("/sendfile", "Send a file to another user with structure /<command> <recipient>");
        commands.put("/disconnect", "Disconnect from the server.");

        settings.add("name_colour");
        settings.add("text_colour");
        settings.add("toggle_censor");
        settings.add("edit_censored");
        settings.add("exit");


        settingValues.add("\u001B[0m");
        settingValues.add("\u001B[0m");

    }

    public static void main(String[] args) throws IOException {

        //Get the port from command line
        int portNumber = Integer.parseInt(args[0]);
        //Create a socket for the server, specifying the port the server is to listen to
        ServerSocket tcpServerSocket = new ServerSocket(portNumber);

        chatRooms.putIfAbsent("Main", new ArrayList<>());

        while (true) {
            try {

                //Accept the socket clients connect
                Socket tcpClientSocket = tcpServerSocket.accept();

                PrintWriter out = new PrintWriter(tcpClientSocket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(tcpClientSocket.getInputStream()));
                String client = in.readLine();
                while(client.contains(" ")){
                    out.println("Invalid username, please enter a name without spaces");
                    client = in.readLine();
                }
                while (clients.contains(client)) {
                    out.println("Username already taken.");
                    client = in.readLine();
                }

                synchronized (messageLock) {
                    clients.add(client);
                    sockets.add(tcpClientSocket);
                }

                out.println("Connected...");

                new Thread(new TCPServer(tcpClientSocket, client)).start();

            } catch (IOException ioException) {
                System.err.println("I/O error");
                ioException.printStackTrace();
                System.exit(1);
            }
        }
    }

    @Override
    public void run() {
        System.out.println("Thread running...");
        try {
            //Write the Unicode characters over the socket by use of BufferedReaders and PrintWriters
            PrintWriter out = new PrintWriter(tcpClientSocket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(tcpClientSocket.getInputStream()));

            synchronized (chatRooms) {
                chatRooms.get("Main").add(tcpClientSocket);
            }
            currentRoom = "Main";
            out.println("Joined chatroom: " + "Main");
            String message = "has joined the chatroom.";
            sendMessage(message, client, currentRoom);

            //Read what the client has received as input and send it back
            String inputLine;
            while (!shutdown && (inputLine = in.readLine()) != null) {
                System.out.println("Received: " + inputLine);
                if (!inputLine.isEmpty()) {
                    if (Objects.equals(inputLine.split(" ")[0], "/port")) {
                        //ignore
                    } else if (inputLine.charAt(0) == '/') {
                        processCommand(inputLine, client);
                    } else {
                        sendMessage(inputLine, client, currentRoom);
                    }
                }
            }

            System.out.println(client + " disconnected.");
            out.println("Disconnected from server...");
            sendMessage("has left.", client, currentRoom);
            synchronized (chatRooms) {
                chatRooms.get(currentRoom).remove(tcpClientSocket);
            }
            messageLock.lock();
            try {
                clients.remove(client);
                sockets.remove(tcpClientSocket);
            } finally {
                messageLock.unlock();
            }
        } catch (IOException ioException) {
            System.err.println("I/O error");
            ioException.printStackTrace();
            System.exit(1);
        } finally {
            try {
                tcpClientSocket.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public synchronized void sendMessage(String message, String client, String roomName) throws IOException {

        message = settingValues.get(0) + client + ": " + settingValues.get(1) + message + "\u001B[0m";

        synchronized (chatRooms) {
            synchronized (messageLock) {
                for (int c = 0; c < clients.size(); c++) {
                    if (!clients.get(c).equals(client) && chatRooms.get(roomName).contains(sockets.get(c))) {
                        if (!clients.get(c).equals(clientInSettings)) {
                            PrintWriter out = new PrintWriter(sockets.get(c).getOutputStream(), true);
                            if (censorVal) {
                                out.println(censor(message));
                            } else {
                                out.println(message);
                            }
                        } else {
                            settingsQueue.offer(message);
                        }
                    }
                }
            }
        }
    }

    public synchronized void sendWhisper(String message, String client, String recipient, String roomName) throws IOException {
        boolean found = false;

        message = settingValues.get(0) + client + "(whisper): " + settingValues.get(1) + message + "\u001B[0m";

        synchronized (chatRooms) {
            synchronized (messageLock) {
                for (int c = 0; c < clients.size(); c++) {
                    if (clients.get(c).equals(recipient)) {
                        if (!clients.get(c).equals(clientInSettings)) {
                            if (chatRooms.get(roomName).contains(sockets.get(c))) {
                                PrintWriter out = new PrintWriter(sockets.get(c).getOutputStream(), true);
                                if (censorVal) {
                                    out.println(censor(message));
                                } else {
                                    out.println(message);
                                }
                                found = true;
                            }
                        } else {
                            settingsQueue.offer(message);
                            found = true;
                        }
                    }
                }
            }
        }
        if (!found) {
            PrintWriter out = new PrintWriter(tcpClientSocket.getOutputStream(), true);
            out.println("Error: user not found, please enter a valid username");
        }
    }

    public synchronized void sendQueue() throws IOException {
        PrintWriter out = new PrintWriter(socketInSettings.getOutputStream(), true);
        while (!settingsQueue.isEmpty()) {
            if (censorVal) {
                out.println(censor(settingsQueue.poll()));
            } else {
                out.println(settingsQueue.poll());
            }
        }
    }

    public synchronized void processCommand(String inputLine, String client) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(tcpClientSocket.getInputStream()));
        PrintWriter out = new PrintWriter(tcpClientSocket.getOutputStream(), true);

        String[] command = inputLine.split(" ");

        if (Objects.equals(command[0], "/whisper")) {
            if (command.length >= 3) {
                String recipient = command[1];
                String message = "";
                for (int c = 2; c < command.length; c++) {
                    message += command[c];
                    message += " ";
                }
                sendWhisper(message, client, recipient, currentRoom);
            } else if (command.length == 2) {
                out.println("Error: Please write a message to send");
            } else {
                out.println("Error: user not found, please enter a valid username");
            }
        } else if (Objects.equals(command[0], "/help")) {
            for (Map.Entry<String, String> com : commands.entrySet()) {
                out.println(com.getKey() + ": " + com.getValue());
            }
        } else if (Objects.equals(command[0], "/members")) {
            String[] membersArray;
            synchronized (messageLock) {
                membersArray = new String[clients.size()];

                for (int i = 0; i < clients.size(); i++) {
                    membersArray[i] = "- " + clients.get(i);
                }
            }

            String members = "Online:\n" + String.join("\n", membersArray);
            out.println(members);
        } else if (Objects.equals(command[0], "/inroom")) {
            ArrayList<String> membersArray = new ArrayList<>();
            synchronized (chatRooms) {
                synchronized (messageLock) {

                    for (int i = 0; i < clients.size(); i++) {
                        if (chatRooms.get(currentRoom).contains(sockets.get(i))) {
                            membersArray.add("- " + clients.get(i));
                        }
                    }
                }
            }

            String members = "In Room:\n" + String.join("\n", membersArray);
            out.println(members);
        } else if (Objects.equals(command[0], "/settings")) {
            settings(inputLine, client, in);
        } else if (Objects.equals(command[0], "/join")) {
            if (command.length < 2) {
                out.println("Error: No room name specified.");
            } else {
                if (!chatRooms.containsKey(command[1])) {
                    out.println("Room '" + command[1] + "' does not exist.");
                } else {
                    joinRoom(command[1], out);
                    out.println("Room '" + command[1] + "' joined.");
                }
            }
        } else if (Objects.equals(command[0], "/create")) {
            if (command.length == 2) {
                if (chatRooms.containsKey(command[1])) {
                    out.println("Room '" + command[1] + "' already exists.");
                } else {
                    createRoom(command[1], out);
                }
            } else {
                out.println("Please specify the name of the room you want to make as one word.");
            }
        } else if (Objects.equals(command[0], "/leave")) {
            leaveRoom(out);
        } else if (Objects.equals(command[0], "/rooms")) {
            out.println("Here is a list of all chatrooms: ");
            synchronized (chatRooms) {
                for (String room : chatRooms.keySet()) {
                    out.println(" - " + room);
                }
            }
        } else if (Objects.equals(command[0], "/sendfile")) {
            if (command.length == 2) {
                String recipient = command[1];
                String c1 = "/sendfile";
                String c2 = "/readfile";
                setUpConnection(recipient, c1, c2, out);
                //this sets up specifically a peer to peer connection
                //between the client who sent the command
                //and the targeted recipient

            } else {
                out.println("Error. Please specify a recipient");
            }
        } else if (Objects.equals(command[0], "/sendstream")) {
            if (command.length == 2) {
                String recipient = command[1];
                setUpConnection(recipient, "/streamfile", "/readstream", out);
            } else {
                out.println("Error. Please specify a recipient");
            }
        }
        else if (Objects.equals(command[0], "/sendcam")) {
            if (command.length == 2) {
                String recipient = command[1];
                setUpConnection(recipient, "/streamcam", "/readstream", out);
            } else {
                out.println("Error. Please specify a recipient");
            }
        } else if (Objects.equals(command[0], "/disconnect")) {
            shutdown = true;
        } else {
            out.println("Error. Command not found. Please try again or use /help for a list of commands.");
        }
    }

    public synchronized void settings(String inputLine, String client, BufferedReader in) throws IOException {
        PrintWriter out = new PrintWriter(tcpClientSocket.getOutputStream(), true);;

        if (settingslock.tryLock()) {
            synchronized (settingslock) {
                clientInSettings = client;
                socketInSettings = tcpClientSocket;

                boolean cont = true;
                HashMap<String, String> colours = new HashMap<>();
                colours.put("default", "\u001B[0m");
                colours.put("black", "\u001B[30m");
                colours.put("white", "\u001B[37m");
                colours.put("red", "\u001B[31m");
                colours.put("yellow", "\u001B[33m");
                colours.put("green", "\u001B[32m");
                colours.put("blue", "\u001B[34m");
                colours.put("purple", "\u001B[35m");
                colours.put("cyan", "\u001B[36m");

                while (cont) {
                    out.println("What setting would you like to change.");
                    out.println(" - name_colour. Current = " + nameVal);
                    out.println(" - text_colour. Current = " + textVal);
                    out.println(" - toggle_censor. Current = " + censorVal);
                    out.println(" - edit_censored.");
                    out.println(" - exit");

                    String chosenSetting = in.readLine();

                    if (chosenSetting.equals(settings.get(0))) {
                        out.println("What colour would you like to change to: Default, Black, White, Red, Yellow, Green, Blue, Purple, Cyan.");

                        String chosenColour = in.readLine().toLowerCase();
                        if (colours.containsKey(chosenColour)) {
                            settingValues.set(0, colours.get(chosenColour));
                            nameVal = chosenColour;
                        } else {
                            out.println("That is not a valid colour.");
                        }

                    } else if (chosenSetting.equals(settings.get(1))) {
                        out.println("What colour would you like to change to: Default, Black, White, Red, Yellow, Green, Blue, Purple, Cyan.");

                        String chosenColour = in.readLine().toLowerCase();
                        if (colours.containsKey(chosenColour)) {
                            settingValues.set(1, colours.get(chosenColour));
                            textVal = chosenColour;
                        } else {
                            out.println("That is not a valid colour.");
                        }
                    } else if (chosenSetting.equals(settings.get(2))) {

                        if (!censorVal) {
                            out.println("Censor on.");
                            censorVal = true;
                        } else {
                            out.println("Censor off.");
                            censorVal = false;
                        }

                    } else if (chosenSetting.equals(settings.get(3))) {
                        out.println("Would you like to add or remove censored words.");
                        String censorChoice = in.readLine();

                        if (censorChoice.equals("add")) {
                            out.println("What word would you like to censor.");
                            String addWord = in.readLine();
                            bannedWords.add(addWord);
                            out.println(addWord + " has been censored.");


                        } else if (censorChoice.equals("remove")) {
                            out.println("What word would you like to un-censor.");
                            for (String bannedWord : bannedWords) {
                                out.println(bannedWord);
                            }
                            String removeWord = in.readLine();
                            if (bannedWords.contains(removeWord)) {
                                bannedWords.remove(removeWord);
                                out.println(removeWord + " has been un-censored.");

                            } else {
                                out.println("That is not a valid choice.");
                            }
                        } else {
                            out.println("That is not a valid choice.");
                        }


                    } else if (chosenSetting.equals(settings.get(4))) {
                        sendQueue();
                        clientInSettings = null;
                        socketInSettings = null;
                        cont = false;
                    } else {
                        out.println("That is not a valid setting.");
                    }
                }
            }
            settingslock.unlock();
        } else {
            out.println("Another user is currently editing settings. Please try again later");
        }
    }

    public String censor(String message) {
        for (String bannedWord : bannedWords) {
            String stars = "";
            for (int i = 0; i < bannedWord.length(); i++) {
                stars = stars.concat("*");
            }

            message = message.replaceAll(bannedWord, stars);
        }
        return message;
    }

    public synchronized void createRoom(String roomName, PrintWriter out) throws IOException {
        synchronized (chatRooms) {
            chatRooms.putIfAbsent(roomName, new ArrayList<>());
            out.println("Chatroom '" + roomName + "' created.");
            System.out.println("Chatroom '" + roomName + "' created.");
            joinRoom(roomName, out);
        }
    }

    public synchronized void joinRoom(String roomName, PrintWriter out) throws IOException {
        synchronized (chatRooms) {
            chatRooms.get(currentRoom).remove(tcpClientSocket);

            String message = "has left the chatroom.";
            sendMessage(message, client, currentRoom);

            chatRooms.get(roomName).add(tcpClientSocket);

            if (!Objects.equals(currentRoom, "Main") && chatRooms.get(currentRoom).isEmpty()) {
                chatRooms.remove(currentRoom);
            }

            currentRoom = roomName;
            out.println("Joined chatroom: " + roomName);
            message = "has joined the chatroom.";
            sendMessage(message, client, currentRoom);
        }
    }

    public synchronized void leaveRoom(PrintWriter out) throws IOException {
        synchronized (chatRooms) {
            if (!Objects.equals(currentRoom, "Main")) {
                chatRooms.get(currentRoom).remove(tcpClientSocket);
                if (chatRooms.get(currentRoom).isEmpty()) {
                    chatRooms.remove(currentRoom);
                } else {
                    String message = "has left the chatroom.";
                    sendMessage(message, client, currentRoom);
                }
                out.println("Left chatroom: " + currentRoom);
                out.println("Joined chatroom: " + currentRoom);
                currentRoom = "Main";
                out.println("Joined chatroom: " + currentRoom);
                chatRooms.get(currentRoom).add(tcpClientSocket);
                sendMessage("has joined the chatroom.", client, currentRoom);
            } else {
                out.println("Main is the default chatroom and therefore cannot be left. Try joining a chatroom instead!");
            }
        }
    }

    public void setUpConnection(String recipient, String sendCommand, String receiveCommand, PrintWriter clientOut) throws IOException {
        Socket targetSocket = null;
        synchronized (messageLock) {
            for (int c = 0; c < clients.size(); c++) {
                if (clients.get(c).equals(recipient)) {
                    targetSocket = sockets.get(c);
                }
            }
        }

        InetAddress targetAddress = targetSocket.getInetAddress();
        PrintWriter targetOut = new PrintWriter(targetSocket.getOutputStream(), true);

        targetOut.println(receiveCommand + " ");
        String message;
        int port = -1;
        boolean foundPort = false;
        BufferedReader targetIn = new BufferedReader(new InputStreamReader(targetSocket.getInputStream()));
        while (!foundPort) {
            message = targetIn.readLine();
            if ((Objects.equals(message.split(" ")[0], "/port"))) {
                port = Integer.parseInt(message.split(" ")[1]);
                foundPort = true;
                targetOut.println("/foundPort");
            }
        }

        clientOut.println(sendCommand + " " + targetAddress + " " + port);
    }
}