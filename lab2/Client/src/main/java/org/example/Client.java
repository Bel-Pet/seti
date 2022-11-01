package org.example;

import java.io.*;
import java.net.Socket;

public class Client {
    private static final int PORT = 4444;
    private static final String HOST = "127.0.0.1";
    private static final int SIZE_MESSAGE = 1024;
    private final String pathToFile;

    private Client(String pathToFile) {
        this.pathToFile = pathToFile;
    }

    public static void run(String pathToFile) {
        Client client = new Client(pathToFile);
        client.run();
    }

    private void run() {
        try (Socket socket = new Socket(HOST, PORT);
             DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             FileInputStream inToFile = new FileInputStream(pathToFile)) {
            System.out.println("Connected");

            File file = new File(pathToFile);

            out.writeUTF(file.getName());
            boolean correctFileName = in.readBoolean();

            if (!correctFileName)
                System.out.println("Incorrect file name");
            else {
                out.writeLong(file.length());
                int count;
                byte[] bytes = new byte[SIZE_MESSAGE];
                System.out.println("Sending packets...");
                while ((count = inToFile.read(bytes)) != -1) {
                    out.write(bytes, 0, count);
                }
                System.out.println("Status: " + in.readBoolean());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}