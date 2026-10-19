
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;

class Neighbour {
    char node;
    String ip;
    int port;
    int cost;

    Neighbour(char node, String ip, int port, int cost) {
        this.node = node;
        this.ip = ip;
        this.port = port;
        this.cost = cost;
    }
}

class TCPConnection implements AutoCloseable {
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;

    public TCPConnection(String myIP,int myPort,String serverIP,int serverPort) throws IOException {
        socket = new Socket();
        socket.bind(new InetSocketAddress(myIP, myPort));
        socket.connect(new InetSocketAddress(serverIP, serverPort));
        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());
    }

    public void send(byte[] data_inbytes) throws IOException {
        out.write(data_inbytes);
        out.flush();
    }

    //read whatever available
    public String receive() throws IOException {
        byte[] buf = new byte[1024];
        int count = in.read(buf);
        if (count == -1) {
            return null;
        }
        String ans=new String(buf, 0, count, "UTF-8");
        return ans;
    }

    //read n bytes, wait until you read n , if you cant read till n
    public byte[] receiveBytes(int n) throws IOException {
        byte[] buf = new byte[n];
        in.readFully(buf);
        return buf;
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

        DatagramSocket my_udp_socket=null;
        //UDP connection for myself
        try{
            my_udp_socket = new DatagramSocket(my_udp_port);
        }
        catch (SocketException e) {
            System.out.println("UDP binding failed");
        }
        

        // TCP connection to the server
        TCPConnection on_connection = null;
        try {
            on_connection = new TCPConnection(my_ip, my_udp_port + 1,server_ipaddr, 5000);
        } catch (IOException e) {
            System.out.println("Failed to establish TCP connection: " + e.getMessage());
            System.exit(1);
        }
        //CONNECT message to the ON
        try {
            byte[] connectMsg = new byte[6];
            byte[] ipBytes = InetAddress.getByName(my_ip).getAddress();
            System.arraycopy(ipBytes, 0, connectMsg, 0, 4);
            connectMsg[4] = (byte) (my_udp_port >> 8);
            connectMsg[5] = (byte) (my_udp_port & 0xFF);
            on_connection.send(connectMsg);
        } catch (IOException e) {
            System.out.println("Failed to send CONNECT: " + e.getMessage());
            System.exit(1);
        }

        //Receive adjacency list from the ON
        try {
            int count = on_connection.receiveBytes(1)[0] & 0xFF;
            List<Neighbour> adjacency = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                ByteBuffer buf = ByteBuffer.wrap(on_connection.receiveBytes(11));
                // buf.order(ByteOrder.BIG_ENDIAN);   // already the default, shown for clarity
                char node = (char) buf.get();                        // 1 byte
                byte[] ipBytes = new byte[4];
                buf.get(ipBytes);                                    // 4 bytes, already in network order
                String ip = InetAddress.getByAddress(ipBytes).getHostAddress();
                int port = buf.getShort() & 0xFFFF;                  // like ntohs
                int cost = buf.getInt();                             // like ntohl

                adjacency.add(new Neighbour(node, ip, port, cost));
            }
        } catch (IOException e) {
            System.out.println("Failed to read LINK-STATE: " + e.getMessage());
            System.exit(1);
        }



    }
}
