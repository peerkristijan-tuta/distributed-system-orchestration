import java.net.*;
import java.util.Scanner;

class Network extends Thread {
    static String receiverIpAddress = "";
    static String orchestratorIpAddress = "";
    static char mediatorInstanceNumber;

    byte[] converter(String text) {
        byte[] bytes = new byte[text.length()];

        for (int x = 0; x < text.length(); x++)
            bytes[x] = (byte) text.charAt(x);

        return bytes;
    }

    String converter(byte[] bytes) {
        String text = "";

        for (int x = 0; x < bytes.length; x++)
            text += (char) bytes[x];

        return text;
    }

    String state = "receive";
    static boolean condition = true;

    public void run() {
        try {
            byte[] bytes = new byte[3];
            
            DatagramSocket socket = new DatagramSocket(8000);
            DatagramPacket packet = new DatagramPacket(bytes, 3);
            socket.setSoTimeout(1000);

            socket.send(
                new DatagramPacket(
                    converter("Mediator " + mediatorInstanceNumber + " launched"), ("Mediator " + mediatorInstanceNumber + " launched").length(), 
                    InetAddress.getByName(orchestratorIpAddress), 60002
                )
            );

            while (condition) {
                try {
                    sleep(1);
                } catch (Exception interruption) {
                    System.out.print(interruption);
                }

                if (state.equals("receive")) {
                    try {
                        socket.receive(packet);
                    } catch (Exception exc) {
                        continue;
                    }
                    state = "deliver";
                } else {
                    bytes[2] = (byte)mediatorInstanceNumber;

                    socket.send(
                        new DatagramPacket(
                            converter("Mediator " + mediatorInstanceNumber + " received and is trying to send - " + converter(bytes).substring(0, 2)), ("Mediator " + mediatorInstanceNumber + " received and is trying to send - " + converter(bytes).substring(0, 2)).length(), 
                            InetAddress.getByName(orchestratorIpAddress), 60002
                        )
                    );

                    while (true) {
                        try {
                            sleep(1);
                        } catch (Exception interruption) {
                            System.out.print(interruption);
                        }

                        if (condition == false)
                            break;

                        socket.send(
                            new DatagramPacket(
                                bytes, 3, 
                                InetAddress.getByName(receiverIpAddress), 8000
                            )
                        );

                        try {
                            socket.receive(packet);
                            state = "receive";
                            break;
                        } catch (SocketTimeoutException timeout) {}
                    }
                }

                try {
                    sleep(1);
                } catch (Exception interruption) {
                    System.out.print(interruption);
                };
            }
        } catch (Exception exc) {
            System.out.println("\n" + exc);
        };
    }
}

class Mediate {
    public static void main(String[] args) {
        Network.receiverIpAddress = args[0];
        Network.orchestratorIpAddress = args[1];
        Network.mediatorInstanceNumber = args[2].charAt(0);
        new Network().start();
    }
}