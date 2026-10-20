
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
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
    private SocketChannel channel;
 
    public TCPConnection(String myIP,int myPort,String serverIP,int serverPort) throws IOException {
        channel = SocketChannel.open();
        channel.bind(new InetSocketAddress(myIP, myPort));
        channel.connect(new InetSocketAddress(serverIP, serverPort));   // blocking connect
    }
 
    public void send(byte[] data_inbytes) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(data_inbytes);
        while (b.hasRemaining()) {
            channel.write(b);
        }
    }
 
    //read n bytes, wait until you read n (works only while the channel is still in blocking mode)
    public byte[] receiveBytes(int n) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(n);
        while (b.hasRemaining()) {
            if (channel.read(b) == -1) {
                throw new EOFException("connection closed before " + n + " bytes arrived");
            }
        }
        return b.array();
    }

    public SocketChannel getChannel() {
        return channel;
    }
    
    @Override
    public void close() throws IOException {
        channel.close();
    }
}


class VN {

    static final int TUPLE_SIZE = 13;         
    static final long PERIOD_MS = 10000;
    static char myId = 0;                   
    static int mySeq = 0;
    static List<Neighbour> neighbours = new ArrayList<>();         
    static Map<Character, Integer> lastSeq = new TreeMap<>();      
    static Map<Character, List<Neighbour>> lsdb = new TreeMap<>(); 
    static DatagramChannel udp;      


    public static void main(String[] args) {
        String server_ipaddr=args[0];
        int my_udp_port= Integer.parseInt(args[1]);

        InetAddress local =null;
        try {
            local=InetAddress.getLocalHost();
        } catch (Exception e) {
            System.out.println("Unable to get my own IP address");
            System.exit(1);
        }
        String my_ip = local.getHostAddress();

        
        //UDP connection for myself
        DatagramSocket my_udp_socket=null;
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
            neighbours = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                ByteBuffer buf = ByteBuffer.wrap(on_connection.receiveBytes(TUPLE_SIZE));  
                char node = (char) buf.get();                       
                byte[] ipBytes = new byte[4];
                buf.get(ipBytes);                                
                String ip = InetAddress.getByAddress(ipBytes).getHostAddress();
                int port = buf.getInt();                            
                int cost = buf.getInt();                            

                if (cost == 0) {
                    myId = node;                                    
                } else {
                    neighbours.add(new Neighbour(node, ip, port, cost));
                }
            }
            //Debugging purposes
            // StringBuilder sb = new StringBuilder();
            // sb.append(myId).append("=0");
            // for (Neighbour n : neighbours) {
            //     sb.append(",").append(n.node).append("=").append(n.cost);
            // }
            // System.out.println(sb);
        } catch (IOException e) {
            System.out.println("Failed to read LINK-STATE: " + e.getMessage());
            System.exit(1);
        }

    }


}
