import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;


public class Receiver2a {
    public static int port_number;
    public static String output_name;
    public static File file;
    public static FileOutputStream fileOutputStream;
    public static ByteArrayOutputStream byteArrayOutputStream;
    public static DatagramSocket socket;
    public static byte[] received_packet;
    public static byte[] net_packet;
    public static int sequence_number;
    public static int expected_sequence_number;
    public static boolean is_last_packet;
    public static DatagramPacket packet;

    public static void do_the_preparation_work(String args_zero,String args_one) throws FileNotFoundException, SocketException {
        port_number = Integer.parseInt(args_zero);
        output_name = args_one;

        file = new File(output_name);
        fileOutputStream = new FileOutputStream(file);
        byteArrayOutputStream = new ByteArrayOutputStream();
        socket = new DatagramSocket(port_number);

        received_packet = new byte[1027];
        net_packet = new byte[1024];

        expected_sequence_number = 1;
        is_last_packet = false;
    }


    public static void main(String[] args) throws SocketException, FileNotFoundException {
        do_the_preparation_work(args[0],args[1]);

        try {

            while (true){
                packet = new DatagramPacket(received_packet, received_packet.length);
                socket.receive(packet);
                sequence_number = ((received_packet[0] & 0xFF) << 8) + (received_packet[1] & 0xFF);

                if (sequence_number == expected_sequence_number){
                    is_last_packet = (received_packet[2]& 0xFF) == 1;
                }

                if (sequence_number == expected_sequence_number && !is_last_packet){

                    for (int i = 0; i< net_packet.length; i++) {
                        net_packet[i] = received_packet[i+3];
                    }

                    byteArrayOutputStream.write(net_packet);
                    expected_sequence_number++;
                }
                else if (sequence_number == expected_sequence_number && is_last_packet) {

                    for (int i = 0; i< net_packet.length; i++) {
                        net_packet[i] = received_packet[i+3];
                    }
                    byte[] lastByte = new byte[packet.getLength()-3];
                    for (int i = 0; i < packet.getLength()-3 ; i ++ ){
                        lastByte[i] = net_packet[i];
                    }
                    net_packet = lastByte;
                    byteArrayOutputStream.write(net_packet);

                    expected_sequence_number++;
                }

                byte[] send_ack = new byte[2];
                send_ack[0] = (byte) ((expected_sequence_number -1) >> 8);
                send_ack[1] = (byte) ((expected_sequence_number -1));
                InetAddress startIP = packet.getAddress();
                int startPort = packet.getPort();
                DatagramPacket ack = new DatagramPacket(send_ack, send_ack.length, startIP, startPort);
                socket.send(ack);

                if (is_last_packet) {
                    break;
                }
            }


            fileOutputStream.write(byteArrayOutputStream.toByteArray());

            fileOutputStream.close();
            byteArrayOutputStream.close();
            socket.close();

        } catch (Exception e) {

        }
    }

}
