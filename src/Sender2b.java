import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.*;
import java.util.ArrayList;


public class Sender2b {

    private static String destination_name;
    private static int destination_portname;
    private static String file_location;
    private static int retrans_timeout;
    private static DatagramSocket socket;
    private static InetAddress destination_address;

    private static int sequence_number;
    private static boolean is_last_packet;
    private static int window_start_location;
    private static int window_size;
    private static ArrayList<byte[]> paket_list;
    private static long[] packet_sent_out_time;
    private static long current_time;
    private static boolean[] ack_receive_record;
    public static byte[] every_bytes;

    private static class Resend_Thread implements Runnable{
        @Override
        public void run() {
            while (true) {//System.out.println("Caught in a landslide");
                int size = window_start_location + window_size;
                for (int i = window_start_location; i < size && i < paket_list.size(); i++) {
                    current_time = System.currentTimeMillis();
                    if (packet_sent_out_time[i] + retrans_timeout <= current_time) {
                        try {
                            byte[] sendByte = paket_list.get(i);
                            DatagramPacket packet = new DatagramPacket(sendByte, sendByte.length, destination_address, destination_portname);
                            socket.send(packet);
                            current_time = System.currentTimeMillis();
                            packet_sent_out_time[i] = current_time;
                        } catch (Exception e) {

                        }
                    }
                }

                if (window_start_location == paket_list.size()) {
                    break;
                }
            }
        }
    }


    private static class Send_Thread implements Runnable {
        @Override
        public void run() {
            try {
                while (true) {//System.out.println("No escape from reality");

                    if (sequence_number < window_start_location + window_size && sequence_number < paket_list.size()) {
                        byte[] sendByte = paket_list.get(sequence_number);
                        DatagramPacket packet = new DatagramPacket(sendByte, sendByte.length, destination_address, destination_portname);
                        socket.send(packet);
                        // System.out.println(sequence+" "+sendByte.length);
                        //Timer? nah
                        packet_sent_out_time[sequence_number] = System.currentTimeMillis();
                        sequence_number++;
                        //System.out.println("Sequence changed to:" + sequence);
                    }

                    if (sequence_number == paket_list.size()) {
                        break;
                    }
                }
            } catch (Exception e) {

            }
        }
    }

    public static void inialise_parameters(String arg_zero,String arg_one, String arg_two,String arg_three,String arg_four) throws SocketException, UnknownHostException {
        destination_name = arg_zero;
        destination_portname = Integer.parseInt(arg_one);
        file_location = arg_two;
        retrans_timeout = Integer.parseInt(arg_three);
        window_size = Integer.parseInt(arg_four);

        socket = new DatagramSocket();
        socket.setSoTimeout(retrans_timeout);
        destination_address = InetAddress.getByName(destination_name);

        paket_list = new ArrayList<byte[]>();
        paket_list.add(new byte[0]);
    }

    public static void read_file() throws IOException {
        File file = new File(file_location);
        every_bytes = new byte[(int) file.length()];
        FileInputStream fileInputStream = new FileInputStream(file);
        fileInputStream.read(every_bytes);
        fileInputStream.close();
    }

    public static void make_packets(){
        sequence_number = 1;

        //put stuff in pktList
        for (int i = 0; i < every_bytes.length; i = i +1024){
            byte[] sendByte = new byte[1027];
            sendByte[0] = (byte) (sequence_number >> 8);
            sendByte[1] = (byte) (sequence_number);
            is_last_packet = (i+1024 >= every_bytes.length);

            if (is_last_packet) {
                //Case 1: last byte
                sendByte[2] = 1;
                for (int j = 0; j < every_bytes.length-i ; j++) {
                    sendByte[j+3] = every_bytes[i+j];
                }
                byte[] shortByte = new byte[every_bytes.length-i+3];
                for (int j = 0; j < every_bytes.length-i+3; j++) {
                    shortByte[j] = sendByte[j];
                }
                paket_list.add(shortByte);

                //if(shortByte.length/1024 !=0){
                //    System.out.println(i%1024);
                // }

            }else{
                //Case 2: not last byte
                sendByte[2] = 0;
                for (int j=0; j<1024; j++) {
                    sendByte[j+3] = every_bytes[i+j];
                }
                paket_list.add(sendByte);
                // if(sendByte.length %1027 !=0){
                //     System.out.println("line 141"+i/1024);
                //     System.out.println("line 141"+i/1024);
                //    System.out.println("line 142"+i%1024);
                // }
            }
            sequence_number++;
        }
        packet_sent_out_time = new long[sequence_number +1];
        ack_receive_record = new boolean[sequence_number +1];
        for (boolean b : ack_receive_record){
            b = false;
        }
    }

    public static class Receive_threads implements Runnable{

        @Override
        public void run() {
            try{

            boolean ack;
            int lastCount = 0;
            while (true){

                //Receive thread/Main Thread
                byte[] respondByte = new byte[2];
                DatagramPacket respondPkt = new DatagramPacket(respondByte, respondByte.length);
                try {
                    socket.setSoTimeout(retrans_timeout);
                    socket.receive(respondPkt);
                    ack = true;
                    lastCount =0;
                } catch (SocketTimeoutException e) {
                    ack = false;
                    lastCount++;
                }

                if (ack) {
                    int respondSeq = (((respondByte[0] & 0xFF) << 8) + (respondByte[1] & 0xFF));
                    ack_receive_record[respondSeq] = true;
                    if (respondSeq == window_start_location) {
                        while (true) {
                            if (ack_receive_record[window_start_location]) {
                                window_start_location++;
                                // System.out.println("Window Changed to: " + windowStart);
                            } else {
                                break;
                            }
                        }
                    }
                    //System.out.println("Current Progress: " + windowStart + "/" + pktList.size());
                }

                if (window_start_location == paket_list.size()){
                    break;
                }

                if (lastCount > 30){
                    //Kill it with fire
                    //System.out.println("Last ACK not received");
                    sequence_number = paket_list.size();
                    window_start_location = paket_list.size();
                    break;
                }
            }
            }catch(Exception e){

            }
        }
    }

    public static void start_threads(){
        //Start the send thread
        Send_Thread send = new Send_Thread();
        Thread sendThd = new Thread(send);
        sendThd.start();

        //Start the resend thread;
        Resend_Thread resend_thread = new Resend_Thread();
        Thread resendThd = new Thread(resend_thread);
        resendThd.start();
    }

    public static void main(String[] args) throws SocketException, UnknownHostException {
        inialise_parameters(args[0],args[1],args[2],args[3],args[4]);

        try {
            read_file();
            make_packets();

            //Get ready to send packs
            sequence_number = 1;
            window_start_location = 1;

            long time = System.currentTimeMillis();

            start_threads();

            Receive_threads receive_threads = new Receive_threads();
            Thread receive_thread_wrapped = new Thread(receive_threads);
            receive_thread_wrapped.start();

            while (true){
                if (window_start_location == paket_list.size()){
                    break;
                }
            }

            time = System.currentTimeMillis() - time;
            // System.out.println("File size of: " + bytes.length + " byte transmitted. \n" +
            //        "Spent       : " + time         + " mills to send. \n" +
            //        "Having speed: " + (((double) bytes.length/1024)/((double) time/1000)) + "KBps \n");
            System.out.println((((double) every_bytes.length/1024)/((double) time/1000)));
            socket.close();

        } catch (Exception e) {

        }
    }

}
