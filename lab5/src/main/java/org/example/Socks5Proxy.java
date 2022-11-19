package org.example;

//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;

import org.xbill.DNS.Record;
import org.xbill.DNS.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.channels.spi.SelectorProvider;


public class Socks5Proxy extends Thread {
    //private static final Logger LOGGER = LoggerFactory.getLogger(Socks5Proxy.class);
    private final int BUFF_SIZE = 8192;
    private final int port;

    private static class Attachment {
        OperationType type;
        ByteBuffer in;
        ByteBuffer out;
        SelectionKey peer;
        int port;
    }

    public Socks5Proxy(int port) {
        this.port = port;
    }

    @Override
    public void run() {
        try (Selector selector = SelectorProvider.provider().openSelector();
             ServerSocketChannel serverChannel = ServerSocketChannel.open()) {

            //LOGGER.info("Server started...");

            serverChannel.configureBlocking(false);
            serverChannel.socket().bind(new InetSocketAddress(port));
            serverChannel.register(selector,serverChannel.validOps());

            while (selector.select() > -1) {
                var iterator = selector.selectedKeys().iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();
                    handleKey(key);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleKey(SelectionKey key) {
        if (key.isValid()) {
            try {
                if (key.isAcceptable()) {
                    accept(key);
                } else if (key.isConnectable()) {
                    connect(key);
                } else if (key.isReadable()) {
                    read(key);
                } else if (key.isWritable()) {
                    write(key);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    private void accept(SelectionKey key) throws IOException {
        SocketChannel channel = ((ServerSocketChannel) key.channel()).accept();
        channel.configureBlocking(false);
        channel.register(key.selector(),SelectionKey.OP_READ);
        //LOGGER.info("Accept: " + channel.getRemoteAddress());
    }

    private void connect(SelectionKey key) throws IOException {
        SocketChannel channel =  (SocketChannel)key.channel();
        Attachment attachment = ((Attachment) key.attachment());

        try {
            while (channel.isConnectionPending()) {
                //LOGGER.info("Finish connect: " + channel.getRemoteAddress());
                channel.finishConnect();

                attachment.in = ByteBuffer.allocate(BUFF_SIZE);
                attachment.in.put(Request.connectRequest()).flip();
                attachment.out = ((Attachment) attachment.peer.attachment()).in;
                ((Attachment) attachment.peer.attachment()).out = attachment.in;
                attachment.peer.interestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ);
                key.interestOps(0);
            }
        } catch (IOException e) {
            e.printStackTrace();
            close(key);
        }
    }

    private void read(SelectionKey key) throws IOException {

        var attachment = (Attachment)key.attachment();
        if (attachment == null) {
            key.attach(attachment= new Attachment());
            attachment.in = ByteBuffer.allocate(BUFF_SIZE);
            attachment.type = OperationType.AUTH_READ;
            key.attach(attachment);
        }

        if (attachment.type == OperationType.DNS_READ ) {
            var channel = (DatagramChannel) key.channel();
            if (channel.read(attachment.in) <= 0) {
                close(key);
                throw new RuntimeException("Invalid DNS reply");
            } else {
                var message = new Message(attachment.in.array());
                var maybeRecord = message.getSection(Section.ANSWER).stream().findAny();
                if (maybeRecord.isPresent()) {
                    registerIPv4(new InetSocketAddress(InetAddress.getByName(maybeRecord.get().rdataToString()),
                            attachment.port), attachment.peer);
                    key.interestOps(0);
                    key.cancel();
                    key.channel().close();
                } else {
                    close(key);
                    throw new RuntimeException("Host cannot be resolved");
                }
            }

        } else {
            SocketChannel channel = (SocketChannel) key.channel();

            try {
                //if (!attachment.peer.isValid()) {close(key);} else
                if (!channel.isConnected() || channel.read(attachment.in) <= 0) {
                    close(key);
                } else if (attachment.type == OperationType.AUTH_READ) {
                    choiceMethod(key);
                } else if (attachment.peer == null) {
                    requestConnectionMessage(key);
                } else {
                    //LOGGER.info("Read data: " + " " + channel.getRemoteAddress());
                    attachment.peer.interestOps(attachment.peer.interestOps() | SelectionKey.OP_WRITE);
                    key.interestOps(key.interestOps() ^ SelectionKey.OP_READ);
                    attachment.in.flip();
                }
            } catch (IOException e) {
                close(key);
                e.printStackTrace();
            }
        }
    }

    private void choiceMethod(SelectionKey key) throws IOException {
        var attachment = (Attachment)key.attachment();
        Request message = new Request(attachment.in.array());
        if (!message.isVersionSocks5()) {
            close(key);
            throw new RuntimeException("Bad request: incorrect SOCKS version");
        }

        attachment.out = attachment.in;
        attachment.out.clear();
        attachment.out.put(message.getRequestWithMethod()).flip();
        attachment.type = OperationType.AUTH_WRITE;
        key.interestOps(SelectionKey.OP_WRITE);
    }

    private void requestConnectionMessage(SelectionKey key) throws IOException {

        var attachment = (Attachment) key.attachment();

        Request buffer = new Request(attachment.in.array());
        if (!buffer.isVersionSocks5() || !buffer.isConnectCommand() || buffer.isIPv6())
            throw new RuntimeException("Bad request: unable to process one of the parameters");

        if (buffer.isIPv4())
            registerIPv4(buffer.getInetSocketAddress(), key);

        if (buffer.isDomain())
            registerHost(buffer.getHost(), buffer.getPort(), key);

        key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
    }

    private void registerIPv4(InetSocketAddress connectAddr, SelectionKey backKey) throws IOException {
        var peer = SocketChannel.open();
        peer.configureBlocking(false);
        peer.connect(connectAddr);

        var peerKey = peer.register(backKey.selector(), SelectionKey.OP_CONNECT);
        ((Attachment) backKey.attachment()).peer = peerKey;
        ((Attachment) backKey.attachment()).in.clear();

        Attachment peerAttachment = new Attachment();
        peerAttachment.peer = backKey;
        peerKey.attach(peerAttachment);
    }

    private void registerHost(String host, int backPort, SelectionKey backKey) throws IOException {

        var message = new Message();
        var record = Record.newRecord(Name.fromString(host + '.').canonicalize(), Type.A, DClass.IN);
        message.addRecord(record, Section.QUESTION);
        message.getHeader().setOpcode(Opcode.QUERY);
        message.getHeader().setFlag(Flags.RD);
        //header.setFlag(Flags.AD);
        //header.setOpcode(Opcode.QUERY);
        //header.setFlag(Flags.RD);

        Attachment attachment = new Attachment();
        attachment.type = OperationType.DNS_WRITE;
        attachment.port = backPort;
        attachment.peer = backKey;
        attachment.in = ByteBuffer.allocate(BUFF_SIZE);
        attachment.in.put(message.toWire());
        attachment.in.flip();
        attachment.out = attachment.in;

        var peer = DatagramChannel.open();
        peer.connect(ResolverConfig.getCurrentConfig().server());
        peer.configureBlocking(false);
        var key = peer.register(backKey.selector(), SelectionKey.OP_WRITE);

        key.attach(attachment);

    }

    private void write(SelectionKey key) throws IOException {

        Attachment attachment = (Attachment) key.attachment();

        if (attachment.type == OperationType.DNS_WRITE) {
            var channel = ((DatagramChannel) key.channel());

            if (channel.write(attachment.out) == -1) {
                close(key);
            } else if (attachment.out.remaining() == 0) {
                attachment.out.clear();
                attachment.type = OperationType.DNS_READ;
                key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE | SelectionKey.OP_READ);
            }
        } else {

            SocketChannel channel = (SocketChannel) key.channel();

            if (channel.write(attachment.out) == -1) {
                close(key);
            } else if (attachment.out.remaining() == 0) {
                if (attachment.type == OperationType.AUTH_WRITE) {
                    attachment.out.clear();
                    key.interestOps(SelectionKey.OP_READ);
                    attachment.type = OperationType.READ;

                } else if (attachment.peer == null) {
                    close(key);
                } else {
                    attachment.out.clear();
                    attachment.peer.interestOps(attachment.peer.interestOps() | SelectionKey.OP_READ);
                    key.interestOps(key.interestOps() ^ SelectionKey.OP_WRITE);
                }
            }
        }

    }

    private void close(SelectionKey key) throws IOException {
        key.cancel();
        key.channel().close();
        SelectionKey peerKey = ((Attachment) key.attachment()).peer;
        if (peerKey != null) {
            ((Attachment)peerKey.attachment()).peer = null;
            if((peerKey.interestOps() & SelectionKey.OP_WRITE) == 0 ) {
                ((Attachment)peerKey.attachment()).out.flip();
            }
            peerKey.interestOps(SelectionKey.OP_WRITE);
        }
        System.out.println();
    }
}
