import java.net.*;
import java.util.*;

class Receive {
    static byte[] converter(String text) {
        byte[] bytes = new byte[text.length()];

        for (int x = 0; x < text.length(); x++)
            bytes[x] = (byte) text.charAt(x);

        return bytes;
    }

    static String converter(byte[] bytes) {
        String text = "";

        for (int x = 0; x < bytes.length; x++)
            text += (char) bytes[x];

        return text;
    }

    public static void main(String[] args) {
        try {
            byte[] bytes = new byte[3];
            DatagramSocket socket = new DatagramSocket(8000);
            DatagramPacket packet = new DatagramPacket(bytes, 3);
            HashMap<Integer, Boolean> piece_tracking = new HashMap<Integer, Boolean>();
            String[] pieces = {"", ""};

            piece_tracking.put(1, false);
            piece_tracking.put(2, false);

            socket.send(
                new DatagramPacket(
                    converter("Receiver launched"), ("Receiver launched").length(), 
                    InetAddress.getByName(args[2]), 60002
                )
            );

            while (piece_tracking.get(1) != true || piece_tracking.get(2) != true) {
                socket.receive(packet);

                if (converter(bytes).charAt(2) == '1') {
                    pieces[0] = "" + converter(bytes).charAt(0) + converter(bytes).charAt(1);
                    piece_tracking.put(1, true);

                    socket.send(
                        new DatagramPacket(
                            new byte[1], 1, 
                            InetAddress.getByName(args[0]), 8000
                        )
                    );
                } else {
                    pieces[1] = "" + converter(bytes).charAt(0) + converter(bytes).charAt(1);
                    piece_tracking.put(2, true);

                    socket.send(
                        new DatagramPacket(
                            new byte[1], 1, 
                            InetAddress.getByName(args[1]), 8000
                        )
                    );
                }
            }

            socket.send(
                new DatagramPacket(
                    converter("The receiver has received packets from mediators and assembled them into " + pieces[0] + pieces[1]), 
                    ("The receiver has received packets from mediators and assembled them into " + pieces[0] + pieces[1]).length(), 
                    InetAddress.getByName(args[2]), 60002
                )
            );
        } catch (Exception exc) {
            System.out.print(exc);
        };
    }
}