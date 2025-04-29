package iped.parsers.instagram;

import iped.parsers.instagram.Contact;

import java.util.ArrayList;
import java.util.List;

public class Chat {
    private ArrayList<Message> messages = new ArrayList<>();
    private boolean isChannel;
    private boolean isGroup;
    private boolean isDeleted;
    private String id;

    public Chat(String id, long messageId, List<Contact> recipients, String data, long timeStamp, Contact from, boolean fromMe) {
        this.id = id;
        Message message = new Message(messageId, recipients, data, timeStamp, from, fromMe);
        this.messages.add(message);
    }

    public boolean isDeleted() {
        return isDeleted;
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
