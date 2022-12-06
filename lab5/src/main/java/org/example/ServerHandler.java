package org.example;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class ServerHandler implements Attachment {
    private final SocketChannel serverChannel;
    private static final int BUFFER_SIZE = 4096;
    private final ByteBuffer readBuff = ByteBuffer.allocate(BUFFER_SIZE);
    private final SelectionKey clientKey;
    private final SelectionKey key;
    private boolean disconnect = false;

    public ServerHandler(SelectionKey clientKey, SocketAddress address) throws IOException {
        this.clientKey = clientKey;
        serverChannel = SocketChannel.open();
        serverChannel.configureBlocking(false);
        key = serverChannel.register(clientKey.selector(), SelectionKey.OP_CONNECT, this);
//        if (address == null) {
//            close();
//
//            return;
//        }
        System.out.println("CONNECTED WITH SERVER SIDE  " + address);
        serverChannel.connect(address);
//        ((ClientHandler) clientKey.attachment()).setOther(key, readBuff, false);
    }

    public void setDisconnect() {
        disconnect = true;
    }

    public boolean getDisconnect() {
        return disconnect;
    }

    @Override
    public void handleEvent() {
        try {
            if (key.isReadable())
                read();
            else if (key.isWritable())
                write();
            else if (key.isConnectable())
                serverConnect(key);
        } catch (IOException e) {
            e.printStackTrace();
            close();
        }
    }

    @Override
    public void close() {
        key.cancel();
        try {
            serverChannel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void read() throws IOException {
        System.out.println("Read server");
        int read_bytes = serverChannel.read(readBuff);
        System.out.println(read_bytes);
        if (read_bytes == -1) {
            close();
//            System.out.println("Disconnect server");
//            key.interestOps(SelectionKey.OP_WRITE);
            ((ClientHandler) clientKey.attachment()).setDisconnect();
            clientKey.interestOps(SelectionKey.OP_WRITE);
            return;
        }
        System.out.println("Read server message");
        if (read_bytes != 0) {
            readBuff.flip();
            clientKey.interestOps(SelectionKey.OP_WRITE);
            key.interestOps(0);
        }
    }

    public void write() throws IOException {
        serverChannel.write(readBuff);
        if (readBuff.remaining() == 0) {
            if (!clientKey.isValid()) {
                close();
                return;
            }
            if (disconnect) {
                serverChannel.shutdownInput();
                if (((ClientHandler) clientKey.attachment()).getDisconnect())
                    close();
                return;
            }
            readBuff.clear();
            key.interestOps(SelectionKey.OP_READ);
            clientKey.interestOps(SelectionKey.OP_READ);
        }
    }

    private void serverConnect(SelectionKey key) throws IOException {
        if (!serverChannel.isConnectionPending())
            return;
        if (!serverChannel.finishConnect())
            return;

        ((ClientHandler) clientKey.attachment()).setOther(key, readBuff, false);
        key.interestOps(0);
        clientKey.interestOps(SelectionKey.OP_WRITE);
    }
}
