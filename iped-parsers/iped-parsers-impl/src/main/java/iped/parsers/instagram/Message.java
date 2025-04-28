package iped.parsers.instagram;

import iped.parsers.telegram.Chat;
import iped.parsers.telegram.Contact;

import java.util.Date;

public class Message {
    private long id = 0;
    private long recipientIds;
    private long userId;
    private String data;
    private Date timeStamp = null;
    boolean fromMe = true;
    private String type = null;
    private Contact from = null;
    private Chat chat = null;

    public Message(long userId, long recipientIds, long timestamp, String data) {
        this.userId = userId;
        this.recipientIds = recipientIds;
        this.data = data;
        this.timeStamp = new Date(timestamp/1000);
    }

    public long getRecipientIds() {
        return recipientIds;
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
}
