package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;

/**
 * G21.13 — World-owned trusted selected Mailbox READ and exact S2C250/31/2.
 * The selected row comes from server state, never a guessed client click.
 */
public final class G2113MailboxTrustedReadIntegrationTest {
    private static final int[] SEED={2113,2114,2115,2116};

    public static void main(String[] args)throws Exception{
        boolean firstReadWire=false;
        boolean replayIdempotent=false;
        boolean persistedRead=false;
        boolean unclaimedAttachmentsUntouched=false;
        boolean foreignAccountIsolated=false;
        boolean noSelectionRejected=false;
        boolean badRowRejected=false;
        boolean recycledIdentityRejected=false;
        boolean transportFailurePersists=false;
        boolean generationTurnoverRejected=false;
        boolean closedScopeRejected=false;
        boolean noSettlementOrLiveRoute=false;

        try(World world=World.isolatedForTest(60000L)){
            WorldPlayer alice=new WorldPlayer();
            WorldPlayer bob=new WorldPlayer();
            long ag=world.registerPlayer(alice,"g2113-alice");
            long bg=world.registerPlayer(bob,"g2113-bob");
            WorldMailboxGateway a=new WorldMailboxGateway(
                world,alice,ag
            );
            WorldMailboxGateway b=new WorldMailboxGateway(
                world,bob,bg
            );

            a.deliver(message("g2113:reward","Reward",true));
            a.deliver(message("g2113:notice","Notice",false));
            b.deliver(message("g2113:bob","Private Bob",false));

            WorldMailboxPresentationSession session=
                a.openRootlessPresentation();
            session.publishInbox(writer(new ByteArrayOutputStream()));

            ByteArrayOutputStream absent=new ByteArrayOutputStream();
            noSelectionRejected=
                rejects(()->session.publishSelectedReadState(
                    writer(absent)
                ))&&
                absent.size()==0&&
                alice.mailbox().unreadCount()==2;

            ByteArrayOutputStream badRow=new ByteArrayOutputStream();
            badRowRejected=
                rejects(()->session.publishTrustedRowDetail(
                    2,writer(badRow)
                ))&&badRow.size()==0&&
                alice.mailbox().unreadCount()==2;

            session.publishTrustedRowDetail(
                0,writer(new ByteArrayOutputStream())
            );

            ByteArrayOutputStream first=new ByteArrayOutputStream();
            boolean changed=session.publishSelectedReadState(
                writer(first)
            );
            firstReadWire=
                changed&&
                decodedUpdate(first.toByteArray(),0)&&
                alice.mailbox().get("g2113:reward").readState==
                    MailboxRewardDeliveryService.ReadState.READ&&
                alice.mailbox().unreadCount()==1&&
                alice.mailboxSnapshotKnown();

            ByteArrayOutputStream repeated=new ByteArrayOutputStream();
            boolean again=session.publishSelectedReadState(
                writer(repeated)
            );
            replayIdempotent=
                !again&&decodedUpdate(repeated.toByteArray(),0)&&
                alice.mailbox().unreadCount()==1;

            unclaimedAttachmentsUntouched=
                alice.mailbox().get("g2113:reward").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                alice.mailbox().get("g2113:reward")
                    .message.attachments.get(0).itemId==995&&
                alice.mailbox().get("g2113:reward")
                    .message.attachments.get(0).amount==5000;

            PlayerSnapshot saved=PlayerSnapshotCodec.capture(
                "g2113-alice",alice
            );
            WorldPlayer restored=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(saved,restored);
            persistedRead=
                restored.mailboxSnapshotKnown()&&
                restored.mailbox().unreadCount()==1&&
                restored.mailbox().get("g2113:reward").readState==
                    MailboxRewardDeliveryService.ReadState.READ&&
                restored.mailbox().get("g2113:notice").readState==
                    MailboxRewardDeliveryService.ReadState.UNREAD;

            foreignAccountIsolated=
                bob.mailbox().size()==1&&
                bob.mailbox().unreadCount()==1&&
                bob.mailbox().get("g2113:reward")==null;

            WorldMailboxPresentationSession stale=
                a.openRootlessPresentation();
            stale.publishInbox(writer(new ByteArrayOutputStream()));
            stale.publishTrustedRowDetail(
                1,writer(new ByteArrayOutputStream())
            );
            a.deleteSafe("g2113:notice");
            a.deliver(message(
                "g2113:notice","Replacement",false
            ));
            ByteArrayOutputStream reused=new ByteArrayOutputStream();
            recycledIdentityRejected=
                rejects(()->stale.publishSelectedReadState(
                    writer(reused)
                ))&&reused.size()==0&&
                alice.mailbox().get("g2113:notice").readState==
                    MailboxRewardDeliveryService.ReadState.UNREAD&&
                "Replacement".equals(
                    alice.mailbox().get("g2113:notice")
                        .message.subject
                );
            stale.close();

            // A throwing transport proves that semantic READ is not
            // implicitly rolled back by failed packet publication.
            WorldMailboxPresentationSession failing=
                a.openRootlessPresentation();
            failing.publishInbox(writer(new ByteArrayOutputStream()));
            failing.publishTrustedRowDetail(
                1,writer(new ByteArrayOutputStream())
            );
            boolean ioRejected=false;
            try{
                failing.publishSelectedReadState(
                    writer(new OutputStream(){
                        @Override public void write(int value)
                            throws IOException{
                            throw new IOException(
                                "G21.13 simulated transport failure"
                            );
                        }
                    })
                );
            }catch(IOException expected){
                ioRejected=true;
            }
            transportFailurePersists=
                ioRejected&&
                alice.mailbox().get("g2113:notice").readState==
                    MailboxRewardDeliveryService.ReadState.READ&&
                "2".equals(PlayerSnapshotCodec.capture(
                    "g2113-alice",alice
                ).value("extension.mailbox-g21.count"));
            failing.close();

            boolean retired=world.unregisterPlayer(alice,ag);
            ByteArrayOutputStream expired=new ByteArrayOutputStream();
            generationTurnoverRejected=
                retired&&
                rejects(()->session.publishSelectedReadState(
                    writer(expired)
                ))&&expired.size()==0;
            long ag2=world.registerPlayer(
                alice,"g2113-alice"
            );
            generationTurnoverRejected &=
                ag2!=ag&&
                rejects(()->session.publishSelectedReadState(
                    writer(expired)
                ))&&expired.size()==0;

            WorldMailboxPresentationSession closed=
                b.openRootlessPresentation();
            closed.publishInbox(writer(new ByteArrayOutputStream()));
            closed.publishTrustedRowDetail(
                0,writer(new ByteArrayOutputStream())
            );
            closed.close();
            ByteArrayOutputStream afterClose=new ByteArrayOutputStream();
            closedScopeRejected=
                rejects(()->closed.publishSelectedReadState(
                    writer(afterClose)
                ))&&afterClose.size()==0&&
                bob.mailbox().unreadCount()==1;

            noSettlementOrLiveRoute=
                Arrays.stream(
                    WorldMailboxPresentationSession.class
                        .getDeclaredMethods()
                ).noneMatch(method->
                    method.getName().toLowerCase()
                        .contains("settle")||
                    Arrays.stream(method.getParameterTypes())
                        .anyMatch(type->
                            type==BankState.class||
                            type==LocalSession.class
                        )
                )&&
                alice.mailbox().get("g2113:reward").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;

            session.close();
        }

        require(
            firstReadWire&&replayIdempotent&&persistedRead&&
            unclaimedAttachmentsUntouched&&foreignAccountIsolated&&
            noSelectionRejected&&badRowRejected&&
            recycledIdentityRejected&&transportFailurePersists&&
            generationTurnoverRejected&&closedScopeRejected&&
            noSettlementOrLiveRoute,
            "G21.13 acceptance"
        );

        System.out.println(
            "G2113_MAILBOX_TRUSTED_READ_PASS"+
            " firstReadWire="+firstReadWire+
            " replayIdempotent="+replayIdempotent+
            " persistedRead="+persistedRead+
            " unclaimedAttachmentsUntouched="+
                unclaimedAttachmentsUntouched+
            " foreignAccountIsolated="+foreignAccountIsolated+
            " noSelectionRejected="+noSelectionRejected+
            " badRowRejected="+badRowRejected+
            " recycledIdentityRejected="+recycledIdentityRejected+
            " transportFailurePersists="+transportFailurePersists+
            " generationTurnoverRejected="+
                generationTurnoverRejected+
            " closedScopeRejected="+closedScopeRejected+
            " noSettlementOrLiveRoute="+noSettlementOrLiveRoute+
            " liveSocketClaim=false"+
            " recoveredClientRowClickClaim=false"+
            " rewardSettlementClaim=false"
        );
    }

    private static boolean decodedUpdate(byte[] wire,int row){
        require(wire.length==7,"subtype31 READ length");
        IsaacCipher decode=new IsaacCipher(SEED.clone());
        int pos=0;
        int opcode=((wire[pos++]&255)-decode.nextInt())&255;
        int length=wire[pos++]&255;
        int subtype=((wire[pos++]&255)<<8)|(wire[pos++]&255);
        return opcode==250&&length==5&&subtype==31&&
            wire[pos++]==2&&(wire[pos++]&255)==row&&
            wire[pos++]==1&&pos==wire.length;
    }

    private static RewardDeliveryMessage message(
        String id,String subject,boolean items
    ){
        return new RewardDeliveryMessage(
            id,subject,"G21.13 synthetic trusted row",
            items?Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,5000)
            ):Collections.emptyList(),
            "CUSTOM_LOCALLAB_G2113_FIXTURE"
        );
    }

    private static ServerPacketWriter writer(OutputStream stream){
        return new ServerPacketWriter(
            stream,new IsaacCipher(SEED.clone())
        );
    }

    private interface Operation{
        Object run()throws Exception;
    }

    private static boolean rejects(Operation call){
        try{
            call.run();
            return false;
        }catch(IllegalArgumentException|
                IllegalStateException expected){
            return true;
        }catch(Exception unexpected){
            throw new IllegalStateException(unexpected);
        }
    }

    private static void require(boolean yes,String message){
        if(!yes)throw new AssertionError(message);
    }

    private G2113MailboxTrustedReadIntegrationTest(){}
}
