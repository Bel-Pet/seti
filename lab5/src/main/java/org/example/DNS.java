package org.example;


import org.xbill.DNS.*;
import org.xbill.DNS.Record;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.Optional;
import java.util.logging.Logger;

public class DNS implements Attachment {
    private static final Logger LOGGER = Logger.getLogger(DNS.class.getName());
    private final DatagramChannel channel;
    private final ByteBuffer in = ByteBuffer.allocate(Message.MAXLENGTH);
    private final ByteBuffer out = in;
    private final SelectionKey otherKey;
    private final SelectionKey key;
    private final int port;

    public DNS(SelectionKey key, String host, int port) throws IOException {
        LOGGER.info("Start search address. Host: " + host + ", Port: " + port);
        channel = DatagramChannel.open();
        channel.connect(ResolverConfig.getCurrentConfig().server());
        channel.configureBlocking(false);
        this.key = channel.register(key.selector(), SelectionKey.OP_WRITE);

        Message message = new Message();
        message.addRecord(
                Record.newRecord(Name.fromString(host + '.').canonicalize(), Type.A, DClass.IN),
                Section.QUESTION);
        message.getHeader().setOpcode(Opcode.QUERY);
        message.getHeader().setFlag(Flags.RD);

        this.port = port;
        otherKey = key;
        in.put(message.toWire());
        in.flip();
    }

    @Override
    public void handleEvent() {
        try {
            if (key.isReadable()) {
                read();
            } else if (key.isWritable()) {
                write();
            }
        } catch (Exception e) {
            e.printStackTrace();
            close();
        }
    }

    //TODO : послать сообщение клиену об ошибке сервера
    private void read() throws IOException {
        if (channel.read(in) <= 0) {
            throw new RuntimeException("Incorrect dns answer");
        }

        Optional<Record> maybeRecord = new Message(in.array()).getSection(Section.ANSWER).stream().findAny();
        if (maybeRecord.isPresent()) {
            Client client = new Client(otherKey, new InetSocketAddress(InetAddress.getByName(maybeRecord.get().rdataToString()), port));
            ((Client) otherKey.attachment()).setOther(client);
            LOGGER.info("DNS answer: " + " " + maybeRecord.get().rdataToString());
        }

        close();
    }

    private void write() throws IOException {
        if (channel.write(out) == -1) {
            close();
        } else if (out.remaining() == 0) {
            out.clear();
            key.interestOps(key.interestOps() ^ SelectionKey.OP_WRITE | SelectionKey.OP_READ);
        }
    }

    private void close() {
        key.cancel();
        try {
            channel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
