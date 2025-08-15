package iped.parsers.instagram;

import java.util.ArrayList;
import java.util.List;

public class Chat {
    private List<Message> messages = new ArrayList<>();
    List<Contact> participants = new ArrayList<>();
    private boolean isChannel;
    private boolean isGroup;
    private boolean isDeleted;
    private String id;
    private Contact user;

    public Chat(Contact user, String id, List<Contact> participants) {
        this.user = user;
        this.participants = participants;
        isGroup = participants.size() > 2;
        this.id = id;
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public void addMessage(Message message){

        for(Message m : this.messages){
            if(m.getId().equals(message.getId())){
                return;
            }
        }

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

    public List<Contact> getParticipants() {
        return participants;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public Contact getUser() {
        return user;
    }

    public String getName() {
        String result = "";
        for(Contact c : participants){
            result = result.concat(c.toString() + "_");
        }
        return result;
    }
}
