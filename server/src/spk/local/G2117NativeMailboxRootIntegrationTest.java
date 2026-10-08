package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collections;

/**
 * Exact pinned-v308 interface root 32019, opened using exact S2C97.
 *
 * Original client root ID / opcode schema are exact. LocalLab owns the
 * choice to publish S2C97 before the subtype31 inbox state, not original
 * SpawnPK server sequencing or settlement policy.
 */
public final class G2117NativeMailboxRootIntegrationTest {
    private static final int[] SEED={2117,2118,2119,2120};
    private static final int ROOT=32019;

    public static void main(String[] args)throws Exception{
        boolean nativeRouteExact=false;
        boolean verifiedRootId=false;
        boolean emptyRootWire=false;
        boolean populatedRootWire=false;
        boolean clientRowAfterOpen=false;
        boolean activeRefreshAfterOpen=false;
        boolean invalidSubjectAtomic=false;
        boolean accountIsolated=false;
        boolean noRewardMutation=false;
        boolean staleGenerationAtomic=false;
        boolean closeRetires=false;
        boolean noServerPolicyClaim=false;

        nativeRouteExact=
            LocalCommandDispatcher.isNativeMailboxRootRoute(
                new String[]{"mail"}
            )&&
            LocalCommandDispatcher.isNativeMailboxRootRoute(
                new String[]{"MAIL"}
            )&&
            !LocalCommandDispatcher.isNativeMailboxRootRoute(
                new String[]{"mailbox"}
            )&&
            !LocalCommandDispatcher.isNativeMailboxRootRoute(
                new String[]{"mail","sync"}
            )&&
            !LocalCommandDispatcher.isNativeMailboxRootRoute(null)&&
            LocalCommandDispatcher.isMailboxRootlessSyncRoute(
                new String[]{"mail","sync"}
            );
        verifiedRootId=
            LocalMailboxRootlessSession.NATIVE_V308_MAILBOX_ROOT==ROOT&&
            Arrays.equals(
                BootstrapPackets.interface97(ROOT),
                new byte[]{(byte)0x7d,(byte)0x13}
            );

        try(World world=World.isolatedForTest(60000L)){
            WorldPlayer alice=new WorldPlayer();
            WorldPlayer bob=new WorldPlayer();
            WorldPlayer empty=new WorldPlayer();
            long ag=world.registerPlayer(alice,"g2117-alice");
            long bg=world.registerPlayer(bob,"g2117-bob");
            long eg=world.registerPlayer(empty,"g2117-empty");
            WorldMailboxGateway a=new WorldMailboxGateway(
                world,alice,ag
            );
            WorldMailboxGateway b=new WorldMailboxGateway(
                world,bob,bg
            );
            b.deliver(message(
                "g2117:bob","Bob private mail",false
            ));

            LocalMailboxRootlessSession nothing=
                new LocalMailboxRootlessSession(
                    world,empty,eg
                );
            ByteArrayOutputStream emptyWire=
                new ByteArrayOutputStream();
            int emptyRows=nothing.openNativeRoot(
                writer(emptyWire)
            );
            emptyRootWire=
                emptyRows==0&&nothing.isActive()&&
                exactRootAndInbox(emptyWire.toByteArray(),0);
            nothing.close();

            a.deliver(message(
                "g2117:reward","Message reward",true
            ));
            a.deliver(message(
                "g2117:notice","Notice",false
            ));
            LocalMailboxRootlessSession nativeView=
                new LocalMailboxRootlessSession(
                    world,alice,ag
                );

            ByteArrayOutputStream actual=
                new ByteArrayOutputStream();
            int published=nativeView.openNativeRoot(
                writer(actual)
            );

            ByteArrayOutputStream independent=
                new ByteArrayOutputStream();
            ServerPacketWriter independentWriter=writer(independent);
            independentWriter.fixed(
                97,BootstrapPackets.interface97(ROOT)
            );
            WorldMailboxPresentationSession baseline=
                a.openRootlessPresentation();
            baseline.publishInbox(independentWriter);
            populatedRootWire=
                published==2&&nativeView.isActive()&&
                exactRootAndInbox(actual.toByteArray(),2)&&
                Arrays.equals(
                    actual.toByteArray(),independent.toByteArray()
                );
            baseline.close();

            ByteArrayOutputStream rowWire=
                new ByteArrayOutputStream();
            ByteArrayOutputStream expectedRow=
                new ByteArrayOutputStream();
            clientRowAfterOpen=
                nativeView.handleWidget(
                    widget(185,32026),writer(rowWire)
                );
            WorldMailboxPresentationSession rowView=
                a.openRootlessPresentation();
            rowView.publishInbox(
                writer(new ByteArrayOutputStream())
            );
            ServerPacketWriter expectedRowWriter=
                writer(expectedRow);
            rowView.publishTrustedRowDetail(
                0,expectedRowWriter
            );
            rowView.publishSelectedReadState(
                expectedRowWriter
            );
            clientRowAfterOpen &=
                rowWire.size()>0&&
                Arrays.equals(
                    rowWire.toByteArray(),
                    expectedRow.toByteArray()
                );
            rowView.close();

            ByteArrayOutputStream refreshed=
                new ByteArrayOutputStream();
            ByteArrayOutputStream refreshExpected=
                new ByteArrayOutputStream();
            activeRefreshAfterOpen=
                nativeView.handleWidget(
                    widget(185,32185),writer(refreshed)
                );
            WorldMailboxPresentationSession refreshView=
                a.openRootlessPresentation();
            refreshView.publishInbox(writer(refreshExpected));
            activeRefreshAfterOpen &=
                refreshed.size()>0&&
                Arrays.equals(
                    refreshed.toByteArray(),
                    refreshExpected.toByteArray()
                );
            refreshView.close();

            WorldPlayer invalid=new WorldPlayer();
            long ig=world.registerPlayer(
                invalid,"g2117-invalid"
            );
            invalid.mailbox().deliver(message(
                "g2117:bad","Bad\nsubject",false
            ));
            LocalMailboxRootlessSession malformed=
                new LocalMailboxRootlessSession(
                    world,invalid,ig
                );
            ByteArrayOutputStream badWire=
                new ByteArrayOutputStream();
            invalidSubjectAtomic=
                rejects(()->malformed.openNativeRoot(
                    writer(badWire)
                ))&&badWire.size()==0&&
                !malformed.isActive()&&
                invalid.mailbox().size()==1;
            malformed.close();

            accountIsolated=
                alice.mailbox().size()==2&&
                alice.mailbox().get("g2117:bob")==null&&
                bob.mailbox().size()==1&&
                bob.mailbox().get("g2117:reward")==null;

            noRewardMutation=
                alice.mailbox().unreadCount()==1&&
                alice.mailbox().get("g2117:reward").readState==
                    MailboxRewardDeliveryService.ReadState.READ&&
                bob.mailbox().unreadCount()==1&&
                alice.mailbox().get("g2117:reward").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                alice.mailbox().get("g2117:reward")
                    .message.attachments.get(0).itemId==995;

            nativeView.onInterfaceClose();
            ByteArrayOutputStream afterClose=
                new ByteArrayOutputStream();
            closeRetires=
                !nativeView.isActive()&&
                !nativeView.handleWidget(
                    widget(185,32026),writer(afterClose)
                )&&afterClose.size()==0;

            int again=nativeView.openNativeRoot(
                writer(new ByteArrayOutputStream())
            );
            closeRetires &=again==2&&nativeView.isActive();

            boolean retired=world.unregisterPlayer(alice,ag);
            ByteArrayOutputStream staleBytes=
                new ByteArrayOutputStream();
            staleGenerationAtomic=
                retired&&
                rejects(()->nativeView.openNativeRoot(
                    writer(staleBytes)
                ))&&staleBytes.size()==0;
            long ag2=world.registerPlayer(
                alice,"g2117-alice"
            );
            staleGenerationAtomic &=
                ag2!=ag&&
                rejects(()->nativeView.openNativeRoot(
                    writer(staleBytes)
                ))&&staleBytes.size()==0;

            nativeView.close();
            noServerPolicyClaim=
                LocalMailboxRootlessSession.AUTHORITY.startsWith(
                    "CUSTOM_LOCALLAB"
                )&&
                noRewardMutation&&
                !nativeView.isActive()&&
                LocalCommandDispatcher.SessionBridge.class
                    .getDeclaredMethod(
                        "openMailboxNative",
                        ServerPacketWriter.class
                    )!=null;
        }

        System.out.println(
            "G2117_NATIVE_MAILBOX_ROOT_DIAGNOSTICS"+
            " nativeRouteExact="+nativeRouteExact+
            " verifiedRootId="+verifiedRootId+
            " emptyRootWire="+emptyRootWire+
            " populatedRootWire="+populatedRootWire+
            " clientRowAfterOpen="+clientRowAfterOpen+
            " activeRefreshAfterOpen="+activeRefreshAfterOpen+
            " invalidSubjectAtomic="+invalidSubjectAtomic+
            " accountIsolated="+accountIsolated+
            " noRewardMutation="+noRewardMutation+
            " staleGenerationAtomic="+staleGenerationAtomic+
            " closeRetires="+closeRetires+
            " noServerPolicyClaim="+noServerPolicyClaim
        );

        require(
            nativeRouteExact&&verifiedRootId&&emptyRootWire&&
            populatedRootWire&&clientRowAfterOpen&&
            activeRefreshAfterOpen&&invalidSubjectAtomic&&
            accountIsolated&&noRewardMutation&&
            staleGenerationAtomic&&closeRetires&&
            noServerPolicyClaim,
            "G21.17 acceptance"
        );

        System.out.println(
            "G2117_NATIVE_MAILBOX_ROOT_PASS"+
            " root32019ExactV308=true"+
            " s2c97Exact=true"+
            " rootThenInboxCustomLocalLab=true"+
            " command103NativeMail=true"+
            " nativeRowC2S185Continues=true"+
            " invalidProjectionAtomic=true"+
            " noOriginalServerOrderClaim=true"+
            " noManualRuntimeProof=true"+
            " rewardSettlementClaim=false"
        );
    }

    private static boolean exactRootAndInbox(
        byte[] bytes,int expectedRows
    ){
        IsaacCipher decode=new IsaacCipher(SEED.clone());
        int position=0;
        if(bytes.length<6)
            return false;
        int opcode=((bytes[position++]&255)-decode.nextInt())&255;
        if(opcode!=97||
           (bytes[position++]&255)!=0x7d||
           (bytes[position++]&255)!=0x13)
            return false;

        int appendCount=0;
        boolean clear=false;
        boolean finalized=false;

        while(position<bytes.length){
            int op=((bytes[position++]&255)-decode.nextInt())&255;
            if(op!=250||position>=bytes.length)
                return false;
            int length=bytes[position++]&255;
            if(length<3||position+length>bytes.length)
                return false;
            int subtype=((bytes[position++]&255)<<8)|
                (bytes[position++]&255);
            if(subtype!=31)return false;
            int operation=bytes[position++]&255;
            if(operation==0){
                if(clear||appendCount!=0||finalized||length!=3)
                    return false;
                clear=true;
            }else if(operation==1){
                if(!clear||finalized||length<6)
                    return false;
                appendCount++;
            }else if(operation==5){
                if(!clear||finalized||length!=3)
                    return false;
                finalized=true;
            }else{
                return false;
            }
            position+=length-3;
        }
        return clear&&finalized&&appendCount==expectedRows;
    }

    private static WidgetActionClientRequest widget(
        int opcode,int widgetId
    ){
        return new WidgetActionClientRequest(
            widgetId,ClientRequestMetadata.exactCurrent(
                opcode,"FIXED2_WIDGET_U16_BE",
                "G2117_PINNED_CLIENT"
            )
        );
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream out
    ){
        return new ServerPacketWriter(
            out,new IsaacCipher(SEED.clone())
        );
    }

    private static RewardDeliveryMessage message(
        String id,String subject,boolean withItems
    ){
        return new RewardDeliveryMessage(
            id,subject,"G21.17 native UI root",
            withItems?Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,50)
            ):Collections.emptyList(),
            "CUSTOM_LOCALLAB_G2117_FIXTURE"
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

    private static void require(boolean value,String label){
        if(!value)throw new AssertionError(label);
    }

    private G2117NativeMailboxRootIntegrationTest(){}
}
