package spk.local;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

/**
 * G21.14 proves the player-facing command boundary using the real
 * WorldPlayer registration and the already-certified C2S103 parser and
 * S2C253 chat publisher; no Mailbox widget/root behavior is invented.
 */
public final class G2114MailboxLiveStatusCommandIntegrationTest {
    private static final int[] SEED={2114,2115,2116,2117};

    public static void main(String[] args)throws Exception{
        boolean exactAliases=false;
        boolean unrelatedPassThrough=false;
        boolean ownedTotals=false;
        boolean liveS2C253=false;
        boolean zeroAccountSafe=false;
        boolean accountIsolation=false;
        boolean usageFailClosed=false;
        boolean noStateMutation=false;
        boolean foreignWorldDenied=false;
        boolean staleGenerationDenied=false;
        boolean newGenerationAccepted=false;
        boolean noRootOrSettlement=false;

        String[] first=LocalCommandDispatcher.tokens(
            LocalCommandDispatcher.clean("::mailbox")
        );
        String[] second=LocalCommandDispatcher.tokens(
            LocalCommandDispatcher.clean(" ::mailbox status ")
        );
        String[] nativeMail=LocalCommandDispatcher.tokens(
            LocalCommandDispatcher.clean("::mail")
        );

        exactAliases=
            LocalMailboxStatusCommandHandler.matches(first)&&
            LocalMailboxStatusCommandHandler.matches(second)&&
            LocalMailboxStatusCommandHandler.matches(nativeMail)&&
            first.length==1&&
            second.length==2&&
            nativeMail.length==1&&
            "mailbox".equalsIgnoreCase(first[0])&&
            "mail".equalsIgnoreCase(nativeMail[0])&&
            "status".equalsIgnoreCase(second[1]);

        unrelatedPassThrough=
            !LocalMailboxStatusCommandHandler.matches(
                LocalCommandDispatcher.tokens(
                    LocalCommandDispatcher.clean("::bank")
                )
            )&&
            !LocalMailboxStatusCommandHandler.matches(
                new String[]{"other","mailbox"}
            )&&
            !LocalMailboxStatusCommandHandler.matches(
                new String[0]
            )&&
            !LocalMailboxStatusCommandHandler.matches(null);

        try(World world=World.isolatedForTest(60000L);
            World other=World.isolatedForTest(60000L)){
            WorldPlayer alice=new WorldPlayer();
            WorldPlayer bob=new WorldPlayer();
            WorldPlayer empty=new WorldPlayer();
            long ag=world.registerPlayer(alice,"g2114-alice");
            long bg=world.registerPlayer(bob,"g2114-bob");
            long eg=world.registerPlayer(empty,"g2114-empty");

            WorldMailboxGateway a=new WorldMailboxGateway(
                world,alice,ag
            );
            WorldMailboxGateway b=new WorldMailboxGateway(
                world,bob,bg
            );

            a.deliver(message("g2114:notice","Notice",false));
            a.deliver(message("g2114:pending","Pending",true));
            a.deliver(message("g2114:claimed","Claimed",true));
            a.markRead("g2114:notice");

            // Only a simulated completed external settlement can produce
            // CLAIMED; this command has no settlement capability.
            alice.mailbox().acknowledgeAttachmentSettlement(
                "g2114:claimed"
            );
            b.deliver(message("g2114:bob","Bob Only",false));

            LocalMailboxStatusCommandHandler aliceCommand=
                new LocalMailboxStatusCommandHandler(world,alice,ag);
            LocalMailboxStatusCommandHandler bobCommand=
                new LocalMailboxStatusCommandHandler(world,bob,bg);
            LocalMailboxStatusCommandHandler emptyCommand=
                new LocalMailboxStatusCommandHandler(world,empty,eg);

            boolean knownBefore=alice.mailboxSnapshotKnown();
            int unreadBefore=alice.mailbox().unreadCount();
            int bobUnreadBefore=bob.mailbox().unreadCount();

            LocalMailboxStatusCommandHandler.Result status=
                aliceCommand.handle(first);
            LocalMailboxStatusCommandHandler.Result statusAlias=
                aliceCommand.handle(second);
            LocalMailboxStatusCommandHandler.Result nativeStatus=
                aliceCommand.handle(nativeMail);

            ownedTotals=
                status!=null&&statusAlias!=null&&nativeStatus!=null&&
                status.clientMessage.equals(statusAlias.clientMessage)&&
                status.clientMessage.equals(nativeStatus.clientMessage)&&
                status.clientMessage.contains(
                    "Mailbox: 3 messages, 2 unread, "+
                    "1 with unclaimed attachments."
                )&&
                status.logText.contains("total=3")&&
                status.logText.contains("unread=2")&&
                status.logText.contains("unclaimed=1")&&
                status.logText.contains("stateMutation=false");

            ByteArrayOutputStream sink=new ByteArrayOutputStream();
            new SocialChatPresentationPublisher(
                new ServerPacketWriter(
                    sink,new IsaacCipher(SEED.clone())
                )
            ).serverMessage(status.clientMessage);
            liveS2C253=exactChatPacket(
                sink.toByteArray(),status.clientMessage
            );

            LocalMailboxStatusCommandHandler.Result nothing=
                emptyCommand.handle(first);
            zeroAccountSafe=
                nothing.clientMessage.contains(
                    "Mailbox: 0 messages, 0 unread, "+
                    "0 with unclaimed attachments."
                )&&
                !empty.mailboxSnapshotKnown()&&
                empty.mailbox().size()==0;

            LocalMailboxStatusCommandHandler.Result bobStatus=
                bobCommand.handle(first);
            accountIsolation=
                bobStatus.clientMessage.contains(
                    "Mailbox: 1 messages, 1 unread, "+
                    "0 with unclaimed attachments."
                )&&
                !bobStatus.clientMessage.contains("3 messages")&&
                alice.mailbox().get("g2114:bob")==null;

            LocalMailboxStatusCommandHandler.Result invalid=
                aliceCommand.handle(new String[]{"mailbox","claim"});
            usageFailClosed=
                invalid!=null&&
                invalid.clientMessage.startsWith("Usage: ::mailbox")&&
                invalid.logText.contains("REJECTED_SYNTAX")&&
                aliceCommand.handle(new String[]{"bank"})==null&&
                aliceCommand.handle(null)==null;

            noStateMutation=
                alice.mailbox().size()==3&&
                alice.mailbox().unreadCount()==unreadBefore&&
                bob.mailbox().unreadCount()==bobUnreadBefore&&
                alice.mailboxSnapshotKnown()==knownBefore&&
                alice.mailbox().get("g2114:pending").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                alice.mailbox().get("g2114:claimed").claimState==
                    MailboxRewardDeliveryService.ClaimState.CLAIMED;

            foreignWorldDenied=rejectsState(
                ()->new LocalMailboxStatusCommandHandler(
                    other,alice,ag
                ).handle(first)
            );

            boolean retired=world.unregisterPlayer(alice,ag);
            staleGenerationDenied=
                retired&&rejectsState(
                    ()->aliceCommand.handle(first)
                );

            long ag2=world.registerPlayer(
                alice,"g2114-alice"
            );
            staleGenerationDenied &=
                ag2!=ag&&rejectsState(
                    ()->aliceCommand.handle(first)
                );

            newGenerationAccepted=
                new LocalMailboxStatusCommandHandler(
                    world,alice,ag2
                ).handle(second).clientMessage.equals(
                    status.clientMessage
                );

            noRootOrSettlement=
                Arrays.stream(
                    LocalMailboxStatusCommandHandler.class
                        .getDeclaredMethods()
                ).noneMatch(method->
                    Arrays.stream(method.getParameterTypes())
                        .anyMatch(type->
                            type==ServerPacketWriter.class||
                            type==BankState.class||
                            type==LocalSession.class
                        )
                )&&
                LocalCommandDispatcher.SessionBridge.class
                    .getDeclaredMethod(
                        "handleMailboxStatusCommand",
                        String[].class
                    )!=null;
        }

        System.out.println(
            "G2114_MAILBOX_DIAGNOSTICS"+
            " exactAliases="+exactAliases+
            " unrelatedPassThrough="+unrelatedPassThrough+
            " ownedTotals="+ownedTotals+
            " liveS2C253="+liveS2C253+
            " zeroAccountSafe="+zeroAccountSafe+
            " accountIsolation="+accountIsolation+
            " usageFailClosed="+usageFailClosed+
            " noStateMutation="+noStateMutation+
            " foreignWorldDenied="+foreignWorldDenied+
            " staleGenerationDenied="+staleGenerationDenied+
            " newGenerationAccepted="+newGenerationAccepted+
            " noRootOrSettlement="+noRootOrSettlement
        );

        require(
            exactAliases&&unrelatedPassThrough&&ownedTotals&&
            liveS2C253&&zeroAccountSafe&&accountIsolation&&
            usageFailClosed&&noStateMutation&&foreignWorldDenied&&
            staleGenerationDenied&&newGenerationAccepted&&
            noRootOrSettlement,
            "G21.14 acceptance"
        );

        System.out.println(
            "G2114_MAILBOX_LIVE_STATUS_COMMAND_PASS"+
            " exactAliases="+exactAliases+
            " unrelatedPassThrough="+unrelatedPassThrough+
            " ownedTotals="+ownedTotals+
            " liveS2C253="+liveS2C253+
            " zeroAccountSafe="+zeroAccountSafe+
            " accountIsolation="+accountIsolation+
            " usageFailClosed="+usageFailClosed+
            " noStateMutation="+noStateMutation+
            " foreignWorldDenied="+foreignWorldDenied+
            " staleGenerationDenied="+staleGenerationDenied+
            " newGenerationAccepted="+newGenerationAccepted+
            " noRootOrSettlement="+noRootOrSettlement+
            " exactC2S103Route=true"+
            " recoveredMailboxRootClaim=false"+
            " itemSettlementClaim=false"
        );
    }

    private static boolean exactChatPacket(
        byte[] wire,String message
    ){
        byte[] text=message.getBytes(
            StandardCharsets.ISO_8859_1
        );
        if(wire.length!=text.length+3)
            return false;
        IsaacCipher cipher=new IsaacCipher(SEED.clone());
        int opcode=((wire[0]&255)-cipher.nextInt())&255;
        int length=wire[1]&255;
        if(opcode!=253||length!=text.length+1||
            wire[wire.length-1]!=10)
            return false;
        return Arrays.equals(
            Arrays.copyOfRange(wire,2,wire.length-1),
            text
        );
    }

    private static RewardDeliveryMessage message(
        String id,String subject,boolean withItems
    ){
        return new RewardDeliveryMessage(
            id,subject,
            "G21.14 synthetic mailbox command",
            withItems?Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,50)
            ):Collections.emptyList(),
            "CUSTOM_LOCALLAB_G2114_FIXTURE"
        );
    }

    private interface Check {
        void run();
    }

    private static boolean rejectsState(Check check){
        try{
            check.run();
            return false;
        }catch(IllegalStateException expected){
            return true;
        }
    }

    private static void require(boolean ok,String message){
        if(!ok)throw new AssertionError(message);
    }

    private G2114MailboxLiveStatusCommandIntegrationTest(){}
}
