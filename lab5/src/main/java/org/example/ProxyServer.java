package org.example;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;

public class ProxyServer implements Attachment {
    private final ServerSocketChannel serverChannel;
    SelectionKey key;
    private final DomainHandler domainHandler;

    public ProxyServer(Selector selector, int port) throws IOException {
        domainHandler = new DomainHandler(port, selector);
        serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(port));
        serverChannel.configureBlocking(false);
        key = serverChannel.register(selector, SelectionKey.OP_ACCEPT, this);
    }

    @Override
    public void handleEvent() {
        try {
            ClientHandler.createClientHandler(domainHandler, key);
        } catch (IOException ex) {
            ex.printStackTrace();
            close();
        }
    }

    @Override
    public void close() {
        key.cancel();
        try {
            domainHandler.close();
            serverChannel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
