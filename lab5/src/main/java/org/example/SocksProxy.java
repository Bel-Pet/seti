package org.example;



import java.io.IOException;
import java.nio.channels.*;
import java.nio.channels.spi.SelectorProvider;


public class SocksProxy {
    private final Selector selector;

    private SocksProxy(int port) throws IOException {
        selector = SelectorProvider.provider().openSelector();
        new Server(selector, port);
    }

    public static void run(int port) throws IOException {
        SocksProxy socksProxy = new SocksProxy(port);
        socksProxy.run();
    }

    private void run() {
        try {
            while (selector.select() > -1) {
                selector.selectedKeys().forEach(key -> {
                    if (key.isValid()) ((Attachment) key.attachment()).handleEvent();
                });
                selector.selectedKeys().clear();
            }
        } catch (IOException e) {
            close();
        }
    }

    private void close() {
        try {
            selector.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
