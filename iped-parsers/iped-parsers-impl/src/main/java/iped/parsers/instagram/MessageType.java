package iped.parsers.instagram;

public enum MessageType {
    MEDIA("media"),
    TEXT("text"),
    VOICE_MEDIA("voice_media");

    private final String value;

    MessageType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}

