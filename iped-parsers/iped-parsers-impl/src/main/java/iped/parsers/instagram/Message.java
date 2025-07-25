package iped.parsers.instagram;


import java.util.Date;

public class Message {
    private String id;
    private String text = null;
    private String link = null;
    private String mediaHash = null;
    private String messageType = null;
    private Date timeStamp = null;
    boolean fromMe = true;
    private Contact from = null;
    Chat chat;

    public Message(Chat chat, String id, String text, long timeStamp, Contact from, boolean fromMe, String messageType) {
        this.chat = chat;
        this.id = id;
        this.text = text;
        this.timeStamp = new Date(timeStamp);
        this.from = from;
        this.fromMe = fromMe;
        this.messageType = messageType.toLowerCase();
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

    public void setText(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public boolean isFromMe() {
        return fromMe;
    }

    public String getId() {
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

    public String getMediaHash() {
        return mediaHash;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }
}
