package iped.parsers.instagram;

import iped.parsers.instagram.Contact;

import java.util.ArrayList;

public class Chat {
    private ArrayList<Message> messages = new ArrayList<>();
    private boolean isChannel;
    private boolean isGroup;
    private boolean isDeleted;
    private String id;

    public Chat(String id, long userId, long recipientIds, long timestamp, String texto) {
        this.id = id;
        Message message = new Message(userId, recipientIds, timestamp, texto);
        this.messages.add(message);
    }

    public void addMessage(Message message){
        this.messages.add(message);
    }

    public String getId() {
        return id;
    }

    public boolean isChannel() {
        return isChannel;
    }

    public boolean isGroup() {
        return isGroup;
    }

    public ArrayList<Message> getMessages() {
        return messages;
    }
}
