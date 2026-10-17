package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * G21.21 intentionally DOES NOT implement inventory settlement. It proves
 * that the exact native-v308 deposit widget reaches an owner-scoped strict
 * preview while all account, inventory and Mailbox state stays unchanged.
 */
public final class G2121MailboxInventoryClaimPreflightIntegrationTest {
    private static final int[] SEED={2121,2122,2123,2124};

    public static void main(String[] args)throws Exception{
        boolean exactWidgetPreflight=false;
        boolean noWireOrMutation=false;
        boolean mixedBundleCapacity=false;
        boolean duplicateStackAggregation=false;
        boolean fullInventoryDenied=false;
        boolean stackOverflowDenied=false;
        boolean unsupportedDefinitionDenied=false;
        boolean unknownStackabilityDenied=false;
        boolean nonstackableCapacityDenied=false;
        boolean wrongMetadataDenied=false;
        boolean staleSameIdDenied=false;
        boolean replayNeverSettles=false;
        boolean emptyAndClaimedDenied=false;
        boolean foreignAccountIsolated=false;
        boolean persistedUnclaimed=false;
        boolean logoutAndClosedDenied=false;
        boolean durabilityGateEnforced=false;

        int nonstack=explicitNonstackable();
        int unknown=unverifiedStackability();

        try(World world=World.isolatedForTest(60000L)){
            WorldPlayer alice=new WorldPlayer();
            WorldPlayer bob=new WorldPlayer();
            long ag=world.registerPlayer(alice,"g2121-alice");
            long bg=world.registerPlayer(bob,"g2121-bob");
            WorldMailboxGateway a=new WorldMailboxGateway(world,alice,ag);
            WorldMailboxGateway b=new WorldMailboxGateway(world,bob,bg);

            a.deliver(message(
                "g2121:reward","Inventory reward",
                attachments(995,25,560,4,995,5)
            ));
            b.deliver(message(
                "g2121:private","Bob private reward",
                attachments(995,10)
            ));

            LocalMailboxRootlessSession live=
                new LocalMailboxRootlessSession(world,alice,ag);
            live.openNativeRoot(writer(new ByteArrayOutputStream()));
            live.handleWidget(
                widget(185,32026),
                writer(new ByteArrayOutputStream())
            );
            PlayerSnapshot before=PlayerSnapshotCodec.capture(
                "g2121-alice",alice
            );
            ByteArrayOutputStream claimOutput=
                new ByteArrayOutputStream();
            exactWidgetPreflight=live.handleWidget(
                widget(185,32181),writer(claimOutput)
            );
            noWireOrMutation=
                exactWidgetPreflight&&
                claimOutput.size()==0&&
                alice.bank().inventorySlots()==0&&
                alice.mailbox().get("g2121:reward").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                before.values().equals(
                    PlayerSnapshotCodec.capture(
                        "g2121-alice",alice
                    ).values()
                );

            WorldMailboxPresentationSession selected=
                a.openRootlessPresentation();
            selected.publishInbox(
                writer(new ByteArrayOutputStream())
            );
            selected.publishTrustedRowDetail(
                0,writer(new ByteArrayOutputStream())
            );
            MailboxInventoryClaimPreflight.Preview preview=
                selected.previewSelectedInventoryClaimFromWidget(
                    widget(185,32181)
                );

            int coinSlot=slotFor(preview,995);
            int deathRuneSlot=slotFor(preview,560);
            mixedBundleCapacity=
                preview.eligible&&
                "PREFLIGHT_ONLY_NOT_SETTLED".equals(preview.reason)&&
                preview.attachmentCount==3&&
                coinSlot>=0&&deathRuneSlot>=0&&
                preview.quantities[coinSlot]==30&&
                preview.quantities[deathRuneSlot]==4&&
                alice.bank().inventorySlots()==0;

            duplicateStackAggregation=
                preview.eligible&&
                Arrays.stream(preview.itemIds)
                    .filter(x->x==995).count()==1&&
                Arrays.stream(preview.itemIds)
                    .filter(x->x==560).count()==1;

            // The preview does not reserve the inventory or consume a
            // mailbox message. Repeating the exact same click is inert.
            ByteArrayOutputStream replay=new ByteArrayOutputStream();
            replayNeverSettles=
                live.handleWidget(widget(185,32181),writer(replay))&&
                replay.size()==0&&alice.bank().inventorySlots()==0&&
                alice.mailbox().get("g2121:reward").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;

            ByteArrayOutputStream wrong=new ByteArrayOutputStream();
            wrongMetadataDenied=
                rejects(()->live.handleWidget(
                    widget(184,32181),writer(wrong)
                ))&&wrong.size()==0&&
                rejects(()->selected
                    .previewSelectedInventoryClaimFromWidget(
                        widget(185,32178)
                    ));

            foreignAccountIsolated=
                bob.mailbox().size()==1&&
                bob.mailbox().get("g2121:reward")==null&&
                alice.mailbox().get("g2121:private")==null&&
                bob.bank().inventorySlots()==0&&
                bob.mailbox().get("g2121:private").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;

            PlayerSnapshot saved=PlayerSnapshotCodec.capture(
                "g2121-alice",alice
            );
            WorldPlayer restored=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(saved,restored);
            persistedUnclaimed=
                restored.bank().inventorySlots()==0&&
                restored.mailbox().get("g2121:reward").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                restored.mailbox().get("g2121:reward").readState==
                    MailboxRewardDeliveryService.ReadState.READ;

            // Same message ID does not authenticate a different
            // immutable envelope even if an older selected row exists.
            alice.mailbox().delete("g2121:reward");
            alice.mailbox().deliver(message(
                "g2121:reward","Replacement",
                attachments(995,1)
            ));
            ByteArrayOutputStream stale=new ByteArrayOutputStream();
            staleSameIdDenied=
                rejects(()->live.handleWidget(
                    widget(185,32181),writer(stale)
                ))&&stale.size()==0&&
                alice.mailbox().get("g2121:reward").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            selected.close();
            live.close();

            // Capacity/metadata cases use detached player fixtures and
            // inspect only. The simulator never calls a mutation method.
            WorldPlayer full=new WorldPlayer();
            int[] ids=new int[BankState.INVENTORY_CAPACITY];
            int[] qty=new int[ids.length];
            Arrays.fill(ids,995);
            Arrays.fill(qty,1);
            full.bank().replaceInventorySemantic(ids,qty);
            MailboxInventoryClaimPreflight.Preview fullPreview=
                inspectFixture(full,attachments(560,3));
            fullInventoryDenied=
                !fullPreview.eligible&&
                "INVENTORY_FULL".equals(fullPreview.reason)&&
                full.bank().inventorySlots()==28&&
                full.mailbox().get("g2121:fixture").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;

            WorldPlayer overflow=new WorldPlayer();
            Arrays.fill(ids,-1);
            Arrays.fill(qty,0);
            ids[0]=995;
            qty[0]=Integer.MAX_VALUE;
            overflow.bank().replaceInventorySemantic(ids,qty);
            MailboxInventoryClaimPreflight.Preview overflowPreview=
                inspectFixture(overflow,attachments(995,1));
            stackOverflowDenied=
                !overflowPreview.eligible&&
                "STACK_OVERFLOW".equals(overflowPreview.reason)&&
                overflow.bank().inventorySlotSnapshot(0).quantity==
                    Integer.MAX_VALUE;

            WorldPlayer notKnown=new WorldPlayer();
            MailboxInventoryClaimPreflight.Preview noDefinition=
                inspectFixture(notKnown,attachments(65534,1));
            unsupportedDefinitionDenied=
                !noDefinition.eligible&&
                ("UNSUPPORTED_ITEM_ID".equals(noDefinition.reason)||
                 "STACKABILITY_NOT_VERIFIED".equals(noDefinition.reason));

            MailboxInventoryClaimPreflight.Preview unknownPreview=
                inspectFixture(new WorldPlayer(),attachments(unknown,1));
            unknownStackabilityDenied=
                unknown>=0&&!unknownPreview.eligible&&
                "STACKABILITY_NOT_VERIFIED".equals(
                    unknownPreview.reason
                );

            WorldPlayer nonstackPlayer=new WorldPlayer();
            Arrays.fill(ids,995);
            Arrays.fill(qty,1);
            ids[26]=-1;ids[27]=-1;
            qty[26]=0;qty[27]=0;
            nonstackPlayer.bank().replaceInventorySemantic(ids,qty);
            MailboxInventoryClaimPreflight.Preview nonstackPreview=
                inspectFixture(
                    nonstackPlayer,attachments(nonstack,3)
                );
            nonstackableCapacityDenied=
                nonstack>=0&&
                !nonstackPreview.eligible&&
                "INVENTORY_FULL".equals(nonstackPreview.reason)&&
                nonstackPlayer.bank().inventorySlots()==26;

            MailboxInventoryClaimPreflight.Preview emptyPreview=
                inspectFixture(
                    new WorldPlayer(),
                    Collections.<RewardDeliveryMessage.Attachment>
                        emptyList()
                );
            WorldPlayer claimed=new WorldPlayer();
            claimed.mailbox().deliver(message(
                "g2121:fixture","Claimed",attachments(995,1)
            ));
            claimed.mailbox().acknowledgeAttachmentSettlement(
                "g2121:fixture"
            );
            MailboxInventoryClaimPreflight.Preview claimedPreview=
                MailboxInventoryClaimPreflight.inspect(
                    claimed,claimed.mailbox().get("g2121:fixture")
                );
            emptyAndClaimedDenied=
                !emptyPreview.eligible&&
                "NOT_UNCLAIMED".equals(emptyPreview.reason)&&
                !claimedPreview.eligible&&
                "NOT_UNCLAIMED".equals(claimedPreview.reason);

            // No stale player may inspect the active C2S185 selection.
            WorldMailboxPresentationSession generationScope=
                a.openRootlessPresentation();
            generationScope.publishInbox(
                writer(new ByteArrayOutputStream())
            );
            generationScope.publishTrustedRowDetail(
                0,writer(new ByteArrayOutputStream())
            );
            boolean unregistered=world.unregisterPlayer(alice,ag);
            logoutAndClosedDenied=
                unregistered&&rejects(()->generationScope
                    .previewSelectedInventoryClaimFromWidget(
                        widget(185,32181)
                    ));
            long next=world.registerPlayer(alice,"g2121-alice");
            logoutAndClosedDenied &=
                next!=ag&&rejects(()->generationScope
                    .previewSelectedInventoryClaimFromWidget(
                        widget(185,32181)
                    ));
            generationScope.close();
            logoutAndClosedDenied &=
                rejects(()->generationScope
                    .previewSelectedInventoryClaimFromWidget(
                        widget(185,32181)
                    ));

            durabilityGateEnforced=
                MailboxInventoryClaimPreflight.AUTHORITY.equals(
                    "CUSTOM_LOCALLAB_G2121_CLAIM_PREFLIGHT_ONLY"
                )&&
                Arrays.stream(
                    MailboxInventoryClaimPreflight.class
                        .getDeclaredMethods()
                ).noneMatch(method->
                    method.getName().contains("settle")||
                    method.getName().contains("commit")||
                    method.getName().contains("grant")
                );
        }

        System.out.println(
            "G2121_MAILBOX_CLAIM_PREFLIGHT_DIAGNOSTICS"+
            " exactWidgetPreflight="+exactWidgetPreflight+
            " noWireOrMutation="+noWireOrMutation+
            " mixedBundleCapacity="+mixedBundleCapacity+
            " duplicateStackAggregation="+duplicateStackAggregation+
            " fullInventoryDenied="+fullInventoryDenied+
            " stackOverflowDenied="+stackOverflowDenied+
            " unsupportedDefinitionDenied="+unsupportedDefinitionDenied+
            " unknownStackabilityDenied="+unknownStackabilityDenied+
            " nonstackableCapacityDenied="+nonstackableCapacityDenied+
            " wrongMetadataDenied="+wrongMetadataDenied+
            " staleSameIdDenied="+staleSameIdDenied+
            " replayNeverSettles="+replayNeverSettles+
            " emptyAndClaimedDenied="+emptyAndClaimedDenied+
            " foreignAccountIsolated="+foreignAccountIsolated+
            " persistedUnclaimed="+persistedUnclaimed+
            " logoutAndClosedDenied="+logoutAndClosedDenied+
            " durabilityGateEnforced="+durabilityGateEnforced
        );

        require(
            exactWidgetPreflight&&noWireOrMutation&&
            mixedBundleCapacity&&duplicateStackAggregation&&
            fullInventoryDenied&&stackOverflowDenied&&
            unsupportedDefinitionDenied&&unknownStackabilityDenied&&
            nonstackableCapacityDenied&&wrongMetadataDenied&&
            staleSameIdDenied&&replayNeverSettles&&
            emptyAndClaimedDenied&&foreignAccountIsolated&&
            persistedUnclaimed&&logoutAndClosedDenied&&
            durabilityGateEnforced,
            "G21.21 acceptance"
        );

        System.out.println(
            "G2121_MAILBOX_CLAIM_PREFLIGHT_PASS"+
            " exactNativeC2S185Widget32181=true"+
            " ownerScopedPreview=true"+
            " allAttachmentsEvaluated=true"+
            " unknownStackabilityFailClosed=true"+
            " twentyEightSlotCapacity=true"+
            " intStackOverflowRejected=true"+
            " zeroRewardSettlement=true"+
            " noInventoryMutation=true"+
            " noPacketEmission=true"+
            " noDurabilityGuaranteeInvented=true"+
            " nativeGameplayRuntimeClaim=false"
        );
    }

    private static MailboxInventoryClaimPreflight.Preview
        inspectFixture(
            WorldPlayer player,
            List<RewardDeliveryMessage.Attachment> items
        ){
        if(player.mailbox().size()==0)
            player.mailbox().deliver(
                message("g2121:fixture","Fixture",items)
            );
        return MailboxInventoryClaimPreflight.inspect(
            player,player.mailbox().get("g2121:fixture")
        );
    }

    private static int explicitNonstackable(){
        for(ItemCatalog.Meta item:ItemCatalog.all()){
            if(item.id>=0&&item.id<65535&&
               "CURRENT_CONFIG_EXPLICIT_NONSTACKABLE".equals(
                   ItemDefinitionRepository.stackabilityEvidence(
                       item.id
                   )
               ))return item.id;
        }
        return -1;
    }

    private static int unverifiedStackability(){
        for(ItemCatalog.Meta item:ItemCatalog.all()){
            if(item.id>=0&&item.id<65535&&
               "UNKNOWN_DEFAULT_NONSTACKABLE".equals(
                   ItemDefinitionRepository.stackabilityEvidence(
                       item.id
                   )
               ))return item.id;
        }
        return -1;
    }

    private static int slotFor(
        MailboxInventoryClaimPreflight.Preview preview,int id
    ){
        if(preview.itemIds==null)return -1;
        for(int i=0;i<preview.itemIds.length;i++)
            if(preview.itemIds[i]==id)return i;
        return -1;
    }

    private static RewardDeliveryMessage message(
        String id,String subject,
        List<RewardDeliveryMessage.Attachment> attachments
    ){
        return new RewardDeliveryMessage(
            id,subject,"G21.21 inventory claim dry run",
            attachments,
            "CUSTOM_LOCALLAB_G2121_TEST"
        );
    }

    private static List<RewardDeliveryMessage.Attachment>
        attachments(long... values){
        if(values.length%2!=0)
            throw new IllegalArgumentException("item pairs");
        ArrayList<RewardDeliveryMessage.Attachment> out=
            new ArrayList<>();
        for(int i=0;i<values.length;i+=2)
            out.add(new RewardDeliveryMessage.Attachment(
                (int)values[i],values[i+1]
            ));
        return out;
    }

    private static WidgetActionClientRequest widget(
        int opcode,int widget
    ){
        return new WidgetActionClientRequest(
            widget,ClientRequestMetadata.exactCurrent(
                opcode,"FIXED2_WIDGET_U16_BE",
                "G2121_PINNED_V308_FIXTURE"
            )
        );
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream bytes
    ){
        return new ServerPacketWriter(
            bytes,new IsaacCipher(SEED.clone())
        );
    }

    private interface Action{ Object run()throws Exception; }

    private static boolean rejects(Action action){
        try{
            action.run();
            return false;
        }catch(IllegalArgumentException|
                IllegalStateException expected){
            return true;
        }catch(Exception unexpected){
            throw new IllegalStateException(unexpected);
        }
    }

    private static void require(boolean condition,String name){
        if(!condition)throw new AssertionError(name);
    }

    private G2121MailboxInventoryClaimPreflightIntegrationTest(){}
}
