package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collections;

/**
 * Permanent proof of the recovered native-v308 Mailbox row widgets.
 * C2S185 fixed u16_be: 32026+(4*i) for rows i=0..34.
 * No inferred client root, live LocalSession route or reward settlement.
 */
public final class G2115MailboxRowWidgetIntegrationTest {
    private static final int[] SEED={2115,2116,2117,2118};

    public static void main(String[] args)throws Exception{
        boolean all35Exact=false;
        boolean gapsAndControlsPassThrough=false;
        boolean wrongOpcodeFailClosed=false;
        boolean noBoundViewFailClosed=false;
        boolean row0WireExact=false;
        boolean row34WireExact=false;
        boolean invalidAttachmentNoWire=false;
        boolean staleSameIdRejected=false;
        boolean accountIsolation=false;
        boolean noDomainMutation=false;
        boolean staleGenerationRejected=false;
        boolean closedScopeRejected=false;
        boolean noRootOrSettlement=false;

        all35Exact=
            MailboxRowWidgetIntentAdapter.FIRST_ROW_WIDGET==32026&&
            MailboxRowWidgetIntentAdapter.ROW_WIDGET_STRIDE==4&&
            MailboxRowWidgetIntentAdapter.VISIBLE_ROWS==35&&
            MailboxRowWidgetIntentAdapter.LAST_ROW_WIDGET==32162;

        for(int i=0;i<35;i++){
            int widgetId=32026+4*i;
            all35Exact &=
                MailboxRowWidgetIntentAdapter.resolveIfRow(
                    widget(185,widgetId)
                )==i&&
                MailboxRowWidgetIntentAdapter.requireRow(
                    widget(185,widgetId)
                )==i;
        }

        int[] unrelated={
            32025,32027,32028,32029,
            32159,32161,32163,32164,
            32168,32175,32178,32181,32184,32185
        };
        gapsAndControlsPassThrough=true;
        for(int id:unrelated){
            gapsAndControlsPassThrough &=
                MailboxRowWidgetIntentAdapter.resolveIfRow(
                    widget(185,id)
                )==-1&&
                rejects(()->MailboxRowWidgetIntentAdapter.requireRow(
                    widget(185,id)
                ));
        }

        wrongOpcodeFailClosed=
            rejects(()->MailboxRowWidgetIntentAdapter.requireRow(
                widget(184,32026)
            ))&&
            MailboxRowWidgetIntentAdapter.resolveIfRow(
                widget(184,32185)
            )==-1&&
            ClientRequestProvenance.values().length==1;

        try(World world=World.isolatedForTest(60000L)){
            WorldPlayer alice=new WorldPlayer();
            WorldPlayer bob=new WorldPlayer();
            long ag=world.registerPlayer(alice,"g2115-alice");
            long bg=world.registerPlayer(bob,"g2115-bob");
            WorldMailboxGateway a=new WorldMailboxGateway(
                world,alice,ag
            );
            WorldMailboxGateway b=new WorldMailboxGateway(
                world,bob,bg
            );

            for(int i=0;i<35;i++){
                a.deliver(message(
                    "g2115:row:"+i,"Message "+i,
                    i==0?Collections.singletonList(
                        new RewardDeliveryMessage.Attachment(995,5)
                    ):Collections.emptyList()
                ));
            }
            b.deliver(message(
                "g2115:bob","Private Bob",
                Collections.emptyList()
            ));

            WorldMailboxPresentationSession view=
                a.openRootlessPresentation();
            ByteArrayOutputStream unbound=
                new ByteArrayOutputStream();
            noBoundViewFailClosed=
                rejects(()->view.publishRowDetailFromWidget(
                    widget(185,32026),writer(unbound)
                ))&&unbound.size()==0;

            view.publishInbox(writer(new ByteArrayOutputStream()));
            WorldMailboxPresentationSession reference=
                a.openRootlessPresentation();
            reference.publishInbox(
                writer(new ByteArrayOutputStream())
            );

            ByteArrayOutputStream row0=new ByteArrayOutputStream();
            ByteArrayOutputStream ref0=new ByteArrayOutputStream();
            MailboxRewardDeliveryService.Snapshot first=
                view.publishRowDetailFromWidget(
                    widget(185,32026),writer(row0)
                );
            reference.publishTrustedRowDetail(
                0,writer(ref0)
            );
            row0WireExact=
                "g2115:row:0".equals(first.message.messageId)&&
                Arrays.equals(
                    row0.toByteArray(),ref0.toByteArray()
                )&&
                decodedSelect(row0.toByteArray(),0);

            ByteArrayOutputStream row34=new ByteArrayOutputStream();
            ByteArrayOutputStream ref34=new ByteArrayOutputStream();
            MailboxRewardDeliveryService.Snapshot last=
                view.publishRowDetailFromWidget(
                    widget(185,32162),writer(row34)
                );
            reference.publishTrustedRowDetail(
                34,writer(ref34)
            );
            row34WireExact=
                "g2115:row:34".equals(last.message.messageId)&&
                Arrays.equals(
                    row34.toByteArray(),ref34.toByteArray()
                )&&
                decodedSelect(row34.toByteArray(),34);
            reference.close();

            ByteArrayOutputStream wrong=new ByteArrayOutputStream();
            wrongOpcodeFailClosed &=
                rejects(()->view.publishRowDetailFromWidget(
                    widget(184,32026),writer(wrong)
                ))&&wrong.size()==0;

            ByteArrayOutputStream invalidRow=new ByteArrayOutputStream();
            gapsAndControlsPassThrough &=
                rejects(()->view.publishRowDetailFromWidget(
                    widget(185,32178),writer(invalidRow)
                ))&&invalidRow.size()==0;

            // An attachment outside the exact S2C53 supported range is
            // injected only in this fixture, bypassing G21.9 delivery
            // preflight, so detail publication must reject before output.
            WorldPlayer malformed=new WorldPlayer();
            long badGeneration=world.registerPlayer(
                malformed,"g2115-bad-item"
            );
            malformed.mailbox().deliver(message(
                "g2115:invalid","Invalid item",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(
                        65535,1
                    )
                )
            ));
            WorldMailboxPresentationSession badView=
                new WorldMailboxGateway(
                    world,malformed,badGeneration
                ).openRootlessPresentation();
            badView.publishInbox(
                writer(new ByteArrayOutputStream())
            );
            ByteArrayOutputStream badOut=new ByteArrayOutputStream();
            invalidAttachmentNoWire=
                rejects(()->badView.publishRowDetailFromWidget(
                    widget(185,32026),writer(badOut)
                ))&&badOut.size()==0&&
                malformed.mailbox().size()==1;
            badView.close();

            // Message IDs alone cannot authenticate a bound row:
            // retire row 1 and redeliver under the same ID.
            a.deleteSafe("g2115:row:1");
            a.deliver(message(
                "g2115:row:1","Replacement row one",
                Collections.emptyList()
            ));
            ByteArrayOutputStream recycled=
                new ByteArrayOutputStream();
            staleSameIdRejected=
                rejects(()->view.publishRowDetailFromWidget(
                    widget(185,32030),writer(recycled)
                ))&&recycled.size()==0&&
                "Replacement row one".equals(
                    alice.mailbox().get("g2115:row:1")
                        .message.subject
                );

            accountIsolation=
                bob.mailbox().size()==1&&
                bob.mailbox().get("g2115:row:0")==null&&
                alice.mailbox().get("g2115:bob")==null;

            noDomainMutation=
                alice.mailbox().size()==35&&
                alice.mailbox().unreadCount()==35&&
                alice.mailbox().get("g2115:row:0").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                alice.mailbox().get("g2115:row:0")
                    .message.attachments.get(0).amount==5&&
                bob.mailbox().unreadCount()==1;

            boolean retired=world.unregisterPlayer(alice,ag);
            ByteArrayOutputStream stale=new ByteArrayOutputStream();
            staleGenerationRejected=
                retired&&rejects(
                    ()->view.publishRowDetailFromWidget(
                        widget(185,32162),writer(stale)
                    )
                )&&stale.size()==0;

            long ag2=world.registerPlayer(
                alice,"g2115-alice"
            );
            staleGenerationRejected &=
                ag2!=ag&&rejects(
                    ()->view.publishRowDetailFromWidget(
                        widget(185,32162),writer(stale)
                    )
                )&&stale.size()==0;

            view.close();
            ByteArrayOutputStream closed=new ByteArrayOutputStream();
            closedScopeRejected=
                rejects(()->view.publishRowDetailFromWidget(
                    widget(185,32026),writer(closed)
                ))&&closed.size()==0;

            noRootOrSettlement=
                Arrays.stream(
                    WorldMailboxPresentationSession.class
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
                alice.mailbox().get("g2115:row:0").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
        }

        System.out.println(
            "G2115_MAILBOX_ROW_WIDGET_DIAGNOSTICS"+
            " all35Exact="+all35Exact+
            " gapsAndControlsPassThrough="+gapsAndControlsPassThrough+
            " wrongOpcodeFailClosed="+wrongOpcodeFailClosed+
            " noBoundViewFailClosed="+noBoundViewFailClosed+
            " row0WireExact="+row0WireExact+
            " row34WireExact="+row34WireExact+
            " invalidAttachmentNoWire="+invalidAttachmentNoWire+
            " staleSameIdRejected="+staleSameIdRejected+
            " accountIsolation="+accountIsolation+
            " noDomainMutation="+noDomainMutation+
            " staleGenerationRejected="+staleGenerationRejected+
            " closedScopeRejected="+closedScopeRejected+
            " noRootOrSettlement="+noRootOrSettlement
        );

        require(
            all35Exact&&gapsAndControlsPassThrough&&
            wrongOpcodeFailClosed&&noBoundViewFailClosed&&
            row0WireExact&&row34WireExact&&
            invalidAttachmentNoWire&&staleSameIdRejected&&
            accountIsolation&&noDomainMutation&&
            staleGenerationRejected&&closedScopeRejected&&
            noRootOrSettlement,
            "G21.15 acceptance"
        );

        System.out.println(
            "G2115_MAILBOX_ROW_WIDGET_PASS"+
            " rowWidgets35=true"+
            " exactC2S185=true"+
            " typedRow0And34Wire=true"+
            " unboundRejected=true"+
            " invalidMetadataRejected=true"+
            " malformedDetailNoWire=true"+
            " staleSameIdDenied=true"+
            " worldGenerationFenced=true"+
            " noMailboxRootClaim=true"+
            " noLiveSocketRouteClaim=true"+
            " rewardSettlementClaim=false"
        );
    }

    private static boolean decodedSelect(byte[] wire,int row){
        if(wire.length<6)
            return false;
        IsaacCipher cipher=new IsaacCipher(SEED.clone());
        int op=((wire[0]&255)-cipher.nextInt())&255;
        int size=wire[1]&255;
        int subtype=((wire[2]&255)<<8)|(wire[3]&255);
        return op==250&&size==4&&subtype==31&&
            (wire[4]&255)==7&&(wire[5]&255)==row;
    }

    private static WidgetActionClientRequest widget(
        int opcode,int widgetId
    ){
        return new WidgetActionClientRequest(
            widgetId,ClientRequestMetadata.exactCurrent(
                opcode,"FIXED2_WIDGET_U16_BE",
                "G2115_PINNED_V308_FIXTURE"
            )
        );
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream stream
    ){
        return new ServerPacketWriter(
            stream,new IsaacCipher(SEED.clone())
        );
    }

    private static RewardDeliveryMessage message(
        String id,String subject,
        java.util.List<RewardDeliveryMessage.Attachment> items
    ){
        return new RewardDeliveryMessage(
            id,subject,"G21.15 row-click fixture",
            items,"CUSTOM_LOCALLAB_G2115_FIXTURE"
        );
    }

    private interface Action {
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
            throw new IllegalStateException(
                "unexpected checked exception",unexpected
            );
        }
    }

    private static void require(boolean yes,String reason){
        if(!yes)throw new AssertionError(reason);
    }

    private G2115MailboxRowWidgetIntegrationTest(){}
}
