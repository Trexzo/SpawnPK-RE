package spk.local;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

/**
 * Exact C2S185 refresh semantic boundary; no live socket route or root.
 */
public final class G2111MailboxRefreshWidgetIntegrationTest {
    private static final int[] SEED={2111,2112,2113,2114};

    public static void main(String[] args)throws Exception{
        boolean exact185Refresh=false;
        boolean staleSelectionHealed=false;
        boolean stableSelectedActionStillRejected=false;
        boolean reboundClearsSelection=false;
        boolean wrongOpcodeFailClosed=false;
        boolean wrongWidgetFailClosed=false;
        boolean badSubjectFailClosed=false;
        boolean foreignAccountIsolated=false;
        boolean generationRolloverRejected=false;
        boolean closedScopeRejected=false;
        boolean noDomainMutation=false;
        boolean noRootSettlementOrLiveRoute=false;

        try(World world=World.isolatedForTest(60000L)){
            WorldPlayer alice=new WorldPlayer();
            WorldPlayer bob=new WorldPlayer();
            long ag=world.registerPlayer(alice,"g2111-alice");
            long bg=world.registerPlayer(bob,"g2111-bob");
            WorldMailboxGateway a=
                new WorldMailboxGateway(world,alice,ag);
            WorldMailboxGateway b=
                new WorldMailboxGateway(world,bob,bg);

            a.deliver(message("g2111:old","Old mail"));
            a.deliver(message("g2111:keeper","Keeper"));
            b.deliver(message("g2111:bob","Private to Bob"));

            WorldMailboxPresentationSession session=
                a.openRootlessPresentation();

            session.publishInbox(
                writer(new ByteArrayOutputStream())
            );
            session.publishTrustedRowDetail(
                0,writer(new ByteArrayOutputStream())
            );

            MailboxSessionSelection raw=a.openReadOnlyView();
            raw.bindInbox();
            raw.selectBoundRow(0);

            // Retire and replace with same ID but distinct immutable
            // message; all non-refresh actions must reject the stale view.
            a.deleteSafe("g2111:old");
            a.deliver(message(
                "g2111:old","Replacement"
            ));

            stableSelectedActionStillRejected=
                rejects(()->raw.resolve(
                    widget(185,32184)
                ))&&
                rejects(()->session.publishTrustedRowDetail(
                    0,writer(new ByteArrayOutputStream())
                ));

            MailboxWidgetIntentAdapter.Intent refresh=
                raw.resolve(widget(185,32185));

            staleSelectionHealed=
                refresh.kind==
                    MailboxWidgetIntentAdapter.Kind.REFRESH_INBOX&&
                refresh.messageId==null&&
                raw.bindInbox().size()==2&&
                raw.selectedMessageId()==null&&
                rejects(()->raw.resolve(widget(185,32184)));

            reboundClearsSelection=staleSelectionHealed;
            raw.close();

            ByteArrayOutputStream wrongOpcode=new ByteArrayOutputStream();
            wrongOpcodeFailClosed=
                rejects(()->session.publishRefreshFromWidget(
                    widget(184,32185),
                    writer(wrongOpcode)
                ))&&wrongOpcode.size()==0;

            ByteArrayOutputStream wrongWidget=new ByteArrayOutputStream();
            wrongWidgetFailClosed=
                rejects(()->session.publishRefreshFromWidget(
                    widget(185,32184),
                    writer(wrongWidget)
                ))&&wrongWidget.size()==0;

            ByteArrayOutputStream out=new ByteArrayOutputStream();
            int size=session.publishRefreshFromWidget(
                widget(185,32185),
                writer(out)
            );

            byte[] bytes=out.toByteArray();
            IsaacCipher cipher=new IsaacCipher(SEED.clone());
            int pos=0;
            pos=assert250(bytes,pos,cipher,new byte[]{0});
            pos=assert250(bytes,pos,cipher,append(0,"Keeper"));
            pos=assert250(bytes,pos,cipher,append(0,"Replacement"));
            pos=assert250(bytes,pos,cipher,new byte[]{5});

            exact185Refresh=size==2&&pos==bytes.length;
            foreignAccountIsolated=
                alice.mailbox().get("g2111:bob")==null&&
                bob.mailbox().size()==1&&
                bob.mailbox().get("g2111:old")==null;

            WorldPlayer invalid=new WorldPlayer();
            long badGen=world.registerPlayer(
                invalid,"g2111-invalid"
            );

            // Direct malformed fixture bypasses G21.9 delivery preflight
            // solely to test the rootless C2S refresh fail-close path.
            invalid.mailbox().deliver(
                message("g2111:invalid","Bad\nsubject")
            );

            WorldMailboxPresentationSession invalidView=
                new WorldMailboxGateway(world,invalid,badGen)
                    .openRootlessPresentation();

            ByteArrayOutputStream bad=new ByteArrayOutputStream();
            badSubjectFailClosed=
                rejects(()->invalidView.publishRefreshFromWidget(
                    widget(185,32185),writer(bad)
                ))&&bad.size()==0&&
                invalid.mailbox().size()==1;
            invalidView.close();

            WorldMailboxPresentationSession bobSession=
                b.openRootlessPresentation();
            bobSession.close();
            ByteArrayOutputStream closed=new ByteArrayOutputStream();
            closedScopeRejected=
                rejects(()->bobSession.publishRefreshFromWidget(
                    widget(185,32185),
                    writer(closed)
                ))&&closed.size()==0;

            boolean retired=world.unregisterPlayer(alice,ag);
            ByteArrayOutputStream stale=new ByteArrayOutputStream();
            generationRolloverRejected=
                retired&&
                rejects(()->session.publishRefreshFromWidget(
                    widget(185,32185),
                    writer(stale)
                ))&&stale.size()==0;

            long next=world.registerPlayer(
                alice,"g2111-alice"
            );
            generationRolloverRejected &=
                next!=ag&&
                rejects(()->session.publishRefreshFromWidget(
                    widget(185,32185),
                    writer(stale)
                ))&&stale.size()==0;

            noDomainMutation=
                alice.mailbox().size()==2&&
                alice.mailbox().unreadCount()==2&&
                alice.mailbox().get("g2111:keeper").claimState==
                    MailboxRewardDeliveryService.ClaimState.EMPTY&&
                alice.mailbox().get("g2111:old").message.subject
                    .equals("Replacement")&&
                bob.mailbox().unreadCount()==1;

            noRootSettlementOrLiveRoute=
                Arrays.stream(
                    WorldMailboxPresentationSession.class
                        .getDeclaredMethods()
                ).noneMatch(m->
                    m.getName().toLowerCase().contains("settle")||
                    Arrays.stream(m.getParameterTypes())
                        .anyMatch(type->
                            type==BankState.class||
                            type==LocalSession.class
                        )
                );

            session.close();
        }

        require(
            exact185Refresh&&
            staleSelectionHealed&&
            stableSelectedActionStillRejected&&
            reboundClearsSelection&&
            wrongOpcodeFailClosed&&
            wrongWidgetFailClosed&&
            badSubjectFailClosed&&
            foreignAccountIsolated&&
            generationRolloverRejected&&
            closedScopeRejected&&
            noDomainMutation&&
            noRootSettlementOrLiveRoute,
            "G21.11 acceptance"
        );

        System.out.println(
            "G2111_MAILBOX_REFRESH_WIDGET_PASS"+
            " exact185Refresh="+exact185Refresh+
            " staleSelectionHealed="+staleSelectionHealed+
            " stableSelectedActionStillRejected="+
                stableSelectedActionStillRejected+
            " reboundClearsSelection="+reboundClearsSelection+
            " wrongOpcodeFailClosed="+wrongOpcodeFailClosed+
            " wrongWidgetFailClosed="+wrongWidgetFailClosed+
            " badSubjectFailClosed="+badSubjectFailClosed+
            " foreignAccountIsolated="+foreignAccountIsolated+
            " generationRolloverRejected="+generationRolloverRejected+
            " closedScopeRejected="+closedScopeRejected+
            " noDomainMutation="+noDomainMutation+
            " noRootSettlementOrLiveRoute="+
                noRootSettlementOrLiveRoute+
            " liveSocketRouteClaim=false"+
            " mailboxRootClaim=false"+
            " rewardSettlementClaim=false"
        );
    }

    private static RewardDeliveryMessage message(
        String id,String subject
    ){
        return new RewardDeliveryMessage(
            id,subject,
            "G21.11 synthetic refresh fixture",
            Collections.emptyList(),
            "CUSTOM_LOCALLAB_G2111_FIXTURE"
        );
    }

    private static WidgetActionClientRequest widget(
        int opcode,int id
    ){
        return new WidgetActionClientRequest(
            id,
            ClientRequestMetadata.exactCurrent(
                opcode,"widget-id-u16",
                "G2111_CLIENT_WIDGET_FIXTURE"
            )
        );
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream sink
    ){
        return new ServerPacketWriter(
            sink,new IsaacCipher(SEED.clone())
        );
    }

    private static byte[] append(int state,String subject){
        byte[] latin=subject.getBytes(
            StandardCharsets.ISO_8859_1
        );
        byte[] payload=new byte[latin.length+3];
        payload[0]=1;
        payload[1]=(byte)state;
        System.arraycopy(latin,0,payload,2,latin.length);
        payload[payload.length-1]=10;
        return payload;
    }

    private static int assert250(
        byte[] wire,int start,
        IsaacCipher decoder,byte[] payload
    ){
        require(start+4<=wire.length,
            "short subtype31 packet");
        int opcode=((wire[start++]&255)-decoder.nextInt())&255;
        require(opcode==250,"expected S2C250");
        int size=wire[start++]&255;
        require(size==payload.length+2,
            "wrong var-byte payload size");
        int subtype=
            ((wire[start++]&255)<<8)|(wire[start++]&255);
        require(subtype==31,"wrong subtype");
        require(start+payload.length<=wire.length,
            "truncated subtype31 packet");
        require(Arrays.equals(
            Arrays.copyOfRange(
                wire,start,start+payload.length
            ),payload
        ),"incorrect subtype31 body");
        return start+payload.length;
    }

    private interface Operation {
        Object run()throws Exception;
    }

    private static boolean rejects(Operation operation){
        try{
            operation.run();
            return false;
        }catch(IllegalStateException|
                IllegalArgumentException expected){
            return true;
        }catch(Exception unexpected){
            throw new IllegalStateException(
                "unexpected checked failure",unexpected
            );
        }
    }

    private static void require(boolean yes,String error){
        if(!yes)
            throw new AssertionError(error);
    }

    private G2111MailboxRefreshWidgetIntegrationTest(){}
}
