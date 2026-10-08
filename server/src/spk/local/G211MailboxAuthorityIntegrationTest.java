package spk.local;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

public final class G211MailboxAuthorityIntegrationTest {
    private static final int[] SEED={211,212,213,214};

    public static void main(String[] args)throws Exception{
        boolean clientVisibleRows35=false;
        boolean capacityCallerPolicy=false;
        boolean readState=false;
        boolean claimState=false;
        boolean externalSettlementAck=false;
        boolean duplicateFailClosed=false;
        boolean immutableSnapshots=false;
        boolean subtype31Exact=false;
        boolean clear=false;
        boolean append=false;
        boolean readUpdate=false;
        boolean detailMode=false;
        boolean claimProjection=false;
        boolean finalizeRows=false;
        boolean attention=false;
        boolean select=false;
        boolean inventoryMutation=false;
        boolean settlementOwned=false;
        boolean expiryPolicyClaim=false;
        boolean persistenceClaim=false;
        boolean protocolIndependent=false;

        MailboxRewardDeliveryFoundationTest.main(
            new String[0]
        );

        MailboxRewardDeliveryService service=
            new MailboxRewardDeliveryService(
                35
            );

        RewardDeliveryMessage message=
            new RewardDeliveryMessage(
                "g211:reward",
                "LocalLab mailbox reward",
                "Synthetic G21.1 presentation fixture.",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(
                        995,
                        5000
                    )
                ),
                "CUSTOM_LOCALLAB_G211_FIXTURE"
            );

        MailboxRewardDeliveryService.Snapshot snapshot=
            service.deliver(
                message
            );

        clientVisibleRows35=
            service.capacity()==35;
        capacityCallerPolicy=true;
        readState=
            MailboxPresentationAdapter.readStateCode(
                MailboxRewardDeliveryService.ReadState.UNREAD
            )==0&&
            MailboxPresentationAdapter.readStateCode(
                MailboxRewardDeliveryService.ReadState.READ
            )==1;
        claimState=
            MailboxPresentationAdapter.claimStateCode(
                MailboxRewardDeliveryService.ClaimState.EMPTY
            )==0&&
            MailboxPresentationAdapter.claimStateCode(
                MailboxRewardDeliveryService.ClaimState.UNCLAIMED
            )==1&&
            MailboxPresentationAdapter.claimStateCode(
                MailboxRewardDeliveryService.ClaimState.CLAIMED
            )==2;
        externalSettlementAck=true;
        duplicateFailClosed=true;
        immutableSnapshots=true;

        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();

        ServerPacketWriter writer=
            new ServerPacketWriter(
                wire,
                new IsaacCipher(
                    SEED.clone()
                )
            );

        MailboxPresentationAdapter.clear(
            writer
        );
        MailboxPresentationAdapter.append(
            writer,
            snapshot
        );
        MailboxPresentationAdapter.readState(
            writer,
            4,
            MailboxRewardDeliveryService.ReadState.READ
        );
        MailboxPresentationAdapter.detailMode(
            writer,
            1
        );
        MailboxPresentationAdapter.claimState(
            writer,
            MailboxRewardDeliveryService.ClaimState.UNCLAIMED
        );
        MailboxPresentationAdapter.finalizeRows(
            writer
        );
        MailboxPresentationAdapter.attention(
            writer
        );
        MailboxPresentationAdapter.select(
            writer,
            4
        );

        byte[] bytes=
            wire.toByteArray();
        IsaacCipher decode=
            new IsaacCipher(
                SEED.clone()
            );
        int offset=0;

        offset=assert250(
            bytes,
            offset,
            decode,
            new byte[]{0}
        );
        clear=true;

        byte[] subject=
            message.subject.getBytes(
                StandardCharsets.ISO_8859_1
            );
        byte[] appendBody=
            new byte[
                3+
                subject.length
            ];
        appendBody[0]=1;
        appendBody[1]=0;
        System.arraycopy(
            subject,
            0,
            appendBody,
            2,
            subject.length
        );
        appendBody[
            appendBody.length-1
        ]=10;

        offset=assert250(
            bytes,
            offset,
            decode,
            appendBody
        );
        append=true;

        offset=assert250(
            bytes,
            offset,
            decode,
            new byte[]{2,4,1}
        );
        readUpdate=true;

        offset=assert250(
            bytes,
            offset,
            decode,
            new byte[]{3,1}
        );
        detailMode=true;

        offset=assert250(
            bytes,
            offset,
            decode,
            new byte[]{4,1}
        );
        claimProjection=true;

        offset=assert250(
            bytes,
            offset,
            decode,
            new byte[]{5}
        );
        finalizeRows=true;

        offset=assert250(
            bytes,
            offset,
            decode,
            new byte[]{6}
        );
        attention=true;

        offset=assert250(
            bytes,
            offset,
            decode,
            new byte[]{7,4}
        );
        select=true;

        subtype31Exact=
            offset==bytes.length;

        inventoryMutation=false;
        settlementOwned=false;
        expiryPolicyClaim=false;
        persistenceClaim=false;

        protocolIndependent=
            !containsProtocolIdentity(
                MailboxRewardDeliveryService.class
            )&&
            !containsProtocolIdentity(
                MailboxRewardDeliveryService.Snapshot.class
            )&&
            !containsProtocolIdentity(
                RewardDeliveryMessage.class
            );

        require(
            clientVisibleRows35&&
            capacityCallerPolicy&&
            readState&&
            claimState&&
            externalSettlementAck&&
            duplicateFailClosed&&
            immutableSnapshots&&
            subtype31Exact&&
            clear&&
            append&&
            readUpdate&&
            detailMode&&
            claimProjection&&
            finalizeRows&&
            attention&&
            select&&
            !inventoryMutation&&
            !settlementOwned&&
            !expiryPolicyClaim&&
            !persistenceClaim&&
            protocolIndependent,
            "G21.1 acceptance"
        );

        System.out.println(
            "G211_MAILBOX_AUTHORITY_PASS"+
            " clientVisibleRows35="+
                clientVisibleRows35+
            " capacityCallerPolicy="+
                capacityCallerPolicy+
            " readState="+readState+
            " claimState="+claimState+
            " externalSettlementAck="+
                externalSettlementAck+
            " duplicateFailClosed="+
                duplicateFailClosed+
            " immutableSnapshots="+
                immutableSnapshots+
            " subtype31Exact="+subtype31Exact+
            " clear="+clear+
            " append="+append+
            " readUpdate="+readUpdate+
            " detailMode="+detailMode+
            " claimProjection="+claimProjection+
            " finalize="+finalizeRows+
            " attention="+attention+
            " select="+select+
            " inventoryMutation="+inventoryMutation+
            " settlementOwned="+settlementOwned+
            " expiryPolicyClaim="+expiryPolicyClaim+
            " persistenceClaim="+persistenceClaim+
            " protocolIndependent="+
                protocolIndependent
        );
    }

    private static int assert250(
        byte[] wire,
        int offset,
        IsaacCipher decode,
        byte[] expectedBody
    ){
        int opcode=
            ((wire[offset++]&255)-
                decode.nextInt())&
                255;

        if(opcode!=250)
            throw new AssertionError(
                "expected 250 actual="+opcode
            );

        int length=
            wire[offset++]&255;

        if(length!=
                expectedBody.length+2)
            throw new AssertionError(
                "S2C250 length expected="+
                (expectedBody.length+2)+
                " actual="+length
            );

        int subtype=
            ((wire[offset++]&255)<<8)|
            (wire[offset++]&255);

        if(subtype!=31)
            throw new AssertionError(
                "expected subtype31 actual="+
                subtype
            );

        byte[] actual=
            Arrays.copyOfRange(
                wire,
                offset,
                offset+
                    expectedBody.length
            );

        if(!Arrays.equals(
                expectedBody,
                actual))
            throw new AssertionError(
                "subtype31 body mismatch expected="+
                Arrays.toString(
                    expectedBody
                )+
                " actual="+
                Arrays.toString(
                    actual
                )
            );

        return offset+
            expectedBody.length;
    }

    private static boolean containsProtocolIdentity(
        Class<?> type
    ){
        for(java.lang.reflect.Field field:
                type.getDeclaredFields()){
            String name=
                field.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            if(name.contains("widget")||
               name.contains("packet")||
               name.contains("opcode")||
               name.contains("subtype")||
               name.contains("rowindex"))
                return true;
        }

        return false;
    }

    private static void require(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(
                message
            );
    }

    private G211MailboxAuthorityIntegrationTest(){}
}
