import java.net.*;

class Send {
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

            socket.send(
                new DatagramPacket(
                    converter("" + args[0].charAt(0) + args[0].charAt(1)),
                    2, InetAddress.getByName(args[1]), 8000
                )
            );

            socket.send(
                new DatagramPacket(
                    converter("" + args[0].charAt(2) + args[0].charAt(3)),
                    2, InetAddress.getByName(args[2]), 8000
                )
            );

            socket.send(
                new DatagramPacket(
                    converter("Sent the message - " + args[0]),
                    ("Sent the message - " + args[0]).length(), InetAddress.getByName(args[3]), 60002
                )
            );
        } catch (Exception exc) {
            System.out.print(exc);
        };
    }
}