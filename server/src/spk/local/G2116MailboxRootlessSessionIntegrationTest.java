package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collections;

/**
 * G21.16: real LocalSession-bridge contract and exact wire using the World
 * owner. Intentionally no claim of opening original native Mailbox root.
 */
public final class G2116MailboxRootlessSessionIntegrationTest {
    private static final int[] SEED={2116,2117,2118,2119};

    public static void main(String[] args)throws Exception{
        boolean syncCommandExact=false;
        boolean inactiveWidgetsPassThrough=false;
        boolean rootlessSyncWire=false;
        boolean typedRowWire=false;
        boolean refreshRebinds=false;
        boolean controlsSuppressed=false;
        boolean foreignAccountIsolated=false;
        boolean staleSameIdRejected=false;
        boolean closeRetires=false;
        boolean generationFence=false;
        boolean terminalClose=false;
        boolean bridgeApiWired=false;
        boolean noRootOrSettlement=false;

        syncCommandExact=
            LocalCommandDispatcher.isMailboxRootlessSyncRoute(
                new String[]{"mail","sync"}
            )&&
            LocalCommandDispatcher.isMailboxRootlessSyncRoute(
                new String[]{"MAILBOX","SYNC"}
            )&&
            !LocalCommandDispatcher.isMailboxRootlessSyncRoute(
                new String[]{"mail"}
            )&&
            !LocalCommandDispatcher.isMailboxRootlessSyncRoute(
                new String[]{"mailbox","claim"}
            )&&
            !LocalCommandDispatcher.isMailboxRootlessSyncRoute(
                new String[]{"other","sync"}
            )&&
            !LocalCommandDispatcher.isMailboxRootlessSyncRoute(null)&&
            !LocalCommandDispatcher.isMailboxRootlessSyncRoute(
                new String[]{"mail","sync","extra"}
            );

        try(World world=World.isolatedForTest(60000L)){
            WorldPlayer alice=new WorldPlayer();
            WorldPlayer bob=new WorldPlayer();
            long ag=world.registerPlayer(alice,"g2116-alice");
            long bg=world.registerPlayer(bob,"g2116-bob");

            WorldMailboxGateway a=new WorldMailboxGateway(
                world,alice,ag
            );
            WorldMailboxGateway b=new WorldMailboxGateway(
                world,bob,bg
            );
            a.deliver(message("g2116:a","Mail A",true));
            a.deliver(message("g2116:b","Mail B",false));
            b.deliver(message("g2116:bob","Private Bob",false));

            LocalMailboxRootlessSession session=
                new LocalMailboxRootlessSession(
                    world,alice,ag
                );

            ByteArrayOutputStream notBound=new ByteArrayOutputStream();
            inactiveWidgetsPassThrough=
                !session.isActive()&&
                !session.handleWidget(
                    widget(185,32026),writer(notBound)
                )&&
                !session.handleWidget(
                    widget(185,32185),writer(notBound)
                )&&notBound.size()==0;

            ByteArrayOutputStream syncWire=
                new ByteArrayOutputStream();
            int count=session.sync(writer(syncWire));
            ByteArrayOutputStream reference=
                new ByteArrayOutputStream();
            WorldMailboxPresentationSession baseline=
                a.openRootlessPresentation();
            baseline.publishInbox(writer(reference));
            rootlessSyncWire=
                count==2&&session.isActive()&&
                Arrays.equals(
                    syncWire.toByteArray(),reference.toByteArray()
                );

            ByteArrayOutputStream rowWire=
                new ByteArrayOutputStream();
            ByteArrayOutputStream rowReference=
                new ByteArrayOutputStream();
            boolean selected=session.handleWidget(
                widget(185,32026),writer(rowWire)
            );
            ServerPacketWriter expectedRowWriter=
                writer(rowReference);
            baseline.publishTrustedRowDetail(
                0,expectedRowWriter
            );
            baseline.publishSelectedReadState(
                expectedRowWriter
            );
            typedRowWire=
                selected&&
                rowWire.size()>0&&
                Arrays.equals(
                    rowWire.toByteArray(),
                    rowReference.toByteArray()
                );
            baseline.close();

            ByteArrayOutputStream controlsWire=
                new ByteArrayOutputStream();
            controlsSuppressed=
                session.handleWidget(
                    widget(185,32178),writer(controlsWire)
                )&&
                session.handleWidget(
                    widget(185,32181),writer(controlsWire)
                )&&
                rejects(()->session.handleWidget(
                    widget(185,32184),writer(controlsWire)
                ))&&
                controlsWire.size()==0&&
                alice.mailbox().get("g2116:a").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                alice.mailbox().size()==2;

            ByteArrayOutputStream badMetadata=
                new ByteArrayOutputStream();
            controlsSuppressed &=
                rejects(()->session.handleWidget(
                    widget(184,32178),writer(badMetadata)
                ))&&badMetadata.size()==0;

            ByteArrayOutputStream ignored=
                new ByteArrayOutputStream();
            inactiveWidgetsPassThrough &=
                !session.handleWidget(
                    widget(185,5500),writer(ignored)
                )&&ignored.size()==0;

            a.deleteSafe("g2116:b");
            a.deliver(message(
                "g2116:b","Replacement B",false
            ));
            ByteArrayOutputStream staleOut=
                new ByteArrayOutputStream();
            staleSameIdRejected=
                rejects(()->session.handleWidget(
                    widget(185,32030),writer(staleOut)
                ))&&staleOut.size()==0&&
                alice.mailbox().get("g2116:b").readState==
                    MailboxRewardDeliveryService.ReadState.UNREAD;

            ByteArrayOutputStream refreshed=
                new ByteArrayOutputStream();
            ByteArrayOutputStream expectedRefresh=
                new ByteArrayOutputStream();
            boolean refreshedHandled=session.handleWidget(
                widget(185,32185),writer(refreshed)
            );
            WorldMailboxPresentationSession fresh=
                a.openRootlessPresentation();
            fresh.publishInbox(writer(expectedRefresh));
            refreshRebinds=
                refreshedHandled&&refreshed.size()>0&&
                Arrays.equals(
                    refreshed.toByteArray(),
                    expectedRefresh.toByteArray()
                );
            fresh.close();

            foreignAccountIsolated=
                bob.mailbox().size()==1&&
                bob.mailbox().get("g2116:a")==null&&
                alice.mailbox().get("g2116:bob")==null&&
                bob.mailbox().unreadCount()==1&&
                alice.mailbox().unreadCount()==1&&
                alice.mailbox().get("g2116:a").readState==
                    MailboxRewardDeliveryService.ReadState.READ;

            session.onInterfaceClose();
            ByteArrayOutputStream afterInterfaceClose=
                new ByteArrayOutputStream();
            closeRetires=
                !session.isActive()&&
                !session.handleWidget(
                    widget(185,32026),writer(afterInterfaceClose)
                )&&afterInterfaceClose.size()==0;

            ByteArrayOutputStream secondSync=
                new ByteArrayOutputStream();
            closeRetires &=
                session.sync(writer(secondSync))==2&&
                session.isActive()&&secondSync.size()>0;

            boolean retired=world.unregisterPlayer(alice,ag);
            ByteArrayOutputStream genOut=new ByteArrayOutputStream();
            generationFence=retired&&
                rejects(()->session.handleWidget(
                    widget(185,32026),writer(genOut)
                ))&&genOut.size()==0&&
                rejects(()->session.sync(writer(genOut)))&&
                genOut.size()==0;
            long ag2=world.registerPlayer(
                alice,"g2116-alice"
            );
            generationFence &=
                ag2!=ag&&
                rejects(()->session.sync(writer(genOut)))&&
                genOut.size()==0;

            session.close();
            ByteArrayOutputStream finalOut=
                new ByteArrayOutputStream();
            terminalClose=
                !session.isActive()&&
                !session.handleWidget(
                    widget(185,32026),writer(finalOut)
                )&&finalOut.size()==0&&
                rejects(()->session.sync(writer(finalOut)));

            bridgeApiWired=
                LocalCommandDispatcher.SessionBridge.class
                    .getDeclaredMethod(
                        "syncMailboxRootless",
                        ServerPacketWriter.class
                    )!=null&&
                LocalPendingRequestDispatcher.SessionBridge.class
                    .getDeclaredMethod(
                        "handleMailboxWidgetAction",
                        WidgetActionClientRequest.class,
                        ServerPacketWriter.class,
                        String.class
                    )!=null&&
                LocalPendingRequestDispatcher.SessionBridge.class
                    .getDeclaredMethod(
                        "onMailboxInterfaceClose"
                    )!=null;

            noRootOrSettlement=
                Arrays.stream(
                    LocalMailboxRootlessSession.class
                        .getDeclaredMethods()
                ).noneMatch(method->
                    method.getName().toLowerCase()
                        .contains("settle")||
                    Arrays.stream(method.getParameterTypes())
                        .anyMatch(type->
                            type==BankState.class||
                            type==LocalSession.class
                        )
                )&&
                alice.mailbox().get("g2116:a").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
        }

        System.out.println(
            "G2116_MAILBOX_ROOTLESS_SESSION_DIAGNOSTICS"+
            " syncCommandExact="+syncCommandExact+
            " inactiveWidgetsPassThrough="+inactiveWidgetsPassThrough+
            " rootlessSyncWire="+rootlessSyncWire+
            " typedRowWire="+typedRowWire+
            " refreshRebinds="+refreshRebinds+
            " controlsSuppressed="+controlsSuppressed+
            " foreignAccountIsolated="+foreignAccountIsolated+
            " staleSameIdRejected="+staleSameIdRejected+
            " closeRetires="+closeRetires+
            " generationFence="+generationFence+
            " terminalClose="+terminalClose+
            " bridgeApiWired="+bridgeApiWired+
            " noRootOrSettlement="+noRootOrSettlement
        );

        require(
            syncCommandExact&&inactiveWidgetsPassThrough&&
            rootlessSyncWire&&typedRowWire&&refreshRebinds&&
            controlsSuppressed&&foreignAccountIsolated&&
            staleSameIdRejected&&closeRetires&&
            generationFence&&terminalClose&&
            bridgeApiWired&&noRootOrSettlement,
            "G21.16 acceptance"
        );

        System.out.println(
            "G2116_MAILBOX_ROOTLESS_SESSION_PASS"+
            " exactC2S103OptIn=true"+
            " sourceWiredSessionBridge=true"+
            " exactC2S185RowAndRefresh=true"+
            " rootlessInboxWire=true"+
            " unownedControlsFailClosed=true"+
            " interfaceCloseRetires=true"+
            " logoutGenerationFenced=true"+
            " noOriginalRootClaim=true"+
            " noRuntimeManualAcceptanceClaim=true"+
            " rewardSettlementClaim=false"
        );
    }

    private static WidgetActionClientRequest widget(
        int opcode,int widget
    ){
        return new WidgetActionClientRequest(
            widget,ClientRequestMetadata.exactCurrent(
                opcode,"FIXED2_WIDGET_U16_BE",
                "G2116_EXACT_V308_FIXTURE"
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
        String id,String subject,boolean attached
    ){
        return new RewardDeliveryMessage(
            id,subject,"G21.16 rootless fixture",
            attached?Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,50)
            ):Collections.emptyList(),
            "CUSTOM_LOCALLAB_G2116_FIXTURE"
        );
    }

    private interface Action {
        Object run()throws Exception;
    }

    private static boolean rejects(Action action){
        try{
            action.run();
            return false;
        }catch(IllegalStateException|
                IllegalArgumentException expected){
            return true;
        }catch(Exception failure){
            throw new IllegalStateException(failure);
        }
    }

    private static void require(boolean ok,String name){
        if(!ok)throw new AssertionError(name);
    }

    private G2116MailboxRootlessSessionIntegrationTest(){}
}
