package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;

/**
 * G21.19 native v308 C2S185 row viewing -> READ acknowledgement.
 * The recovered client has READ/UNREAD row state; acknowledging on row
 * view is an explicit CUSTOM_LOCALLAB policy, not original server timing.
 */
public final class G2119MailboxNativeReadIntegrationTest {
    private static final int[] SEED={2119,2120,2121,2122};

    public static void main(String[] args)throws Exception{
        boolean nativeRootAndRowWire=false;
        boolean exactSelectedDetailReadOrder=false;
        boolean firstReadPersisted=false;
        boolean replayIdempotent=false;
        boolean wrongRowZeroWire=false;
        boolean malformedDetailZeroWire=false;
        boolean foreignAccountIsolated=false;
        boolean transportFailurePersists=false;
        boolean refreshHealsRead=false;
        boolean sameIdRecycledRejected=false;
        boolean closedScopeFailClosed=false;
        boolean staleGenerationDenied=false;
        boolean attachmentNotClaimed=false;
        boolean noOriginalServerPolicyClaim=false;

        try(World world=World.isolatedForTest(60000L)){
            WorldPlayer alice=new WorldPlayer();
            WorldPlayer bob=new WorldPlayer();
            long ag=world.registerPlayer(alice,"g2119-alice");
            long bg=world.registerPlayer(bob,"g2119-bob");
            WorldMailboxGateway a=new WorldMailboxGateway(world,alice,ag);
            WorldMailboxGateway b=new WorldMailboxGateway(world,bob,bg);

            a.deliver(message("g2119:reward","Reward",true));
            a.deliver(message("g2119:notice","Notice",false));
            b.deliver(message("g2119:bob","Bob Only",false));

            LocalMailboxRootlessSession session=
                new LocalMailboxRootlessSession(world,alice,ag);
            ByteArrayOutputStream rootWire=new ByteArrayOutputStream();
            nativeRootAndRowWire=
                session.openNativeRoot(writer(rootWire))==2&&
                session.isNativeRootOpen()&&rootWire.size()>10&&
                firstOpcodeAndRoot(rootWire.toByteArray(),32019);

            ByteArrayOutputStream rowWire=new ByteArrayOutputStream();
            boolean selected=session.handleWidget(
                widget(185,32026),writer(rowWire)
            );
            exactSelectedDetailReadOrder=
                selected&&exactDetailThenRead(rowWire.toByteArray(),0)&&
                rowWire.size()>7;

            // Independent presentation built from the existing G21.13
            // primitives must exactly match SELECT, detail, then READ.
            WorldMailboxPresentationSession expected=
                a.openRootlessPresentation();
            expected.publishInbox(writer(new ByteArrayOutputStream()));
            ByteArrayOutputStream expectedWire=
                new ByteArrayOutputStream();
            ServerPacketWriter expectedWriter=writer(expectedWire);
            expected.publishTrustedRowDetail(0,expectedWriter);
            boolean replayChanged=expected.publishSelectedReadState(
                expectedWriter
            );
            exactSelectedDetailReadOrder &=
                Arrays.equals(
                    rowWire.toByteArray(),expectedWire.toByteArray()
                )&&!replayChanged;
            expected.close();

            PlayerSnapshot saved=PlayerSnapshotCodec.capture(
                "g2119-alice",alice
            );
            WorldPlayer restored=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(saved,restored);
            firstReadPersisted=
                alice.mailboxSnapshotKnown()&&
                alice.mailbox().unreadCount()==1&&
                alice.mailbox().get("g2119:reward").readState==
                    MailboxRewardDeliveryService.ReadState.READ&&
                restored.mailboxSnapshotKnown()&&
                restored.mailbox().unreadCount()==1&&
                restored.mailbox().get("g2119:reward").readState==
                    MailboxRewardDeliveryService.ReadState.READ&&
                restored.mailbox().get("g2119:notice").readState==
                    MailboxRewardDeliveryService.ReadState.UNREAD;

            ByteArrayOutputStream repeat=new ByteArrayOutputStream();
            replayIdempotent=
                session.handleWidget(
                    widget(185,32026),writer(repeat)
                )&&
                exactDetailThenRead(repeat.toByteArray(),0)&&
                alice.mailbox().unreadCount()==1;

            ByteArrayOutputStream outOfRange=new ByteArrayOutputStream();
            wrongRowZeroWire=
                rejects(()->session.handleWidget(
                    widget(185,32162),writer(outOfRange)
                ))&&outOfRange.size()==0&&
                alice.mailbox().unreadCount()==1;

            // Preflight failure must abort the *whole* SELECT/detail/READ
            // wire batch before changing unread state.
            WorldPlayer malformed=new WorldPlayer();
            long mg=world.registerPlayer(
                malformed,"g2119-malformed"
            );
            malformed.mailbox().deliver(new RewardDeliveryMessage(
                "g2119:bad","Malformed",
                "Unpublishable attachment",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(65535,1)
                ),
                "CUSTOM_LOCALLAB_G2119_BAD"
            ));
            LocalMailboxRootlessSession malformedScope=
                new LocalMailboxRootlessSession(world,malformed,mg);
            malformedScope.sync(writer(new ByteArrayOutputStream()));
            ByteArrayOutputStream malformedWire=
                new ByteArrayOutputStream();
            malformedDetailZeroWire=
                rejects(()->malformedScope.handleWidget(
                    widget(185,32026),writer(malformedWire)
                ))&&malformedWire.size()==0&&
                malformed.mailbox().unreadCount()==1&&
                !malformed.mailboxSnapshotKnown();
            malformedScope.close();

            foreignAccountIsolated=
                bob.mailbox().size()==1&&
                bob.mailbox().unreadCount()==1&&
                bob.mailbox().get("g2119:reward")==null;

            // A transport that rejects the final committed batch must
            // not undo the semantic READ of a previously unread row.
            final int[] writes={0};
            boolean failed=false;
            try{
                session.handleWidget(
                    widget(185,32030),
                    writer(new OutputStream(){
                        @Override public void write(int value)
                            throws IOException{
                            writes[0]++;
                            throw new IOException(
                                "G21.19 synthetic outbound failure"
                            );
                        }
                    })
                );
            }catch(IOException expectedFailure){
                failed=true;
            }
            transportFailurePersists=
                failed&&writes[0]>0&&
                alice.mailbox().unreadCount()==0&&
                alice.mailbox().get("g2119:notice").readState==
                    MailboxRewardDeliveryService.ReadState.READ;

            ByteArrayOutputStream actualRefresh=
                new ByteArrayOutputStream();
            ByteArrayOutputStream expectedRefresh=
                new ByteArrayOutputStream();
            session.handleWidget(
                widget(185,32185),writer(actualRefresh)
            );
            WorldMailboxPresentationSession compare=
                a.openRootlessPresentation();
            compare.publishInbox(writer(expectedRefresh));
            compare.close();
            refreshHealsRead=
                actualRefresh.size()>0&&
                Arrays.equals(
                    actualRefresh.toByteArray(),
                    expectedRefresh.toByteArray()
                )&&
                alice.mailbox().unreadCount()==0;

            // A bound immutable identity must not accept a replacement
            // with the same message ID. The rejected click sends no
            // packets and does not mark the new envelope READ.
            a.deleteSafe("g2119:notice");
            a.deliver(message(
                "g2119:notice","Replacement",false
            ));
            ByteArrayOutputStream recycled=
                new ByteArrayOutputStream();
            sameIdRecycledRejected=
                rejects(()->session.handleWidget(
                    widget(185,32030),writer(recycled)
                ))&&recycled.size()==0&&
                alice.mailbox().get("g2119:notice").readState==
                    MailboxRewardDeliveryService.ReadState.UNREAD;

            attachmentNotClaimed=
                alice.mailbox().get("g2119:reward").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                alice.mailbox().get("g2119:reward")
                    .message.attachments.get(0).amount==25;

            session.onInterfaceClose();
            ByteArrayOutputStream retired=new ByteArrayOutputStream();
            closedScopeFailClosed=
                !session.isActive()&&
                !session.handleWidget(
                    widget(185,32026),writer(retired)
                )&&retired.size()==0;

            session.sync(writer(new ByteArrayOutputStream()));
            boolean unregistered=world.unregisterPlayer(alice,ag);
            ByteArrayOutputStream stale=new ByteArrayOutputStream();
            staleGenerationDenied=
                unregistered&&
                rejects(()->session.handleWidget(
                    widget(185,32026),writer(stale)
                ))&&stale.size()==0;
            long ag2=world.registerPlayer(
                alice,"g2119-alice"
            );
            staleGenerationDenied &=
                ag2!=ag&&
                rejects(()->session.handleWidget(
                    widget(185,32026),writer(stale)
                ))&&stale.size()==0;

            session.close();
            noOriginalServerPolicyClaim=
                LocalMailboxRootlessSession.AUTHORITY.startsWith(
                    "CUSTOM_LOCALLAB"
                )&&
                LocalMailboxRootlessSession.NATIVE_V308_MAILBOX_ROOT==
                    32019&&
                !session.isActive();
        }

        System.out.println(
            "G2119_MAILBOX_NATIVE_READ_DIAGNOSTICS"+
            " nativeRootAndRowWire="+nativeRootAndRowWire+
            " exactSelectedDetailReadOrder="+
                exactSelectedDetailReadOrder+
            " firstReadPersisted="+firstReadPersisted+
            " replayIdempotent="+replayIdempotent+
            " wrongRowZeroWire="+wrongRowZeroWire+
            " malformedDetailZeroWire="+malformedDetailZeroWire+
            " foreignAccountIsolated="+foreignAccountIsolated+
            " transportFailurePersists="+transportFailurePersists+
            " refreshHealsRead="+refreshHealsRead+
            " sameIdRecycledRejected="+sameIdRecycledRejected+
            " closedScopeFailClosed="+closedScopeFailClosed+
            " staleGenerationDenied="+staleGenerationDenied+
            " attachmentNotClaimed="+attachmentNotClaimed+
            " noOriginalServerPolicyClaim="+noOriginalServerPolicyClaim
        );

        require(
            nativeRootAndRowWire&&exactSelectedDetailReadOrder&&
            firstReadPersisted&&replayIdempotent&&wrongRowZeroWire&&
            malformedDetailZeroWire&&foreignAccountIsolated&&
            transportFailurePersists&&refreshHealsRead&&
            sameIdRecycledRejected&&closedScopeFailClosed&&
            staleGenerationDenied&&attachmentNotClaimed&&
            noOriginalServerPolicyClaim,
            "G21.19 acceptance"
        );

        System.out.println(
            "G2119_MAILBOX_NATIVE_READ_PASS"+
            " exactC2S185RowView=true"+
            " exactS2C250ReadState=true"+
            " selectedDetailThenRead=true"+
            " semanticReadPersistent=true"+
            " idempotentReplay=true"+
            " fullBatchPreflightFailClosed=true"+
            " transportFailureNoSemanticRollback=true"+
            " refreshReconciliation=true"+
            " staleGenerationFenced=true"+
            " claimSettlement=false"+
            " originalServerReadTimingClaim=false"+
            " manualNativeRuntimeClaim=false"
        );
    }

    private static boolean firstOpcodeAndRoot(
        byte[] wire,int root
    ){
        if(wire.length<3)return false;
        IsaacCipher decode=new IsaacCipher(SEED.clone());
        return (((wire[0]&255)-decode.nextInt())&255)==97&&
            (wire[1]&255)==((root>>>8)&255)&&
            (wire[2]&255)==(root&255);
    }

    /** Decode all five exact packets, including the final op2 READ=1. */
    private static boolean exactDetailThenRead(
        byte[] wire,int row
    ){
        int[] types={250,53,126,250,250};
        IsaacCipher decode=new IsaacCipher(SEED.clone());
        int pos=0;
        for(int i=0;i<types.length;i++){
            if(pos>=wire.length)return false;
            int opcode=((wire[pos++]&255)-decode.nextInt())&255;
            if(opcode!=types[i])return false;
            int length;
            if(opcode==250){
                if(pos>=wire.length)return false;
                length=wire[pos++]&255;
            }else{
                if(pos+1>=wire.length)return false;
                length=((wire[pos++]&255)<<8)|
                    (wire[pos++]&255);
            }
            if(length<=0||pos+length>wire.length)
                return false;
            if(opcode==250){
                if(length<3||
                   (wire[pos]&255)!=0||
                   (wire[pos+1]&255)!=31)
                    return false;
                int command=wire[pos+2]&255;
                if(i==0&&
                   !(command==7&&length==4&&
                     (wire[pos+3]&255)==row))
                    return false;
                if(i==3&&!(command==4&&length==4))
                    return false;
                if(i==4&&!(
                    command==2&&length==5&&
                    (wire[pos+3]&255)==row&&
                    (wire[pos+4]&255)==1
                ))return false;
            }
            pos+=length;
        }
        return pos==wire.length;
    }

    private static WidgetActionClientRequest widget(
        int opcode,int widgetId
    ){
        return new WidgetActionClientRequest(
            widgetId,ClientRequestMetadata.exactCurrent(
                opcode,"FIXED2_WIDGET_U16_BE",
                "G2119_NATIVE_READ_TEST"
            )
        );
    }

    private static RewardDeliveryMessage message(
        String id,String subject,boolean items
    ){
        return new RewardDeliveryMessage(
            id,subject,"G21.19 fixture",
            items?Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,25)
            ):Collections.emptyList(),
            "CUSTOM_LOCALLAB_G2119_TEST"
        );
    }

    private static ServerPacketWriter writer(OutputStream out){
        return new ServerPacketWriter(
            out,new IsaacCipher(SEED.clone())
        );
    }

    private interface Action{
        Object run()throws Exception;
    }

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

    private static void require(boolean ok,String reason){
        if(!ok)throw new AssertionError(reason);
    }

    private G2119MailboxNativeReadIntegrationTest(){}
}
