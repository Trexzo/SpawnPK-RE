package spk.local;

import java.lang.reflect.Field;
import java.util.*;

public final class MailboxRewardDeliveryFoundationTest {
    public static void main(String[] args){
        assertExactClientCompatibleCapacity();
        assertReadAndClaimLifecycle();
        assertCapacityAndDuplicateGuards();
        assertImmutabilityAndProtocolBoundary();

        System.out.println(
            "MAILBOX_REWARD_DELIVERY_FOUNDATION_PASS "+
            "clientVisibleRows=35 "+
            "capacityCallerPolicy=true "+
            "readState=true "+
            "claimState=true "+
            "externalSettlementAck=true "+
            "duplicateFailClosed=true "+
            "immutableSnapshots=true "+
            "inventoryMutation=false "+
            "protocolIdentityInState=false"
        );
    }

    private static void assertExactClientCompatibleCapacity(){
        MailboxRewardDeliveryService service=
            new MailboxRewardDeliveryService(
                35
            );

        if(service.capacity()!=35)
            throw new AssertionError(
                "capacity="+
                service.capacity()
            );

        for(int i=0;i<35;i++){
            service.deliver(
                message(
                    "reward:"+i,
                    Collections.emptyList()
                )
            );
        }

        if(service.size()!=35||
           service.unreadCount()!=35)
            throw new AssertionError(
                "35-row mailbox size="+
                service.size()+
                " unread="+
                service.unreadCount()
            );

        expectIllegalState(
            ()->service.deliver(
                message(
                    "reward:overflow",
                    Collections.emptyList()
                )
            )
        );
    }

    private static void assertReadAndClaimLifecycle(){
        MailboxRewardDeliveryService service=
            new MailboxRewardDeliveryService(
                4
            );

        RewardDeliveryMessage.Attachment coins=
            new RewardDeliveryMessage.Attachment(
                995,
                5000
            );

        RewardDeliveryMessage.Attachment item=
            new RewardDeliveryMessage.Attachment(
                4151,
                1
            );

        MailboxRewardDeliveryService.Snapshot reward=
            service.deliver(
                message(
                    "event:contest-1",
                    Arrays.asList(
                        coins,
                        item
                    )
                )
            );

        if(reward.readState!=
                MailboxRewardDeliveryService
                    .ReadState.UNREAD||
           reward.claimState!=
                MailboxRewardDeliveryService
                    .ClaimState.UNCLAIMED)
            throw new AssertionError(
                "initial reward="+reward
            );

        if(!service.markRead(
                "EVENT:CONTEST-1"))
            throw new AssertionError(
                "first markRead=false"
            );

        if(service.markRead(
                "event:contest-1"))
            throw new AssertionError(
                "second markRead=true"
            );

        if(service.unreadCount()!=0)
            throw new AssertionError(
                "unreadCount="+
                service.unreadCount()
            );

        if(!service
                .acknowledgeAttachmentSettlement(
                    "event:contest-1"))
            throw new AssertionError(
                "first settlement ack=false"
            );

        if(service
                .acknowledgeAttachmentSettlement(
                    "event:contest-1"))
            throw new AssertionError(
                "second settlement ack=true"
            );

        MailboxRewardDeliveryService.Snapshot claimed=
            service.get(
                "event:contest-1"
            );

        if(claimed.claimState!=
                MailboxRewardDeliveryService
                    .ClaimState.CLAIMED||
           claimed.message.attachments
               .size()!=2)
            throw new AssertionError(
                "claimed="+claimed
            );

        MailboxRewardDeliveryService.Snapshot plain=
            service.deliver(
                message(
                    "notice:no-items",
                    Collections.emptyList()
                )
            );

        if(plain.claimState!=
                MailboxRewardDeliveryService
                    .ClaimState.EMPTY)
            throw new AssertionError(
                "plain claimState="+
                plain.claimState
            );

        expectIllegalState(
            ()->service
                .acknowledgeAttachmentSettlement(
                    "notice:no-items"
                )
        );

        MailboxRewardDeliveryService.Snapshot removed=
            service.delete(
                "event:contest-1"
            );

        if(removed==null||
           service.get(
               "event:contest-1")!=null||
           service.size()!=1)
            throw new AssertionError(
                "delete failed"
            );

        if(service.delete(
                "missing:id")!=null)
            throw new AssertionError(
                "missing delete should return null"
            );
    }

    private static void assertCapacityAndDuplicateGuards(){
        MailboxRewardDeliveryService service=
            new MailboxRewardDeliveryService(
                2
            );

        service.deliver(
            message(
                "a",
                Collections.emptyList()
            )
        );

        expectIllegalState(
            ()->service.deliver(
                message(
                    "A",
                    Collections.emptyList()
                )
            )
        );

        service.deliver(
            message(
                "b",
                Collections.emptyList()
            )
        );

        expectIllegalState(
            ()->service.deliver(
                message(
                    "c",
                    Collections.emptyList()
                )
            )
        );

        expectIllegalArgument(
            ()->service.markRead(
                "missing"
            )
        );

        expectIllegalArgument(
            ()->new RewardDeliveryMessage.Attachment(
                -1,
                1
            )
        );

        expectIllegalArgument(
            ()->new RewardDeliveryMessage.Attachment(
                995,
                0
            )
        );
    }

    private static void assertImmutabilityAndProtocolBoundary(){
        ArrayList<RewardDeliveryMessage.Attachment>
            source=
                new ArrayList<>();

        source.add(
            new RewardDeliveryMessage.Attachment(
                995,
                1
            )
        );

        RewardDeliveryMessage message=
            message(
                "immutable:test",
                source
            );

        source.clear();

        if(message.attachments.size()!=1)
            throw new AssertionError(
                "attachment defensive copy failed"
            );

        boolean attachmentsImmutable=false;

        try{
            message.attachments.clear();
        }catch(UnsupportedOperationException expected){
            attachmentsImmutable=true;
        }

        if(!attachmentsImmutable)
            throw new AssertionError(
                "attachments mutable"
            );

        MailboxRewardDeliveryService service=
            new MailboxRewardDeliveryService(
                2
            );

        service.deliver(message);

        List<MailboxRewardDeliveryService.Snapshot>
            snapshots=
                service.snapshot();

        boolean snapshotsImmutable=false;

        try{
            snapshots.clear();
        }catch(UnsupportedOperationException expected){
            snapshotsImmutable=true;
        }

        if(!snapshotsImmutable)
            throw new AssertionError(
                "snapshot list mutable"
            );

        for(Class<?> type:
                new Class<?>[]{
                    RewardDeliveryMessage.class,
                    MailboxRewardDeliveryService
                        .Snapshot.class
                }){
            for(Field field:
                    type.getDeclaredFields()){
                String name=
                    field.getName()
                        .toLowerCase(
                            Locale.ROOT
                        );

                if(name.contains("widget")||
                   name.contains("packet")||
                   name.contains("opcode")||
                   name.contains("subtype")||
                   name.contains("rowindex"))
                    throw new AssertionError(
                        "protocol identity leaked into "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );
            }
        }
    }

    private static RewardDeliveryMessage message(
        String id,
        List<RewardDeliveryMessage.Attachment>
            attachments
    ){
        return new RewardDeliveryMessage(
            id,
            "LocalLab reward",
            "Synthetic domain foundation message.",
            attachments,
            "CUSTOM_LOCALLAB"
        );
    }

    private static void expectIllegalArgument(
        Runnable action
    ){
        boolean failed=false;

        try{
            action.run();
        }catch(IllegalArgumentException expected){
            failed=true;
        }

        if(!failed)
            throw new AssertionError(
                "expected IllegalArgumentException"
            );
    }

    private static void expectIllegalState(
        Runnable action
    ){
        boolean failed=false;

        try{
            action.run();
        }catch(IllegalStateException expected){
            failed=true;
        }

        if(!failed)
            throw new AssertionError(
                "expected IllegalStateException"
            );
    }
}
