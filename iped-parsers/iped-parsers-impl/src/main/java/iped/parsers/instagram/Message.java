package iped.parsers.instagram;


import java.util.Date;

public class Message {
    private long id;
    private String data;
    private String messageType;
    private Date timeStamp = null;
    boolean fromMe = true;
    private String type = null;
    private Contact from = null;
    Chat chat;

    public Message(Chat chat, long id, String data, long timeStamp, Contact from, boolean fromMe, String messageType) {
        this.chat = chat;
        this.id = id;
        this.data = (data == null || data.isEmpty()) ? messageType : data;
        this.timeStamp = new Date(timeStamp / 1000);
        this.from = from;
        this.fromMe = fromMe;
        this.messageType = messageType;
    }

    public String getMessageType() {
        return messageType;
    }

    public Chat getChat() {
        return chat;
    }

    public Contact getFrom() {
        return from;
    }

    public Date getTimeStamp() {
        return timeStamp;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getData() {
        return data;
    }

    public boolean isFromMe() {
        return fromMe;
    }

    public long getId() {
        return id;
    }

    public Contact getRecipientContact() {
        for (Contact p : this.chat.getParticipants()) {
            if (!p.getId().equals(from.getId())) {
                return p;
            }
        }
        return new Contact("");
    }
}
