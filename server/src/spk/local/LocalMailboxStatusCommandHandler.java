package spk.local;

import java.util.List;
import java.util.Objects;

/**
 * CUSTOM_LOCALLAB player-facing Mailbox counts over the existing
 * exact-current C2S103 text command pipeline.
 *
 * No Mailbox client root/row-click is recovered, and this command
 * never opens an interface, exposes mail bodies, grants rewards,
 * or mutates any player/domain state.
 */
final class LocalMailboxStatusCommandHandler {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G2114_MAILBOX_STATUS_COMMAND";

    static final class Result {
        final String logText;
        final String clientMessage;

        Result(String logText,String clientMessage){
            this.logText=Objects.requireNonNull(logText,"logText");
            this.clientMessage=
                Objects.requireNonNull(clientMessage,"clientMessage");
        }
    }

    private final World world;
    private final WorldPlayer owner;
    private final long registrationGeneration;

    LocalMailboxStatusCommandHandler(
        World world,WorldPlayer owner,long registrationGeneration
    ){
        this.world=Objects.requireNonNull(world,"world");
        this.owner=Objects.requireNonNull(owner,"owner");
        this.registrationGeneration=registrationGeneration;
    }

    static boolean matches(String[] tokens){
        return tokens!=null&&tokens.length>0&&
            "mailbox".equalsIgnoreCase(tokens[0]);
    }

    Result handle(String[] tokens){
        if(!matches(tokens))
            return null;

        // Even unsupported subcommands pass the same registration fence.
        // Do not obtain mailbox data through an unregistered player object.
        List<MailboxRewardDeliveryService.Snapshot> rows=
            new WorldMailboxGateway(
                world,owner,registrationGeneration
            ).snapshot();

        if(tokens.length>2||
           (tokens.length==2&&
            !"status".equalsIgnoreCase(tokens[1])))
            return new Result(
                "G2114_MAILBOX_COMMAND result=REJECTED_SYNTAX"+
                " authority="+AUTHORITY+
                " stateMutation=false liveRoot=false",
                "Usage: ::mailbox or ::mailbox status. "+
                "The Mailbox interface is not available yet."
            );

        int unread=0;
        int unclaimed=0;

        for(MailboxRewardDeliveryService.Snapshot row:rows){
            MailboxRewardDeliveryService.Snapshot checked=
                Objects.requireNonNull(row,"Mailbox row");
            if(checked.readState==
                    MailboxRewardDeliveryService.ReadState.UNREAD)
                unread++;
            if(checked.claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED)
                unclaimed++;
        }

        return new Result(
            "G2114_MAILBOX_COMMAND result=STATUS"+
            " total="+rows.size()+
            " unread="+unread+
            " unclaimed="+unclaimed+
            " worldOwned=true"+
            " stateMutation=false"+
            " liveRoot=false"+
            " authority="+AUTHORITY,
            "Mailbox: "+rows.size()+" messages, "+unread+
            " unread, "+unclaimed+" with unclaimed attachments. "+
            "The Mailbox interface is not available yet."
        );
    }
}
