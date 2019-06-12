package com.github.brdr3.messenger.core.server;

import com.github.brdr3.messenger.core.util.Message;
import com.github.brdr3.messenger.core.util.Tuple;
import com.github.brdr3.messenger.core.util.User;
import com.google.gson.Gson;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MessengerServer {

    private final int port = 15672;
    private final Queue<Message> messageQueue;
    private HashMap<User, List<Tuple<Message, Boolean>>> history;
    private final Gson gson = new Gson();
    private final Thread receiver;
    private final Thread printer;
    private final User user;

    public MessengerServer(String username)
            throws UnknownHostException {
        this.user = new User("server", InetAddress.getByName("localhost"), port);
        this.messageQueue = new ConcurrentLinkedQueue<>();

        this.receiver = new Thread() {
            @Override
            public void run() {
                receive();
            }
        };

        this.printer = new Thread() {
            @Override
            public void run() {
                print();
            }
        };

        history = new HashMap<>();
    }

    public void run() {
        receiver.start();
        printer.start();
    }

    public void receive() {
        DatagramSocket socket;
        DatagramPacket dgPacket;
        byte buffer[] = new byte[10000];
        String jsonMessage;
        Message message;

        try {
            socket = new DatagramSocket(user.getPort());
            while (true) {
                dgPacket = new DatagramPacket(buffer, buffer.length, user.getAddress(), user.getPort());

                socket.receive(dgPacket);

                jsonMessage = new String(dgPacket.getData()).trim();
                message = gson.fromJson(jsonMessage, Message.class);

                processMessage(message);

                cleanBuffer(buffer);
                message = null;
                jsonMessage = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void print() {
        while (true) {
            Message m = messageQueue.poll();
            if (m != null) {
                String json = gson.toJson(m);
                syncPrint("Message Received: " + json);
            }
        }
    }

    private void cleanBuffer(byte[] buffer) {
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = 0;
        }
    }

    private boolean isDuplicated(Message message) {
        for (Object t : history.get(message.getFrom())) {
            Tuple<Message, Boolean> tup = (Tuple<Message, Boolean>) t;
            if (tup.getX().getId().equals(message.getId())) {
                return true;
            }
        }
        return false;
    }

    private boolean isNotDuplicated(Message message) {
        return !isDuplicated(message);
    }

    private boolean outOfOrder(Message message) {
        
        if(history.get(message.getFrom()) == null) {
            try {
                history.put(message.getFrom().clone(), new LinkedList<>());
            } catch (CloneNotSupportedException ex) {
                ex.printStackTrace();
            }
        }
        
        return history
                .get(message.getFrom())
                .stream()
                .map(m -> m.getX().getId())
                .reduce(new Long(0), (m1, m2) -> m1 > m2 ? m1 : m2) + 1 != message.getId();
    }

    private boolean inOrder(Message message) {
        return !outOfOrder(message);
    }

    public void processMessage(Message m)  {
        if (inOrder(m) && isNotDuplicated(m)) {
            Tuple<Message, Boolean> t = new Tuple(m, false);
            
            LinkedList<Tuple<Message, Boolean>> list = new LinkedList(history.get(m.getFrom()));
            list.add(t);
            try {
                history.put(m.getFrom().clone(), list);
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            LinkedList<Message> messagesToPrint = messagesToPrint(m.getFrom());

            for (Message msg : messagesToPrint) {
                messageQueue.add(msg);
            }

        } else if (isDuplicated(m)) {
            syncPrint("Message duplicated.. " + m.getId());
        } else if (outOfOrder(m)) {
            syncPrint("Message out of order.. " + m.getId());
            Tuple<Message, Boolean> t = new Tuple(m, false);
            
            LinkedList<Tuple<Message, Boolean>> list = new LinkedList(history.get(m.getFrom()));
            list.add(t);
            try {
                history.put(m.getFrom().clone(), list);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private void syncPrint(String s) {
        synchronized (System.out) {
            System.out.println(s);
        }
    }

    private LinkedList<Message> messagesToPrint(User u) {
        LinkedList<Message> candidates = new LinkedList<>();

        for (Tuple<Message, Boolean> t : history.get(u)) {
            if (!t.getY() && t.getX().getId() <= getMaxId(u)) {
                candidates.add(t.getX());
                history.get(u)
                       .get(history.get(u).indexOf(t))
                       .setY(Boolean.TRUE);
            }
        }

        Collections.sort(candidates, (Message m1, Message m2) -> {
            if (m1.getId() > m2.getId()) {
                return 1;
            } else if (m1.getId().equals(m2.getId())) {
                return 0;
            } else {
                return -1;
            }
        });

        return candidates;
    }

    private Long getMaxId(User u) {
        Long i = history.get(u)
                      .stream()
                      .map(m -> m.getX().getId())
                      .reduce((m1, m2) -> m1 > m2 ? m1 : m2)
                      .orElse(new Long(1));
                
        return i;
    }
}
