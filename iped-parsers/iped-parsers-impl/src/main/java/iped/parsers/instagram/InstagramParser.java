package iped.parsers.instagram;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import iped.parsers.util.ItemInfo;
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
    private static Logger logger = LoggerFactory.getLogger(InstagramParser.class);

    public static final String INSTAGRAM = "Instagram";
    public static final MediaType INSTAGRAM_ACCOUNT = MediaType.parse("application/x-instagram-account");
    public static final MediaType INSTAGRAM_USER_CONF = MediaType.parse("application/x-instagram-user-conf");
    public static final MediaType INSTAGRAM_DB = MediaType.parse("application/x-instagram-db");
    public static final MediaType INSTAGRAM_DB_IOS = MediaType.parse("application/x-instagram-db-ios");
    public static final MediaType INSTAGRAM_CHAT = MediaType.parse("application/x-instagram-chat");
    public static final MediaType INSTAGRAM_CONTACT = MediaType.parse("contact/x-instagram-contact");
    public static final MediaType INSTAGRAM_MESSAGE = MediaType.parse("message/x-instagram-message");
    public static final MediaType INSTAGRAM_ATTACHMENT = MediaType.parse("message/x-instagram-attachment");
    public static final MediaType INSTAGRAM_CALL = MediaType.parse("call/x-instagram-call");

    private static Set<MediaType> SUPPORTED_TYPES = MediaType.set(INSTAGRAM_DB, INSTAGRAM_CONTACT, INSTAGRAM_USER_CONF, INSTAGRAM_DB_IOS);

    // TODO improve this: prefix to show 'attachment' before body text (values
    // are sorted)
    private static final String ATTACHMENT_PREFIX = "! ";

    // TODO externalize to locale properties
    private static final String ATTACHMENT_MESSAGE = ATTACHMENT_PREFIX + "Attachment: ";

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

        logger.info("---------- entrou parse inicial ---------");
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
        } else if (mimetype.equals(INSTAGRAM_CONTACT.toString())) {
            parseChatContacts(stream, handler, metadata, context);
        }
    }

    private void parseChatContacts(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) {
        logger.info("---------- entrou parse CONTACTS ---------");
        try {
            chatContacts.addAll(decodeAndroidContacts(stream));
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (SAXException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Contact> decodeAndroidContacts(InputStream stream) throws ParserConfigurationException, IOException, SAXException {
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

                if(getContact(id) == null) {
                    contacts.add(new Contact(id, username, fullName));
                }
            }
        }
        return contacts;
    }

    private void parseAndroidAccount(InputStream stream, ContentHandler handler, Metadata metadata,
                                     ParseContext context) throws SAXException, IOException, TikaException {

        try {
            logger.info("---------- entrou parse android ---------");
            Contact user = decodeAndroidAccount(stream);
            if (user != null) {
                createAccountHTML(user, handler, context);
                users.add(user);
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
        Extractor ex = new Extractor();
        IItemSearcher searcher = context.get(IItemSearcher.class);
        // ex.setSearcher(searcher);
        // ex.searchAvatarFileName(user, user.getPhotos());
        if (user.getAvatar() != null) {
            meta.set(ExtraProperties.THUMBNAIL_BASE64, Base64.getEncoder().encodeToString(user.getAvatar()));
        }

        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
            new ParsingEmbeddedDocumentExtractor(context));
        ReportGenerator reportGenerator = new ReportGenerator(searcher);
        byte[] bytes = reportGenerator.genarateContactHtml(user);
        ByteArrayInputStream contactStream = new ByteArrayInputStream(bytes);
        extractor.parseEmbedded(contactStream, handler, meta, false);

    }

    private Contact decodeAndroidAccount(InputStream xmlInputStream) {
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
                return user;
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
                user.setName(userInfo.optString("full_name"));
            }

            logger.info(user.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }


        return user;
    }

    private void parseInstagramDBIOS(InputStream stream, ContentHandler handler, Metadata metadata,
                                     ParseContext context) {
        // TODO Auto-generated method stub

    }

    private void parseInstagramDBAndroid(InputStream stream, ContentHandler handler, Metadata metadata,
                                         ParseContext context) throws TikaException {

        logger.info("--- PARSE DB ANDROID ---");
        IItemSearcher searcher = context.get(IItemSearcher.class);

        String dbPath = ((ItemInfo) context.get(ItemInfo.class)).getPath();
        populateContacts(searcher, dbPath);

        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
            new ParsingEmbeddedDocumentExtractor(context));
        try (Connection conn = getConnection(stream, metadata, context)) {
            PreparedStatement pstmt = conn.prepareStatement(Extractor.GET_CHATS);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                long messageId = rs.getLong("_id");
                String chatId = rs.getString("thread_id");
                String userId = rs.getString("user_id");
                long recipientIds = rs.getLong("recipient_ids");
                long timestamp = rs.getLong("timestamp");
                String text = rs.getString("text");
                String messageInfoJson = rs.getString("message");
                addMessage(messageId, chatId, userId, recipientIds, timestamp, text, messageInfoJson);
            }

            generateChat(searcher, handler, extractor);


        } catch (Exception e1) {
            e1.printStackTrace();
            throw new TikaException("Error parsing instagram database", e1);
        }
    }

    private void populateContacts(IItemSearcher searcher, String dbPath) {
        String query;
        List<IItemReader> result;
        Contact user;

        if (searcher == null) {
            return;
        }

        query = BasicProps.CONTENTTYPE + ":\"" + INSTAGRAM_ACCOUNT.toString() + "\"";
        result = searcher.search(query);
        if (!result.isEmpty()) {
            IItemReader item = result.get(0);
            try (InputStream is = item.getBufferedInputStream()) {
                logger.info("__________ Encontrou o xml configurações da conta de usuário ------");
                user = decodeAndroidAccount(is);
                if (user != null) {
                    users.add(user);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

        query = BasicProps.CONTENTTYPE + ":\"" + INSTAGRAM_CONTACT.toString() + "\"";
        result = searcher.search(query);
        for (IItemReader item : result) {
            try (InputStream is = item.getBufferedInputStream()) {
                logger.info("__________ Encontrou o xml de contatos ------");
                chatContacts.addAll(decodeAndroidContacts(is));
            } catch (Exception e) {
                e.printStackTrace();
            }
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
        title += "_" + "teste";
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

//            if (c.isGroupOrChannel()) {
//                ChatGroup cg = (ChatGroup) c;
//                for (long id : cg.getMembers()) {
//                    chatMetadata.add(ExtraProperties.PARTICIPANTS, e.getContact(id).toString());
//                }
//                for (long id : cg.getAdmins()) {
//                    chatMetadata.add(ExtraProperties.COMMUNICATION_PREFIX + "ChannelAdmins",
//                        e.getContact(id).toString());
//                }
//                int participantsCount = cg.getParticipantsCount();
//                if (participantsCount > 0) {
//                    chatMetadata.add(ExtraProperties.PARTICIPANTS + "Count", String.valueOf(participantsCount));
//                }
//            }

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
            //meta.set(ExtraProperties.PARENT_VIEW_POSITION, String.valueOf(m.getId()));
            meta.set(ExtraProperties.USER_ACCOUNT_TYPE, INSTAGRAM);
            meta.set(ExtraProperties.MESSAGE_DATE, m.getTimeStamp().toString());
            meta.set(TikaCoreProperties.CREATED, m.getTimeStamp().toString());
            meta.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());
//            if (m.getLatitude() != null && m.getLongitude() != null) {
//                meta.set(ExtraProperties.LOCATIONS, m.getLatitude() + ";" + m.getLongitude());
//            }
//            meta.set(org.apache.tika.metadata.Message.MESSAGE_FROM, m.getFrom().toString());
//            if (m.getChat().isGroupOrChannel()) {
//                ChatGroup groupChat = (ChatGroup) m.getChat();
//                String to = groupChat.isGroup() ? "Group " : "Channel ";
//                to += groupChat.getName() + " (id:" + groupChat.getId() + ")";
//                meta.add(org.apache.tika.metadata.Message.MESSAGE_TO, to);
//                meta.set(ExtraProperties.IS_GROUP_MESSAGE, "true");
//            }
//            if (meta.get(org.apache.tika.metadata.Message.MESSAGE_TO) == null) {
//                if (m.getToId() != 0) {
//                    meta.set(org.apache.tika.metadata.Message.MESSAGE_TO, e.getContact(m.getToId()).toString());
//                } else if (m.isFromMe()) {
//                    meta.set(org.apache.tika.metadata.Message.MESSAGE_TO, m.getChat().getC().toString());
//                } else if (account != null) {
//                    meta.set(org.apache.tika.metadata.Message.MESSAGE_TO, account.toString());
//                }
//            }
//
//            meta.set(ExtraProperties.MESSAGE_BODY, m.getData());
//
//            meta.set("mediaName", m.getMediaName());
//
//            if (m.getMediaMime() != null) {
//                meta.add(ExtraProperties.MESSAGE_BODY, ATTACHMENT_MESSAGE + m.getMediaMime());
//            }
//            if (m.getMediaSize() != 0) {
//                meta.set("mediaSize", Long.toString(m.getMediaSize()));
//            }
//            if (Util.isValidHash(m.getMediaHash())) {
//                meta.set(StandardParser.INDEXER_CONTENT_TYPE, TELEGRAM_ATTACHMENT.toString());
//                meta.set(ExtraProperties.LINKED_ITEMS, BasicProps.HASH + ":" + m.getMediaHash()); //$NON-NLS-1$
//                if (!m.getChildPornSets().isEmpty()) {
//                    meta.set("hash:status", "pedo");
//                    for (String set : m.getChildPornSets()) {
//                        meta.add("hash:set", set);
//                    }
//                }
//                // TODO store thumb in metadata?
//            }
//            if (m.isPhoneCall()) {
//                meta.set(StandardParser.INDEXER_CONTENT_TYPE, TELEGRAM_CALL.toString());
//            }
//            // system messages
//            if (meta.get(ExtraProperties.MESSAGE_BODY) == null && m.getType() != null && !m.getType().isEmpty()) {
//                meta.add(ExtraProperties.MESSAGE_BODY, m.getType().toUpperCase());
//            }

            meta.set(BasicProps.LENGTH, "");
            extractor.parseEmbedded(new EmptyInputStream(), handler, meta, false);
        }
    }

    private void addMessage(long messageId, String id, String userId, long recipientIds, long timeStamp, String texto, String messageInfoJson) throws JsonProcessingException {
        Chat chat = null;
        Contact from = null;
        boolean fromMe = false;
        List<Contact> recipients = new ArrayList<>();

        ObjectMapper objectMapper = new ObjectMapper();

        JsonNode rootNode = objectMapper.readTree(messageInfoJson);
        fromMe = rootNode.path("is_sent_by_viewer").asBoolean();
        String fromId = rootNode.path("user_id").asText();
        from = new Contact(fromId);
        JsonNode recipientArray = rootNode.path("thread_key").path("recipient_ids");

        for (JsonNode idNode : recipientArray) {
            recipients.add(new Contact(idNode.asText()));
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
            chat = new Chat(id, messageId, recipients, texto, timeStamp, from, fromMe);
            chats.add(chat);
        } else {
            Message message = new Message(messageId, recipients, texto, timeStamp, from, fromMe);
            chat.addMessage(message);
        }
    }

}
