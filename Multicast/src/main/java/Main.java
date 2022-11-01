import java.net.*;

public class Main {
    private static final int INTERVAL = 5000;
    private static final int PORT = 44444;
    private static double TIME = 0;
    private static final MapCopies copies = new MapCopies();

    private static void multicastReceive(InetSocketAddress address) {
        try(MulticastSocket mcReceiveSocket = new MulticastSocket(PORT)) {
            DatagramPacket receivePacket = new DatagramPacket("".getBytes(), "".length());

            //mcReceiveSocket.joinGroup(address, NetworkInterface.getByIndex(1));
            mcReceiveSocket.joinGroup(address.getAddress());
            mcReceiveSocket.setNetworkInterface(NetworkInterface.getByIndex(1));
            mcReceiveSocket.setSoTimeout(INTERVAL);

            while(!Thread.currentThread().isInterrupted()) {
                mcReceiveSocket.receive(receivePacket);
                copies.add(receivePacket.getSocketAddress(), System.currentTimeMillis());
                copies.delete(INTERVAL);
            }
        } catch(Exception e) {
            System.err.println(e.getMessage());
        }
    }

    private static void socketSend(InetSocketAddress address) {
        try(DatagramSocket sendingSocket = new DatagramSocket()) {
            DatagramPacket sendingPacket = new DatagramPacket("".getBytes(), 0, address);

            while (!Thread.currentThread().isInterrupted()) {
                if(System.currentTimeMillis() > TIME + INTERVAL) {
                    sendingSocket.send(sendingPacket);
                    TIME = System.currentTimeMillis();
                }
            }
        } catch(Exception e) {
            System.err.println(e.getMessage());
        }
    }

    public static void main(String[] args) {

        /*if (args.length != 1) {
            System.out.println("Usage: IPv4 or IPv6 address");
            return;
        }*/

        InetSocketAddress address;

        try {
            address = new InetSocketAddress(InetAddress.getByName(/*args[0]*/"224.0.0.1"), PORT);
        } catch (UnknownHostException e) {
            System.err.printf("Error address format: %s", args[0]);
            return;
        }

        Thread receive = new Thread(() -> {multicastReceive(address);});
        Thread send = new Thread(() -> {socketSend(address);});

        send.start();
        receive.start();
    }
}