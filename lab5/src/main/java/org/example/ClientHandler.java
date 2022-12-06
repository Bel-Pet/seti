package org.example;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class ClientHandler implements Attachment {
    private static final int BUFFER_SIZE = 4096;
    private final SocketChannel clientChannel;
    private final DomainHandler domainHandler;
    private final SelectionKey key;
    private SelectionKey serverKey;
    private ByteBuffer readBuff = ByteBuffer.allocate(BUFFER_SIZE);
    private ByteBuffer writeBuff;
    private OperationType state = OperationType.HELLO;
    private boolean disconnect = false;
    private Request headerRequest;

    private ClientHandler(DomainHandler domainHandler, SelectionKey key) throws IOException {
        this.domainHandler = domainHandler;
        clientChannel = ((ServerSocketChannel) key.channel()).accept();
        clientChannel.configureBlocking(false);
        this.key = clientChannel.register(key.selector(), SelectionKey.OP_READ, this);
    }

    public static ClientHandler createClientHandler(DomainHandler domainHandler, SelectionKey key) throws IOException {
        return new ClientHandler(domainHandler, key);
    }

    @Override
    public void handleEvent() {
        try {
            if (key.isReadable()) read();
            else if (key.isWritable()) write();
        } catch (IOException e) {
            e.printStackTrace();
            close();
        }
    }

    @Override
    public void close() {
        key.cancel();
        try {
            clientChannel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setOther(SelectionKey serverKey, ByteBuffer serverBuff, Boolean check) {
        this.serverKey = serverKey;
        readBuff = serverBuff;
        if (check) {
            writeBuff = ByteBuffer.wrap(headerRequest.getDisconnectResponse());
            key.interestOps(SelectionKey.OP_WRITE);
        }
        writeBuff = ByteBuffer.wrap(headerRequest.getResponse());
    }

    public void setDisconnect() {
        disconnect = true;
    }

    public boolean getDisconnect() {
        return disconnect;
    }

    private void read() throws IOException {
        switch (state) {
            case HELLO -> readHello();
            case HEADER -> readHeader();
            case MESSAGE -> readRequest();
        }
    }

    private void readHello() throws IOException {
        int counted = clientChannel.read(readBuff);
        if (counted == -1) {
            close();
            return;
        }
        Request request = new Request(readBuff, state);
        if (!request.isRequest()) {
            return;
        }
        System.out.println("GOT HELLO FROM " + clientChannel.socket().getInetAddress());
        if (!request.checkVersion()) {
            close();
            return;
        }
        if (!request.isValid()) setDisconnect();
        key.interestOps(SelectionKey.OP_WRITE);
        writeBuff = ByteBuffer.wrap(request.getResponse());
        readBuff.clear();
    }

    private void readHeader() throws IOException {
        int counted = clientChannel.read(readBuff);
        if (counted == -1) {
            close();
            return;
        }
        headerRequest = new Request(readBuff, state);
        if (!headerRequest.isRequest()) {
            return;
        }
        System.out.println("GOT REQUEST FROM " + clientChannel.socket().getInetAddress());
        if (!headerRequest.isValid()) {
            setDisconnect();
            writeBuff = ByteBuffer.wrap(headerRequest.getResponse());
            key.interestOps(SelectionKey.OP_WRITE);
            return;
        }
        key.interestOps(0);
        switch (headerRequest.getAddressType()) {
            case 0x01 -> {
                ServerHandler server = new ServerHandler(key,
                        new InetSocketAddress(InetAddress.getByAddress(headerRequest.getAddress()),
                                headerRequest.getPort()));
            }
            case 0x03 -> {
                domainHandler.sendToResolve(new String(headerRequest.getAddress()), headerRequest.getPort(), key);
            }
        }
        readBuff.clear();
    }

    private void readRequest() throws IOException {
        System.out.println("Read client");
        int counted = clientChannel.read(readBuff);
        System.out.println(counted);
        if (counted == -1) {
            ((ServerHandler) serverKey.attachment()).setDisconnect();
            serverKey.interestOps(SelectionKey.OP_WRITE);
            return;
        }
        System.out.println("READ NEW MSG FROM " + clientChannel.getRemoteAddress());
        //TODO: Maybe no need if
        if (counted != 0) {
            readBuff.flip();
            serverKey.interestOps(SelectionKey.OP_WRITE);
            key.interestOps(0);
        }
    }

    private void write() throws IOException {
        switch (state) {
            case HELLO -> writeHello();
            case HEADER -> writeHeader();
            case MESSAGE -> writeResponse();
        }
    }

    private void writeHello() throws IOException {
        clientChannel.write(writeBuff);
        if (writeBuff.remaining() != 0)
            return;
        key.interestOps(SelectionKey.OP_READ);
        state = OperationType.HEADER;
    }

    private void writeHeader() throws IOException {
        clientChannel.write(writeBuff);
        if (writeBuff.remaining() != 0)
            return;
        if (serverKey == null || !serverKey.isValid()) {
            close();
            System.out.println("NOT TCP REQUEST BY " + clientChannel.socket().getInetAddress());
            return;
        }

        key.interestOps(SelectionKey.OP_READ);
        serverKey.interestOps(SelectionKey.OP_READ);
        state = OperationType.MESSAGE;
    }

    private void writeResponse() throws IOException {
        clientChannel.write(readBuff);
        if (readBuff.remaining() != 0)
            return;
        if (!serverKey.isValid()) {
            close();
            return;
        }
        if (disconnect) {
            clientChannel.shutdownInput();
            if (((ServerHandler) serverKey.attachment()).getDisconnect())
                close();
        }
        readBuff.clear();
        key.interestOps(SelectionKey.OP_READ);
        serverKey.interestOps(SelectionKey.OP_READ);
    }
}
