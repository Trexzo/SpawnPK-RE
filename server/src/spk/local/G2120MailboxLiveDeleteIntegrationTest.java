package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;

/**
 * G21.20: exact native C2S185 widget32184 safe Delete plus full inbox
 * refresh; semantics are CUSTOM_LOCALLAB, not original server policy.
 */
public final class G2120MailboxLiveDeleteIntegrationTest {
    private static final int[] SEED={2120,2121,2122,2123};

    public static void main(String[] args)throws Exception{
        boolean emptyDeletedAndRefreshed=false;
        boolean selectionRetired=false;
        boolean unclaimedNeverDeleted=false;
        boolean claimedDeletedAndRefreshed=false;
        boolean wrongOpcodeNoMutation=false;
        boolean sameIdRecycledRejected=false;
        boolean invalidSurvivorNoDeletion=false;
        boolean failedTransportKeepsTombstone=false;
        boolean refreshReconcilesTransport=false;
        boolean emptyTombstoneRestored=false;
        boolean foreignAccountIsolated=false;
        boolean staleGenerationZeroWire=false;
        boolean closedScopeNoActions=false;
        boolean noSettlement=false;
        boolean boundedExactPackets=false;

        try(World world=World.isolatedForTest(60000L)){
            WorldPlayer alice=new WorldPlayer();
            WorldPlayer bob=new WorldPlayer();
            long ag=world.registerPlayer(alice,"g2120-alice");
            long bg=world.registerPlayer(bob,"g2120-bob");
            WorldMailboxGateway a=new WorldMailboxGateway(world,alice,ag);
            WorldMailboxGateway b=new WorldMailboxGateway(world,bob,bg);

            a.deliver(message("g2120:empty","Empty",false));
            a.deliver(message("g2120:pending","Pending",true));
            a.deliver(message("g2120:claimed","Claimed",true));
            alice.mailbox().acknowledgeAttachmentSettlement(
                "g2120:claimed"
            );
            b.deliver(message("g2120:bob","Bob Private",true));

            LocalMailboxRootlessSession session=
                new LocalMailboxRootlessSession(world,alice,ag);
            session.openNativeRoot(writer(new ByteArrayOutputStream()));

            // Native row view marks READ as of certified G21.19.
            session.handleWidget(
                widget(185,32026),writer(new ByteArrayOutputStream())
            );
            ByteArrayOutputStream firstWire=
                new ByteArrayOutputStream();
            boolean first=session.handleWidget(
                widget(185,32184),writer(firstWire)
            );
            ByteArrayOutputStream firstExpected=
                new ByteArrayOutputStream();
            MailboxInboxProjection.publish(
                writer(firstExpected),alice.mailbox().snapshot()
            );
            emptyDeletedAndRefreshed=
                first&&alice.mailbox().size()==2&&
                alice.mailbox().get("g2120:empty")==null&&
                alice.mailboxSnapshotKnown()&&
                Arrays.equals(firstWire.toByteArray(),
                              firstExpected.toByteArray());
            boundedExactPackets=
                exactInboxWire(firstWire.toByteArray(),2);

            ByteArrayOutputStream repeated=new ByteArrayOutputStream();
            selectionRetired=
                rejects(()->session.handleWidget(
                    widget(185,32184),writer(repeated)
                ))&&repeated.size()==0&&
                alice.mailbox().size()==2;

            // Current row zero is UNCLAIMED and must never be deleted.
            session.handleWidget(
                widget(185,32026),writer(new ByteArrayOutputStream())
            );
            ByteArrayOutputStream refused=new ByteArrayOutputStream();
            unclaimedNeverDeleted=
                rejects(()->session.handleWidget(
                    widget(185,32184),writer(refused)
                ))&&refused.size()==0&&
                alice.mailbox().size()==2&&
                alice.mailbox().get("g2120:pending").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                alice.mailbox().get("g2120:pending")
                    .message.attachments.get(0).amount==25;

            ByteArrayOutputStream wrong=new ByteArrayOutputStream();
            wrongOpcodeNoMutation=
                rejects(()->session.handleWidget(
                    widget(184,32184),writer(wrong)
                ))&&wrong.size()==0&&
                alice.mailbox().size()==2;

            // G21.12 supports only an EXTERNALLY acknowledged CLAIMED
            // envelope; live widget does not perform that settlement.
            session.handleWidget(
                widget(185,32030),writer(new ByteArrayOutputStream())
            );
            ByteArrayOutputStream claimedWire=
                new ByteArrayOutputStream();
            boolean deletedClaimed=session.handleWidget(
                widget(185,32184),writer(claimedWire)
            );
            ByteArrayOutputStream claimedExpected=
                new ByteArrayOutputStream();
            MailboxInboxProjection.publish(
                writer(claimedExpected),alice.mailbox().snapshot()
            );
            claimedDeletedAndRefreshed=
                deletedClaimed&&alice.mailbox().size()==1&&
                alice.mailbox().get("g2120:claimed")==null&&
                Arrays.equals(claimedWire.toByteArray(),
                              claimedExpected.toByteArray());
            boundedExactPackets &=
                exactInboxWire(claimedWire.toByteArray(),1);

            // A same-ID replacement cannot inherit the bound original
            // object, even if its index and messageId happen to match.
            a.deliver(message("g2120:reuse","Original",false));
            session.handleWidget(
                widget(185,32185),writer(new ByteArrayOutputStream())
            );
            session.handleWidget(
                widget(185,32030),writer(new ByteArrayOutputStream())
            );
            a.deleteSafe("g2120:reuse");
            a.deliver(message("g2120:reuse","Replacement",false));
            ByteArrayOutputStream reused=new ByteArrayOutputStream();
            sameIdRecycledRejected=
                rejects(()->session.handleWidget(
                    widget(185,32184),writer(reused)
                ))&&reused.size()==0&&
                "Replacement".equals(
                    alice.mailbox().get("g2120:reuse").message.subject
                )&&
                alice.mailbox().get("g2120:reuse").readState==
                    MailboxRewardDeliveryService.ReadState.UNREAD;

            // A malformed survivor inserted *after* the original bind
            // must be discovered by candidate preflight before deleting
            // an otherwise-safe selected envelope.
            WorldPlayer invalid=new WorldPlayer();
            long ig=world.registerPlayer(invalid,"g2120-invalid");
            invalid.mailbox().deliver(
                message("g2120:safe","Safe",false)
            );
            LocalMailboxRootlessSession invalidView=
                new LocalMailboxRootlessSession(world,invalid,ig);
            invalidView.sync(writer(new ByteArrayOutputStream()));
            invalidView.handleWidget(
                widget(185,32026),
                writer(new ByteArrayOutputStream())
            );
            invalid.mailbox().deliver(
                message("g2120:bad","Invalid\nsubject",false)
            );
            ByteArrayOutputStream preflight=new ByteArrayOutputStream();
            invalidSurvivorNoDeletion=
                rejects(()->invalidView.handleWidget(
                    widget(185,32184),writer(preflight)
                ))&&preflight.size()==0&&
                invalid.mailbox().get("g2120:safe")!=null&&
                invalid.mailbox().unreadCount()==1;
            invalidView.close();

            // Semantic delete is committed before outbound batch flush;
            // transport rejection does not resurrect the removed mail.
            WorldPlayer failing=new WorldPlayer();
            long fg=world.registerPlayer(failing,"g2120-failing");
            failing.mailbox().deliver(
                message("g2120:last","Last message",false)
            );
            LocalMailboxRootlessSession failureScope=
                new LocalMailboxRootlessSession(world,failing,fg);
            failureScope.sync(writer(new ByteArrayOutputStream()));
            failureScope.handleWidget(
                widget(185,32026),
                writer(new ByteArrayOutputStream())
            );
            final int[] writes={0};
            boolean ioFailed=false;
            try{
                failureScope.handleWidget(
                    widget(185,32184),
                    writer(new OutputStream(){
                        @Override public void write(int b)
                            throws IOException{
                            writes[0]++;
                            throw new IOException(
                                "G21.20 simulated socket failure"
                            );
                        }
                    })
                );
            }catch(IOException expected){
                ioFailed=true;
            }
            failedTransportKeepsTombstone=
                ioFailed&&writes[0]>0&&
                failing.mailbox().size()==0&&
                failing.mailboxSnapshotKnown();

            ByteArrayOutputStream healed=new ByteArrayOutputStream();
            boolean didRefresh=failureScope.handleWidget(
                widget(185,32185),writer(healed)
            );
            refreshReconcilesTransport=
                didRefresh&&exactInboxWire(healed.toByteArray(),0)&&
                failing.mailbox().size()==0;

            PlayerSnapshot saved=PlayerSnapshotCodec.capture(
                "g2120-failing",failing
            );
            WorldPlayer restored=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(saved,restored);
            emptyTombstoneRestored=
                restored.mailbox().size()==0&&
                restored.mailboxSnapshotKnown()&&
                "0".equals(saved.value("extension.mailbox-g21.count"));
            failureScope.close();

            foreignAccountIsolated=
                bob.mailbox().size()==1&&
                bob.mailbox().unreadCount()==1&&
                bob.mailbox().get("g2120:pending")==null&&
                alice.mailbox().get("g2120:bob")==null;

            // A valid selected envelope cannot be acted upon after
            // unregister or a new WorldPlayer registration generation.
            session.handleWidget(
                widget(185,32185),writer(new ByteArrayOutputStream())
            );
            session.handleWidget(
                widget(185,32030),writer(new ByteArrayOutputStream())
            );
            boolean retired=world.unregisterPlayer(alice,ag);
            ByteArrayOutputStream stale=new ByteArrayOutputStream();
            staleGenerationZeroWire=
                retired&&rejects(()->session.handleWidget(
                    widget(185,32184),writer(stale)
                ))&&stale.size()==0;
            long ag2=world.registerPlayer(alice,"g2120-alice");
            staleGenerationZeroWire &=
                ag2!=ag&&rejects(()->session.handleWidget(
                    widget(185,32184),writer(stale)
                ))&&stale.size()==0;

            session.close();
            ByteArrayOutputStream closed=new ByteArrayOutputStream();
            closedScopeNoActions=
                !session.isActive()&&
                !session.handleWidget(
                    widget(185,32184),writer(closed)
                )&&closed.size()==0;

            noSettlement=
                alice.mailbox().get("g2120:pending").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                alice.mailbox().get("g2120:pending")
                    .message.attachments.get(0).itemId==995&&
                LocalMailboxRootlessSession.AUTHORITY.startsWith(
                    "CUSTOM_LOCALLAB"
                );
        }

        System.out.println(
            "G2120_MAILBOX_LIVE_DELETE_DIAGNOSTICS"+
            " emptyDeletedAndRefreshed="+emptyDeletedAndRefreshed+
            " selectionRetired="+selectionRetired+
            " unclaimedNeverDeleted="+unclaimedNeverDeleted+
            " claimedDeletedAndRefreshed="+claimedDeletedAndRefreshed+
            " wrongOpcodeNoMutation="+wrongOpcodeNoMutation+
            " sameIdRecycledRejected="+sameIdRecycledRejected+
            " invalidSurvivorNoDeletion="+invalidSurvivorNoDeletion+
            " failedTransportKeepsTombstone="+failedTransportKeepsTombstone+
            " refreshReconcilesTransport="+refreshReconcilesTransport+
            " emptyTombstoneRestored="+emptyTombstoneRestored+
            " foreignAccountIsolated="+foreignAccountIsolated+
            " staleGenerationZeroWire="+staleGenerationZeroWire+
            " closedScopeNoActions="+closedScopeNoActions+
            " noSettlement="+noSettlement+
            " boundedExactPackets="+boundedExactPackets
        );
        require(
            emptyDeletedAndRefreshed&&selectionRetired&&
            unclaimedNeverDeleted&&claimedDeletedAndRefreshed&&
            wrongOpcodeNoMutation&&sameIdRecycledRejected&&
            invalidSurvivorNoDeletion&&failedTransportKeepsTombstone&&
            refreshReconcilesTransport&&emptyTombstoneRestored&&
            foreignAccountIsolated&&staleGenerationZeroWire&&
            closedScopeNoActions&&noSettlement&&boundedExactPackets,
            "G21.20 acceptance"
        );
        System.out.println(
            "G2120_MAILBOX_LIVE_DELETE_PASS"+
            " exactC2S185Widget32184=true"+
            " emptyAndExternallyClaimedOnly=true"+
            " unclaimedProtected=true"+
            " postDeleteS2C250Inbox=true"+
            " candidatePreflightBeforeMutation=true"+
            " batchFailureTombstoneRetained=true"+
            " refreshReconciliation=true"+
            " playerGenerationFenced=true"+
            " rewardSettlementClaim=false"+
            " originalDeletionPolicyClaim=false"+
            " manualClientRuntimeClaim=false"
        );
    }

    private static boolean exactInboxWire(
        byte[] wire,int expectedRows
    ){
        IsaacCipher decoder=new IsaacCipher(SEED.clone());
        int pos=0,seenRows=0;
        boolean clear=false,finalized=false;
        while(pos<wire.length){
            int op=((wire[pos++]&255)-decoder.nextInt())&255;
            if(op!=250||pos>=wire.length)return false;
            int length=wire[pos++]&255;
            if(length<3||pos+length>wire.length)return false;
            int subtype=((wire[pos++]&255)<<8)|(wire[pos++]&255);
            if(subtype!=31)return false;
            int instruction=wire[pos++]&255;
            if(instruction==0){
                if(clear||seenRows!=0||finalized||length!=3)
                    return false;
                clear=true;
            }else if(instruction==1){
                if(!clear||finalized||length<5)
                    return false;
                seenRows++;
            }else if(instruction==5){
                if(!clear||finalized||length!=3)
                    return false;
                finalized=true;
            }else return false;
            pos+=length-3;
        }
        return clear&&finalized&&seenRows==expectedRows;
    }

    private static WidgetActionClientRequest widget(
        int opcode,int id
    ){
        return new WidgetActionClientRequest(
            id,ClientRequestMetadata.exactCurrent(
                opcode,"FIXED2_WIDGET_U16_BE","G2120_EXACT_V308"
            )
        );
    }

    private static ServerPacketWriter writer(OutputStream output){
        return new ServerPacketWriter(
            output,new IsaacCipher(SEED.clone())
        );
    }

    private static RewardDeliveryMessage message(
        String id,String subject,boolean attached
    ){
        return new RewardDeliveryMessage(
            id,subject,"G21.20 semantic deletion",
            attached?Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,25)
            ):Collections.emptyList(),
            "CUSTOM_LOCALLAB_G2120_FIXTURE"
        );
    }

    private interface Action { Object run()throws Exception; }

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

    private static void require(boolean yes,String label){
        if(!yes)throw new AssertionError(label);
    }

    private G2120MailboxLiveDeleteIntegrationTest(){}
}
