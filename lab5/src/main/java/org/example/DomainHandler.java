package org.example;

import org.xbill.DNS.Record;
import org.xbill.DNS.*;


import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.*;

public class DomainHandler implements Attachment {
    private final DatagramChannel resolverChannel = DatagramChannel.open();
    private final InetSocketAddress DnsServerAddr;

    private final ByteBuffer readBuff = ByteBuffer.allocate(Message.MAXLENGTH);
    private final ByteBuffer writeBuff = ByteBuffer.allocate(Message.MAXLENGTH);

    private final SelectionKey key;

    private final Deque<Message> deque = new LinkedList<>();

    private final Map<Integer, Map.Entry<SelectionKey, Short>> attachments = new HashMap<>();

    public DomainHandler(int port, Selector selector) throws IOException {
        resolverChannel.configureBlocking(false);
        resolverChannel.register(selector, 0, this);
        key = resolverChannel.keyFor(selector);
        resolverChannel.bind(new InetSocketAddress(port));
        DnsServerAddr = ResolverConfig.getCurrentConfig().server();
        resolverChannel.connect(DnsServerAddr);
        readBuff.clear();
        writeBuff.clear();

    }

    public void sendToResolve(String domainName, Short port, SelectionKey clientKey) {
        try {
            Message dnsRequest = Message.newQuery(Record.newRecord(new Name(domainName + '.'), Type.A, DClass.IN));
            deque.addLast(dnsRequest);
            attachments.put(dnsRequest.getHeader().getID(), Map.entry(clientKey, port));
            key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
        } catch (TextParseException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void close() {
        key.cancel();
        try {
            resolverChannel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void handleEvent() {
        try {
            if (key.isReadable())
                read(key);
            else if (key.isWritable())
                write(key);
        } catch (IOException ex) {
            ex.printStackTrace();
            close();
        }
    }

    public void read(SelectionKey key) throws IOException {
        if (resolverChannel.receive(readBuff) != null) {
            readBuff.flip();
            byte[] data = new byte[readBuff.limit()];
            readBuff.get(data);
            readBuff.clear();
            Message response = new Message(data);
            var session = attachments.remove(response.getHeader().getID());
            Optional<Record> maybe = response.getSection(Section.ANSWER).stream().findAny();
            if (maybe.isPresent()) {
                new ServerHandler(session.getKey(),
                        new InetSocketAddress(InetAddress.getByName(maybe.get().rdataToString()),
                                session.getValue()));
           } // else {
//                new ServerHandler(session.getKey().getKey(), null);
//            }

        }
        if (attachments.isEmpty())
            key.interestOps(key.interestOps() ^ SelectionKey.OP_READ);
    }

    public void write(SelectionKey key) throws IOException {
        Message dnsRequest = deque.pollFirst();
        while (dnsRequest != null) {
            writeBuff.clear();
            writeBuff.put(dnsRequest.toWire());
            writeBuff.flip();
            if (resolverChannel.send(writeBuff, DnsServerAddr) == 0) {
                deque.addFirst(dnsRequest);
                break;
            }
            key.interestOps(key.interestOps() | SelectionKey.OP_READ);
            dnsRequest = deque.pollFirst();
        }
        key.interestOps(key.interestOps() ^ SelectionKey.OP_WRITE);
    }
}

