package org.example;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.logging.Logger;

public class Client implements Attachment {
    private static final Logger LOGGER = Logger.getLogger(Client.class.getName());
    private final int BUFF_SIZE = 8192;
    private final SelectionKey key;
    private final SocketChannel client;
    private final ByteBuffer in = ByteBuffer.allocate(BUFF_SIZE);
    private ByteBuffer out = in;
    private Client other;
    private OperationType type;
    private SelectionKey otherKey;

    public Client(SelectionKey key) throws IOException {
        client = ((ServerSocketChannel) key.channel()).accept();
        client.configureBlocking(false);
        this.key = client.register(key.selector(),SelectionKey.OP_READ, this);
        type = OperationType.READ_HELLO;
        LOGGER.info("Accept: " + client.getRemoteAddress());

    }

    public Client(SelectionKey otherKey, InetSocketAddress connectAddr) throws IOException {
        client = SocketChannel.open();
        client.configureBlocking(false);
        client.connect(connectAddr);
        key = client.register(otherKey.selector(), SelectionKey.OP_CONNECT, this);

        other = (Client) otherKey.attachment();
        this.otherKey = otherKey;
        in.put(Request.connectRequest()).flip();
        out = other.getIn();
        LOGGER.info(String.join("Connect: ",
                ((SocketChannel) otherKey.channel()).getRemoteAddress().toString(),
                client.getRemoteAddress().toString()));
    }


    @Override
    public void handleEvent() {
        try {
            if (key.isConnectable()) {
                connect();
            } else if (key.isReadable()) {
                read();
            } else if (key.isWritable()) {
                write();
            }
        } catch (Exception e) {
            e.printStackTrace();
            close();
        }
    }

    public ByteBuffer getIn() {
        return in;
    }

    public SelectionKey getKey() {
        return key;
    }

    public void setOther(Client other) {
        this.other = other;
        otherKey = other.getKey();
        in.clear();
        out = other.getIn();
    }

    public void deleteOtherKey() {
        otherKey = null;
    }

    private void connect() throws IOException {
        //TODO : послать сообщение клиену об ошибке сервера
        if (!client.isConnectionPending() || !client.finishConnect())
            return;
        LOGGER.info("Finish connect: " + client.getRemoteAddress());

        otherKey.interestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ);
        key.interestOps(0);
    }

    private void read() throws IOException {
        if (client.read(in) <= 0) {
            close();
        } else if (type == OperationType.READ_HELLO) {
            LOGGER.info("Read hello: " + " " + client.getRemoteAddress());
            readHello(key);
        } else if (otherKey == null) {
            LOGGER.info("Read header: " + " " + client.getRemoteAddress());
            readHeader(key);
        } else {
            LOGGER.info("Read data: " + " " + client.getRemoteAddress());
            otherKey.interestOpsOr(SelectionKey.OP_WRITE);
            key.interestOps(key.interestOps() ^ SelectionKey.OP_READ);
            in.flip();
        }
    }

    //TODO : послать сообщение клиену об ошибке сервера
    private void readHello(SelectionKey key) {
        Request request = new Request(in.array());
        if (request.isInvalidVersion())
            throw new RuntimeException("Bad request: incorrect SOCKS version");

        out.clear();
        out.put(request.getMethod()).flip();
        type = OperationType.WRITE;
        key.interestOps(SelectionKey.OP_WRITE);
    }

    //TODO : послать сообщение клиену об ошибке сервера
    private void readHeader(SelectionKey key) throws IOException {
        Request request = new Request(in.array());
        if (request.isInvalidVersion())
             throw new RuntimeException("Bad request: incorrect SOCKS version");

        if (!request.isConnectCommand()) {
            throw new RuntimeException("Bad request: incorrect command");
        }

        if (request.isIPv6()) {
            throw new RuntimeException("Bad request: IPv6");
        }

        if (request.isIPv4()) {
            other = new Client(key, request.getInetSocketAddress());
            setOther(other);
        }

        if (request.isDomain())
            new DNS(key, request.getHost(), request.getPort());

        key.interestOps(key.interestOps() ^ SelectionKey.OP_READ);
    }

    private void write() throws IOException {
        if (client.write(out) == -1) {
            close();
        } else if (out.remaining() == 0) {
            if (type == OperationType.WRITE) {
                out.clear();
                key.interestOps(SelectionKey.OP_READ);
                type = OperationType.READ;
                LOGGER.info("Write header: " + client.getRemoteAddress());
            } else if (otherKey == null) {
                close();
            } else {
                LOGGER.info("Write data: " + client.getRemoteAddress());
                out.clear();
                otherKey.interestOpsOr(SelectionKey.OP_READ);
                key.interestOps(key.interestOps() ^ SelectionKey.OP_WRITE);
            }
        }
    }

    private void close() {
        key.cancel();
        try {
            client.close();
            // TODO : переписать!!!
            if (otherKey != null) {
                other.deleteOtherKey();
                if ((otherKey.interestOps() & SelectionKey.OP_WRITE) == 0) {
                    in.flip();
                }
                otherKey.interestOps(SelectionKey.OP_WRITE);
            }
            other = null;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
