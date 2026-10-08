package spk.local;

import java.util.Collections;
import java.util.List;

public final class G216MailboxSessionSelectionIntegrationTest {
    public static void main(String[] args)throws Exception{
        boolean generationBound=false;
        boolean boundRowSelection=false;
        boolean selectedWidgetIntent=false;
        boolean inboxRebindClearsSelection=false;
        boolean refreshWithoutSelection=false;
        boolean invalidRowFailClosed=false;
        boolean emptyDepositFailClosed=false;
        boolean deletedRedeliveredIdRejected=false;
        boolean overLimitFailClosed=false;
        boolean foreignWorldRejected=false;
        boolean generationTurnoverDenied=false;
        boolean newGenerationAccepted=false;
        boolean closedViewDenied=false;
        boolean mailboxStateUnchanged=false;
        boolean noWireOrSettlement=false;
        boolean noClientRowDecodeClaim=false;

        try(World world=World.isolatedForTest(60000L);
            World foreignWorld=World.isolatedForTest(60000L)){
            WorldPlayer alice=new WorldPlayer();
            WorldPlayer bob=new WorldPlayer();

            long aliceGen=world.registerPlayer(alice,"mailbox-g216-alice");
            long bobGen=foreignWorld.registerPlayer(
                bob,"mailbox-g216-bob"
            );

            MailboxRewardDeliveryService mailbox=
                new MailboxRewardDeliveryService(4);

            mailbox.deliver(
                message(
                    "g216:notice",
                    "Notice",
                    false
                )
            );
            mailbox.deliver(
                message(
                    "g216:reward",
                    "Reward",
                    true
                )
            );

            MailboxSessionSelection selection=
                new MailboxSessionSelection(
                    world,
                    alice,
                    aliceGen,
                    mailbox
                );

            generationBound=alice.accepts(aliceGen);

            List<MailboxRewardDeliveryService.Snapshot> rows=
                selection.bindInbox();

            boundRowSelection=
                rows.size()==2&&
                rows.get(0).message.messageId.equals("g216:notice")&&
                rows.get(1).message.messageId.equals("g216:reward")&&
                selection.selectBoundRow(1).message.messageId
                    .equals("g216:reward")&&
                "g216:reward".equals(selection.selectedMessageId());

            MailboxWidgetIntentAdapter.Intent bank=
                selection.resolve(
                    widget(MailboxWidgetIntentAdapter.DEPOSIT_BANK_WIDGET)
                );

            selectedWidgetIntent=
                bank.kind==
                    MailboxWidgetIntentAdapter.Kind.DEPOSIT_TO_BANK&&
                "g216:reward".equals(bank.messageId);

            invalidRowFailClosed=
                rejects(()->selection.selectBoundRow(-1))&&
                rejects(()->selection.selectBoundRow(2));

            selection.selectBoundRow(0);
            emptyDepositFailClosed=
                rejects(()->selection.resolve(
                    widget(MailboxWidgetIntentAdapter.DEPOSIT_BANK_WIDGET)
                ));

            selection.bindInbox();
            inboxRebindClearsSelection=
                selection.selectedMessageId()==null&&
                rejects(()->selection.resolve(
                    widget(MailboxWidgetIntentAdapter.DELETE_MESSAGE_WIDGET)
                ));

            MailboxWidgetIntentAdapter.Intent refresh=
                selection.resolve(
                    widget(MailboxWidgetIntentAdapter.REFRESH_INBOX_WIDGET)
                );

            refreshWithoutSelection=
                refresh.kind==
                    MailboxWidgetIntentAdapter.Kind.REFRESH_INBOX&&
                refresh.messageId==null;

            selection.selectBoundRow(1);
            mailbox.delete("g216:reward");
            mailbox.deliver(
                message(
                    "g216:reward",
                    "Replacement",
                    true
                )
            );

            deletedRedeliveredIdRejected=
                rejects(selection::selectedMessageId)&&
                rejects(()->selection.resolve(
                    widget(MailboxWidgetIntentAdapter.DELETE_MESSAGE_WIDGET)
                ));

            selection.bindInbox();
            selection.selectBoundRow(1);

            MailboxRewardDeliveryService many=
                new MailboxRewardDeliveryService(36);

            for(int i=0;i<36;i++)
                many.deliver(
                    message("g216:full:"+i,"Subject "+i,false)
                );

            MailboxSessionSelection full=
                new MailboxSessionSelection(
                    world,
                    alice,
                    aliceGen,
                    many
                );

            overLimitFailClosed=
                rejects(full::bindInbox)&&
                rejects(()->full.selectBoundRow(0));

            foreignWorldRejected=
                rejects(()->new MailboxSessionSelection(
                    world,
                    bob,
                    bobGen,
                    mailbox
                ));

            mailboxStateUnchanged=
                mailbox.size()==2&&
                mailbox.unreadCount()==2&&
                mailbox.get("g216:notice").claimState==
                    MailboxRewardDeliveryService.ClaimState.EMPTY&&
                mailbox.get("g216:reward").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                mailbox.get("g216:reward").message.subject.equals("Replacement");

            noWireOrSettlement=
                java.util.Arrays.stream(
                    MailboxSessionSelection.class.getDeclaredMethods()
                ).noneMatch(m->
                    java.util.Arrays.stream(
                        m.getParameterTypes()
                    ).anyMatch(p->
                        p==ServerPacketWriter.class
                    )
                )&&
                mailbox.get("g216:reward").message.attachments.size()==1;

            noClientRowDecodeClaim=
                MailboxSessionSelection.class
                    .getDeclaredFields().length==8;

            boolean unregistered=
                world.unregisterPlayer(alice,aliceGen);

            generationTurnoverDenied=
                unregistered&&
                rejects(selection::selectedMessageId)&&
                rejects(()->selection.resolve(
                    widget(MailboxWidgetIntentAdapter.REFRESH_INBOX_WIDGET)
                ));

            long newGeneration=
                world.registerPlayer(alice,"mailbox-g216-alice");

            generationTurnoverDenied &=
                newGeneration!=aliceGen&&
                rejects(selection::bindInbox)&&
                rejects(full::bindInbox);

            MailboxSessionSelection next=
                new MailboxSessionSelection(
                    world,
                    alice,
                    newGeneration,
                    mailbox
                );

            newGenerationAccepted=
                next.bindInbox().size()==2&&
                next.selectBoundRow(1).message.subject
                    .equals("Replacement");

            next.close();
            closedViewDenied=
                rejects(next::bindInbox)&&
                rejects(next::selectedMessageId)&&
                rejects(()->next.resolve(
                    widget(MailboxWidgetIntentAdapter.REFRESH_INBOX_WIDGET)
                ));

            // Owned views do not change inventory, reward envelopes,
            // read flags, bank state or storage policy.
            mailboxStateUnchanged &=
                mailbox.unreadCount()==2&&
                mailbox.get("g216:reward").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;

            full.close();
            selection.close();
        }

        require(
            generationBound&&
            boundRowSelection&&
            selectedWidgetIntent&&
            inboxRebindClearsSelection&&
            refreshWithoutSelection&&
            invalidRowFailClosed&&
            emptyDepositFailClosed&&
            deletedRedeliveredIdRejected&&
            overLimitFailClosed&&
            foreignWorldRejected&&
            generationTurnoverDenied&&
            newGenerationAccepted&&
            closedViewDenied&&
            mailboxStateUnchanged&&
            noWireOrSettlement&&
            noClientRowDecodeClaim,
            "G21.6 acceptance"
        );

        System.out.println(
            "G216_MAILBOX_SESSION_SELECTION_PASS"+
            " generationBound="+generationBound+
            " boundRowSelection="+boundRowSelection+
            " selectedWidgetIntent="+selectedWidgetIntent+
            " inboxRebindClearsSelection="+inboxRebindClearsSelection+
            " refreshWithoutSelection="+refreshWithoutSelection+
            " invalidRowFailClosed="+invalidRowFailClosed+
            " emptyDepositFailClosed="+emptyDepositFailClosed+
            " deletedRedeliveredIdRejected="+deletedRedeliveredIdRejected+
            " overLimitFailClosed="+overLimitFailClosed+
            " foreignWorldRejected="+foreignWorldRejected+
            " generationTurnoverDenied="+generationTurnoverDenied+
            " newGenerationAccepted="+newGenerationAccepted+
            " closedViewDenied="+closedViewDenied+
            " mailboxStateUnchanged="+mailboxStateUnchanged+
            " noWireOrSettlement="+noWireOrSettlement+
            " noClientRowDecodeClaim="+noClientRowDecodeClaim+
            " liveSessionClaim=false"+
            " mailboxPersistenceClaim=false"+
            " rewardSettlementClaim=false"
        );
    }

    private static RewardDeliveryMessage message(
        String id,
        String subject,
        boolean items
    ){
        return new RewardDeliveryMessage(
            id,
            subject,
            "G21.6 view fixture",
            items
                ?Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,100)
                )
                :Collections.emptyList(),
            "CUSTOM_LOCALLAB_G216_FIXTURE"
        );
    }

    private static WidgetActionClientRequest widget(int id){
        return new WidgetActionClientRequest(
            id,
            ClientRequestMetadata.exactCurrent(
                185,"widget-id-u16",
                "G216_EXACT_CLIENT_C2S185"
            )
        );
    }

    private interface Action{
        Object run();
    }

    private static boolean rejects(Action action){
        try{
            action.run();
            return false;
        }catch(IllegalArgumentException |
                IllegalStateException expected){
            return true;
        }
    }

    private static void require(boolean ok,String detail){
        if(!ok)throw new AssertionError(detail);
    }

    private G216MailboxSessionSelectionIntegrationTest(){}
}
