package spk.local;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * G21.22 inert, versioned account-snapshot PREPARED intent.
 *
 * An intent is NOT a settlement: no item, claim state or socket write happens.
 * Even a successfully saved intent grants nothing at load/replay. The current
 * async persistence + non-atomic file-move fallback do not justify a durable
 * inventory-plus-Mailbox commit/ACK. Keep native C2S185 widget 32181 gated.
 *
 * The SHA-256 key is for accidental mismatch/replay detection, not an
 * authentication signature against adversarial snapshot-file editing.
 */
final class MailboxPreparedClaimJournal {
    static final String NAMESPACE="mailbox-claim-intent";
    static final String VERSION="1";
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G2122_PREPARED_ONLY_NO_GRANT";
    static final String STATE="PREPARED_NO_GRANT";
    private static final int SLOTS=BankState.INVENTORY_CAPACITY;
    private static final int MAX_TEXT=16384;

    static final class Intent {
        final String account;
        final String messageId;
        final String attachmentFingerprint;
        final String idempotencyKey;
        private final int[] beforeIds;
        private final int[] beforeAmounts;
        private final int[] afterIds;
        private final int[] afterAmounts;

        private Intent(
            String account,String messageId,String attachments,
            int[] beforeIds,int[] beforeAmounts,
            int[] afterIds,int[] afterAmounts
        ){
            this.account=account;
            this.messageId=messageId;
            this.attachmentFingerprint=attachments;
            this.beforeIds=beforeIds.clone();
            this.beforeAmounts=beforeAmounts.clone();
            this.afterIds=afterIds.clone();
            this.afterAmounts=afterAmounts.clone();
            this.idempotencyKey=sha256(material());
        }

        int[] expectedItemIds(){return beforeIds.clone();}
        int[] expectedQuantities(){return beforeAmounts.clone();}
        int[] proposedItemIds(){return afterIds.clone();}
        int[] proposedQuantities(){return afterAmounts.clone();}

        private String material(){
            return VERSION+"|"+AUTHORITY+"|"+STATE+"|"+
                account+"|"+messageId+"|"+attachmentFingerprint+"|"+
                encodeSlots(beforeIds,beforeAmounts)+"|"+
                encodeSlots(afterIds,afterAmounts);
        }
    }

    /** Stage a deterministic proposal from real, current player state. */
    static Intent prepare(
        WorldPlayer player,
        MailboxRewardDeliveryService.Snapshot selected
    ){
        WorldPlayer owner=Objects.requireNonNull(player,"player");
        MailboxRewardDeliveryService.Snapshot bound=
            Objects.requireNonNull(selected,"selected");
        synchronized(owner.mutationLock()){
            String account=canonicalAccount(owner.username());
            if(!owner.registered())
                throw new IllegalStateException(
                    "claim journal requires registered owner"
                );
            MailboxRewardDeliveryService.Snapshot live=
                owner.mailbox().get(bound.message.messageId);
            if(live==null||live.message!=bound.message)
                throw new IllegalStateException("stale claim envelope");
            MailboxInventoryClaimPreflight.Preview preview=
                MailboxInventoryClaimPreflight.inspect(owner,live);
            if(!preview.eligible)
                throw new IllegalStateException(
                    "inventory preflight denied "+preview.reason
                );

            int[] beforeIds=new int[SLOTS];
            int[] beforeAmounts=new int[SLOTS];
            Arrays.fill(beforeIds,-1);
            for(int i=0;i<SLOTS;i++){
                BankState.InventorySlotSnapshot slot=
                    owner.bank().inventorySlotSnapshot(i);
                if(slot.occupied){
                    beforeIds[i]=slot.itemId;
                    beforeAmounts[i]=slot.quantity;
                }
            }
            String attachments=encodeAttachments(
                live.message.attachments
            );
            return new Intent(
                account,live.message.messageId,attachments,
                beforeIds,beforeAmounts,
                preview.itemIds,preview.quantities
            );
        }
    }

    /** Immutable canonical representation for one snapshot namespace. */
    static SortedMap<String,String> encode(Intent intent){
        Intent in=Objects.requireNonNull(intent,"intent");
        TreeMap<String,String> values=new TreeMap<>();
        values.put("version",VERSION);
        values.put("authority",AUTHORITY);
        values.put("state",STATE);
        values.put("account",in.account);
        values.put("message",in.messageId);
        values.put("attachments",in.attachmentFingerprint);
        values.put("before",
            encodeSlots(in.beforeIds,in.beforeAmounts));
        values.put("after",
            encodeSlots(in.afterIds,in.afterAmounts));
        values.put("key",in.idempotencyKey);
        return Collections.unmodifiableSortedMap(values);
    }

    /** All-or-nothing decode; reject unknown keys, aliases and alterations. */
    static Intent decode(Map<String,String> untrusted){
        Objects.requireNonNull(untrusted,"journal");
        if(untrusted.size()!=9)
            throw new IllegalArgumentException(
                "prepared journal key count"
            );
        for(Map.Entry<String,String> entry:untrusted.entrySet()){
            if(entry.getKey()==null||entry.getValue()==null||
               entry.getValue().length()>MAX_TEXT)
                throw new IllegalArgumentException(
                    "invalid journal key or size"
                );
        }
        if(!VERSION.equals(untrusted.get("version"))||
           !AUTHORITY.equals(untrusted.get("authority"))||
           !STATE.equals(untrusted.get("state")))
            throw new IllegalArgumentException(
                "unsupported claim journal state or authority"
            );
        String account=canonicalAccount(untrusted.get("account"));
        String message=RewardDeliveryMessage.normalizeMessageId(
            untrusted.get("message")
        );
        if(!message.equals(untrusted.get("message")))
            throw new IllegalArgumentException(
                "noncanonical journal message ID"
            );
        String attachments=canonicalAttachments(
            untrusted.get("attachments")
        );
        int[][] before=decodeSlots(untrusted.get("before"));
        int[][] after=decodeSlots(untrusted.get("after"));
        Intent canonical=new Intent(
            account,message,attachments,
            before[0],before[1],after[0],after[1]
        );
        if(!encode(canonical).equals(untrusted))
            throw new IllegalArgumentException(
                "noncanonical or changed prepared journal"
            );
        return canonical;
    }

    /**
     * Only record a PREPARED-only marker in the same account snapshot
     * extension space. No grant is permitted even if this is later saved.
     * A different uncommitted proposal may not silently replace the first.
     */
    static boolean stageOnly(WorldPlayer player,Intent proposed){
        WorldPlayer owner=Objects.requireNonNull(player,"player");
        Intent intent=Objects.requireNonNull(proposed,"intent");
        synchronized(owner.mutationLock()){
            Intent live=prepare(
                owner,requireCurrentEnvelope(owner,intent)
            );
            if(!live.idempotencyKey.equals(intent.idempotencyKey))
                throw new IllegalStateException(
                    "prepared claim preimage or attachments changed"
                );
            SortedMap<String,String> existing=
                owner.snapshotExtensions().namespace(NAMESPACE);
            if(!existing.isEmpty()){
                Intent previous=decode(existing);
                if(!previous.idempotencyKey.equals(intent.idempotencyKey))
                    throw new IllegalStateException(
                        "conflicting prepared claim intent"
                    );
                return false;
            }
            owner.snapshotExtensions().replaceNamespace(
                NAMESPACE,encode(intent)
            );
            return true;
        }
    }

    /** PREPARED remains inert on restart; no implicit replay grant. */
    static Intent inspectPrepared(WorldPlayer player){
        WorldPlayer owner=Objects.requireNonNull(player,"player");
        synchronized(owner.mutationLock()){
            SortedMap<String,String> values=
                owner.snapshotExtensions().namespace(NAMESPACE);
            return values.isEmpty()?null:decode(values);
        }
    }

    private static MailboxRewardDeliveryService.Snapshot
        requireCurrentEnvelope(WorldPlayer player,Intent intent){
        if(!canonicalAccount(player.username()).equals(intent.account))
            throw new IllegalStateException("prepared owner mismatch");
        MailboxRewardDeliveryService.Snapshot live=
            player.mailbox().get(intent.messageId);
        if(live==null||
           live.claimState!=
               MailboxRewardDeliveryService.ClaimState.UNCLAIMED)
            throw new IllegalStateException(
                "no unclaimed envelope for prepared intent"
            );
        return live;
    }

    private static String canonicalAccount(String username){
        if(username==null||
           username.trim().isEmpty()||
           !username.equals(username.trim().toLowerCase(
               java.util.Locale.ROOT
           ))||
           username.length()>64||
           !username.matches("[a-z0-9_-]+"))
            throw new IllegalArgumentException(
                "noncanonical claim journal account"
            );
        return username;
    }

    private static String encodeAttachments(
        java.util.List<RewardDeliveryMessage.Attachment> items
    ){
        if(items==null||items.isEmpty()||items.size()>128)
            throw new IllegalArgumentException(
                "claim attachments count"
            );
        StringBuilder out=new StringBuilder();
        for(RewardDeliveryMessage.Attachment item:items){
            if(item==null||item.itemId<0||item.itemId>=65535||
               item.amount<=0||item.amount>Integer.MAX_VALUE)
                throw new IllegalArgumentException(
                    "invalid prepared attachment"
                );
            if(out.length()>0)out.append(',');
            out.append(item.itemId).append(':').append(item.amount);
        }
        return out.toString();
    }

    private static String canonicalAttachments(String text){
        if(text==null||text.isEmpty()||text.length()>MAX_TEXT)
            throw new IllegalArgumentException("claim attachment encoding");
        String[] parts=text.split(",",-1);
        java.util.ArrayList<RewardDeliveryMessage.Attachment> items=
            new java.util.ArrayList<>();
        for(String part:parts){
            String[] pair=part.split(":",-1);
            if(pair.length!=2)
                throw new IllegalArgumentException(
                    "noncanonical attachment pair"
                );
            int id=parseInt(pair[0]);
            int count=parseInt(pair[1]);
            items.add(new RewardDeliveryMessage.Attachment(id,count));
        }
        String canonical=encodeAttachments(items);
        if(!canonical.equals(text))
            throw new IllegalArgumentException(
                "noncanonical claim attachments"
            );
        return canonical;
    }

    private static String encodeSlots(int[] ids,int[] amounts){
        if(ids==null||amounts==null||
           ids.length!=SLOTS||amounts.length!=SLOTS)
            throw new IllegalArgumentException(
                "invalid prepared inventory length"
            );
        StringBuilder out=new StringBuilder();
        for(int i=0;i<SLOTS;i++){
            int id=ids[i],count=amounts[i];
            if(id==-1?count!=0:id<0||id>=65535||count<=0)
                throw new IllegalArgumentException(
                    "invalid prepared inventory slot "+i
                );
            if(i>0)out.append(',');
            out.append(id).append(':').append(count);
        }
        return out.toString();
    }

    private static int[][] decodeSlots(String value){
        if(value==null||value.length()>MAX_TEXT)
            throw new IllegalArgumentException(
                "prepared inventory too large"
            );
        String[] parts=value.split(",",-1);
        if(parts.length!=SLOTS)
            throw new IllegalArgumentException(
                "prepared inventory must be 28 slots"
            );
        int[] ids=new int[SLOTS],amounts=new int[SLOTS];
        for(int i=0;i<SLOTS;i++){
            String[] pair=parts[i].split(":",-1);
            if(pair.length!=2)
                throw new IllegalArgumentException(
                    "bad prepared inventory slot"
                );
            ids[i]=parseInt(pair[0]);
            amounts[i]=parseInt(pair[1]);
        }
        if(!encodeSlots(ids,amounts).equals(value))
            throw new IllegalArgumentException(
                "noncanonical prepared inventory"
            );
        return new int[][]{ids,amounts};
    }

    private static int parseInt(String value){
        try{
            int parsed=Integer.parseInt(value);
            if(!Integer.toString(parsed).equals(value))
                throw new IllegalArgumentException(
                    "noncanonical integer"
                );
            return parsed;
        }catch(NumberFormatException invalid){
            throw new IllegalArgumentException(
                "invalid journal integer",invalid
            );
        }
    }

    private static String sha256(String value){
        try{
            byte[] hash=MessageDigest.getInstance("SHA-256").digest(
                value.getBytes(StandardCharsets.UTF_8)
            );
            StringBuilder out=new StringBuilder();
            for(byte b:hash)
                out.append(String.format(
                    java.util.Locale.ROOT,"%02x",b&255
                ));
            return out.toString();
        }catch(NoSuchAlgorithmException impossible){
            throw new IllegalStateException(
                "Java SHA-256 unavailable",impossible
            );
        }
    }

    private MailboxPreparedClaimJournal(){}
}
