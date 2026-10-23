
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
        this.node=node;this.ip=ip;this.port=port;this.cost=cost;
    }
}

class TCPConnection implements AutoCloseable {
    private final SocketChannel channel;
 
    public TCPConnection(String myIP,int myPort,String serverIP,int serverPort) throws IOException {
        channel = SocketChannel.open();
        channel.bind(new InetSocketAddress(myIP, 0));
        //System.out.println(" waiting to connect");
        channel.connect(new InetSocketAddress(serverIP, serverPort));//blocking
        //System.out.println("connected");
    }
 
    public void send(byte[] data_inbytes) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(data_inbytes);
        while (b.hasRemaining()) {
            channel.write(b);
        }
    }
    public byte[] receiveBytes(int n) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(n);
        while (b.hasRemaining()) {
            if (channel.read(b)==-1) throw new EOFException("connection closed before "+n+" bytes arrived");
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

    static final int TUPLE_SIZE = 13;          // node(1) + ip(4) + port(2) + cost(4)
    static final long PERIOD_MS = 10000;
    static char myId = 0;                   
    static int mySeq = 0;
    static List<Neighbour> neighbours = new ArrayList<>();          // my neighbours
    static Map<Character, Integer> lastSeq = new TreeMap<>();       // sequence numbers i have seen till now
    static Map<Character, List<Neighbour>> lsdb = new TreeMap<>();  // list state storage
    static DatagramChannel udp;       // periodic LSP + graph print


    public static void main(String[] args) {
        String server_ipaddr=args[0];
        int my_udp_port= Integer.parseInt(args[1]);

        String my_ip = null;
        try (DatagramSocket probe=new DatagramSocket()) {
            probe.connect(InetAddress.getByName(server_ipaddr),5000);
            my_ip=probe.getLocalAddress().getHostAddress();
        } catch (IOException e) {
            System.out.println("Unable to get my own IP address");
            System.exit(1);
        }

        
        //UDP connection for myself
        // DatagramSocket my_udp_socket=null;
        // try{
        //     my_udp_socket = new DatagramSocket(my_udp_port);
        // }
        // catch (SocketException e) {
        //     System.out.println("UDP binding failed");
        // }
        
        try {
            udp = DatagramChannel.open();
            udp.bind(new InetSocketAddress(my_ip,my_udp_port));
        } catch (IOException e) {
            System.out.println("UDP binding failed");
            System.exit(1);
        }


        // TCP connection to the server
        TCPConnection on_connection = null;
        try {
            on_connection=new TCPConnection(my_ip,my_udp_port+1,server_ipaddr,5000);
        } catch (IOException e) {
            System.out.println("Failed to establish TCP connection: "+e.getMessage());
            System.exit(1);
        }
        //CONNECT message to the ON
        try {
            byte[] connectMsg=new byte[6];
            byte[] ipBytes=InetAddress.getByName(my_ip).getAddress();
            System.arraycopy(ipBytes,0,connectMsg,0,4);
            connectMsg[4]=(byte)(my_udp_port>>8);
            connectMsg[5]=(byte)(my_udp_port&0xFF);
            on_connection.send(connectMsg);
        } catch (IOException e) {
            System.out.println("Failed to send CONNECT: "+e.getMessage());
            System.exit(1);
        }

        //Receive adjacency list from the ON
        try {
            int count=on_connection.receiveBytes(1)[0]&0xFF;
            neighbours=new ArrayList<>();
            for (int i=0;i<count;i++) {
                ByteBuffer buf=ByteBuffer.wrap(on_connection.receiveBytes(TUPLE_SIZE));   // 13 bytes
                // buf.order(ByteOrder.BIG_ENDIAN);   // already the default, shown for clarity
                char node=(char)buf.get();                        // 1 byte
                byte[] ipBytes=new byte[4];
                buf.get(ipBytes);                                    // 4 bytes
                String ip=InetAddress.getByAddress(ipBytes).getHostAddress();
                int port=buf.getInt();                             // 4 bytes
                int cost=buf.getInt();                             // 4 bytes

                if (cost==0) myId=node;                            // the zero-cost tuple is myself
                else neighbours.add(new Neighbour(node,ip,port,cost));
            }
            printLinkState(myId,neighbours); 
            //Debugging purposes
            // StringBuilder sb = new StringBuilder();
            // sb.append(myId).append("=0");
            // for (Neighbour n : neighbours) {
            //     sb.append(",").append(n.node).append("=").append(n.cost);
            // }
            // System.out.println(sb);
        } catch (IOException e) {
            System.out.println("Failed to receive adjacency list from ON: "+e.getMessage());
            System.exit(1);
        }


        //main LSP loop
        try {

            long lastPeriodic=System.currentTimeMillis();

            Selector selector=Selector.open();
 
            udp.configureBlocking(false);
            udp.register(selector, SelectionKey.OP_READ);
            ByteBuffer udpBuf=ByteBuffer.allocate(512);

            SocketChannel tcp=on_connection.getChannel();
            tcp.configureBlocking(false);
            tcp.register(selector, SelectionKey.OP_READ);
            ByteBuffer tcpBuf=ByteBuffer.allocate(1024); 
            originate_lsp(); //initial advertisement
 
            while (true) {
                selector.select(1000);     //wait for a sec
 
                Iterator<SelectionKey> it=selector.selectedKeys().iterator();
                while (it.hasNext()) {
                    SelectionKey key=it.next();
                    it.remove();
                    if(key.channel()==tcp){
                        int n=tcp.read(tcpBuf);
                        if (n==-1) {
                            System.out.println("Oracle Node closed the connection");
                            key.cancel();
                            continue;
                        }
                        handleTcp(tcpBuf);
                    } else if (key.channel()==udp) {
                        udpBuf.clear();
                        SocketAddress from = udp.receive(udpBuf);
                        if (from==null)continue;
                        udpBuf.flip();
                        byte[] data=new byte[udpBuf.remaining()];
                        udpBuf.get(data);
                        handleLsp(data,(InetSocketAddress)from);
                    }
                }
 
                long current_time=System.currentTimeMillis();
                if (current_time-lastPeriodic>=PERIOD_MS) {
                    originate_lsp();printGraph();
                    lastPeriodic=current_time;
                }
            }
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
static void printLinkState(char id, List<Neighbour> list) {
    StringBuilder sb=new StringBuilder();
    sb.append(id).append("=0");
    for (Neighbour n:list) {
        sb.append(",").append(n.node).append("=").append(n.cost);
    }
    System.out.println(sb);
}
    static void originate_lsp() throws IOException {
        mySeq++;
        lsdb.put(myId, neighbours);
        lastSeq.put(myId, mySeq);
        byte[] pkt = encode_into_lsp(myId, mySeq, neighbours);
        for (Neighbour n : neighbours) {
            InetSocketAddress dest = new InetSocketAddress(n.ip, n.port);
            udp.send(ByteBuffer.wrap(pkt), dest);
            udp.send(ByteBuffer.wrap(pkt), dest);
        }
    }
    static byte[] encode_into_lsp(char origin,int seq,List<Neighbour> list) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(6 + list.size() * TUPLE_SIZE);
        b.put((byte) origin); //1 byte
        b.putInt(seq); //4 bytes
        b.put((byte) list.size()); //1 byte
        for (Neighbour n : list) {
            b.put((byte) n.node);
            b.put(InetAddress.getByName(n.ip).getAddress());
            b.putInt(n.port);
            b.putInt(n.cost);
        }
        return b.array();
    }
    static Neighbour readTuple(ByteBuffer b) throws IOException {
        char node=(char)(b.get() & 0xFF);
        byte[] ip=new byte[4];
        b.get(ip);
        String ipStr=InetAddress.getByAddress(ip).getHostAddress();
        int port=b.getInt();
        int cost=b.getInt();
        return new Neighbour(node,ipStr,port,cost);
    }
    static void handleTcp(ByteBuffer tcpBuf) throws IOException {
        tcpBuf.flip();                                   // switch from writing to reading
        while (tcpBuf.remaining() >= 1) {
            int count=tcpBuf.get(tcpBuf.position())&0xFF; //peek the count without reading
            if (tcpBuf.remaining()<1+count*TUPLE_SIZE) break; // incomplete message so wait for more bytes
            tcpBuf.get();                                // read the count byte to move the cursor
            char self = 0;
            List<Neighbour> list = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Neighbour n = readTuple(tcpBuf);
                if (n.cost == 0) {
                    self = n.node;
                } else {
                    list.add(n);
                }
            }
 
            // debugging purposes
            // StringBuilder sb = new StringBuilder();
            // if (self != 0) sb.append(self).append("=0");
            // for (Neighbour n:list) {
            //     if (sb.length() > 0) {
            //         sb.append(",");
            //     }
            //     sb.append(n.node).append("=").append(n.cost);
            // }
            // System.out.println(sb);
 
            //store the new 
            if(self!=0){
                myId=self;
                neighbours=list;
                printLinkState(myId,neighbours); 
                originate_lsp();                             // link state changed: advertise immediately
            }
        }
        tcpBuf.compact();                                //flips back and keeps the left over things
    }
    static void handleLsp(byte[] data, InetSocketAddress from) throws IOException {
        if (data.length<6) return; //safety
        ByteBuffer b=ByteBuffer.wrap(data);
        char origin=(char)(b.get()&0xFF);
        int seq=b.getInt();
        int count=b.get()&0xFF;
        if (data.length<6+ (count*TUPLE_SIZE)) return; //corrupted
        if (origin == myId) return; //ignore loop backs
        Integer prev_seq_num=lastSeq.get(origin);
        if (prev_seq_num!=null && seq<=prev_seq_num)return;//outdated

        List<Neighbour> list=new ArrayList<>();
        for (int i=0;i<count;i++) {
            list.add(readTuple(b));
        }
        lastSeq.put(origin,seq);
        lsdb.put(origin,list);
 
        String senderIp=from.getAddress().getHostAddress();
        for (Neighbour n:neighbours) {
            boolean isSender=n.ip.equals(senderIp) && n.port==from.getPort();
            //forward packet to everyone else except the neighbour who send you
            if (!isSender) {
                InetSocketAddress dest=new InetSocketAddress(n.ip,n.port);
                udp.send(ByteBuffer.wrap(data),dest);
                udp.send(ByteBuffer.wrap(data),dest);
            }
        }
    }
    static int costBetween(char a, char b) {
        int ab=-1,ba=-1;
        List<Neighbour> la=lsdb.get(a);
        if (la!=null){
            for (Neighbour n:la)if (n.node==b)ab=n.cost;
        }
        List<Neighbour> lb=lsdb.get(b);
        if (lb!=null){
            for (Neighbour n:lb)if(n.node==a)ba=n.cost;
        }
        if (ab==-1||ba==-1) return -1;
        return ab;
    }
    static void printGraph() {
        boolean[] known=new boolean[26];
        for (Map.Entry<Character, List<Neighbour>> e : lsdb.entrySet()) {
            known[e.getKey()-'A']=true;
            for (Neighbour n:e.getValue())known[n.node-'A']=true;
        }

        char[] nodes=new char[26];
        int m=0;
        for (int i = 0; i < 26; i++) {
            if (known[i]) {
                nodes[m]=(char)('A'+i);
                m++;
            }
        }

        System.out.println("--- known graph at node " + myId + " ---");
        if (m < 2) {
            System.out.println("\t" + myId);
            System.out.println(myId + "\t0");
            return;
        }

        //print all knows nodes to act as a header
        StringBuilder header = new StringBuilder("\t");
        for (int j = 1; j < m; j++) {
            header.append(nodes[j]);
            if (j < m - 1) {
                header.append('\t');
            }
        }
        System.out.println(header);
        // print the upper triangle
        for (int i = 0; i < m - 1; i++) {
            StringBuilder sb = new StringBuilder();
            sb.append(nodes[i]).append('\t');
            for (int t = 0; t < i; t++) {
                sb.append('\t');
            }
            for (int j = i + 1; j < m; j++) {
                sb.append(costBetween(nodes[i], nodes[j]));
                if (j < m - 1) {
                    sb.append('\t');
                }
            }
            System.out.println(sb);
        }
    }
}
