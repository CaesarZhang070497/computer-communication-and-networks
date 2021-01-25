
import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.*;


public class Receiver2b {

    public static int port_number;
    public static String output_name;
    public static int window_size;

    public static int sequence_number;
    public static int window_start = 1;
    public static int last_sequence = -1;
    public static HashMap<Integer, byte[]> received_set = new HashMap<Integer, byte[]>();
    public static byte[] original_packet;
    public static byte[] net_packet;
    public static DatagramSocket socket;
    public static DatagramPacket packet;
    public static byte[] send_ack_packet;
    public static void read_argument(String arg_zero,String arg_one,String arg_two) throws SocketException {
        port_number = Integer.parseInt(arg_zero);
        output_name = arg_one;
        window_size = Integer.parseInt(arg_two);
        socket = new DatagramSocket(port_number);
    }

    public static void write_file() throws IOException {
        //Write the actual file
        File file = new File(output_name);
        FileOutputStream fileOutputStream = new FileOutputStream(file);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ArrayList<Integer> keys = new ArrayList<Integer>(received_set.keySet());
        Collections.sort(keys);
        for (int i = 0; i < last_sequence; i++){
            if (i+1 != keys.get(i)) System.out.println(i+1 + " " + keys.get((i)));
        }
        for (Integer i : keys){
            //System.out.println(i);
            try { byteArrayOutputStream.write(received_set.get(i)); } catch (Exception e) {}
        }
        fileOutputStream.write(byteArrayOutputStream.toByteArray());

        fileOutputStream.close();
        byteArrayOutputStream.close();

    }

    public static void receive_packets() throws IOException {
        original_packet = new byte[1027];
        net_packet = new byte[1024];

        packet = new DatagramPacket(original_packet, original_packet.length);
        socket.receive(packet);
        sequence_number = ((original_packet[0] & 0xFF) << 8) + (original_packet[1] & 0xFF);
        //System.out.println("PKT of sequence " + sequence + " received.");
    }

    public static void send_ack() throws IOException {
        send_ack_packet = new byte[2];
        send_ack_packet[0] = (byte) (sequence_number >> 8);
        send_ack_packet[1] = (byte) (sequence_number);
        InetAddress startIP = packet.getAddress();
        int startPort = packet.getPort();
        DatagramPacket ack = new DatagramPacket(send_ack_packet, send_ack_packet.length, startIP, startPort);
        socket.send(ack);
    }

    public static void main(String[] args) throws SocketException {
        read_argument(args[0],args[1],args[2]);

        try {
            // byte[] packetByte = new byte[1027];
            // byte[] fileByte = new byte[1024];

            while (true){

                receive_packets();
                //Send ACK
                send_ack();
                //System.out.println("ACK of sequence " + sequence + " sent.");

                //if in window
                if (sequence_number >= window_start && sequence_number < (window_start + window_size)){

                    boolean last = (original_packet[2]& 0xFF) == 1;

                    //Write/Overwrite pkt
                    for (int i = 0; i< net_packet.length; i++) {
                        net_packet[i] = original_packet[i+3];
                    }
                    if (last) {//Remove blanks
                        byte[] lastByte = new byte[packet.getLength()-3];
                        for (int i = 0; i < packet.getLength()-3 ; i ++ ){
                            lastByte[i] = net_packet[i];
                        }
                        net_packet = lastByte;
                    }
                    //DAMN HASHMAPS
                    //received.put(sequence, fileByte);
                    received_set.put(sequence_number, Arrays.copyOf(net_packet, net_packet.length));

                    //Move Window when available
                    if (sequence_number == window_start) {
                        while (true) {
                            if (received_set.containsKey(window_start)) {
                                window_start++;
                                //System.out.println("Window Changed to: " + windowStart);
                            } else {
                                break;
                            }
                        }
                    }

                    //Update last sequence
                    if (last) {
                        last_sequence = sequence_number;
                    }

                    //Stop if received everything
                    if (last_sequence != -1 && last_sequence == received_set.size()){
                        break;
                    }
                }
            }

            write_file();
            socket.close();

        } catch (Exception e) {

        }
    }

}