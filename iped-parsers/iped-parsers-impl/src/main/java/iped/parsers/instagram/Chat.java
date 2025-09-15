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

    public void setUser(Contact user) {
        this.user = user;
    }

    public void addMessage(Message message){

        for(Message m : this.messages){
            if(m.getId().equals(message.getId())){
                return;
            }
        }

        this.messages.add(message);
    }

    public void addParticipant(Contact participant){

        for(Contact c : this.participants){
            if(c.getId().equals(participant.getId())){
                return;
            }
        }

        this.participants.add(participant);
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
        StringBuilder result = new StringBuilder();

        for(int i = 0; i < participants.size(); i++){
            result.append(participants.get(i).toString());

            if(i < participants.size() - 1)
                result.append("_");
        }

        return result.toString();
    }
}
