package org.example;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

public record Attachment(SelectionKey peer, int port, ByteBuffer in, ByteBuffer out, OperationType type) {
}
