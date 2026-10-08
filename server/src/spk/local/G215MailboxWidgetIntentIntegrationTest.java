package spk.local;

import java.util.Arrays;
import java.util.Collections;

public final class G215MailboxWidgetIntentIntegrationTest {
    public static void main(String[] args)throws Exception{
        boolean bank32178=false;
        boolean inventory32181=false;
        boolean delete32184=false;
        boolean refresh32185=false;
        boolean selectionNormalized=false;
        boolean invalidDepositFailClosed=false;
        boolean claimedDepositFailClosed=false;
        boolean staleSelectionFailClosed=false;
        boolean noSelectionFailClosed=false;
        boolean wrongOpcodeFailClosed=false;
        boolean unknownWidgetPassThrough=false;
        boolean domainUnchanged=false;
        boolean noPacketSideEffect=false;
        boolean noSettlementClaim=false;
        boolean noRootClaim=false;

        MailboxRewardDeliveryService service=
            new MailboxRewardDeliveryService(4);

        MailboxRewardDeliveryService.Snapshot reward=
            service.deliver(
                message(
                    "g215:reward",
                    Collections.singletonList(
                        new RewardDeliveryMessage.Attachment(995,1000)
                    )
                )
            );

        MailboxRewardDeliveryService.Snapshot plain=
            service.deliver(
                message(
                    "g215:plain",
                    Collections.emptyList()
                )
            );

        MailboxWidgetIntentAdapter.Intent bank=
            resolve(
                service,
                "  G215:REWARD  ",
                MailboxWidgetIntentAdapter.DEPOSIT_BANK_WIDGET
            );

        bank32178=
            bank.kind==MailboxWidgetIntentAdapter.Kind.DEPOSIT_TO_BANK&&
            bank.observedClaimState==
                MailboxRewardDeliveryService.ClaimState.UNCLAIMED;

        MailboxWidgetIntentAdapter.Intent inventory=
            resolve(
                service,
                "g215:reward",
                MailboxWidgetIntentAdapter.DEPOSIT_INVENTORY_WIDGET
            );

        inventory32181=
            inventory.kind==
                MailboxWidgetIntentAdapter.Kind.DEPOSIT_TO_INVENTORY&&
            inventory.observedClaimState==
                MailboxRewardDeliveryService.ClaimState.UNCLAIMED;

        selectionNormalized=
            "g215:reward".equals(bank.messageId)&&
            "g215:reward".equals(inventory.messageId);

        MailboxWidgetIntentAdapter.Intent delete=
            resolve(
                service,
                "g215:plain",
                MailboxWidgetIntentAdapter.DELETE_MESSAGE_WIDGET
            );

        delete32184=
            delete.kind==MailboxWidgetIntentAdapter.Kind.DELETE_MESSAGE&&
            "g215:plain".equals(delete.messageId)&&
            delete.observedClaimState==
                MailboxRewardDeliveryService.ClaimState.EMPTY;

        MailboxWidgetIntentAdapter.Intent refresh=
            resolve(
                service,
                null,
                MailboxWidgetIntentAdapter.REFRESH_INBOX_WIDGET
            );

        refresh32185=
            refresh.kind==MailboxWidgetIntentAdapter.Kind.REFRESH_INBOX&&
            refresh.messageId==null&&
            refresh.observedClaimState==null;

        invalidDepositFailClosed=
            rejects(
                service,
                "g215:plain",
                MailboxWidgetIntentAdapter.DEPOSIT_BANK_WIDGET
            )&&
            rejects(
                service,
                "g215:plain",
                MailboxWidgetIntentAdapter.DEPOSIT_INVENTORY_WIDGET
            );

        staleSelectionFailClosed=
            rejects(
                service,
                "g215:missing",
                MailboxWidgetIntentAdapter.DELETE_MESSAGE_WIDGET
            )&&
            rejects(
                service,
                "g215:missing",
                MailboxWidgetIntentAdapter.DEPOSIT_BANK_WIDGET
            );

        noSelectionFailClosed=
            rejects(
                service,
                null,
                MailboxWidgetIntentAdapter.DELETE_MESSAGE_WIDGET
            )&&
            rejects(
                service,
                null,
                MailboxWidgetIntentAdapter.DEPOSIT_BANK_WIDGET
            );

        boolean badOpcodeRejected=false;
        try{
            MailboxWidgetIntentAdapter.resolve(
                new WidgetActionClientRequest(
                    32184,
                    ClientRequestMetadata.exactCurrent(
                        184,
                        "widget",
                        "G215_WRONG_OPCODE"
                    )
                ),
                service,
                "g215:plain"
            );
        }catch(IllegalArgumentException expected){
            badOpcodeRejected=true;
        }
        wrongOpcodeFailClosed=badOpcodeRejected;

        unknownWidgetPassThrough=
            MailboxWidgetIntentAdapter.resolve(
                widget(32023),
                service,
                "g215:plain"
            )==null&&
            MailboxWidgetIntentAdapter.resolve(
                widget(32166),
                service,
                null
            )==null;

        domainUnchanged=
            service.size()==2&&
            service.unreadCount()==2&&
            service.get("g215:reward").claimState==
                MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
            service.get("g215:plain").claimState==
                MailboxRewardDeliveryService.ClaimState.EMPTY&&
            service.get("g215:reward").message.attachments
                .get(0).amount==1000;

        service.acknowledgeAttachmentSettlement(
            "g215:reward"
        );

        claimedDepositFailClosed=
            rejects(
                service,
                "g215:reward",
                MailboxWidgetIntentAdapter.DEPOSIT_BANK_WIDGET
            )&&
            rejects(
                service,
                "g215:reward",
                MailboxWidgetIntentAdapter.DEPOSIT_INVENTORY_WIDGET
            );

        noSettlementClaim=
            bank.messageId.equals("g215:reward")&&
            service.get("g215:reward").message.attachments.size()==1;

        noPacketSideEffect=
            Arrays.stream(
                MailboxWidgetIntentAdapter.class.getDeclaredMethods()
            ).noneMatch(m->
                Arrays.stream(
                    m.getParameterTypes()
                ).anyMatch(t->
                    t==ServerPacketWriter.class
                )
            );

        noRootClaim=
            MailboxWidgetIntentAdapter.class
                .getDeclaredFields().length==5;

        require(
            bank32178&&
            inventory32181&&
            delete32184&&
            refresh32185&&
            selectionNormalized&&
            invalidDepositFailClosed&&
            claimedDepositFailClosed&&
            staleSelectionFailClosed&&
            noSelectionFailClosed&&
            wrongOpcodeFailClosed&&
            unknownWidgetPassThrough&&
            domainUnchanged&&
            noPacketSideEffect&&
            noSettlementClaim&&
            noRootClaim,
            "G21.5 acceptance"
        );

        System.out.println(
            "G215_MAILBOX_WIDGET_INTENTS_PASS"+
            " bank32178="+bank32178+
            " inventory32181="+inventory32181+
            " delete32184="+delete32184+
            " refresh32185="+refresh32185+
            " selectionNormalized="+selectionNormalized+
            " invalidDepositFailClosed="+invalidDepositFailClosed+
            " claimedDepositFailClosed="+claimedDepositFailClosed+
            " staleSelectionFailClosed="+staleSelectionFailClosed+
            " noSelectionFailClosed="+noSelectionFailClosed+
            " wrongOpcodeFailClosed="+wrongOpcodeFailClosed+
            " unknownWidgetPassThrough="+unknownWidgetPassThrough+
            " domainUnchanged="+domainUnchanged+
            " noPacketSideEffect="+noPacketSideEffect+
            " noSettlementClaim="+noSettlementClaim+
            " noRootClaim="+noRootClaim+
            " liveRouterClaim=false"+
            " persistenceClaim=false"
        );
    }

    private static RewardDeliveryMessage message(
        String id,
        java.util.List<RewardDeliveryMessage.Attachment> items
    ){
        return new RewardDeliveryMessage(
            id,
            "LocalLab Mailbox G21.5 widget test",
            "Pure typed-intent fixture",
            items,
            "CUSTOM_LOCALLAB_G215_FIXTURE"
        );
    }

    private static WidgetActionClientRequest widget(
        int id
    ){
        return new WidgetActionClientRequest(
            id,
            ClientRequestMetadata.exactCurrent(
                185,
                "widget-id-u16",
                "G215_EXACT_C2S185_FIXTURE"
            )
        );
    }

    private static MailboxWidgetIntentAdapter.Intent resolve(
        MailboxRewardDeliveryService service,
        String selected,
        int widgetId
    ){
        return MailboxWidgetIntentAdapter.resolve(
            widget(widgetId),
            service,
            selected
        );
    }

    private static boolean rejects(
        MailboxRewardDeliveryService service,
        String selected,
        int widgetId
    ){
        try{
            resolve(service,selected,widgetId);
            return false;
        }catch(IllegalStateException expected){
            return true;
        }
    }

    private static void require(
        boolean valid,
        String detail
    ){
        if(!valid)
            throw new AssertionError(detail);
    }

    private G215MailboxWidgetIntentIntegrationTest(){}
}
