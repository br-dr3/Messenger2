package com.github.brdr3.messenger.core.client;

import com.github.brdr3.messenger.core.util.Message;
import com.github.brdr3.messenger.core.util.Message.MessageBuilder;
import com.github.brdr3.messenger.core.util.User;
import com.google.gson.Gson;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.LinkedList;
import java.util.Scanner;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MessengerClient {

    public User user;
    public User server;
    private final Gson gson = new Gson();
    public final Scanner scanner = new Scanner(System.in);
    private final Thread sender;
    public LinkedList<Message> history;
    public ConcurrentLinkedQueue<Message> messageQueue;
    public int orderTestMessages;
    public int duplicatedTestMessages;
    public int lostTestMessages;
    private Long lastId;

    public MessengerClient(String username,
            int port,
            String serverHostname,
            int serverPort)
            throws Exception {

        this.server = new User("server",
                InetAddress.getByName(serverHostname),
                serverPort);
        this.user
                = new User(username,
                        InetAddress.getByName(InetAddress.getLocalHost()
                                .getHostAddress()),
                        port);

        this.sender = new Thread() {
            @Override
            public void run() {
                send();
            }
        };

        messageQueue = new ConcurrentLinkedQueue<>();
        history = new LinkedList<>();
        orderTestMessages = 10;
        duplicatedTestMessages = 10;
        lostTestMessages = 10;
        lastId = new Long(0);
    }

    public void run() {
        sender.start();
    }

    public void send() {

        String userMessage;
        String content = "";
        MessageBuilder mb = new MessageBuilder();

        while (true) {
            userMessage = null;
            while (userMessage == null) {
                userMessage = scanner.nextLine();
            }

            if (userMessage.trim().equals("/testOrder")) {
                testOrder();
            } else if (userMessage.trim().equals("/testDuplicated")) {
                testDuplicated();
            } else if (userMessage.trim().equals("/testLost")) {
                testLost();
            } else {
                try {
                    sendMessage(mb.id(++lastId)
                            .content(userMessage)
                            .from(user)
                            .to(server)
                            .build());
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    private void cleanBuffer(byte buf[]) {
        for (int i = 0; i < buf.length; i++) {
            buf[i] = 0;
        }
    }

    private void sendMessage(Message m) throws Exception {
        String jsonMessage = gson.toJson(m);
        byte buffer[] = new byte[10000];
        DatagramSocket socket;
        DatagramPacket packet;

        buffer = jsonMessage.getBytes();
        packet = new DatagramPacket(buffer,
                buffer.length,
                server.getAddress(),
                server.getPort());

        socket = new DatagramSocket();
        socket.send(packet);

        history.add(m);
        System.out.println("Message sent: " + jsonMessage);
        cleanBuffer(buffer);
        socket.close();
    }

    private void syncronizedPrint(String s) {
        synchronized (System.out) {
            System.out.println(s);
        }
    }

    private void testOrder() {
        Long maxId = getMaxId();
        MessageBuilder mb = new MessageBuilder();
        for (int i = 1; i <= orderTestMessages; i++) {
            try {
                sendMessage(mb.content("Message " + i + ", id = " + (maxId - i))
                        .to(server)
                        .from(user)
                        .id(maxId + orderTestMessages - i + 1)
                        .build());

                ++lastId;

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        try {
            sendMessage(mb.content("End test")
                    .to(server).from(user).id(++lastId).build());
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    private void testDuplicated() {
        Message m = history.getLast();

        for (int i = 0; i < duplicatedTestMessages; i++) {
            try {
                sendMessage(m);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private void testLost() {
        Long id = getMaxId();

        try {
            sendMessage(new MessageBuilder()
                    .content("This message has id greater than it was supposed to be.")
                    .id(id + lostTestMessages)
                    .from(user)
                    .to(server)
                    .build());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private Long getMaxId() {
        return history.stream()
                .map(m -> m.getId())
                .reduce((m1, m2) -> m1 > m2 ? m1 : m2).orElse(new Long(1));
    }
}
