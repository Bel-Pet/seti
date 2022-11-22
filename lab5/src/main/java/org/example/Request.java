package org.example;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

public record Request(byte[] buffer) {

    public static byte[] connectRequest() {
        return new byte[]{0x05, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
    }

    public static byte[] noConnectRequest() {
        return new byte[]{0x05, 0x08, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
    }

    public static byte[] no2ConnectRequest() {
        return new byte[]{0x05, 0x07, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
    }

    public boolean isInvalidVersion() {
        return buffer[0] != 0x05;
    }

    public boolean isIPv4() {
        return buffer[3] == 0x01;
    }

    public boolean isDomain() {
        return buffer[3] == 0x03;
    }

    public boolean isIPv6() {
        return buffer[3] == 0x04;
    }

    public boolean isConnectCommand() {
        return buffer[1] == 0x01;
    }

    public InetSocketAddress getInetSocketAddress() throws UnknownHostException {
        InetAddress connectAddr = InetAddress.getByAddress(new byte[]{buffer[4], buffer[5], buffer[6], buffer[7]});
        int connectPort = ((buffer[8] & 0xFF) << 8) + (buffer[8 + 1] & 0xFF);
        return new InetSocketAddress(connectAddr, connectPort);
    }

    public String getHost() {
        return new String(Arrays.copyOfRange(buffer, 5, 5 +  buffer[4]));
    }

    public int getPort() {
        return 5 + buffer[4];
    }

    public byte[] getMethod() {
        return findNoAuthMethod() ? new byte[]{0x05, 0x00} : new byte[]{0x05, (byte) 0xFF};
    }

    private boolean findNoAuthMethod() {
        for (int i = 0; i < buffer[1]; i++) {
            if (buffer[i + 2] == 0x00) return true;
        }
        return false;
    }
}
