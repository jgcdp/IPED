package iped.parsers.instagram;


import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Message {
    private long id;
    private List<Contact> recipients = null;
    private String data;
    private Date timeStamp = null;
    boolean fromMe = true;
    private String type = null;
    private Contact from = null;

    public Message(long id, List<Contact> recipients, String data, long timeStamp, Contact from, boolean fromMe) {
        this.id = id;
        this.recipients = recipients;
        this.data = data;
        this.timeStamp = new Date(timeStamp/1000);
        this.from = from;
        this.fromMe = fromMe;
    }

    public Contact getFrom() {
        return from;
    }

    public List<Contact> getRecipients() {
        return recipients;
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
