package spk.local;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * G21.64: terminal transaction identity embedded in the SAME immutable
 * account snapshot as the hypothetical inventory credit and Mailbox
 * CLAIMED state. This is a detached, non-granting postimage builder.
 *
 * A coherent record does not establish durable positive COMMIT,
 * live owner transition, exactly-once credit or restart authorization.
 */
final class MailboxAtomicTerminalSnapshot {
    static final String NAMESPACE="mailbox-terminal-snapshot";
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G2164_TERMINAL_ACCOUNT_NO_GRANT";
    static final String TERMINAL="TERMINAL_NO_GRANT";
    static final String VERSION="1";
    private static final String PREFIX="extension."+NAMESPACE+".";

    enum State { ABSENT, COHERENT_TERMINAL_NO_GRANT, INVALID_TERMINAL }

    static final class Observation {
        final State state;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean rollbackAuthorized=false;
        final boolean releaseAuthorized=false;
        final boolean clientAckAuthorized=false;
        Observation(State state){this.state=state;}
    }

    static PlayerSnapshot compose(
        MailboxSettlementPostimagePlanner.Proposal p
    ){
        Objects.requireNonNull(p,"proposal");
        if(!p.account.equals(p.preparedPreimage.username())||
           !p.account.equals(p.hypotheticalPostimage.username())||
           MailboxPreparedRestartAdmission.inspect(p.preparedPreimage)
               .state!=MailboxPreparedRestartAdmission.State
                   .VALID_PREPARED_UNCLAIMED||
           p.classify(p.hypotheticalPostimage)!=
               MailboxSettlementPostimagePlanner.RecoveryClass
                   .EXACT_HYPOTHETICAL_POSTIMAGE||
           contains(p.hypotheticalPostimage))
            throw new IllegalArgumentException(
                "G21.64 terminal proposal is not a fresh preimage");
        String before=StrictDurablePlayerSnapshotWriter
            .canonicalSnapshotSha256(p.preparedPreimage);
        String after=StrictDurablePlayerSnapshotWriter
            .canonicalSnapshotSha256(p.hypotheticalPostimage);
        SortedMap<String,String> record=fields(
            p.account,p.messageId,p.idempotencyKey,before,after);
        TreeMap<String,String> values=
            new TreeMap<>(p.hypotheticalPostimage.values());
        for(Map.Entry<String,String> field:record.entrySet())
            values.put(PREFIX+field.getKey(),field.getValue());
        PlayerSnapshot terminal=new PlayerSnapshot(
            PlayerSnapshot.CURRENT_VERSION,p.account,values);
        if(inspect(terminal).state!=State.COHERENT_TERMINAL_NO_GRANT)
            throw new IllegalStateException(
                "G21.64 generated terminal snapshot invalid");
        return terminal;
    }

    /** Read-only validation. Never mutates a WorldPlayer or filesystem. */
    static Observation inspect(PlayerSnapshot account){
        Objects.requireNonNull(account,"account");
        if(!contains(account))return new Observation(State.ABSENT);
        try{
            TreeMap<String,String> record=new TreeMap<>();
            TreeMap<String,String> baseValues=
                new TreeMap<>(account.values());
            for(Map.Entry<String,String> entry:account.values().entrySet()){
                if(entry.getKey().startsWith(PREFIX)){
                    record.put(entry.getKey().substring(PREFIX.length()),
                        entry.getValue());
                    baseValues.remove(entry.getKey());
                }
            }
            if(record.size()!=9||
               !VERSION.equals(record.get("version"))||
               !AUTHORITY.equals(record.get("authority"))||
               !TERMINAL.equals(record.get("state"))||
               !account.username().equals(record.get("account"))||
               !hex64(record.get("key"))||
               !hex64(record.get("before"))||
               !hex64(record.get("after"))||
               !hex64(record.get("checksum")))
                return invalid();
            String message=RewardDeliveryMessage.normalizeMessageId(
                record.get("message"));
            if(!message.equals(record.get("message"))||
               !record.equals(fields(account.username(),message,
                   record.get("key"),record.get("before"),
                   record.get("after"))))
                return invalid();

            PlayerSnapshot base=new PlayerSnapshot(
                account.version(),account.username(),baseValues);
            if(base.version()!=PlayerSnapshot.CURRENT_VERSION||
               !record.get("after").equals(
                   StrictDurablePlayerSnapshotWriter
                       .canonicalSnapshotSha256(base)))
                return invalid();

            PlayerSnapshot normalized=
                PlayerSnapshotCodec.validateAndNormalize(account);
            if(!normalized.values().equals(account.values()))
                return invalid();
            WorldPlayer detached=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(base,detached);
            MailboxPreparedClaimJournal.Intent intent=
                MailboxPreparedClaimJournal.inspectPrepared(detached);
            if(intent==null||
               !account.username().equals(intent.account)||
               !message.equals(intent.messageId)||
               !record.get("key").equals(intent.idempotencyKey))
                return invalid();
            MailboxRewardDeliveryService.Snapshot claim=
                detached.mailbox().get(message);
            if(claim==null||claim.claimState!=
                    MailboxRewardDeliveryService.ClaimState.CLAIMED)
                return invalid();
            StringBuilder attachments=new StringBuilder();
            for(RewardDeliveryMessage.Attachment item:
                    claim.message.attachments){
                if(attachments.length()>0)attachments.append(',');
                attachments.append(item.itemId).append(':')
                    .append(item.amount);
            }
            if(!intent.attachmentFingerprint.equals(
                    attachments.toString()))
                return invalid();
            int[] ids=intent.proposedItemIds();
            int[] qty=intent.proposedQuantities();
            if(ids.length!=BankState.INVENTORY_CAPACITY||
               qty.length!=BankState.INVENTORY_CAPACITY)
                return invalid();
            for(int slot=0;slot<ids.length;slot++){
                BankState.InventorySlotSnapshot current=
                    detached.bank().inventorySlotSnapshot(slot);
                if(ids[slot]!=(current.occupied?current.itemId:-1)||
                   qty[slot]!=(current.occupied?current.quantity:0))
                    return invalid();
            }
            return new Observation(State.COHERENT_TERMINAL_NO_GRANT);
        }catch(RuntimeException malformed){
            return invalid();
        }
    }

    private static boolean contains(PlayerSnapshot p){
        for(String key:p.values().keySet())
            if(key.startsWith(PREFIX))return true;
        return false;
    }
    private static Observation invalid(){
        return new Observation(State.INVALID_TERMINAL);
    }
    private static SortedMap<String,String> fields(
        String account,String message,String key,String before,String after
    ){
        if(!account.matches("[a-z0-9_-]{1,64}")||
           !RewardDeliveryMessage.normalizeMessageId(message)
               .equals(message)||
           !hex64(key)||!hex64(before)||!hex64(after))
            throw new IllegalArgumentException("invalid G21.64 identity");
        TreeMap<String,String> map=new TreeMap<>();
        map.put("version",VERSION);
        map.put("authority",AUTHORITY);
        map.put("state",TERMINAL);
        map.put("account",account);
        map.put("message",message);
        map.put("key",key);
        map.put("before",before);
        map.put("after",after);
        map.put("checksum",sha256(VERSION+"|"+AUTHORITY+"|"+TERMINAL+
            "|"+account+"|"+message+"|"+key+"|"+before+"|"+after));
        return map;
    }
    private static boolean hex64(String value){
        return value!=null&&value.matches("[0-9a-f]{64}");
    }
    private static String sha256(String input){
        try{
            byte[] digest=MessageDigest.getInstance("SHA-256")
                .digest(input.getBytes(StandardCharsets.UTF_8));
            char[] hex=new char[digest.length*2];
            char[] digits="0123456789abcdef".toCharArray();
            for(int i=0;i<digest.length;i++){
                hex[2*i]=digits[(digest[i]&255)>>>4];
                hex[2*i+1]=digits[digest[i]&15];
            }
            return new String(hex);
        }catch(NoSuchAlgorithmException impossible){
            throw new IllegalStateException("SHA-256 unavailable",impossible);
        }
    }
    private MailboxAtomicTerminalSnapshot(){}
}
