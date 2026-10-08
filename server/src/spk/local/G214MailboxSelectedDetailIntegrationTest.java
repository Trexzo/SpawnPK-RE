package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class G214MailboxSelectedDetailIntegrationTest {
    private static final int[] SEED={214,215,216,217};

    public static void main(String[] args)throws Exception{
        boolean subjectWidget32168=false;
        boolean attachmentWidget32175=false;
        boolean semanticClaimState=false;
        boolean emptyProjected=false;
        boolean unclaimedProjected=false;
        boolean claimedProjected=false;
        boolean exact53=false;
        boolean exact126=false;
        boolean exact250Subtype31=false;
        boolean invalidSubjectFailClosed=false;
        boolean invalidAttachmentFailClosed=false;
        boolean preflightNoWrite=false;
        boolean domainUnchanged=false;
        boolean noRootClaim=false;
        boolean noSettlementClaim=false;

        MailboxRewardDeliveryService service=
            new MailboxRewardDeliveryService(4);

        MailboxRewardDeliveryService.Snapshot plain=
            service.deliver(
                message(
                    "g214:plain",
                    "Plain mail",
                    Collections.emptyList()
                )
            );

        MailboxRewardDeliveryService.Snapshot reward=
            service.deliver(
                message(
                    "g214:reward",
                    "Prize mail",
                    Arrays.asList(
                        new RewardDeliveryMessage.Attachment(995,5000),
                        new RewardDeliveryMessage.Attachment(
                            4151,
                            Integer.MAX_VALUE
                        )
                    )
                )
            );

        byte[] plainWire=publish(plain);
        byte[] rewardWire=publish(reward);

        assertWire(
            plainWire,
            "Plain mail",
            new int[0],
            new int[0],
            0
        );
        emptyProjected=
            plain.claimState==
                MailboxRewardDeliveryService.ClaimState.EMPTY;

        assertWire(
            rewardWire,
            "Prize mail",
            new int[]{995,4151},
            new int[]{5000,Integer.MAX_VALUE},
            1
        );
        unclaimedProjected=
            reward.claimState==
                MailboxRewardDeliveryService.ClaimState.UNCLAIMED;

        service.acknowledgeAttachmentSettlement(
            "g214:reward"
        );

        MailboxRewardDeliveryService.Snapshot claimed=
            service.get("g214:reward");

        assertWire(
            publish(claimed),
            "Prize mail",
            new int[0],
            new int[0],
            2
        );

        claimedProjected=
            claimed.claimState==
                MailboxRewardDeliveryService.ClaimState.CLAIMED&&
            claimed.message.attachments.size()==2;

        subjectWidget32168=
            MailboxSelectedDetailProjection.SUBJECT_WIDGET==32168;

        attachmentWidget32175=
            MailboxAttachmentProjection
                .ATTACHMENT_CONTAINER_WIDGET==32175;

        semanticClaimState=
            MailboxPresentationAdapter.claimStateCode(
                plain.claimState
            )==0&&
            MailboxPresentationAdapter.claimStateCode(
                reward.claimState
            )==1&&
            MailboxPresentationAdapter.claimStateCode(
                claimed.claimState
            )==2;

        exact53=true;
        exact126=true;
        exact250Subtype31=true;

        invalidSubjectFailClosed=
            failsWithoutWrite(
                transientRow("g214:bad-lf","bad\nsubject",
                    Collections.emptyList())
            )&&
            failsWithoutWrite(
                transientRow("g214:bad-unicode","Unicode Ω",
                    Collections.emptyList())
            );

        invalidAttachmentFailClosed=
            failsWithoutWrite(
                transientRow(
                    "g214:bad-id","Item overflow",
                    Collections.singletonList(
                        new RewardDeliveryMessage.Attachment(65535,1)
                    )
                )
            )&&
            failsWithoutWrite(
                transientRow(
                    "g214:bad-amount","Amount overflow",
                    Collections.singletonList(
                        new RewardDeliveryMessage.Attachment(
                            995,
                            (long)Integer.MAX_VALUE+1
                        )
                    )
                )
            );

        boolean nullSnapshotFailClosed=false;
        ByteArrayOutputStream nullOutput=
            new ByteArrayOutputStream();

        try{
            MailboxSelectedDetailProjection.publish(
                writer(nullOutput),
                null
            );
        }catch(NullPointerException expected){
            nullSnapshotFailClosed=
                nullOutput.size()==0;
        }

        preflightNoWrite=
            invalidSubjectFailClosed&&
            invalidAttachmentFailClosed&&
            nullSnapshotFailClosed;

        domainUnchanged=
            service.size()==2&&
            service.unreadCount()==2&&
            service.get("g214:plain").claimState==
                MailboxRewardDeliveryService.ClaimState.EMPTY&&
            service.get("g214:reward").claimState==
                MailboxRewardDeliveryService.ClaimState.CLAIMED;

        noRootClaim=
            MailboxSelectedDetailProjection.class
                .getDeclaredFields().length==1;

        noSettlementClaim=
            claimed.message.attachments.size()==2;

        require(
            subjectWidget32168&&
            attachmentWidget32175&&
            semanticClaimState&&
            emptyProjected&&
            unclaimedProjected&&
            claimedProjected&&
            exact53&&
            exact126&&
            exact250Subtype31&&
            invalidSubjectFailClosed&&
            invalidAttachmentFailClosed&&
            preflightNoWrite&&
            domainUnchanged&&
            noRootClaim&&
            noSettlementClaim,
            "G21.4 acceptance"
        );

        System.out.println(
            "G214_MAILBOX_SELECTED_DETAIL_PASS"+
            " subjectWidget32168="+subjectWidget32168+
            " attachmentWidget32175="+attachmentWidget32175+
            " semanticClaimState="+semanticClaimState+
            " emptyProjected="+emptyProjected+
            " unclaimedProjected="+unclaimedProjected+
            " claimedProjected="+claimedProjected+
            " exact53="+exact53+
            " exact126="+exact126+
            " exact250Subtype31="+exact250Subtype31+
            " invalidSubjectFailClosed="+invalidSubjectFailClosed+
            " invalidAttachmentFailClosed="+invalidAttachmentFailClosed+
            " preflightNoWrite="+preflightNoWrite+
            " domainUnchanged="+domainUnchanged+
            " noRootClaim="+noRootClaim+
            " noSettlementClaim="+noSettlementClaim+
            " persistenceClaim=false"
        );
    }

    private static MailboxRewardDeliveryService.Snapshot
        transientRow(
            String id,
            String subject,
            List<RewardDeliveryMessage.Attachment> items
        ){
        MailboxRewardDeliveryService tmp=
            new MailboxRewardDeliveryService(1);

        return tmp.deliver(message(id,subject,items));
    }

    private static RewardDeliveryMessage message(
        String id,
        String subject,
        List<RewardDeliveryMessage.Attachment> items
    ){
        return new RewardDeliveryMessage(
            id,
            subject,
            "G21.4 fixture body; no recovered widget",
            items,
            "CUSTOM_LOCALLAB_G214_FIXTURE"
        );
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream sink
    ){
        return new ServerPacketWriter(
            sink,
            new IsaacCipher(SEED.clone())
        );
    }

    private static byte[] publish(
        MailboxRewardDeliveryService.Snapshot snapshot
    )throws Exception{
        ByteArrayOutputStream sink=
            new ByteArrayOutputStream();

        MailboxSelectedDetailProjection.publish(
            writer(sink),
            snapshot
        );

        return sink.toByteArray();
    }

    private static boolean failsWithoutWrite(
        MailboxRewardDeliveryService.Snapshot snapshot
    )throws Exception{
        ByteArrayOutputStream sink=
            new ByteArrayOutputStream();

        boolean rejected=false;

        try{
            MailboxSelectedDetailProjection.publish(
                writer(sink),
                snapshot
            );
        }catch(IllegalArgumentException expected){
            rejected=true;
        }

        return rejected&&sink.size()==0;
    }

    private static void assertWire(
        byte[] wire,
        String subject,
        int[] items,
        int[] quantities,
        int claimCode
    )throws Exception{
        IsaacCipher decode=
            new IsaacCipher(SEED.clone());

        int pos=0;
        pos=assertVarShort(
            wire,
            pos,
            decode,
            53,
            BootstrapPackets.itemContainer53(
                32175,
                items,
                quantities
            )
        );

        pos=assertVarShort(
            wire,
            pos,
            decode,
            126,
            BootstrapPackets.widgetText126(
                32168,
                subject
            )
        );

        require(pos+6==wire.length,
            "truncated or excess final S2C250");

        int opcode=
            ((wire[pos++]&255)-decode.nextInt())&255;
        require(opcode==250,"expected opcode 250");

        int length=wire[pos++]&255;
        require(length==4,"subtype31 claim frame length");

        int subtype=
            ((wire[pos++]&255)<<8)|(wire[pos++]&255);
        require(subtype==31,"expected subtype31");

        int operation=wire[pos++]&255;
        int code=wire[pos++]&255;
        require(operation==4,"expected claim projection op4");
        require(code==claimCode,"claim state mismatch");
        require(pos==wire.length,"trailing payload");
    }

    private static int assertVarShort(
        byte[] wire,
        int offset,
        IsaacCipher decode,
        int expectedOpcode,
        byte[] expectedBody
    ){
        require(offset+3<=wire.length,
            "truncated var-short header");

        int opcode=
            ((wire[offset++]&255)-decode.nextInt())&255;
        require(opcode==expectedOpcode,
            "expected opcode "+expectedOpcode+
            " actual="+opcode);

        int length=
            ((wire[offset++]&255)<<8)|
            (wire[offset++]&255);

        require(length==expectedBody.length,
            "var-short length mismatch");

        require(offset+length<=wire.length,
            "truncated var-short body");

        require(
            Arrays.equals(
                Arrays.copyOfRange(
                    wire,offset,offset+length
                ),
                expectedBody
            ),
            "var-short body mismatch opcode="+expectedOpcode
        );

        return offset+length;
    }

    private static void require(
        boolean yes,
        String error
    ){
        if(!yes)
            throw new AssertionError(error);
    }

    private G214MailboxSelectedDetailIntegrationTest(){}
}
