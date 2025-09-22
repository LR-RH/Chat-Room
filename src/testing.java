import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class testing {
    public static void main(String[] args) throws IOException, InterruptedException {


        String hostName = "localhost";
        int portNumber = 7;

        Socket clientsocket = new Socket(hostName, portNumber);
        ServerSocket socket = new ServerSocket(portNumber);

        TCPServer server = new TCPServer(clientsocket, "test");
        server.run();


    }
}
