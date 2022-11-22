package org.example;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;

public class Server extends SelectionKey {
    private ServerSocketChannel channel;


    public Server(SelectionKey key) {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
    }

    @Override
    public SelectableChannel channel() {
        return null;
    }

    @Override
    public Selector selector() {
        return null;
    }

    @Override
    public boolean isValid() {
        return false;
    }

    @Override
    public void cancel() {

    }

    @Override
    public int interestOps() {
        return 0;
    }

    @Override
    public SelectionKey interestOps(int ops) {
        return null;
    }

    @Override
    public int readyOps() {
        return 0;
    }
}
