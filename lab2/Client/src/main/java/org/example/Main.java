package org.example;

public class Main {
    public static void main(String[] args) {
        /*if (args.length != 1) {
            System.err.println("Print path to file");
            return;
        }*/
        
        Client.run(/*args[0]/*/"./downloads/lab1.txt");
    }
}
