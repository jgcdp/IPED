package iped.parsers.instagram;

public enum MessageType {
    MEDIA("media"),
    IMAGE("image"),
    VIDEO("video"),
    TEXT("text"),
    AUDIO("voice"),
    CLIP("clip"),
    LINK("link");

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

