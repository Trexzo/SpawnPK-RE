package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class G213MailboxAttachmentProjectionIntegrationTest {
    private static final int[] SEED={213,214,215,216};

    public static void main(String[] args)throws Exception{
        boolean widget32175=false;
        boolean emptyCleared=false;
        boolean unclaimedItems=false;
        boolean largeQuantityExact=false;
        boolean claimedCleared=false;
        boolean overlongQuantityFailClosed=false;
        boolean invalidItemFailClosed=false;
        boolean oversizeFrameFailClosed=false;
        boolean preflightNoWrite=false;
        boolean domainUnchanged=false;
        boolean noRootClaim=false;
        boolean noSettlementClaim=false;

        MailboxRewardDeliveryService service=
            new MailboxRewardDeliveryService(4);

        MailboxRewardDeliveryService.Snapshot notice=
            service.deliver(
                message(
                    "g213:notice",
                    Collections.emptyList()
                )
            );

        ByteArrayOutputStream emptyBytes=
            new ByteArrayOutputStream();

        MailboxAttachmentProjection.publishContainer(
            writer(emptyBytes),
            notice
        );

        assert53(
            emptyBytes.toByteArray(),
            new int[0],
            new int[0]
        );

        emptyCleared=notice.claimState==
            MailboxRewardDeliveryService.ClaimState.EMPTY;

        List<RewardDeliveryMessage.Attachment> goods=
            Arrays.asList(
                new RewardDeliveryMessage.Attachment(995,5000L),
                new RewardDeliveryMessage.Attachment(4151,1L),
                new RewardDeliveryMessage.Attachment(
                    65534,
                    Integer.MAX_VALUE
                )
            );

        MailboxRewardDeliveryService.Snapshot reward=
            service.deliver(message("g213:reward",goods));

        ByteArrayOutputStream unclaimedBytes=
            new ByteArrayOutputStream();

        MailboxAttachmentProjection.publishContainer(
            writer(unclaimedBytes),
            reward
        );

        assert53(
            unclaimedBytes.toByteArray(),
            new int[]{995,4151,65534},
            new int[]{5000,1,Integer.MAX_VALUE}
        );

        widget32175=
            MailboxAttachmentProjection.ATTACHMENT_CONTAINER_WIDGET==32175;

        unclaimedItems=reward.claimState==
            MailboxRewardDeliveryService.ClaimState.UNCLAIMED;

        largeQuantityExact=
            unclaimedBytes.size()>emptyBytes.size();

        boolean settled=
            service.acknowledgeAttachmentSettlement("g213:reward");

        MailboxRewardDeliveryService.Snapshot claimed=
            service.get("g213:reward");

        ByteArrayOutputStream claimedBytes=
            new ByteArrayOutputStream();

        MailboxAttachmentProjection.publishContainer(
            writer(claimedBytes),
            claimed
        );

        assert53(
            claimedBytes.toByteArray(),
            new int[0],
            new int[0]
        );

        claimedCleared=
            settled&&
            claimed.claimState==
                MailboxRewardDeliveryService.ClaimState.CLAIMED&&
            claimed.message.attachments.size()==3;

        overlongQuantityFailClosed=rejectWithoutWire(
            transientSnapshot(
                new RewardDeliveryMessage.Attachment(
                    995,
                    (long)Integer.MAX_VALUE+1
                )
            )
        );

        invalidItemFailClosed=rejectWithoutWire(
            transientSnapshot(
                new RewardDeliveryMessage.Attachment(
                    65535,
                    1
                )
            )
        );

        // Smallest nonempty slot payload is 3 bytes. 22,000 entries
        // exceed a 65,535-byte S2C53 var-short frame (4 + 3*22,000).
        List<RewardDeliveryMessage.Attachment> huge=
            new ArrayList<>();

        for(int i=0;i<22000;i++)
            huge.add(
                new RewardDeliveryMessage.Attachment(995,1)
            );

        MailboxRewardDeliveryService hugeService=
            new MailboxRewardDeliveryService(1);

        MailboxRewardDeliveryService.Snapshot hugeReward=
            hugeService.deliver(message("g213:huge",huge));

        oversizeFrameFailClosed=
            rejectWithoutWire(hugeReward);

        preflightNoWrite=
            overlongQuantityFailClosed&&
            invalidItemFailClosed&&
            oversizeFrameFailClosed;

        domainUnchanged=
            service.size()==2&&
            service.unreadCount()==2&&
            service.get("g213:notice").claimState==
                MailboxRewardDeliveryService.ClaimState.EMPTY&&
            service.get("g213:reward").claimState==
                MailboxRewardDeliveryService.ClaimState.CLAIMED;

        noRootClaim=
            MailboxAttachmentProjection.class
                .getDeclaredFields().length==1&&
            MailboxAttachmentProjection.ATTACHMENT_CONTAINER_WIDGET==32175;

        noSettlementClaim=
            claimed.message.attachments.size()==3;

        require(
            widget32175&&
            emptyCleared&&
            unclaimedItems&&
            largeQuantityExact&&
            claimedCleared&&
            overlongQuantityFailClosed&&
            invalidItemFailClosed&&
            oversizeFrameFailClosed&&
            preflightNoWrite&&
            domainUnchanged&&
            noRootClaim&&
            noSettlementClaim,
            "G21.3 acceptance"
        );

        System.out.println(
            "G213_MAILBOX_ATTACHMENT_PROJECTION_PASS"+
            " widget32175="+widget32175+
            " emptyCleared="+emptyCleared+
            " unclaimedItems="+unclaimedItems+
            " largeQuantityExact="+largeQuantityExact+
            " claimedCleared="+claimedCleared+
            " overlongQuantityFailClosed="+overlongQuantityFailClosed+
            " invalidItemFailClosed="+invalidItemFailClosed+
            " oversizeFrameFailClosed="+oversizeFrameFailClosed+
            " preflightNoWrite="+preflightNoWrite+
            " domainUnchanged="+domainUnchanged+
            " noRootClaim="+noRootClaim+
            " noSettlementClaim="+noSettlementClaim+
            " persistenceClaim=false"
        );
    }

    private static RewardDeliveryMessage message(
        String id,
        List<RewardDeliveryMessage.Attachment> attachments
    ){
        return new RewardDeliveryMessage(
            id,
            "G21.3 LocalLab attachment fixture",
            "No expiry or deposit authority",
            attachments,
            "CUSTOM_LOCALLAB_G213_FIXTURE"
        );
    }

    private static MailboxRewardDeliveryService.Snapshot
        transientSnapshot(
            RewardDeliveryMessage.Attachment item
        ){
        MailboxRewardDeliveryService service=
            new MailboxRewardDeliveryService(1);

        return service.deliver(
            message(
                "g213:invalid",
                Collections.singletonList(item)
            )
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

    private static boolean rejectWithoutWire(
        MailboxRewardDeliveryService.Snapshot row
    )throws Exception{
        ByteArrayOutputStream sink=new ByteArrayOutputStream();
        boolean rejected=false;

        try{
            MailboxAttachmentProjection.publishContainer(
                writer(sink),
                row
            );
        }catch(IllegalArgumentException expected){
            rejected=true;
        }

        return rejected&&sink.size()==0;
    }

    /**
     * Independently decodes the var-short envelope, widget U16/slot-count,
     * one-byte vs Y-int amount encoding, and transformed item-id encoding.
     */
    private static void assert53(
        byte[] bytes,
        int[] expectedItems,
        int[] expectedQty
    ){
        require(expectedItems.length==expectedQty.length,
            "expected fixture mismatch");

        IsaacCipher cipher=new IsaacCipher(SEED.clone());
        int p=0;

        require(bytes.length>=7,"truncated S2C53");

        int opcode=((bytes[p++]&255)-cipher.nextInt())&255;
        require(opcode==53,"opcode expected 53 actual="+opcode);

        int frameLength=
            ((bytes[p++]&255)<<8)|(bytes[p++]&255);

        require(frameLength==bytes.length-3,
            "S2C53 frame length");

        int widget=((bytes[p++]&255)<<8)|(bytes[p++]&255);
        require(widget==32175,"widget expected 32175");

        int slots=((bytes[p++]&255)<<8)|(bytes[p++]&255);
        require(slots==expectedItems.length,
            "slot count mismatch");

        for(int i=0;i<slots;i++){
            require(p+3<=bytes.length,
                "truncated item entry");

            int quantity=bytes[p++]&255;

            if(quantity==255){
                require(p+6<=bytes.length,
                    "truncated large quantity");

                int p0=bytes[p++]&255;
                int p1=bytes[p++]&255;
                int p2=bytes[p++]&255;
                int p3=bytes[p++]&255;

                quantity=
                    (p1<<24)|(p0<<16)|(p3<<8)|p2;
            }

            int low=(bytes[p++]&255);
            int high=(bytes[p++]&255);
            int itemId=((high<<8)|((low-128)&255))-1;

            require(quantity==expectedQty[i],
                "quantity mismatch index="+i);
            require(itemId==expectedItems[i],
                "item mismatch index="+i);
        }

        require(p==bytes.length,
            "trailing S2C53 bytes="+(bytes.length-p));
    }

    private static void require(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(message);
    }

    private G213MailboxAttachmentProjectionIntegrationTest(){}
}
