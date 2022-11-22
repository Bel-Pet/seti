package org.example;


import java.util.Optional;
import java.util.logging.Logger;

import org.xbill.DNS.Record;
import org.xbill.DNS.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.channels.spi.SelectorProvider;


public class Socks5Proxy {
    private static final Logger LOGGER = Logger.getLogger(Socks5Proxy.class.getName());
    private final int BUFF_SIZE = 8192;
    private final int port;

    private static class Attachment {
        OperationType type;
        ByteBuffer in;
        ByteBuffer out;
        SelectionKey peer;
        int port;
    }

    private Socks5Proxy(int port) {
        this.port = port;
    }

    public static void run(int port) {
        Socks5Proxy socks5Proxy = new Socks5Proxy(port);
        socks5Proxy.run();
    }

    private void run() {
        try (Selector selector = SelectorProvider.provider().openSelector();
             ServerSocketChannel serverChannel = ServerSocketChannel.open()) {

            LOGGER.info("Server started...");
            serverChannel.configureBlocking(false);
            serverChannel.socket().bind(new InetSocketAddress(port));
            serverChannel.register(selector,serverChannel.validOps());

            while (selector.select() > -1) {
                selector.selectedKeys().forEach(this::handleKey);
                selector.selectedKeys().clear();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleKey(SelectionKey key) {
        try {
            if (!key.isValid()) {
                key.cancel();
                key.channel().close();
                if (key.channel() != null)
                    key.channel().close();
                Attachment attachment = (Attachment) key.attachment();
                if (attachment != null && attachment.peer != null && attachment.peer.channel() != null) {
                    attachment.peer.channel().close();
                    attachment.peer.cancel();
                }
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
            close(key);
        }

        try {
            if (key.isAcceptable()) {
                accept(key);
            } else if (key.isConnectable()) {
                connect(key);
            } else if (key.isReadable()) {
                Attachment attachment = (Attachment) key.attachment();
                if (attachment != null && attachment.type == OperationType.DNS_READ) {
                    readAnswerDNS(key);
                } else {
                    read(key);
                }
            } else if (key.isWritable()) {
                Attachment attachment = (Attachment) key.attachment();
                if (attachment.type == OperationType.DNS_WRITE) {
                    writeDNS(key);
                } else {
                    write(key);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            close(key);
        }
    }
    private void accept(SelectionKey key) throws IOException {
        SocketChannel channel = ((ServerSocketChannel) key.channel()).accept();
        channel.configureBlocking(false);
        channel.register(key.selector(),SelectionKey.OP_READ);
        LOGGER.info("Accept: " + channel.getRemoteAddress());
    }

    private void connect(SelectionKey key) {
        SocketChannel channel =  (SocketChannel)key.channel();
        Attachment attachment = ((Attachment) key.attachment());

        try {
            if (!channel.isConnectionPending() || !channel.finishConnect())
                return;
            LOGGER.info("Finish connect: " + channel.getRemoteAddress());

//            attachment.in = ByteBuffer.allocate(BUFF_SIZE);
//            attachment.in.put(Request.connectRequest()).flip();
//            attachment.out = ((Attachment) attachment.peer.attachment()).in;
//            ((Attachment) attachment.peer.attachment()).out = attachment.in;
            attachment.peer.interestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ);
            key.interestOps(0);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Error connect");
        }
    }

    private void read(SelectionKey key) {
        SocketChannel channel = (SocketChannel) key.channel();
        Attachment attachment = (Attachment)key.attachment();
        if (attachment == null) {
            key.attach(attachment= new Attachment());
            attachment.in = ByteBuffer.allocate(BUFF_SIZE);
            attachment.out = attachment.in;
            attachment.type = OperationType.HELLO;
            key.attach(attachment);
        }

        try {
            if (channel.read(attachment.in) <= 0) {
                close(key);
            } else if (attachment.type == OperationType.HELLO) {
                readHello(key);
            } else if (attachment.peer == null) {
                readHeader(key);
            } else {
                LOGGER.info("Read data: " + " " + channel.getRemoteAddress());
                attachment.peer.interestOpsOr(SelectionKey.OP_WRITE);
                key.interestOps(key.interestOps() ^ SelectionKey.OP_READ);
                attachment.in.flip();
            }
        } catch (IOException e) {
            e.printStackTrace();
            close(key);
        }
    }

    private void readAnswerDNS(SelectionKey key) throws IOException {
        DatagramChannel channel = (DatagramChannel) key.channel();
        Attachment attachment = (Attachment)key.attachment();

        if (channel.read(attachment.in) <= 0)
            throw new RuntimeException("Invalid DNS reply");

        Optional<Record> maybeRecord = new Message(attachment.in.array()).getSection(Section.ANSWER)
                .stream()
                .findAny();

        if (maybeRecord.isEmpty())
            throw new RuntimeException("Host cannot be resolved");

        LOGGER.info("DNS answer: " + " " + maybeRecord.get().rdataToString());
        registerIPv4(new InetSocketAddress(InetAddress.getByName(maybeRecord.get().rdataToString()),
                attachment.port), attachment.peer);
        key.interestOps(0);
        close(key);
    }

    private void readHello(SelectionKey key) throws IOException {
        Attachment attachment = (Attachment)key.attachment();
        Request request = new Request(attachment.in.array());
        if (request.isInvalidVersion())
            throw new RuntimeException("Bad request: incorrect SOCKS version");

        attachment.out.clear();
        attachment.out.put(request.getMethod()).flip();
        attachment.type = OperationType.WRITE;

        key.interestOps(SelectionKey.OP_WRITE);
    }

    private void readHeader(SelectionKey key) throws IOException {

        Attachment attachment = (Attachment) key.attachment();

        Request request = new Request(attachment.in.array());
        if (request.isInvalidVersion())
            throw new RuntimeException("Bad request: unable to process one of the parameters");

        if (!request.isConnectCommand()) {
            attachment.out = attachment.in;
            attachment.out.clear();
            attachment.out.put(Request.no2ConnectRequest()).flip();
            attachment.type = OperationType.WRITE;
            throw new RuntimeException("Bad request: IPv6");
        }

        if (request.isIPv6()) {
            attachment.out = attachment.in;
            attachment.out.clear();
            attachment.out.put(Request.noConnectRequest()).flip();
            attachment.type = OperationType.WRITE;
            throw new RuntimeException("Bad request: IPv6");
        }

        if (request.isIPv4())
            registerIPv4(request.getInetSocketAddress(), key);

        if (request.isDomain())
            registerHost(request.getHost(), request.getPort(), key);

        key.interestOps(key.interestOps() ^ SelectionKey.OP_READ);
    }

    private void registerIPv4(InetSocketAddress connectAddr, SelectionKey backKey) throws IOException {
        SocketChannel peer = SocketChannel.open();
        peer.configureBlocking(false);
        peer.connect(connectAddr);

        SelectionKey peerKey = peer.register(backKey.selector(), SelectionKey.OP_CONNECT);

        ((Attachment) backKey.attachment()).peer = peerKey;
        ((Attachment) backKey.attachment()).in.clear();

        Attachment peerAttachment = new Attachment();
        peerAttachment.peer = backKey;
        peerAttachment.in = ByteBuffer.allocate(BUFF_SIZE);
        peerAttachment.in.put(Request.connectRequest()).flip();
        peerAttachment.out = ((Attachment) peerAttachment.peer.attachment()).in;
        ((Attachment) peerAttachment.peer.attachment()).out = peerAttachment.in;
        peerKey.attach(peerAttachment);
    }

    private void registerHost(String host, int backPort, SelectionKey backKey) throws IOException {
        DatagramChannel peer = DatagramChannel.open();
        peer.connect(ResolverConfig.getCurrentConfig().server());
        peer.configureBlocking(false);
        SelectionKey key = peer.register(backKey.selector(), SelectionKey.OP_WRITE);

        Message message = new Message();
        message.addRecord(
                Record.newRecord(Name.fromString(host + '.').canonicalize(), Type.A, DClass.IN),
                Section.QUESTION);
        message.getHeader().setOpcode(Opcode.QUERY);
        message.getHeader().setFlag(Flags.RD);

        Attachment attachment = new Attachment();
        attachment.type = OperationType.DNS_WRITE;
        attachment.port = backPort;
        attachment.peer = backKey;
        attachment.in = ByteBuffer.allocate(BUFF_SIZE);
        attachment.in.put(message.toWire());
        attachment.in.flip();
        attachment.out = attachment.in;

        key.attach(attachment);
    }

    private void write(SelectionKey key) throws IOException {
        Attachment attachment = (Attachment) key.attachment();
        SocketChannel channel = (SocketChannel) key.channel();

        if (channel.write(attachment.out) == -1) {
            close(key);
        } else if (attachment.out.remaining() == 0) {
            if (attachment.type == OperationType.WRITE) {
                attachment.out.clear();
                key.interestOps(SelectionKey.OP_READ);
                attachment.type = OperationType.READ;
                LOGGER.info("Output shutdown: " + " " + channel.getRemoteAddress());

            } else if (attachment.peer == null) {
                close(key);
            } else {
                LOGGER.info("Write data: " + " " + channel.getRemoteAddress());
                attachment.out.clear();
                attachment.peer.interestOpsOr(SelectionKey.OP_READ);
                key.interestOps(key.interestOps() ^ SelectionKey.OP_WRITE);
            }
        }
    }

    private void writeDNS(SelectionKey key) throws IOException {
        DatagramChannel channel = ((DatagramChannel) key.channel());
        Attachment attachment = (Attachment) key.attachment();

        if (channel.write(attachment.out) == -1) {
            close(key);
        } else if (attachment.out.remaining() == 0) {
            attachment.out.clear();
            attachment.type = OperationType.DNS_READ;
            key.interestOps(key.interestOps() ^ SelectionKey.OP_WRITE | SelectionKey.OP_READ);
        }
    }

    private void close(SelectionKey key) {
        try {
            key.cancel();
            key.channel().close();
            SelectionKey peerKey = ((Attachment) key.attachment()).peer;
            if (peerKey != null) {
                ((Attachment) peerKey.attachment()).peer = null;
                if ((peerKey.interestOps() & SelectionKey.OP_WRITE) == 0) {
                    ((Attachment) peerKey.attachment()).out.flip();
                }
                peerKey.interestOps(SelectionKey.OP_WRITE);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
