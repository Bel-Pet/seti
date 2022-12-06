package org.example;


import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;

public class Proxy {
    private final Selector selector;
    private final ProxyServer proxyServer;

    private Proxy(int port) throws IOException {
        selector = SelectorProvider.provider().openSelector();
        proxyServer = new ProxyServer(selector, port);
    }

    public static void run(int port) throws IOException {
        Proxy proxy = new Proxy(port);
        proxy.run();
    }

    private void run() {
        try {
            while (selector.select() > -1) {
                selector.selectedKeys().forEach(this::handleEvent);
                selector.selectedKeys().clear();
            }
        } catch (IOException e) {
            close();
        }
    }

    public void handleEvent(SelectionKey key) {
        if (key.isValid()) ((Attachment) key.attachment()).handleEvent();
    }

    private void close() {
        try {
            proxyServer.close();
            selector.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
