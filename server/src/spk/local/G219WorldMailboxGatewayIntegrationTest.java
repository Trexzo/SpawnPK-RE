package spk.local;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Hosted proof for the G21.9 account- and generation-owned command gate. */
public final class G219WorldMailboxGatewayIntegrationTest {
    public static void main(String[] args)throws Exception{
        boolean boundToWorldPlayer=false;
        boolean accountIsolation=false;
        boolean strictMessagePreflight=false;
        boolean invalidDeliveryNoMutation=false;
        boolean readIdempotent=false;
        boolean unclaimedDeletionDenied=false;
        boolean claimedDeletionAllowed=false;
        boolean emptyDeletionAllowed=false;
        boolean viewUsesOwnedMailbox=false;
        boolean reusedIdentityRejected=false;
        boolean tombstoneRoundTrip=false;
        boolean foreignWorldRejected=false;
        boolean generationTurnoverRejected=false;
        boolean newGenerationAccepted=false;
        boolean noPacketsOrSettlement=false;
        boolean noC2SRootClaim=false;

        try(World world=World.isolatedForTest(60000L);
            World other=World.isolatedForTest(60000L)){
            WorldPlayer alice=new WorldPlayer();
            WorldPlayer bob=new WorldPlayer();

            long aGen=world.registerPlayer(alice,"g219-alice");
            long bGen=world.registerPlayer(bob,"g219-bob");

            WorldMailboxGateway a=
                new WorldMailboxGateway(world,alice,aGen);
            WorldMailboxGateway b=
                new WorldMailboxGateway(world,bob,bGen);

            boolean mismatchedGenerationDenied=
                rejectsState(()->new WorldMailboxGateway(
                    world,alice,bGen+1000
                ));

            MailboxRewardDeliveryService.Snapshot notice=
                a.deliver(message(
                    "g219:notice",
                    "Notice",
                    "Server-side G21.9 fixture",
                    Collections.emptyList()
                ));

            MailboxRewardDeliveryService.Snapshot reward=
                a.deliver(message(
                    "g219:reward",
                    "Reward",
                    "External settlement must run elsewhere",
                    Collections.singletonList(
                        new RewardDeliveryMessage.Attachment(
                            995,5000
                        )
                    )
                ));

            boundToWorldPlayer=
                a.snapshot().size()==2&&
                notice.message==alice.mailbox()
                    .get("g219:notice").message&&
                reward.message==alice.mailbox()
                    .get("g219:reward").message&&
                alice.mailboxSnapshotKnown();

            accountIsolation=
                b.snapshot().isEmpty()&&
                bob.mailbox().size()==0&&
                !bob.mailboxSnapshotKnown();

            int initialSize=a.snapshot().size();

            strictMessagePreflight=
                rejectsArgument(()->a.deliver(message(
                    "g219:bad-subject",
                    "bad\nsubject",
                    "body",
                    Collections.emptyList()
                )))&&
                rejectsArgument(()->a.deliver(message(
                    "g219:bad-item",
                    "Bad item",
                    "body",
                    Collections.singletonList(
                        new RewardDeliveryMessage.Attachment(
                            65535,1
                        )
                    )
                )))&&
                rejectsArgument(()->a.deliver(message(
                    "g219:bad-qty",
                    "Bad quantity",
                    "body",
                    Collections.singletonList(
                        new RewardDeliveryMessage.Attachment(
                            995,2147483648L
                        )
                    )
                )))&&
                rejectsArgument(()->a.deliver(message(
                    "g219:bad-body",
                    "Bad body",
                    repeat('x',8193),
                    Collections.emptyList()
                )))&&
                rejectsArgument(()->a.deliver(message(
                    "g219:bad-count",
                    "Too many items",
                    "body",
                    manyAttachments(129)
                )));

            invalidDeliveryNoMutation=
                a.snapshot().size()==initialSize&&
                a.snapshot().get(0).message==notice.message&&
                a.snapshot().get(1).message==reward.message&&
                b.snapshot().isEmpty();

            readIdempotent=
                a.markRead("g219:notice")&&
                !a.markRead("g219:notice")&&
                alice.mailbox().unreadCount()==1;

            unclaimedDeletionDenied=
                rejectsState(()->a.deleteSafe("g219:reward"))&&
                a.snapshot().size()==2&&
                alice.mailbox().get("g219:reward").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;

            MailboxSessionSelection view=a.openReadOnlyView();
            List<MailboxRewardDeliveryService.Snapshot> rows=
                view.bindInbox();

            viewUsesOwnedMailbox=
                rows.size()==2&&
                rows.get(0).message==notice.message&&
                rows.get(1).message==reward.message&&
                view.selectBoundRow(0).message==notice.message;

            MailboxRewardDeliveryService.Snapshot deleted=
                a.deleteSafe("g219:notice");

            emptyDeletionAllowed=
                deleted.message==notice.message&&
                a.snapshot().size()==1;

            a.deliver(message(
                "g219:notice",
                "Replacement notice",
                "New immutable envelope",
                Collections.emptyList()
            ));

            reusedIdentityRejected=
                rejectsState(view::selectedMessageId)&&
                rejectsState(()->view.resolve(widget(32184)));

            view.close();

            // Test fixture acknowledges an *external* completed
            // settlement. The gateway itself cannot do that operation.
            alice.mailbox().acknowledgeAttachmentSettlement(
                "g219:reward"
            );
            MailboxRewardDeliveryService.Snapshot removedClaimed=
                a.deleteSafe("g219:reward");

            claimedDeletionAllowed=
                removedClaimed.claimState==
                    MailboxRewardDeliveryService.ClaimState.CLAIMED&&
                a.snapshot().size()==1;

            a.deleteSafe("g219:notice");

            PlayerSnapshot tombstone=
                PlayerSnapshotCodec.capture("g219-alice",alice);

            WorldPlayer fresh=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(tombstone,fresh);

            tombstoneRoundTrip=
                "0".equals(tombstone.value(
                    "extension.mailbox-g21.count"
                ))&&
                fresh.mailbox().size()==0&&
                fresh.mailboxSnapshotKnown();

            foreignWorldRejected=
                rejectsState(()->new WorldMailboxGateway(
                    other,alice,aGen
                ))&&
                rejectsState(()->new WorldMailboxGateway(
                    world,new WorldPlayer(),aGen
                ));

            boolean removed=world.unregisterPlayer(alice,aGen);
            generationTurnoverRejected=
                removed&&
                rejectsState(a::snapshot)&&
                rejectsState(()->a.deliver(message(
                    "g219:old-session",
                    "Old session",
                    "Must never commit",
                    Collections.emptyList()
                )))&&
                rejectsState(a::openReadOnlyView);

            long nextGeneration=
                world.registerPlayer(alice,"g219-alice");

            generationTurnoverRejected &=
                nextGeneration!=aGen&&
                rejectsState(a::snapshot);

            WorldMailboxGateway next=
                new WorldMailboxGateway(
                    world,alice,nextGeneration
                );

            newGenerationAccepted=
                next.snapshot().size()==0&&
                next.deliver(message(
                    "g219:new-generation",
                    "Fresh generation",
                    "Allowed",
                    Collections.emptyList()
                )).message.messageId.equals(
                    "g219:new-generation"
                );

            noPacketsOrSettlement=
                Arrays.stream(
                    WorldMailboxGateway.class.getDeclaredMethods()
                ).noneMatch(method->
                    method.getName().toLowerCase().contains("settle")||
                    method.getName().contains("acknowledge")||
                    Arrays.stream(method.getParameterTypes())
                        .anyMatch(type->
                            type==ServerPacketWriter.class||
                            type==BankState.class
                        )
                )&&
                bob.mailbox().size()==0;

            noC2SRootClaim=
                Arrays.stream(
                    WorldMailboxGateway.class.getDeclaredMethods()
                ).noneMatch(method->
                    Arrays.stream(method.getParameterTypes())
                        .anyMatch(type->
                            type==WidgetActionClientRequest.class
                        )
                )&&
                mismatchedGenerationDenied;
        }

        require(
            boundToWorldPlayer&&
            accountIsolation&&
            strictMessagePreflight&&
            invalidDeliveryNoMutation&&
            readIdempotent&&
            unclaimedDeletionDenied&&
            claimedDeletionAllowed&&
            emptyDeletionAllowed&&
            viewUsesOwnedMailbox&&
            reusedIdentityRejected&&
            tombstoneRoundTrip&&
            foreignWorldRejected&&
            generationTurnoverRejected&&
            newGenerationAccepted&&
            noPacketsOrSettlement&&
            noC2SRootClaim,
            "G21.9 acceptance"
        );

        System.out.println(
            "G219_WORLD_MAILBOX_GATEWAY_PASS"+
            " boundToWorldPlayer="+boundToWorldPlayer+
            " accountIsolation="+accountIsolation+
            " strictMessagePreflight="+strictMessagePreflight+
            " invalidDeliveryNoMutation="+invalidDeliveryNoMutation+
            " readIdempotent="+readIdempotent+
            " unclaimedDeletionDenied="+unclaimedDeletionDenied+
            " claimedDeletionAllowed="+claimedDeletionAllowed+
            " emptyDeletionAllowed="+emptyDeletionAllowed+
            " viewUsesOwnedMailbox="+viewUsesOwnedMailbox+
            " reusedIdentityRejected="+reusedIdentityRejected+
            " tombstoneRoundTrip="+tombstoneRoundTrip+
            " foreignWorldRejected="+foreignWorldRejected+
            " generationTurnoverRejected="+generationTurnoverRejected+
            " newGenerationAccepted="+newGenerationAccepted+
            " noPacketsOrSettlement="+noPacketsOrSettlement+
            " noC2SRootClaim="+noC2SRootClaim+
            " liveRouterClaim=false"+
            " itemSettlementClaim=false"
        );
    }

    private static RewardDeliveryMessage message(
        String id,
        String subject,
        String body,
        List<RewardDeliveryMessage.Attachment> items
    ){
        return new RewardDeliveryMessage(
            id,subject,body,items,
            "CUSTOM_LOCALLAB_G219_FIXTURE"
        );
    }

    private static List<RewardDeliveryMessage.Attachment>
        manyAttachments(int count){
        java.util.ArrayList<RewardDeliveryMessage.Attachment>
            result=new java.util.ArrayList<>();
        for(int i=0;i<count;i++)
            result.add(
                new RewardDeliveryMessage.Attachment(995,1)
            );
        return result;
    }

    private static String repeat(char c,int count){
        char[] chars=new char[count];
        Arrays.fill(chars,c);
        return new String(chars);
    }

    private static WidgetActionClientRequest widget(int id){
        return new WidgetActionClientRequest(
            id,
            ClientRequestMetadata.exactCurrent(
                185,"widget-id-u16",
                "G219_EXACT_CLIENT_C2S185"
            )
        );
    }

    private interface Action {
        Object run()throws Exception;
    }

    private static boolean rejectsArgument(Action action){
        try{
            action.run();
            return false;
        }catch(IllegalArgumentException expected){
            return true;
        }catch(Exception other){
            throw new IllegalStateException(other);
        }
    }

    private static boolean rejectsState(Action action){
        try{
            action.run();
            return false;
        }catch(IllegalStateException expected){
            return true;
        }catch(Exception other){
            throw new IllegalStateException(other);
        }
    }

    private static void require(boolean ok,String detail){
        if(!ok)
            throw new AssertionError(detail);
    }

    private G219WorldMailboxGatewayIntegrationTest(){}
}
