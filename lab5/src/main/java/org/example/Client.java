package org.example;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class Client {
    SocketChannel socketChannel;

    public Client(SelectionKey key) throws IOException {
        socketChannel = ((ServerSocketChannel) key.channel()).accept();
        socketChannel.configureBlocking(false);
        socketChannel.register(key.selector(),SelectionKey.OP_READ);
        //LOGGER.info("Accept: " + channel.getRemoteAddress());
    }


}
