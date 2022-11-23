package org.example;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.logging.Logger;

public class Server implements Attachment {
    private static final Logger LOGGER = Logger.getLogger(Server.class.getName());
    private final ServerSocketChannel server;
    private final SelectionKey key;

    public Server(Selector selector, int port) throws IOException {
        server = ServerSocketChannel.open();
        server.configureBlocking(false);
        server.socket().bind(new InetSocketAddress(port));
        key = server.register(selector,server.validOps(), this);
        LOGGER.info("Server started...");
    }

    @Override
    public void handleEvent() {
        try {
            new Client(key);
        } catch (IOException e) {
            e.printStackTrace();
            close();
        }
    }

    void close() {
        key.cancel();
        try {
            server.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
