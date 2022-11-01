package org.example;


public class Main {
    public static void main(String[] args) {
        /*if (args.length != 1) {
            System.err.println("Print port to server");
            return;
        }*/

        Server.run(Integer.parseInt(/*args[0]*/"4444"));
    }
}
