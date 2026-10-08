package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collections;

/**
 * G21.12: exact-C2S185 widget32184 safe deletion within World ownership.
 * Synthetic typed request only; no LocalSession or original root claim.
 */
public final class G2112MailboxSafeDeleteWidgetIntegrationTest {
    public static void main(String[] args)throws Exception{
        boolean emptyEnvelopeDeleted=false;
        boolean unclaimedRetained=false;
        boolean externallyClaimedDeleted=false;
        boolean wrongOpcodeNoMutation=false;
        boolean wrongWidgetNoMutation=false;
        boolean staleReusedIdRejected=false;
        boolean selectionRetiredAfterDelete=false;
        boolean foreignAccountIsolated=false;
        boolean emptyTombstoneRoundTrip=false;
        boolean staleGenerationRejected=false;
        boolean closedScopeRejected=false;
        boolean noSettlementOrSocket=false;

        try(World world=World.isolatedForTest(60000L)){
            WorldPlayer alice=new WorldPlayer();
            WorldPlayer bob=new WorldPlayer();
            long ag=world.registerPlayer(alice,"g2112-alice");
            long bg=world.registerPlayer(bob,"g2112-bob");
            WorldMailboxGateway a=new WorldMailboxGateway(
                world,alice,ag
            );
            WorldMailboxGateway b=new WorldMailboxGateway(
                world,bob,bg
            );

            a.deliver(message("g2112:empty","Empty",false));
            a.deliver(message("g2112:pending","Pending",true));
            a.deliver(message("g2112:claimed","Claimed",true));
            // Fixture models an already completed EXTERNAL settlement only.
            alice.mailbox().acknowledgeAttachmentSettlement(
                "g2112:claimed"
            );
            b.deliver(message("g2112:bob","Bob's mail",false));

            WorldMailboxPresentationSession session=
                a.openRootlessPresentation();
            session.publishInbox(writer());
            session.publishTrustedRowDetail(0,writer());

            int initial=alice.mailbox().size();
            wrongOpcodeNoMutation=
                rejects(()->session.deleteSafeFromWidget(
                    widget(184,32184)
                ))&&alice.mailbox().size()==initial;
            wrongWidgetNoMutation=
                rejects(()->session.deleteSafeFromWidget(
                    widget(185,32185)
                ))&&alice.mailbox().size()==initial;

            MailboxRewardDeliveryService.Snapshot removed=
                session.deleteSafeFromWidget(widget(185,32184));

            emptyEnvelopeDeleted=
                removed!=null&&
                removed.claimState==
                    MailboxRewardDeliveryService.ClaimState.EMPTY&&
                "g2112:empty".equals(removed.message.messageId)&&
                alice.mailbox().get("g2112:empty")==null&&
                alice.mailbox().size()==2;

            selectionRetiredAfterDelete=
                rejects(()->session.deleteSafeFromWidget(
                    widget(185,32184)
                ));

            session.publishInbox(writer());
            session.publishTrustedRowDetail(0,writer());
            int pendingCount=alice.mailbox().size();
            unclaimedRetained=
                rejects(()->session.deleteSafeFromWidget(
                    widget(185,32184)
                ))&&
                alice.mailbox().size()==pendingCount&&
                alice.mailbox().get("g2112:pending").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;

            session.publishInbox(writer());
            session.publishTrustedRowDetail(1,writer());
            MailboxRewardDeliveryService.Snapshot cleared=
                session.deleteSafeFromWidget(widget(185,32184));

            externallyClaimedDeleted=
                cleared!=null&&
                "g2112:claimed".equals(cleared.message.messageId)&&
                cleared.claimState==
                    MailboxRewardDeliveryService.ClaimState.CLAIMED&&
                alice.mailbox().get("g2112:claimed")==null&&
                alice.mailbox().size()==1;

            a.deliver(message("g2112:reused","Original",false));
            WorldMailboxPresentationSession stale=
                a.openRootlessPresentation();
            stale.publishInbox(writer());
            stale.publishTrustedRowDetail(1,writer());
            a.deleteSafe("g2112:reused");
            a.deliver(message("g2112:reused","Replacement",false));

            staleReusedIdRejected=
                rejects(()->stale.deleteSafeFromWidget(
                    widget(185,32184)
                ))&&
                alice.mailbox().get("g2112:reused")!=null&&
                "Replacement".equals(
                    alice.mailbox().get("g2112:reused")
                        .message.subject
                );
            stale.close();

            foreignAccountIsolated=
                bob.mailbox().size()==1&&
                bob.mailbox().get("g2112:bob")!=null&&
                alice.mailbox().get("g2112:bob")==null;

            WorldPlayer clean=new WorldPlayer();
            long cg=world.registerPlayer(
                clean,"g2112-empty-tombstone"
            );
            WorldMailboxGateway c=new WorldMailboxGateway(
                world,clean,cg
            );
            c.deliver(message("g2112:last","Last",false));
            WorldMailboxPresentationSession blank=
                c.openRootlessPresentation();
            blank.publishInbox(writer());
            blank.publishTrustedRowDetail(0,writer());
            blank.deleteSafeFromWidget(widget(185,32184));
            PlayerSnapshot snapshot=PlayerSnapshotCodec.capture(
                "g2112-empty-tombstone",clean
            );
            WorldPlayer restored=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(snapshot,restored);
            emptyTombstoneRoundTrip=
                clean.mailbox().size()==0&&
                restored.mailbox().size()==0&&
                !clean.snapshotExtensions().namespace(
                    LocalLabMailboxPersistence.NAMESPACE
                ).isEmpty()==false;
            // Capture owns the persisted zero-count tombstone; the
            // hydrated Mailbox must not replay deleted messages.
            emptyTombstoneRoundTrip &=
                LocalLabMailboxPersistence.decode(
                    restored.snapshotExtensions().namespace(
                        LocalLabMailboxPersistence.NAMESPACE
                    )
                ).isEmpty();
            blank.close();

            boolean retired=world.unregisterPlayer(alice,ag);
            staleGenerationRejected=
                retired&&
                rejects(()->session.deleteSafeFromWidget(
                    widget(185,32184)
                ));
            long ag2=world.registerPlayer(
                alice,"g2112-alice"
            );
            staleGenerationRejected &=
                ag2!=ag&&
                rejects(()->session.deleteSafeFromWidget(
                    widget(185,32184)
                ));

            session.close();
            closedScopeRejected=
                rejects(()->session.deleteSafeFromWidget(
                    widget(185,32184)
                ));

            noSettlementOrSocket=
                Arrays.stream(
                    WorldMailboxPresentationSession.class
                        .getDeclaredMethods()
                ).noneMatch(method->
                    method.getName().toLowerCase().contains("settle")||
                    Arrays.stream(method.getParameterTypes())
                        .anyMatch(type->
                            type==BankState.class||
                            type==LocalSession.class
                        )
                )&&
                alice.mailbox().get("g2112:pending").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
        }

        require(
            emptyEnvelopeDeleted&&unclaimedRetained&&
            externallyClaimedDeleted&&wrongOpcodeNoMutation&&
            wrongWidgetNoMutation&&staleReusedIdRejected&&
            selectionRetiredAfterDelete&&foreignAccountIsolated&&
            emptyTombstoneRoundTrip&&staleGenerationRejected&&
            closedScopeRejected&&noSettlementOrSocket,
            "G21.12 acceptance"
        );

        System.out.println(
            "G2112_MAILBOX_SAFE_DELETE_WIDGET_PASS"+
            " emptyEnvelopeDeleted="+emptyEnvelopeDeleted+
            " unclaimedRetained="+unclaimedRetained+
            " externallyClaimedDeleted="+externallyClaimedDeleted+
            " wrongOpcodeNoMutation="+wrongOpcodeNoMutation+
            " wrongWidgetNoMutation="+wrongWidgetNoMutation+
            " staleReusedIdRejected="+staleReusedIdRejected+
            " selectionRetiredAfterDelete="+selectionRetiredAfterDelete+
            " foreignAccountIsolated="+foreignAccountIsolated+
            " emptyTombstoneRoundTrip="+emptyTombstoneRoundTrip+
            " staleGenerationRejected="+staleGenerationRejected+
            " closedScopeRejected="+closedScopeRejected+
            " noSettlementOrSocket="+noSettlementOrSocket+
            " originalDeletionPolicyClaim=false"+
            " liveSocketAndRootClaim=false"
        );
    }

    private static RewardDeliveryMessage message(
        String id,String subject,boolean attachments
    ){
        return new RewardDeliveryMessage(
            id,subject,
            "G21.12 safe delete fixture",
            attachments
                ?Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,1)
                ):Collections.emptyList(),
            "CUSTOM_LOCALLAB_G2112_FIXTURE"
        );
    }

    private static WidgetActionClientRequest widget(
        int opcode,int widgetId
    ){
        return new WidgetActionClientRequest(
            widgetId,
            ClientRequestMetadata.exactCurrent(
                opcode,"widget-id-u16",
                "G2112_WIDGET_FIXTURE"
            )
        );
    }

    private static ServerPacketWriter writer(){
        return new ServerPacketWriter(
            new ByteArrayOutputStream(),
            new IsaacCipher(new int[]{2112,2113,2114,2115})
        );
    }

    private interface Operation {
        Object run()throws Exception;
    }

    private static boolean rejects(Operation operation){
        try{
            operation.run();
            return false;
        }catch(IllegalArgumentException|
                IllegalStateException expected){
            return true;
        }catch(Exception unexpected){
            throw new IllegalStateException(
                "unexpected checked error",unexpected
            );
        }
    }

    private static void require(boolean yes,String message){
        if(!yes)throw new AssertionError(message);
    }

    private G2112MailboxSafeDeleteWidgetIntegrationTest(){}
}
