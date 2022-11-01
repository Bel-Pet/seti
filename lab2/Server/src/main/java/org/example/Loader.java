package org.example;

import java.io.*;
import java.net.Socket;

public class Loader extends Thread {
    private static final String UPLOAD_DIRECTORY = "./uploads/";
    private static final int SIZE_MESSAGE = 3 * 1024;
    private static final int PERIODS = 3000;
    private final Socket socket;
    private final String threadNumber;

    Loader(Socket socket) {
        this.socket = socket;
        this.threadNumber = "Thread[" + Thread.currentThread().getId() + "] ";
    }

    public void run() {
        try (DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream())) {

            String fileName = in.readUTF();
            System.out.println(threadNumber + "File name: " + fileName);
            boolean correctFileName = fileName.indexOf('\\') == -1 && fileName.indexOf('/') == -1;
            out.writeBoolean(correctFileName);

            if (!correctFileName)
                System.out.println(threadNumber + "Incorrect file name");
            else {
                Long fileLength = in.readLong();
                System.out.println(threadNumber + "File length: " + fileLength);
                boolean status = writeToFile(in, fileName, fileLength);
                out.writeBoolean(status);
                System.out.println(threadNumber + "Status: " + status);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Boolean writeToFile(DataInputStream in, String fileName, Long fileLength) throws IOException {
        File file = new File(UPLOAD_DIRECTORY, fileName);
        if (!file.isFile()) {
            if (!file.createNewFile())
                throw new IOException("Error create file");
        }

        try (OutputStream outToFile = new FileOutputStream(file.getAbsolutePath())) {
            System.out.println(threadNumber + "Receiving packets...");
            int count;
            long countBytes = 0;
            byte[] bytes = new byte[SIZE_MESSAGE];
            long time = System.currentTimeMillis();
            while (fileLength > file.length()) {
                if (time + PERIODS < System.currentTimeMillis()) {
                    System.out.println(threadNumber + "Speed: " + countBytes / PERIODS + "KB/s");
                    time = System.currentTimeMillis();
                    countBytes = 0;
                }
                count = in.read(bytes);
                if (count == -1) break;
                outToFile.write(bytes, 0, count);
                countBytes += count;
            }
            System.out.println(threadNumber +
                    "Finish speed: " + countBytes  / (time + PERIODS - System.currentTimeMillis()) + "KB/s");
        }
        return fileLength.equals(file.length());
    }
}
