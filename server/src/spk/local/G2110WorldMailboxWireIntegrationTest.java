package spk.local;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

/**
 * G21.10 rootless client-wire integration.
 * Uses real WorldPlayer-owned data and decodes every emitted ISAAC opcode.
 */
public final class G2110WorldMailboxWireIntegrationTest {
    private static final int[] SEED={2110,2111,2112,2113};

    public static void main(String[] args)throws Exception{
        boolean ownedInboxExact=false;
        boolean orderedReadStates=false;
        boolean selectedDetailExact=false;
        boolean claimedClearsContainer=false;
        boolean emptyClearsContainer=false;
        boolean secondAccountIsolated=false;
        boolean outOfRangeFailClosed=false;
        boolean staleIdentityFailClosed=false;
        boolean badInboxPreflightNoWire=false;
        boolean badDetailPreflightNoWire=false;
        boolean logoutGenerationRejected=false;
        boolean closedScopeRejected=false;
        boolean semanticStateUnchanged=false;
        boolean noRootOrSettlement=false;

        try(World world=World.isolatedForTest(60000L)){
            WorldPlayer alice=new WorldPlayer();
            WorldPlayer bob=new WorldPlayer();
            long ag=world.registerPlayer(alice,"g2110-alice");
            long bg=world.registerPlayer(bob,"g2110-bob");

            WorldMailboxGateway a=
                new WorldMailboxGateway(world,alice,ag);
            WorldMailboxGateway b=
                new WorldMailboxGateway(world,bob,bg);

            a.deliver(message(
                "g2110:notice","Notice",
                Collections.emptyList()
            ));
            a.deliver(message(
                "g2110:reward","Reward",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,5000)
                )
            ));
            a.markRead("g2110:notice");

            b.deliver(message(
                "g2110:bob","Private Bob mail",
                Collections.emptyList()
            ));

            WorldMailboxPresentationSession presentation=
                a.openRootlessPresentation();

            ByteArrayOutputStream inbox=new ByteArrayOutputStream();
            int count=presentation.publishInbox(writer(inbox));
            byte[] wire=inbox.toByteArray();
            IsaacCipher decoder=new IsaacCipher(SEED.clone());
            int pos=0;
            pos=assert250(wire,pos,decoder,new byte[]{0});
            pos=assert250(wire,pos,decoder,append(1,"Notice"));
            pos=assert250(wire,pos,decoder,append(0,"Reward"));
            pos=assert250(wire,pos,decoder,new byte[]{5});

            ownedInboxExact=count==2&&pos==wire.length;
            orderedReadStates=
                alice.mailbox().get("g2110:notice").readState==
                    MailboxRewardDeliveryService.ReadState.READ&&
                alice.mailbox().get("g2110:reward").readState==
                    MailboxRewardDeliveryService.ReadState.UNREAD;

            ByteArrayOutputStream selected=new ByteArrayOutputStream();
            MailboxRewardDeliveryService.Snapshot chosen=
                presentation.publishTrustedRowDetail(
                    1,writer(selected)
                );

            assertDetail(
                selected.toByteArray(),
                1,
                "Reward",
                new int[]{995},
                new int[]{5000},
                1
            );

            selectedDetailExact=
                "g2110:reward".equals(chosen.message.messageId)&&
                chosen.claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;

            // Simulated *external* settlement acknowledgement only.
            // Presentation itself never moves items or acknowledges claims.
            alice.mailbox().acknowledgeAttachmentSettlement(
                "g2110:reward"
            );

            presentation.publishInbox(
                writer(new ByteArrayOutputStream())
            );

            ByteArrayOutputStream claimed=new ByteArrayOutputStream();
            presentation.publishTrustedRowDetail(
                1,writer(claimed)
            );
            assertDetail(
                claimed.toByteArray(),
                1,
                "Reward",
                new int[0],
                new int[0],
                2
            );

            claimedClearsContainer=
                alice.mailbox().get("g2110:reward").message.attachments
                    .get(0).amount==5000;

            ByteArrayOutputStream empty=new ByteArrayOutputStream();
            presentation.publishTrustedRowDetail(
                0,writer(empty)
            );
            assertDetail(
                empty.toByteArray(),
                0,
                "Notice",
                new int[0],
                new int[0],
                0
            );
            emptyClearsContainer=empty.size()>0;

            WorldMailboxPresentationSession bobView=
                b.openRootlessPresentation();
            ByteArrayOutputStream bobWire=new ByteArrayOutputStream();
            int bobCount=bobView.publishInbox(writer(bobWire));
            byte[] bw=bobWire.toByteArray();
            IsaacCipher bdecode=new IsaacCipher(SEED.clone());
            int bp=0;
            bp=assert250(bw,bp,bdecode,new byte[]{0});
            bp=assert250(
                bw,bp,bdecode,append(0,"Private Bob mail")
            );
            bp=assert250(bw,bp,bdecode,new byte[]{5});

            secondAccountIsolated=
                bobCount==1&&bp==bw.length&&
                alice.mailbox().get("g2110:bob")==null&&
                bob.mailbox().get("g2110:reward")==null;
            bobView.close();

            ByteArrayOutputStream outside=new ByteArrayOutputStream();
            outOfRangeFailClosed=
                rejects(()->presentation.publishTrustedRowDetail(
                    2,writer(outside)
                ))&&outside.size()==0;

            // The view remains bound to the original message identity.
            a.deleteSafe("g2110:reward");
            a.deliver(message(
                "g2110:reward","Replacement reward",
                Collections.emptyList()
            ));

            ByteArrayOutputStream recycled=new ByteArrayOutputStream();
            staleIdentityFailClosed=
                rejects(()->presentation.publishTrustedRowDetail(
                    1,writer(recycled)
                ))&&recycled.size()==0;

            WorldPlayer badOwner=new WorldPlayer();
            long badGen=world.registerPlayer(
                badOwner,"g2110-untrusted-mail"
            );
            WorldMailboxGateway malformed=
                new WorldMailboxGateway(
                    world,badOwner,badGen
                );

            badOwner.mailbox().deliver(message(
                "g2110:bad-inbox",
                "bad\nsubject",
                Collections.emptyList()
            ));

            WorldMailboxPresentationSession badView=
                malformed.openRootlessPresentation();

            ByteArrayOutputStream badInbox=new ByteArrayOutputStream();
            badInboxPreflightNoWire=
                rejects(()->badView.publishInbox(
                    writer(badInbox)
                ))&&badInbox.size()==0;
            badView.close();

            WorldPlayer badItemOwner=new WorldPlayer();
            long badItemGen=world.registerPlayer(
                badItemOwner,"g2110-bad-item"
            );
            WorldMailboxGateway badItemGateway=
                new WorldMailboxGateway(
                    world,badItemOwner,badItemGen
                );

            // Direct malformed-domain fixture bypasses the G21.9 gateway
            // solely to demonstrate that packet preflight rejects it.
            badItemOwner.mailbox().deliver(message(
                "g2110:invalid-attachment",
                "Invalid attachment",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(
                        65535,1
                    )
                )
            ));

            WorldMailboxPresentationSession badItemView=
                badItemGateway.openRootlessPresentation();

            badItemView.publishInbox(
                writer(new ByteArrayOutputStream())
            );

            ByteArrayOutputStream badDetail=new ByteArrayOutputStream();
            badDetailPreflightNoWire=
                rejects(()->badItemView.publishTrustedRowDetail(
                    0,writer(badDetail)
                ))&&badDetail.size()==0;

            badItemView.close();

            boolean removed=world.unregisterPlayer(alice,ag);

            ByteArrayOutputStream stale=new ByteArrayOutputStream();
            logoutGenerationRejected=
                removed&&
                rejects(()->presentation.publishInbox(
                    writer(stale)
                ))&&
                rejects(()->presentation.publishTrustedRowDetail(
                    0,writer(stale)
                ))&&
                stale.size()==0;

            long ag2=world.registerPlayer(
                alice,"g2110-alice"
            );
            logoutGenerationRejected &=
                ag2!=ag&&
                rejects(()->presentation.publishInbox(
                    writer(stale)
                ))&&stale.size()==0;

            presentation.close();

            closedScopeRejected=
                rejects(()->presentation.publishInbox(
                    writer(new ByteArrayOutputStream())
                ));

            semanticStateUnchanged=
                alice.mailbox().size()==2&&
                bob.mailbox().size()==1&&
                badOwner.mailbox().size()==1&&
                badItemOwner.mailbox().size()==1&&
                alice.mailbox().get("g2110:notice").readState==
                    MailboxRewardDeliveryService.ReadState.READ&&
                alice.mailbox().get("g2110:reward").claimState==
                    MailboxRewardDeliveryService.ClaimState.EMPTY;

            noRootOrSettlement=
                Arrays.stream(
                    WorldMailboxPresentationSession.class
                        .getDeclaredMethods()
                ).noneMatch(method->
                    method.getName().toLowerCase().contains("settle")||
                    method.getName().toLowerCase().contains("openroot")||
                    Arrays.stream(method.getParameterTypes())
                        .anyMatch(type->
                            type==BankState.class||
                            (type==WidgetActionClientRequest.class&&
                             !method.getName().equals(
                                 "publishRefreshFromWidget"
                             )&&
                             !method.getName().equals(
                                 "deleteSafeFromWidget"
                             )&&
                             !method.getName().equals(
                                 "publishRowDetailFromWidget"
                             )&&
                             !method.getName().equals(
                                 "deleteSafeAndPublishInboxFromWidget"
                             )&&
                             !(method.isSynthetic()&&
                               (method.getName().startsWith(
                                 "lambda$publishRefreshFromWidget$"
                               )||
                               method.getName().startsWith(
                                 "lambda$deleteSafeFromWidget$"
                               )||
                               method.getName().startsWith(
                                 "lambda$deleteSafeAndPublishInboxFromWidget$"
                               ))))
                        )
                );
        }

        require(
            ownedInboxExact&&
            orderedReadStates&&
            selectedDetailExact&&
            claimedClearsContainer&&
            emptyClearsContainer&&
            secondAccountIsolated&&
            outOfRangeFailClosed&&
            staleIdentityFailClosed&&
            badInboxPreflightNoWire&&
            badDetailPreflightNoWire&&
            logoutGenerationRejected&&
            closedScopeRejected&&
            semanticStateUnchanged&&
            noRootOrSettlement,
            "G21.10 acceptance"
        );

        System.out.println(
            "G2110_WORLD_MAILBOX_WIRE_PASS"+
            " ownedInboxExact="+ownedInboxExact+
            " orderedReadStates="+orderedReadStates+
            " selectedDetailExact="+selectedDetailExact+
            " claimedClearsContainer="+claimedClearsContainer+
            " emptyClearsContainer="+emptyClearsContainer+
            " secondAccountIsolated="+secondAccountIsolated+
            " outOfRangeFailClosed="+outOfRangeFailClosed+
            " staleIdentityFailClosed="+staleIdentityFailClosed+
            " badInboxPreflightNoWire="+badInboxPreflightNoWire+
            " badDetailPreflightNoWire="+badDetailPreflightNoWire+
            " logoutGenerationRejected="+logoutGenerationRejected+
            " closedScopeRejected="+closedScopeRejected+
            " semanticStateUnchanged="+semanticStateUnchanged+
            " noRootOrSettlement="+noRootOrSettlement+
            " liveSocketClaim=false"+
            " clientRowClickClaim=false"+
            " originalServerOrderingClaim=false"
        );
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream sink
    ){
        return new ServerPacketWriter(
            sink,new IsaacCipher(SEED.clone())
        );
    }

    private static RewardDeliveryMessage message(
        String id,String subject,
        java.util.List<RewardDeliveryMessage.Attachment> items
    ){
        return new RewardDeliveryMessage(
            id,subject,
            "G21.10 rootless wire fixture",
            items,
            "CUSTOM_LOCALLAB_G2110_FIXTURE"
        );
    }

    private static byte[] append(int state,String subject){
        byte[] str=subject.getBytes(
            StandardCharsets.ISO_8859_1
        );
        byte[] body=new byte[3+str.length];
        body[0]=1;
        body[1]=(byte)state;
        System.arraycopy(str,0,body,2,str.length);
        body[body.length-1]=10;
        return body;
    }

    private static int assert250(
        byte[] wire,int position,
        IsaacCipher cipher,byte[] body
    ){
        require(position+4<=wire.length,
            "truncated S2C250");
        int opcode=((wire[position++]&255)-cipher.nextInt())&255;
        require(opcode==250,"opcode 250 expected actual "+opcode);
        int len=wire[position++]&255;
        require(len==body.length+2,"subtype31 length");
        int subtype=
            ((wire[position++]&255)<<8)|
            (wire[position++]&255);
        require(subtype==31,"S2C250 subtype31");
        require(position+body.length<=wire.length,
            "short S2C250 body");
        require(Arrays.equals(
            Arrays.copyOfRange(
                wire,position,position+body.length
            ),body
        ),"S2C250 body mismatch");
        return position+body.length;
    }

    private static int assertVarShort(
        byte[] wire,int position,
        IsaacCipher cipher,int opcode,byte[] body
    ){
        require(position+3<=wire.length,
            "truncated VAR_SHORT");
        int actual=
            ((wire[position++]&255)-cipher.nextInt())&255;
        require(actual==opcode,"wrong VAR_SHORT opcode "+actual);
        int size=
            ((wire[position++]&255)<<8)|
            (wire[position++]&255);
        require(size==body.length,
            "wrong VAR_SHORT size");
        require(position+size<=wire.length,
            "short VAR_SHORT body");
        require(Arrays.equals(
            Arrays.copyOfRange(wire,position,position+size),
            body
        ),"VAR_SHORT body mismatch");
        return position+size;
    }

    private static void assertDetail(
        byte[] wire,
        int row,String subject,
        int[] items,int[] quantities,
        int claimState
    )throws java.io.IOException{
        IsaacCipher cipher=new IsaacCipher(SEED.clone());
        int pos=assert250(
            wire,0,cipher,
            new byte[]{7,(byte)row}
        );
        pos=assertVarShort(
            wire,pos,cipher,53,
            BootstrapPackets.itemContainer53(
                32175,items,quantities
            )
        );
        pos=assertVarShort(
            wire,pos,cipher,126,
            BootstrapPackets.widgetText126(32168,subject)
        );
        pos=assert250(
            wire,pos,cipher,
            new byte[]{4,(byte)claimState}
        );
        require(pos==wire.length,
            "trailing selected detail packets");
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
            throw new IllegalStateException(unexpected);
        }
    }

    private static void require(boolean yes,String msg){
        if(!yes)throw new AssertionError(msg);
    }

    private G2110WorldMailboxWireIntegrationTest(){}
}
