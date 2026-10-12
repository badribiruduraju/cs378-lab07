
import java.io.*;
import java.net.*;

public class TCPConnection implements AutoCloseable {
    private Socket socket;
    private InputStream in;
    private OutputStream out;

    public TCPConnection(String myIP,int myPort,String serverIP,int serverPort) throws IOException {
        socket = new Socket();
        socket.bind(new InetSocketAddress(myIP, myPort));
        socket.connect(new InetSocketAddress(serverIP, serverPort));
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    public void send(String request) {
        out.println(request);
    }

    public String receive() throws IOException {
        return in.readLine();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}


class VN {
    public static void main(String[] args) {
        String server_ipaddr=args[1];
        int my_udp_port= Integer.parseInt(args[2]);

        InetAddress local =null;
        try {
            local=InetAddress.getLocalHost();
        } catch (Exception e) {
            System.out.println("Unable to get my own IP address");
            System.exit(1);
        }
        String my_ip = local.getHostAddress();

        // TCP connection to the server
        TCPConnection on_connection = null;
        try {
            on_connection = new TCPConnection(my_ip, my_udp_port + 1,server_ipaddr, 5000);
        } catch (IOException e) {
            System.out.println("Failed to establish TCP connection: " + e.getMessage());
            System.exit(1);
        }
        String Request=
        on_connection.send()


        //UDP connection for 
        try (DatagramSocket socket = new DatagramSocket(my_udp_port)){

        }
        catch (SocketException e) {
            System.out.println("UDP binding failed");
        }





    }
}
