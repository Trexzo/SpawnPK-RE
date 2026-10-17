package spk.local;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Versioned CUSTOM_LOCALLAB account snapshot extension for Mailbox envelopes.
 *
 * The original SpawnPK mail storage, expiry, item generation and settlement
 * rules have NOT been recovered. This codec only preserves explicit local
 * domain state, including external settlement acknowledgements already
 * recorded by the semantic service. Deserializing CLAIMED never awards items.
 */
final class LocalLabMailboxPersistence {
    static final String NAMESPACE="mailbox-g21";
    static final String VERSION="1";
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G217_MAILBOX_PERSISTENCE_V1";
    static final int MAX_MESSAGES=128;
    static final int MAX_ATTACHMENTS=128;
    static final int MAX_TEXT_BYTES=8192;
    static final int MAX_KEYS=
        3+MAX_MESSAGES*(7+2*MAX_ATTACHMENTS);

    static SortedMap<String,String> encode(
        MailboxRewardDeliveryService service
    ){
        Objects.requireNonNull(service,"service");
        List<MailboxRewardDeliveryService.RestoredEntry> rows=
            new ArrayList<>();

        for(MailboxRewardDeliveryService.Snapshot row:
                service.snapshot())
            rows.add(new MailboxRewardDeliveryService.RestoredEntry(
                row.message,
                row.readState,
                row.claimState
            ));

        return encodeRows(rows);
    }

    private static SortedMap<String,String> encodeRows(
        List<MailboxRewardDeliveryService.RestoredEntry> rows
    ){
        Objects.requireNonNull(rows,"rows");

        if(rows.size()>MAX_MESSAGES)
            throw invalid("message count",rows.size());

        TreeMap<String,String> out=new TreeMap<>();
        out.put("version",VERSION);
        out.put("authority",AUTHORITY);
        out.put("count",Integer.toString(rows.size()));

        Set<String> ids=new HashSet<>();

        for(int i=0;i<rows.size();i++){
            MailboxRewardDeliveryService.RestoredEntry row=
                Objects.requireNonNull(rows.get(i),"row");
            RewardDeliveryMessage msg=
                Objects.requireNonNull(row.message,"message");

            if(!ids.add(msg.messageId))
                throw invalid("duplicate ID",msg.messageId);

            if(row.readState==null||row.claimState==null)
                throw invalid("state null",i);

            if(msg.hasAttachments()
                ?row.claimState==MailboxRewardDeliveryService.ClaimState.EMPTY
                :row.claimState!=MailboxRewardDeliveryService.ClaimState.EMPTY)
                throw invalid("claim/attachments mismatch",i);

            if(msg.attachments.size()>MAX_ATTACHMENTS)
                throw invalid("attachments count",i);

            String base="row."+i+".";
            out.put(base+"id",encodeText(msg.messageId));
            out.put(base+"subject",encodeText(msg.subject));
            out.put(base+"body",encodeText(msg.body));
            out.put(base+"source",encodeText(msg.sourceAuthority));
            out.put(base+"read",row.readState.name());
            out.put(base+"claim",row.claimState.name());
            out.put(base+"attachments",
                Integer.toString(msg.attachments.size()));

            for(int j=0;j<msg.attachments.size();j++){
                RewardDeliveryMessage.Attachment item=
                    Objects.requireNonNull(
                        msg.attachments.get(j),
                        "attachment"
                    );

                if(item.itemId<0||item.amount<=0)
                    throw invalid("attachment value",j);

                out.put(base+"item."+j+".id",
                    Integer.toString(item.itemId));
                out.put(base+"item."+j+".amount",
                    Long.toString(item.amount));
            }
        }

        return Collections.unmodifiableSortedMap(out);
    }

    /**
     * Entirely validate the untrusted namespace before publishing any state.
     * Returns immutable rows to feed the semantic service's atomic restore.
     * Empty namespace represents an account without Mailbox persistence.
     */
    static List<MailboxRewardDeliveryService.RestoredEntry> decode(
        SortedMap<String,String> persisted
    ){
        if(persisted==null||persisted.isEmpty())
            return Collections.emptyList();

        if(persisted.size()>MAX_KEYS)
            throw invalid("key budget",persisted.size());

        if(!VERSION.equals(persisted.get("version"))||
            !AUTHORITY.equals(persisted.get("authority")))
            throw invalid("version or authority",persisted.keySet());

        int count=parseInt(persisted.get("count"),"count");

        if(count<0||count>MAX_MESSAGES)
            throw invalid("message count",count);

        ArrayList<MailboxRewardDeliveryService.RestoredEntry> rows=
            new ArrayList<>();
        Set<String> ids=new HashSet<>();

        for(int i=0;i<count;i++){
            String base="row."+i+".";
            String id=decodeText(
                persisted.get(base+"id"),base+"id");
            String subject=decodeText(
                persisted.get(base+"subject"),base+"subject");
            String body=decodeText(
                persisted.get(base+"body"),base+"body");
            String authority=decodeText(
                persisted.get(base+"source"),base+"source");

            MailboxRewardDeliveryService.ReadState read=
                parseRead(persisted.get(base+"read"));
            MailboxRewardDeliveryService.ClaimState claim=
                parseClaim(persisted.get(base+"claim"));

            int itemCount=parseInt(
                persisted.get(base+"attachments"),
                base+"attachments"
            );

            if(itemCount<0||itemCount>MAX_ATTACHMENTS)
                throw invalid("attachments count",itemCount);

            ArrayList<RewardDeliveryMessage.Attachment> items=
                new ArrayList<>();

            for(int j=0;j<itemCount;j++){
                String itemBase=base+"item."+j+".";
                int itemId=parseInt(
                    persisted.get(itemBase+"id"),
                    itemBase+"id"
                );
                long quantity=parseLong(
                    persisted.get(itemBase+"amount"),
                    itemBase+"amount"
                );
                items.add(
                    new RewardDeliveryMessage.Attachment(
                        itemId,
                        quantity
                    )
                );
            }

            RewardDeliveryMessage message=
                new RewardDeliveryMessage(
                    id,subject,body,items,authority
                );

            // Reject aliases/noncanonical normalized IDs.
            if(!message.messageId.equals(id))
                throw invalid("noncanonical message ID",id);

            if(!ids.add(message.messageId))
                throw invalid("duplicate message ID",id);

            MailboxRewardDeliveryService.RestoredEntry row=
                new MailboxRewardDeliveryService.RestoredEntry(
                    message,read,claim
                );

            rows.add(row);
        }

        // This exact canonical round-trip also rejects every unknown key,
        // missing key, noncanonical numeric, extra item and mismatched state.
        if(!encodeRows(rows).equals(persisted))
            throw invalid("noncanonical/unknown fields",persisted.keySet());

        return Collections.unmodifiableList(rows);
    }

    private static String encodeText(String value){
        Objects.requireNonNull(value,"text");
        byte[] bytes=value.getBytes(StandardCharsets.UTF_8);
        if(bytes.length>MAX_TEXT_BYTES||
            !value.equals(new String(bytes,StandardCharsets.UTF_8)))
            throw invalid("UTF-8 text",value.length());
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static String decodeText(String value,String key){
        if(value==null||
            value.length()>4*((MAX_TEXT_BYTES+2)/3)+4)
            throw invalid("text length",key);
        try{
            byte[] decoded=Base64.getDecoder().decode(value);
            if(decoded.length>MAX_TEXT_BYTES||
                !Base64.getEncoder().encodeToString(decoded).equals(value))
                throw invalid("noncanonical base64",key);
            CharBuffer text=StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(decoded));
            return text.toString();
        }catch(IllegalArgumentException |
                CharacterCodingException failure){
            throw invalid("invalid UTF-8/Base64",key);
        }
    }

    private static int parseInt(String text,String name){
        try{
            int parsed=Integer.parseInt(text);
            if(!Integer.toString(parsed).equals(text))
                throw invalid("noncanonical integer",name);
            return parsed;
        }catch(RuntimeException failure){
            throw invalid("invalid integer",name);
        }
    }

    private static long parseLong(String text,String name){
        try{
            long parsed=Long.parseLong(text);
            if(!Long.toString(parsed).equals(text))
                throw invalid("noncanonical long",name);
            return parsed;
        }catch(RuntimeException failure){
            throw invalid("invalid long",name);
        }
    }

    private static MailboxRewardDeliveryService.ReadState parseRead(
        String text
    ){
        try{
            return MailboxRewardDeliveryService.ReadState.valueOf(text);
        }catch(RuntimeException failure){
            throw invalid("read state",text);
        }
    }

    private static MailboxRewardDeliveryService.ClaimState parseClaim(
        String text
    ){
        try{
            return MailboxRewardDeliveryService.ClaimState.valueOf(text);
        }catch(RuntimeException failure){
            throw invalid("claim state",text);
        }
    }

    private static IllegalArgumentException invalid(
        String field,Object value
    ){
        return new IllegalArgumentException(
            "invalid LocalLab Mailbox persistence "+
            field+"="+value
        );
    }

    private LocalLabMailboxPersistence(){}
}
