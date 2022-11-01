package org.example;

import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class Server {
    private final int port;
    static final String HOST = "127.0.0.1";
    private static final int BACKLOG = 2;

    private Server(int port) {
        this.port = port;
    }

    public static void run(int port) {
        Server server = new Server(port);
        server.run();
    }

    private void run() {
        try (ServerSocket socket = new ServerSocket(port, BACKLOG, InetAddress.getByName(HOST))) {
            Thread stop = new Thread(() -> {
                Scanner scanner = new Scanner(System.in);
                while(true) {
                    if (Objects.equals(scanner.next(), "stop"))
                        return;
                }
            });
            stop.start();

            ExecutorService executorService = Executors.newFixedThreadPool(BACKLOG);

            while (stop.isAlive()) {
                Socket clientSocket = socket.accept();
                executorService.execute(new Loader(clientSocket));
            }

            executorService.shutdown();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}