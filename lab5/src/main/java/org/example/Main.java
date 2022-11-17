package org.example;


public class Main {

    public static void main(String[] args) {
        Socks5Proxy server = new Socks5Proxy(1080);
        server.start();
    }
}
