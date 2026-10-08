package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

/**
 * G21.18: native Mailbox participates in the common World/trade/native-root
 * replacement arbiter. Competing roots retire the selected Mailbox ONLY
 * after a successful replacement publication.
 *
 * This is host-simulated exact wire/source-boundary proof, not a manual
 * pinned-v308 client screenshot or recovered original server policy.
 */
public final class G2118MailboxRootOwnershipIntegrationTest {
    private static final int[] SEED={2118,2119,2120,2121};

    public static void main(String[] args)throws Exception{
        boolean nativeUsesCommonArbiter=false;
        boolean ownRootNotSelfRetired=false;
        boolean otherRootRetiresNative=false;
        boolean noPostRetireWidgetWrites=false;
        boolean failedCompetingRootPreserved=false;
        boolean rejectedRootPreserved=false;
        boolean failedMailboxReopenPreserved=false;
        boolean rootlessVsNativeSeparate=false;
        boolean rootlessAlsoRetired=false;
        boolean secondNativeRootRestores=false;
        boolean closeRetires=false;
        boolean staleGenerationFailClosed=false;
        boolean foreignAccountIsolated=false;
        boolean noSettlementOrMutation=false;
        boolean noUnverifiedClientPolicy=false;

        try(World world=World.isolatedForTest(60000L)){
            WorldPlayer alice=new WorldPlayer();
            WorldPlayer bob=new WorldPlayer();
            long ag=world.registerPlayer(alice,"g2118-alice");
            long bg=world.registerPlayer(bob,"g2118-bob");
            WorldMailboxGateway a=new WorldMailboxGateway(world,alice,ag);
            WorldMailboxGateway b=new WorldMailboxGateway(world,bob,bg);
            a.deliver(message("g2118:a","Alice",true));
            b.deliver(message("g2118:b","Bob",false));

            LocalMailboxRootlessSession mailbox=
                new LocalMailboxRootlessSession(world,alice,ag);
            ByteArrayOutputStream nativeBytes=
                new ByteArrayOutputStream();
            ServerPacketWriter nativeWriter=writer(nativeBytes);
            String nativeResult=
                LocalSession.replaceRootAndRetireMailboxIfCompeting(
                    world,alice,ag,
                    ()->{
                        int count=mailbox.openNativeRoot(nativeWriter);
                        return "MAILBOX_OPENED_"+count;
                    },
                    mailbox,true
                );
            nativeUsesCommonArbiter=
                "MAILBOX_OPENED_1".equals(nativeResult)&&
                exactRoot97(nativeBytes.toByteArray(),32019);
            ownRootNotSelfRetired=
                mailbox.isActive()&&mailbox.isNativeRootOpen();

            // A failed competing publisher must not invalidate the
            // old selected Mailbox view or leak a new root packet.
            ByteArrayOutputStream failureBytes=
                new ByteArrayOutputStream();
            boolean failedWithIo=false;
            try{
                LocalSession.replaceRootAndRetireMailboxIfCompeting(
                    world,alice,ag,
                    ()->{
                        throw new IOException(
                            "G21.18 simulated competing root failure"
                        );
                    },
                    mailbox,false
                );
            }catch(IOException expected){
                failedWithIo=true;
            }
            failedCompetingRootPreserved=
                failedWithIo&&mailbox.isNativeRootOpen()&&
                failureBytes.size()==0;

            String nonCommitted=
                LocalSession.replaceRootAndRetireMailboxIfCompeting(
                    world,alice,ag,
                    ()->null,
                    mailbox,false
                );
            rejectedRootPreserved=
                nonCommitted==null&&mailbox.isNativeRootOpen();

            // Reopening a malformed inbox must abort the root+inbox
            // batch and preserve the previously published native scope.
            alice.mailbox().deliver(message(
                "g2118:invalid","Invalid\\nsubject",false
            ));
            ByteArrayOutputStream rejectedMailbox=
                new ByteArrayOutputStream();
            boolean invalidRejected=false;
            try{
                LocalSession.replaceRootAndRetireMailboxIfCompeting(
                    world,alice,ag,
                    ()->{
                        mailbox.openNativeRoot(
                            writer(rejectedMailbox)
                        );
                        return "INCORRECT_ROOT";
                    },
                    mailbox,true
                );
            }catch(IllegalArgumentException expected){
                invalidRejected=true;
            }
            failedMailboxReopenPreserved=
                invalidRejected&&rejectedMailbox.size()==0&&
                mailbox.isNativeRootOpen()&&
                alice.mailbox().get("g2118:a").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            a.deleteSafe("g2118:invalid");

            // Successful competing root: exact S2C97 opens a different
            // client interface and retires all previously bound mail rows.
            ByteArrayOutputStream competitor=
                new ByteArrayOutputStream();
            String otherRoot=
                LocalSession.replaceRootAndRetireMailboxIfCompeting(
                    world,alice,ag,
                    ()->{
                        writer(competitor).fixed(
                            97,BootstrapPackets.interface97(47500)
                        );
                        return "ITEM_LIBRARY_OPENED";
                    },
                    mailbox,false
                );
            otherRootRetiresNative=
                "ITEM_LIBRARY_OPENED".equals(otherRoot)&&
                exactRoot97(competitor.toByteArray(),47500)&&
                !mailbox.isActive()&&!mailbox.isNativeRootOpen();
            ByteArrayOutputStream afterRetire=
                new ByteArrayOutputStream();
            noPostRetireWidgetWrites=
                !mailbox.handleWidget(
                    widget(185,32026),writer(afterRetire)
                )&&
                !mailbox.handleWidget(
                    widget(185,32185),writer(afterRetire)
                )&&afterRetire.size()==0;

            // Explicit developer-only ::mail sync never opens a root,
            // but it creates a bounded inbox scope which must also
            // be retired when a *different* native root wins.
            ByteArrayOutputStream rootlessBytes=
                new ByteArrayOutputStream();
            rootlessVsNativeSeparate=
                mailbox.sync(writer(rootlessBytes))==1&&
                mailbox.isActive()&&!mailbox.isNativeRootOpen()&&
                rootlessBytes.size()>0&&
                !exactRoot97(rootlessBytes.toByteArray(),32019);
            String third=
                LocalSession.replaceRootAndRetireMailboxIfCompeting(
                    world,alice,ag,()->"PK_RATINGS_OPENED",
                    mailbox,false
                );
            rootlessAlsoRetired=
                "PK_RATINGS_OPENED".equals(third)&&
                !mailbox.isActive();

            ByteArrayOutputStream nativeAgain=
                new ByteArrayOutputStream();
            String reopened=
                LocalSession.replaceRootAndRetireMailboxIfCompeting(
                    world,alice,ag,
                    ()->{
                        mailbox.openNativeRoot(writer(nativeAgain));
                        return "MAILBOX_REOPENED";
                    },
                    mailbox,true
                );
            secondNativeRootRestores=
                "MAILBOX_REOPENED".equals(reopened)&&
                mailbox.isNativeRootOpen()&&
                exactRoot97(nativeAgain.toByteArray(),32019);

            // Rootless refresh of already visible native Mailbox keeps
            // the same *native root* ownership until replacement/close.
            secondNativeRootRestores &=
                mailbox.sync(
                    writer(new ByteArrayOutputStream())
                )==1&&mailbox.isNativeRootOpen();

            foreignAccountIsolated=
                bob.mailbox().size()==1&&
                bob.mailbox().get("g2118:a")==null&&
                alice.mailbox().get("g2118:b")==null;

            noSettlementOrMutation=
                alice.mailbox().size()==1&&
                alice.mailbox().unreadCount()==1&&
                alice.mailbox().get("g2118:a").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                alice.mailbox().get("g2118:a")
                    .message.attachments.get(0).amount==5&&
                bob.mailbox().unreadCount()==1;

            mailbox.onInterfaceClose();
            ByteArrayOutputStream closedWire=
                new ByteArrayOutputStream();
            closeRetires=
                !mailbox.isActive()&&
                !mailbox.isNativeRootOpen()&&
                !mailbox.handleWidget(
                    widget(185,32026),writer(closedWire)
                )&&closedWire.size()==0;

            String postClose=
                LocalSession.replaceRootAndRetireMailboxIfCompeting(
                    world,alice,ag,
                    ()->{
                        mailbox.openNativeRoot(
                            writer(new ByteArrayOutputStream())
                        );
                        return "MAILBOX_POST_CLOSE";
                    },
                    mailbox,true
                );
            closeRetires &=
                "MAILBOX_POST_CLOSE".equals(postClose)&&
                mailbox.isNativeRootOpen();

            boolean removed=world.unregisterPlayer(alice,ag);
            ByteArrayOutputStream staleBytes=
                new ByteArrayOutputStream();
            String noCurrent=
                LocalSession.replaceRootAndRetireMailboxIfCompeting(
                    world,alice,ag,
                    ()->{
                        writer(staleBytes).fixed(
                            97,BootstrapPackets.interface97(47500)
                        );
                        return "STALE_COMMITTED";
                    },
                    mailbox,false
                );
            long ag2=world.registerPlayer(
                alice,"g2118-alice"
            );
            String noOldGeneration=
                LocalSession.replaceRootAndRetireMailboxIfCompeting(
                    world,alice,ag,
                    ()->"STALE_COMMITTED",
                    mailbox,false
                );
            staleGenerationFailClosed=
                removed&&ag2!=ag&&noCurrent==null&&
                noOldGeneration==null&&
                staleBytes.size()==0&&
                // Local session owner object may still *look* active,
                // but the World mutation-generation fence denies I/O.
                mailbox.isNativeRootOpen()&&
                rejects(()->mailbox.handleWidget(
                    widget(185,32026),
                    writer(new ByteArrayOutputStream())
                ));

            mailbox.close();
            noUnverifiedClientPolicy=
                LocalMailboxRootlessSession.AUTHORITY.startsWith(
                    "CUSTOM_LOCALLAB"
                )&&
                LocalMailboxRootlessSession.NATIVE_V308_MAILBOX_ROOT==
                    32019&&
                !mailbox.isActive();
        }

        System.out.println(
            "G2118_MAILBOX_ROOT_OWNERSHIP_DIAGNOSTICS"+
            " nativeUsesCommonArbiter="+nativeUsesCommonArbiter+
            " ownRootNotSelfRetired="+ownRootNotSelfRetired+
            " otherRootRetiresNative="+otherRootRetiresNative+
            " noPostRetireWidgetWrites="+
                noPostRetireWidgetWrites+
            " failedCompetingRootPreserved="+
                failedCompetingRootPreserved+
            " rejectedRootPreserved="+rejectedRootPreserved+
            " failedMailboxReopenPreserved="+
                failedMailboxReopenPreserved+
            " rootlessVsNativeSeparate="+
                rootlessVsNativeSeparate+
            " rootlessAlsoRetired="+rootlessAlsoRetired+
            " secondNativeRootRestores="+secondNativeRootRestores+
            " closeRetires="+closeRetires+
            " staleGenerationFailClosed="+
                staleGenerationFailClosed+
            " foreignAccountIsolated="+foreignAccountIsolated+
            " noSettlementOrMutation="+noSettlementOrMutation+
            " noUnverifiedClientPolicy="+
                noUnverifiedClientPolicy
        );

        require(
            nativeUsesCommonArbiter&&ownRootNotSelfRetired&&
            otherRootRetiresNative&&noPostRetireWidgetWrites&&
            failedCompetingRootPreserved&&rejectedRootPreserved&&
            failedMailboxReopenPreserved&&rootlessVsNativeSeparate&&
            rootlessAlsoRetired&&secondNativeRootRestores&&
            closeRetires&&staleGenerationFailClosed&&
            foreignAccountIsolated&&noSettlementOrMutation&&
            noUnverifiedClientPolicy,
            "G21.18 acceptance"
        );

        System.out.println(
            "G2118_MAILBOX_ROOT_OWNERSHIP_PASS"+
            " nativeMailboxSharedWorldArbiter=true"+
            " afterCommitOtherRootRetiresMail=true"+
            " failedRootKeepsPriorScope=true"+
            " rootlessNativeSeparate=true"+
            " noStaleWidgetWrites=true"+
            " generationFenced=true"+
            " noSettlement=true"+
            " manualNativeClientRuntimeClaim=false"
        );
    }

    private static boolean exactRoot97(byte[] bytes,int root){
        if(bytes.length<3)
            return false;
        IsaacCipher decoder=new IsaacCipher(SEED.clone());
        return (((bytes[0]&255)-decoder.nextInt())&255)==97&&
            (bytes[1]&255)==((root>>>8)&255)&&
            (bytes[2]&255)==(root&255);
    }

    private static WidgetActionClientRequest widget(
        int opcode,int id
    ){
        return new WidgetActionClientRequest(
            id,ClientRequestMetadata.exactCurrent(
                opcode,"FIXED2_WIDGET_U16_BE",
                "G2118_PINNED_V308_FIXTURE"
            )
        );
    }

    private static RewardDeliveryMessage message(
        String id,String subject,boolean items
    ){
        return new RewardDeliveryMessage(
            id,subject,"G21.18 root ownership",
            items?Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,5)
            ):Collections.emptyList(),
            "CUSTOM_LOCALLAB_G2118_FIXTURE"
        );
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream bytes
    ){
        return new ServerPacketWriter(
            bytes,new IsaacCipher(SEED.clone())
        );
    }

    private interface Action{
        Object run()throws Exception;
    }

    private static boolean rejects(Action action){
        try{
            action.run();
            return false;
        }catch(IllegalStateException|
                IllegalArgumentException expected){
            return true;
        }catch(Exception unexpected){
            throw new IllegalStateException(unexpected);
        }
    }

    private static void require(boolean value,String label){
        if(!value)throw new AssertionError(label);
    }

    private G2118MailboxRootOwnershipIntegrationTest(){}
}
