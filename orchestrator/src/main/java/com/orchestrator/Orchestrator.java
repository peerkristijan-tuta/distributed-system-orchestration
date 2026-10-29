package com.orchestrator;

import wrappers.AwsClient;
import java.util.HashMap;
import java.util.Scanner;
import java.net.*;
import java.io.IOException;

public class Orchestrator {
    static String converter(byte[] bytes) {
        String text = "";

        for (int x = 0; x < bytes.length; x++)
            text += (char) bytes[x];

        return text;
    }
    
    public static void main(String[] args) throws InterruptedException, IOException {
        if (args[0].equals("input")) {
            DatagramSocket socket = new DatagramSocket(60000);
            byte[] signalToProceed = new byte[1];
            Scanner scanner = new Scanner(System.in);

            System.out.println("Awaiting system readiness...");
            socket.receive(new DatagramPacket(signalToProceed, 1));

            while(true) {
                System.out.print(
                    "Activation Status Change Options\n1. Sender\n2. Mediator 1\n3. Mediator 2\n4. Receiver\n5. Exit\nChoice: "
                );

                String input = scanner.nextLine();

                socket.send(
                    new DatagramPacket(
                        new byte[] {(byte) input.charAt(0)}, 1, InetAddress.getByName("127.0.0.1"), 60001
                    )
                );

                if (input.equals("5")) return;

                socket.receive(new DatagramPacket(signalToProceed, 1));
            }
        } else {
            System.out.println("Logging into AWS...");
            AwsClient client = AwsClient.login(args[0], args[1]);

            System.out.println("Initializing EC2s for the distributed system...");
            String[] ec2Ids = new String[4];
            Thread[] threads = new Thread[4];
            String[] ec2Ips = new String[4];

            for (int x = 0; x < 4; x++) {
                final int index = x;
                threads[index] = new Thread(() -> {
                    ec2Ids[index] = client.createEC2("/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64", "t4g.small");
                    try {
                        Thread.sleep(10000);
                    } catch (Exception ec) {}
                    client.associateEc2WithIaM(ec2Ids[index], args[2]);

                    int checkInAttemptCount = 0;
                    while (true) {
                        checkInAttemptCount++;
                        if (client.checkSSMCheckInStatus(ec2Ids[index]) && client.checkEC2InitializationStatus(ec2Ids[index])) {
                            ec2Ips[index] = client.getPrivateIp(ec2Ids[index]);
                            break;
                        } else if (checkInAttemptCount == 4) {
                            while (true) {
                                client.terminateEc2(ec2Ids[index]);

                                while (true) {
                                    if (client.checkInstanceTerminationStatus(ec2Ids[index]))
                                        break;
                                    else {
                                        try {
                                            Thread.sleep(10000);
                                        } catch (Exception exc) {}
                                    }
                                }

                                ec2Ids[index] = client.createEC2("/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64", "t4g.small");
                                try {
                                    Thread.sleep(10000);
                                } catch (Exception exc) {}
                                client.associateEc2WithIaM(ec2Ids[index], args[2]);

                                checkInAttemptCount = 0;
                                while (true) {
                                    if (checkInAttemptCount == 4) break;
                                    if (client.checkSSMCheckInStatus(ec2Ids[index]) && client.checkEC2InitializationStatus(ec2Ids[index])) {
                                        ec2Ips[index] = client.getPrivateIp(ec2Ids[index]);
                                        break;
                                    } else {
                                        checkInAttemptCount++;
                                        try {
                                            Thread.sleep(10000);
                                        } catch (Exception exc) {}
                                    }
                                }

                                if (checkInAttemptCount != 4) break;
                            }
                        }

                        try {
                            Thread.sleep(10000);
                        } catch (Exception exc) {}
                    }
                });
                threads[index].start();
            }

            for (Thread thread : threads)
                thread.join();

            System.out.println("Creating S3 buckets and uploading distributed system files...");
            String s3BucketName = client.createS3("kristijan-distributed-system-files");
            client.uploadToS3(s3BucketName, "Send.java");
            client.uploadToS3(s3BucketName, "Mediate.java");
            client.uploadToS3(s3BucketName, "Receive.java");

            System.out.println("Setting up the EC2s to be distributed system nodes....");
            for (int x = 0; x < 4; x++) {
                final int index = x;
                threads[index] = new Thread(() -> {
                    client.ssmToEc2(ec2Ids[index], "sudo dnf install -y java-21-amazon-corretto-devel", true);

                    switch (index) {
                        case 0:
                            client.ssmToEc2(ec2Ids[index], "aws s3 cp s3://kristijan-distributed-system-files/Send.java .", true);
                            client.ssmToEc2(ec2Ids[index], "javac Send.java", true);
                            break;
                        case 1:
                            client.ssmToEc2(ec2Ids[index], "aws s3 cp s3://kristijan-distributed-system-files/Mediate.java .", true);
                            client.ssmToEc2(ec2Ids[index], "javac Mediate.java", true);
                            break;
                        case 2:
                            client.ssmToEc2(ec2Ids[index], "aws s3 cp s3://kristijan-distributed-system-files/Mediate.java .", true);
                            client.ssmToEc2(ec2Ids[index], "javac Mediate.java", true);
                            break;
                        default:
                            client.ssmToEc2(ec2Ids[index], "aws s3 cp s3://kristijan-distributed-system-files/Receive.java .", true);
                            client.ssmToEc2(ec2Ids[index], "javac Receive.java", true);
                    }

                    client.ssmToEc2(ec2Ids[index], "sudo dnf install -y dnf-plugins-core && sudo dnf config-manager --add-repo https://pkgs.tailscale.com/stable/fedora/tailscale.repo && sudo dnf install -y tailscale", true);
                    client.ssmToEc2(ec2Ids[index], "sudo systemctl start tailscaled", true);
                    client.ssmToEc2(ec2Ids[index], "sudo tailscale up --authkey=" + args[3], true);
                });

                threads[index].start();
            }

            for (Thread thread : threads) 
                thread.join();

            DatagramSocket nodeListener = new DatagramSocket(60002);
            new Thread(() -> {
                byte[] client_output = new byte[100];
                while(true) {
                    try {
                        nodeListener.receive(new DatagramPacket(client_output, 100));
                        System.out.println(converter(client_output));
                        client_output = new byte[100];
                    } catch (Exception exc) {
                        System.out.println("");
                        break;
                    }
                }
            }).start();
            
            boolean[] activationStatus = new boolean[] {false, false, false};
            DatagramSocket inputListener = new DatagramSocket(60001);
            while(true) {
                inputListener.send(
                    new DatagramPacket(
                        new byte[] {(byte) 1}, 1, InetAddress.getByName("127.0.0.1"), 60000
                    )
                );

                byte[] input = new byte[1];
                inputListener.receive(new DatagramPacket(input, 1));
                boolean exitLoop = false;

                switch (converter(input)) {
                    case "1":
                        client.ssmToEc2(ec2Ids[0], "java Send " + "test " + ec2Ips[1] + " " + ec2Ips[2] + " " + args[4], true);
                        break;
                    case "2":
                        if (activationStatus[0]) {
                            client.ssmToEc2(ec2Ids[1], "pkill -f Mediate", true);
                            System.out.println("Mediator 1 killed");
                            activationStatus[0] = false;
                        } else {
                            client.ssmToEc2(ec2Ids[1], "java Mediate " + ec2Ips[3] + " " + args[4] + " 1", false);
                            activationStatus[0] = true;
                        }

                        break;
                    case "3":
                        if (activationStatus[1]) {
                            client.ssmToEc2(ec2Ids[2], "pkill -f Mediate", true);
                            System.out.println("Mediator 2 killed");
                            activationStatus[1] = false;
                        } else {
                            client.ssmToEc2(ec2Ids[2], "java Mediate " + ec2Ips[3] + " " + args[4] + " 2", false);
                            activationStatus[1] = true;
                        }

                        break;
                    case "4":
                        if (activationStatus[2]) {
                            client.ssmToEc2(ec2Ids[3], "pkill -f Receive", true);
                            System.out.println("Receiver killed");
                            activationStatus[2] = false;
                        } else {
                            client.ssmToEc2(ec2Ids[3], "java Receive " + ec2Ips[1] + " " + ec2Ips[2] + " " + args[4], false);
                            activationStatus[2] = true;
                        }

                        break;
                    default:
                        nodeListener.close();
                        exitLoop = true;
                } 

                if (exitLoop)
                    break;
            }

            System.out.println("Terminating EC2s and deleting S3 bucket...");
            for (String ec2Id : ec2Ids) client.terminateEc2(ec2Id);
            client.deleteS3(s3BucketName);
        }
    }
}