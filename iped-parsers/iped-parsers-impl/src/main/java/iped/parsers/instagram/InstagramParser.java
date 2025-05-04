package iped.parsers.instagram;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.*;
import java.util.Base64;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import com.dd.plist.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import iped.parsers.util.Util;
import iped.properties.BasicProps;
import iped.utils.EmptyInputStream;
import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.github.openjson.JSONArray;
import com.github.openjson.JSONObject;

import iped.data.IItemReader;
import iped.parsers.sqlite.SQLite3DBParser;
import iped.parsers.standard.StandardParser;
import iped.parsers.util.PhoneParsingConfig;
import iped.properties.ExtraProperties;
import iped.search.IItemSearcher;

public class InstagramParser extends SQLite3DBParser {

    /**
     *
     */
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(InstagramParser.class);

    public static final String INSTAGRAM = "Instagram";
    public static final MediaType INSTAGRAM_ACCOUNT = MediaType.parse("application/x-instagram-account");
    public static final MediaType INSTAGRAM_USER_CONF = MediaType.parse("application/x-instagram-user-conf");
    public static final MediaType INSTAGRAM_USER_CONF_IOS = MediaType.parse("application/x-instagram-user-conf-ios");
    public static final MediaType INSTAGRAM_DB = MediaType.parse("application/x-instagram-db");
    public static final MediaType INSTAGRAM_DB_IOS = MediaType.parse("application/x-instagram-db-ios");
    public static final MediaType INSTAGRAM_CHAT = MediaType.parse("application/x-instagram-chat");
    public static final MediaType INSTAGRAM_CONTACT_CONF = MediaType.parse("application/x-instagram-contact-conf");
    public static final MediaType INSTAGRAM_CONTACT_CONF_IOS = MediaType.parse("application/x-instagram-contact-conf-ios");
    public static final MediaType INSTAGRAM_CONTACT = MediaType.parse("contact/x-instagram-contact");
    public static final MediaType INSTAGRAM_MESSAGE = MediaType.parse("message/x-instagram-message");
    public static final MediaType INSTAGRAM_ATTACHMENT = MediaType.parse("message/x-instagram-attachment");
    public static final MediaType INSTAGRAM_CALL = MediaType.parse("call/x-instagram-call");

    private static final Set<MediaType> SUPPORTED_TYPES = MediaType.set(INSTAGRAM_DB, INSTAGRAM_CONTACT_CONF, INSTAGRAM_USER_CONF, INSTAGRAM_DB_IOS, INSTAGRAM_CONTACT_CONF_IOS, INSTAGRAM_USER_CONF_IOS);

    // TODO improve this: prefix to show 'attachment' before body text (values
    // are sorted)
    private static final String ATTACHMENT_PREFIX = "! ";

    // TODO externalize to locale properties
    private static final String ATTACHMENT_MESSAGE = ATTACHMENT_PREFIX + "Attachment: ";
    private static final String QUERY_GET_MESSAGES = "SELECT * FROM messages";
    private static final String QUERY_GET_MESSAGE_THREAD = "SELECT * FROM threads WHERE thread_id = ?";

    private static boolean enabledForUfdr = false;

    private boolean extractMessages = true;
    private int minChatSplitSize = 6000000;

    private ArrayList<Contact> users = new ArrayList<>();
    private ArrayList<Chat> chats = new ArrayList<>();
    private ArrayList<Contact> chatContacts = new ArrayList<>();

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public static boolean isEnabledForUfdr() {
        return enabledForUfdr;
    }

    @Field
    public void setEnabledForUfdr(boolean enable) {
        enabledForUfdr = enable;
    }

    @Field
    public void setExtractMessages(boolean extractMessages) {
        this.extractMessages = extractMessages;
    }

    @Field
    public void setMinChatSplitSize(int minChatSplitSize) {
        this.minChatSplitSize = minChatSplitSize;
    }

    public Contact getContact(String id) {
        for (Contact c : chatContacts) {
            if (c.getId().equals(id)) {
                return c;
            }
        }

        return null;
    }

    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
        throws IOException, SAXException, TikaException {

        IItemReader item = context.get(IItemReader.class);
        if ((!enabledForUfdr || PhoneParsingConfig.isExternalPhoneParsersOnly())
            && PhoneParsingConfig.isFromUfdrDatasourceReader(item)) {
            return;
        }

        String mimetype = metadata.get(StandardParser.INDEXER_CONTENT_TYPE);
        if (mimetype.equals(INSTAGRAM_DB.toString())) {
            parseInstagramDBAndroid(stream, handler, metadata, context);
        } else if (mimetype.equals(INSTAGRAM_DB_IOS.toString())) {
            parseInstagramDBIOS(stream, handler, metadata, context);
        } else if (mimetype.equals(INSTAGRAM_USER_CONF.toString())) {
            parseAndroidAccount(stream, handler, metadata, context);
        } else if (mimetype.equals(INSTAGRAM_CONTACT_CONF.toString())) {
            parseChatContacts(stream, handler, metadata, context);
        } else if (mimetype.equals(INSTAGRAM_CONTACT_CONF_IOS.toString())) {
            parseChatContactsIOS(stream, handler, metadata, context);
        } else if (mimetype.equals(INSTAGRAM_USER_CONF_IOS.toString())) {
            parseIOSAccount(stream, handler, metadata, context);
        }
    }

    private void parseIOSAccount(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws TikaException {
        try {
            users = (ArrayList<Contact>) decodeIOSAccount(stream);
            for (Contact u : users) {
                createAccountHTML(u, handler, context);
            }

        } catch (Exception e) {
            throw new TikaException("Error parsing instagram account", e);
        }
    }


    private List<Contact> decodeIOSAccount(InputStream stream) throws PropertyListFormatException, IOException, ParseException, ParserConfigurationException, SAXException {
        List<Contact> usersDecoded = new ArrayList<>();
        NSArray primaryArray = null;
        NSDictionary root = (NSDictionary) PropertyListParser.parse(stream);
        if (root.containsKey("all-logged-in-users-account-linking-infos")) {
            NSDictionary usersConfig = (NSDictionary) root.objectForKey("all-logged-in-users-account-linking-infos");
            for (NSObject userConfig : usersConfig.values()) {
                byte[] userPlist = ((NSData) userConfig).bytes();
                NSDictionary userPlistRoot = (NSDictionary) PropertyListParser.parse(userPlist);

                for (String key : userPlistRoot.allKeys()) {
                    NSObject value = userPlistRoot.objectForKey(key);
                    if (value instanceof NSArray) {
                        primaryArray = (NSArray) value;
                        break;
                    }
                }

                if (primaryArray == null)
                    return usersDecoded;

                for (NSObject element : primaryArray.getArray()) {
                    if (element instanceof NSDictionary) {
                        if (((NSDictionary) element).containsKey("NS.keys") && ((NSDictionary) element).containsKey("NS.objects")) {
                            NSArray arrayIndexKeys = (NSArray) ((NSDictionary) element).objectForKey("NS.keys");
                            NSArray arrayIndexObjects = (NSArray) ((NSDictionary) element).objectForKey("NS.objects");
                            Contact user = new Contact();
                            String infoType, id, userName, fullName;

                            for (int i = 0, indexKeyValue, indexObjectValue; i < arrayIndexKeys.count() && i < arrayIndexObjects.count(); i++) {
                                indexKeyValue = Util.fromBytesToInt(((UID) arrayIndexKeys.getArray()[i]).getBytes());
                                indexObjectValue = Util.fromBytesToInt(((UID) arrayIndexObjects.getArray()[i]).getBytes());
                                infoType = ((NSString) primaryArray.getArray()[indexKeyValue]).getContent();

                                if (infoType.equals("full_name")) {
                                    fullName = ((NSString) primaryArray.getArray()[indexObjectValue]).getContent();
                                    user.setFullname(fullName);
                                } else if (infoType.equals("id")) {
                                    id = ((NSString) primaryArray.getArray()[indexObjectValue]).getContent();
                                    user.setId(id);
                                } else if (infoType.equals("username")) {
                                    userName = ((NSString) primaryArray.getArray()[indexObjectValue]).getContent();
                                    user.setUsername(userName);
                                }
                            }

                            if (user.getId() != null) {
                                usersDecoded.add(user);
                            }
                        }
                    }
                }
            }
        }
        return usersDecoded;
    }

    private void parseChatContactsIOS(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) {
        try {
            List<Contact> contactsDecoded = decodeIOSContacts(stream);
            extractContacts(context, contactsDecoded, handler);
        } catch (IOException | PropertyListFormatException | ParseException | ParserConfigurationException e) {
            throw new RuntimeException(e);
        } catch (SAXException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Contact> decodeIOSContacts(InputStream stream) throws PropertyListFormatException, IOException, ParseException, ParserConfigurationException, SAXException {
        List<Contact> contacts = new ArrayList<>();
        Contact contact = null;
        NSDictionary root = (NSDictionary) PropertyListParser.parse(stream);
        NSArray array = null;

        for (String key : root.allKeys()) {
            NSObject value = root.objectForKey(key);
            if (value instanceof NSArray) {
                array = (NSArray) value;
                break;
            }
        }

        int idArrayValue, userNameArrayValue, fullNameArrayValue;
        String id, userName, fullName;
        UID value;

        for (NSObject element : array.getArray()) {
            if (element instanceof NSDictionary) {
                if (((NSDictionary) element).containsKey("userName")) {
                    value = (UID) ((NSDictionary) element).objectForKey("pk");
                    idArrayValue = Util.fromBytesToInt(value.getBytes());
                    id = ((NSString) array.getArray()[idArrayValue]).getContent();

                    value = (UID) ((NSDictionary) element).objectForKey("userName");
                    userNameArrayValue = Util.fromBytesToInt(value.getBytes());
                    userName = ((NSString) array.getArray()[userNameArrayValue]).getContent();

                    value = (UID) ((NSDictionary) element).objectForKey("fullName");
                    fullNameArrayValue = Util.fromBytesToInt(value.getBytes());
                    fullName = ((NSString) array.getArray()[fullNameArrayValue]).getContent();

                    contacts.add(new Contact(id, userName, fullName));
                }
            }
        }

        return contacts;
    }

    private void parseChatContacts(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext
        context) {
        try {
            List<Contact> contactsDecoded = decodeAndroidContacts(stream);
            extractContacts(context, contactsDecoded, handler);
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (SAXException e) {
            throw new RuntimeException(e);
        }
    }

    private void extractContacts(ParseContext context, List<Contact> contactsDecodes, ContentHandler handler) throws
        IOException, SAXException {
        IItemSearcher searcher = context.get(IItemSearcher.class);
        ReportGenerator r = new ReportGenerator(searcher);
        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
            new ParsingEmbeddedDocumentExtractor(context));
        for (Contact c : contactsDecodes) {
            byte[] bytes = r.generateContactHtml(c);
            Metadata cMetadata = new Metadata();
            cMetadata.set(StandardParser.INDEXER_CONTENT_TYPE, INSTAGRAM_CONTACT.toString());
            cMetadata.set(TikaCoreProperties.TITLE, c.toString());
            cMetadata.set(ExtraProperties.USER_NAME, c.getName());
            cMetadata.set(ExtraProperties.USER_PHONE, c.getPhone());
            cMetadata.set(ExtraProperties.USER_ACCOUNT, c.getId() + "");
            cMetadata.set(ExtraProperties.USER_ACCOUNT_TYPE, INSTAGRAM);
            cMetadata.set(ExtraProperties.USER_NOTES, c.getUsername());
            cMetadata.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());
            if (c.getAvatar() != null) {
                cMetadata.set(ExtraProperties.THUMBNAIL_BASE64, Base64.getEncoder().encodeToString(c.getAvatar()));
            }
            ByteArrayInputStream contactStream = new ByteArrayInputStream(bytes);
            extractor.parseEmbedded(contactStream, handler, cMetadata, false);
        }
        chatContacts.addAll(contactsDecodes);
    }

    private List<Contact> decodeAndroidContacts(InputStream stream) throws
        ParserConfigurationException, IOException, SAXException {
        List<Contact> contacts = new ArrayList<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(stream);
        doc.getDocumentElement().normalize();

        // Get all <string> nodes
        NodeList nodes = doc.getElementsByTagName("string");

        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            String nameAttr = element.getAttribute("name");

            // Filter only user entries (e.g., name="user:123456789")
            if (nameAttr.startsWith("user:")) {
                String rawJson = element.getTextContent();

                // Decode HTML entities (&quot; → ") and parse JSON
                String decodedJson = rawJson.replace("&quot;", "\"").replace("&amp;", "&");
                JSONObject json = new JSONObject(decodedJson);

                String id = json.optString("id", "");
                String username = json.optString("username", "");
                String fullName = json.optString("full_name", "");

                if (getContact(id) == null) {
                    contacts.add(new Contact(id, username, fullName));
                }
            }
        }
        return contacts;
    }

    private void parseAndroidAccount(InputStream stream, ContentHandler handler, Metadata metadata,
                                     ParseContext context) throws SAXException, IOException, TikaException {

        try {
            users = (ArrayList<Contact>) decodeAndroidAccount(stream);
            for (Contact u : users) {
                createAccountHTML(u, handler, context);
            }

        } catch (Exception e) {
            throw new TikaException("Error parsing instagram account", e);
        }

    }

    private void createAccountHTML(Contact user, ContentHandler handler, ParseContext context)
        throws IOException, SAXException {
        Metadata meta = new Metadata();
        meta.set(StandardParser.INDEXER_CONTENT_TYPE, INSTAGRAM_ACCOUNT.toString());
        meta.set(TikaCoreProperties.TITLE, "Instagram - " + user.getFullname());
        meta.set(ExtraProperties.USER_NAME, user.getName());
        meta.set(ExtraProperties.USER_PHONE, user.getPhone());
        meta.set(ExtraProperties.USER_ACCOUNT, user.getUsername());
        meta.set(ExtraProperties.USER_ACCOUNT_TYPE, INSTAGRAM);
        meta.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());
        IItemSearcher searcher = context.get(IItemSearcher.class);
        // ex.setSearcher(searcher);
        // ex.searchAvatarFileName(user, user.getPhotos());
        if (user.getAvatar() != null) {
            meta.set(ExtraProperties.THUMBNAIL_BASE64, Base64.getEncoder().encodeToString(user.getAvatar()));
        }

        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
            new ParsingEmbeddedDocumentExtractor(context));
        ReportGenerator reportGenerator = new ReportGenerator(searcher);
        byte[] bytes = reportGenerator.generateContactHtml(user);
        ByteArrayInputStream contactStream = new ByteArrayInputStream(bytes);
        extractor.parseEmbedded(contactStream, handler, meta, false);

    }

    private List<Contact> decodeAndroidAccount(InputStream xmlInputStream) {
        List<Contact> usersDecoded = new ArrayList<>();
        Contact user = null;

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(xmlInputStream);
            doc.getDocumentElement().normalize();

            // Extract the JSON string inside <string name="user_access_map">
            NodeList stringNodes = doc.getElementsByTagName("string");
            String rawJsonString = null;

            for (int i = 0; i < stringNodes.getLength(); i++) {
                String nameAttr = stringNodes.item(i).getAttributes().getNamedItem("name").getNodeValue();
                if ("user_access_map".equals(nameAttr)) {
                    rawJsonString = stringNodes.item(i).getTextContent();
                    break;
                }
            }

            if (rawJsonString == null) {
                logger.info("user_access_map string not found.");
                return usersDecoded;
            }

            // Decode escaped XML entities
            String jsonString = rawJsonString.replace("&quot;", "\"").replace("&amp;", "&");

            // Parse the JSON array
            JSONArray userArray = new JSONArray(jsonString);

            // Extract and print desired fields
            for (int i = 0; i < userArray.length(); i++) {
                JSONObject entry = userArray.getJSONObject(i);
                JSONObject userInfo = entry.getJSONObject("user_info");

                user = new Contact(userInfo.optString("id"));
                user.setUsername(userInfo.optString("username"));
                user.setFullname(userInfo.optString("full_name"));
                usersDecoded.add(user);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }


        return usersDecoded;
    }

    private void parseInstagramDBIOS(InputStream stream, ContentHandler handler, Metadata metadata,
                                     ParseContext context) throws TikaException {
        logger.info("--- PARSE DB IOS ---");
        IItemSearcher searcher = context.get(IItemSearcher.class);
        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
            new ParsingEmbeddedDocumentExtractor(context));
        try (Connection conn = getConnection(stream, metadata, context)) {
            PreparedStatement pstmt = conn.prepareStatement(QUERY_GET_MESSAGES);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String messageId = rs.getString("message_id");
                String chatId = rs.getString("thread_id");
                byte[] messagePlist = rs.getBytes("archive");
                addMessageIOS(messageId, chatId, messagePlist, conn);
            }

            generateChat(searcher, handler, extractor);


        } catch (Exception e1) {
            e1.printStackTrace();
            throw new TikaException("Error parsing instagram database", e1);
        }

    }

    private void addMessageIOS(String messageId, String chatId, byte[] messagePlist, Connection conn) throws
        PropertyListFormatException, IOException, ParseException, ParserConfigurationException, SAXException, SQLException {
        NSDictionary root = (NSDictionary) PropertyListParser.parse(messagePlist);
        NSArray primaryArray = null;
        int indexObjectValue;
        String fromId = null;
        String data = null;
        String messageType = null;
        long timestamp = 0;
        Chat chat = null;
        boolean fromMe = false;
        Contact from = null;
        Contact user = null;

        for (Chat chatRunner : chats) {
            if (chatRunner.getId().equals(chatId)) {
                chat = chatRunner;
            }
        }

        for (String key : root.allKeys()) {
            NSObject value = root.objectForKey(key);
            if (value instanceof NSArray) {
                primaryArray = (NSArray) value;
                break;
            }
        }

        if (primaryArray == null)
            return;

        for (NSObject element : primaryArray.getArray()) {
            if (element instanceof NSDictionary) {
                if (((NSDictionary) element).containsKey("NSString*senderPk")) {
                    indexObjectValue = Util.fromBytesToInt(((UID) ((NSDictionary) element).objectForKey("NSString*senderPk")).getBytes());
                    fromId = ((NSString) primaryArray.getArray()[indexObjectValue]).getContent();
                } else if (((NSDictionary) element).containsKey("NS.time")) {
                    double appleDate = ((NSNumber) ((NSDictionary) element).objectForKey("NS.time")).doubleValue();
                    timestamp = Util.toTimeStamp(appleDate);
                } else if (((NSDictionary) element).containsKey("NSString*string") && ((NSDictionary) element).containsKey("codedSubtype")) {
                    indexObjectValue = Util.fromBytesToInt(((UID) ((NSDictionary) element).objectForKey("NSString*string")).getBytes());
                    data = ((NSString) primaryArray.getArray()[indexObjectValue]).getContent();

                    indexObjectValue = Util.fromBytesToInt(((UID) ((NSDictionary) element).objectForKey("codedSubtype")).getBytes());
                    messageType = ((NSString) primaryArray.getArray()[indexObjectValue]).getContent();
                } else if (((NSDictionary) element).containsKey("CODED_SUBTYPE")) {
                    indexObjectValue = Util.fromBytesToInt(((UID) ((NSDictionary) element).objectForKey("CODED_SUBTYPE")).getBytes());
                    messageType = ((NSString) primaryArray.getArray()[indexObjectValue]).getContent();
                }
            }
        }

        // needs to get another plist from another sqlite table.
        if (chat == null) {
            PreparedStatement pstmt = conn.prepareStatement(QUERY_GET_MESSAGE_THREAD);
            pstmt.setString(1, chatId);
            ResultSet rs = pstmt.executeQuery();
            String userId = rs.getString("viewer_id");
            byte[] metadataPlist = rs.getBytes("metadata");
            root = (NSDictionary) PropertyListParser.parse(metadataPlist);
            primaryArray = null;
            List<Contact> participants = new ArrayList<>();
            Contact participant = null;
            String participantId, participantFullName, participantUserName;

            for (String key : root.allKeys()) {
                NSObject value = root.objectForKey(key);
                if (value instanceof NSArray) {
                    primaryArray = (NSArray) value;
                    break;
                }
            }

            if (primaryArray == null)
                return;

            for (NSObject element : primaryArray.getArray()) {
                if (element instanceof NSDictionary) {
                    if (((NSDictionary) element).containsKey("pk") && ((NSDictionary) element).containsKey("fullName") && ((NSDictionary) element).containsKey("userName")) {
                        indexObjectValue = Util.fromBytesToInt(((UID) ((NSDictionary) element).objectForKey("pk")).getBytes());
                        participantId = ((NSString) primaryArray.getArray()[indexObjectValue]).getContent();

                        indexObjectValue = Util.fromBytesToInt(((UID) ((NSDictionary) element).objectForKey("fullName")).getBytes());
                        participantFullName = ((NSString) primaryArray.getArray()[indexObjectValue]).getContent();

                        indexObjectValue = Util.fromBytesToInt(((UID) ((NSDictionary) element).objectForKey("userName")).getBytes());
                        participantUserName = ((NSString) primaryArray.getArray()[indexObjectValue]).getContent();

                        participant = getContact(participantId);
                        if (participant == null) {
                            participant = new Contact(participantId, participantUserName, participantFullName);
                            chatContacts.add(participant);
                        }
                        participants.add(participant);
                    }
                }
            }

            user = getUser(userId);
            if (user == null)
                user = new Contact(userId);

            from = getFromAllContacts(fromId);
            if (from == null)
                from = new Contact(fromId);

            if(!participants.contains(user))
                participants.add(user);

            fromMe = userId.equals(fromId);
            chat = new Chat(user, chatId, messageId, participants, data, timestamp, from, fromMe, messageType);
            chats.add(chat);
        } else {
            from = getFromAllContacts(fromId);
            if (from == null)
                from = new Contact(fromId);

            fromMe = fromId.equals(chat.getUser().getId());
            Message message = new Message(chat, messageId, data, timestamp, from, fromMe, messageType);
            chat.addMessage(message);
        }
    }


    private void parseInstagramDBAndroid(InputStream stream, ContentHandler handler, Metadata metadata,
                                         ParseContext context) throws TikaException {

        IItemSearcher searcher = context.get(IItemSearcher.class);
        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
            new ParsingEmbeddedDocumentExtractor(context));
        try (Connection conn = getConnection(stream, metadata, context)) {
            PreparedStatement pstmt = conn.prepareStatement(QUERY_GET_MESSAGES);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String messageId = rs.getString("_id");
                String chatId = rs.getString("thread_id");
                String userId = rs.getString("user_id");
                long timestamp = rs.getLong("timestamp");
                String text = rs.getString("text");
                String messageInfoJson = rs.getString("message");
                String messageType = rs.getString("message_type");
                timestamp = timestamp/1000;
                addMessageAndroid(messageId, chatId, userId, timestamp, text, messageInfoJson, messageType);
            }

            generateChat(searcher, handler, extractor);


        } catch (Exception e1) {
            e1.printStackTrace();
            throw new TikaException("Error parsing instagram database", e1);
        }
    }

    private String getChatNamePrefix(Chat c) {
        String title = "Instagram_";
        if (c.isChannel()) {
            title += "Channel";
        } else if (c.isGroup()) {
            title += "Group";
        } else {
            title += "Chat";
        }
        title += "_" + c.getName();
        return title;
    }

    private void generateChat(IItemSearcher searcher, ContentHandler handler,
                              EmbeddedDocumentExtractor extractor) throws SAXException, IOException {
        for (Chat c : chats) {
            int frag = 0;
            int firstMsg = 0;
            byte[] bytes;
            ReportGenerator r = new ReportGenerator(searcher);

            r.setMinChatSplitSize(this.minChatSplitSize);
            bytes = r.generateNextChatHtml(c);
            int nextMsg = r.getNextMsgNum();

            String chatName = getChatNamePrefix(c);
            if (frag > 0 || nextMsg < c.getMessages().size())
                chatName += "_" + frag++; //$NON-NLS-1$

            Metadata chatMetadata = new Metadata();
            chatMetadata.set(TikaCoreProperties.TITLE, chatName);
            chatMetadata.set(StandardParser.INDEXER_CONTENT_TYPE, INSTAGRAM_CHAT.toString());
            chatMetadata.set(ExtraProperties.ITEM_VIRTUAL_ID, c.getId());
            chatMetadata.set(ExtraProperties.DELETED, Boolean.toString(c.isDeleted()));
            chatMetadata.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());

            if (c.isGroup()) {
                for (Contact p : c.getParticipants()) {
                    chatMetadata.add(ExtraProperties.PARTICIPANTS, p.toString());
                }

                int participantsCount = c.getParticipants().size();
                if (participantsCount > 0) {
                    chatMetadata.add(ExtraProperties.PARTICIPANTS + "Count", String.valueOf(participantsCount));
                }
            }

            List<Message> msgSubset = c.getMessages().subList(firstMsg, nextMsg);

            if (extractMessages && !msgSubset.isEmpty()) {
                chatMetadata.set(BasicProps.HASCHILD, Boolean.TRUE.toString());
            }
            //storeLinkedHashes(msgSubset, chatMetadata);

            ByteArrayInputStream chatStream = new ByteArrayInputStream(bytes);
            extractor.parseEmbedded(chatStream, handler, chatMetadata, false);

            if (extractMessages) {
                extractMessages(chatName, msgSubset, c.getId(), handler, extractor);
            }

            firstMsg = nextMsg;
        }
    }

    private void extractMessages(String chatName, List<Message> messages, String parentId,
                                 ContentHandler handler, EmbeddedDocumentExtractor extractor) throws SAXException, IOException {
        int msgCount = 0;
        for (Message m : messages) {
            Metadata meta = new Metadata();
            meta.set(TikaCoreProperties.TITLE, chatName + "_message_" + msgCount++); //$NON-NLS-1$
            meta.set(StandardParser.INDEXER_CONTENT_TYPE, INSTAGRAM_MESSAGE.toString());
            meta.set(ExtraProperties.PARENT_VIRTUAL_ID, parentId);
            meta.set(ExtraProperties.PARENT_VIEW_POSITION, m.getId());
            meta.set(ExtraProperties.USER_ACCOUNT_TYPE, INSTAGRAM);
            meta.set(ExtraProperties.MESSAGE_DATE, m.getTimeStamp().toString());
            meta.set(TikaCoreProperties.CREATED, m.getTimeStamp().toString());
            meta.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());
//            if (m.getLatitude() != null && m.getLongitude() != null) {
//                meta.set(ExtraProperties.LOCATIONS, m.getLatitude() + ";" + m.getLongitude());
//            }
            meta.set(org.apache.tika.metadata.Message.MESSAGE_FROM, m.getFrom().toString());
            if (m.getChat().isGroup()) {
                String to = "Group ";
                to += m.getChat().getName() + " (id:" + m.getChat().getId() + ")";
                meta.add(org.apache.tika.metadata.Message.MESSAGE_TO, to);
                meta.set(ExtraProperties.IS_GROUP_MESSAGE, "true");
            }
            if (meta.get(org.apache.tika.metadata.Message.MESSAGE_TO) == null) {
                if (!m.getChat().getParticipants().isEmpty()) {
                    meta.set(org.apache.tika.metadata.Message.MESSAGE_TO, m.getRecipientContact().toString());
                } else if (m.isFromMe()) {
                    meta.set(org.apache.tika.metadata.Message.MESSAGE_TO, m.getFrom().toString());
                }
            }

            meta.set(ExtraProperties.MESSAGE_BODY, m.getData());

            meta.set("mediaName", m.getMessageType());

//            if (m.getMediaMime() != null) {
//                meta.add(ExtraProperties.MESSAGE_BODY, ATTACHMENT_MESSAGE + m.getMediaMime());
//            }
//            if (m.getMediaSize() != 0) {
//                meta.set("mediaSize", Long.toString(m.getMediaSize()));
//            }

            meta.set(BasicProps.LENGTH, "");
            extractor.parseEmbedded(new EmptyInputStream(), handler, meta, false);
        }
    }

    private void addMessageAndroid(String messageId, String id, String userId, long timeStamp, String texto, String
        messageInfoJson, String messageType) throws JsonProcessingException {
        Chat chat = null;
        Contact from = null;
        Contact recipient = null;
        boolean fromMe = false;
        List<Contact> participants = new ArrayList<>();

        ObjectMapper objectMapper = new ObjectMapper();

        JsonNode rootNode = objectMapper.readTree(messageInfoJson);
        String fromId = rootNode.path("user_id").asText();
        from = getFromAllContacts(fromId);

        if (getUser(fromId) != null || rootNode.path("is_sent_by_viewer").asBoolean()) {
            fromMe = true;
        }

        if (from == null) {
            from = new Contact(fromId);
        }

        if (chats == null) {
            chats = new ArrayList<>();
        }

        for (Chat chatRunner : chats) {
            if (chatRunner.getId().equals(id)) {
                chat = chatRunner;
            }
        }

        if (chat == null) {
            Contact user = getUser(userId);
            if (user == null) {
                user = new Contact(userId);
            }
            participants.add(user);
            JsonNode recipientArray = rootNode.path("thread_key").path("recipient_ids");

            for (JsonNode idNode : recipientArray) {
                recipient = getFromAllContacts(idNode.asText());

                if (recipient == null) {
                    recipient = new Contact(idNode.asText());
                }

                participants.add(recipient);
            }

            chat = new Chat(user, id, messageId, participants, texto, timeStamp, from, fromMe, messageType);
            chats.add(chat);
        } else {
            Message message = new Message(chat, messageId, texto, timeStamp, from, fromMe, messageType);
            chat.addMessage(message);
        }
    }

    private Contact getUser(String userId) {
        for (Contact u : users) {
            if (u.getId().equals(userId))
                return u;
        }
        return null;
    }

    private Contact getFromAllContacts(String id) {
        Contact c = getUser(id);
        if (c != null) {
            return c;
        } else {
            return getContact(id);
        }
    }

}
