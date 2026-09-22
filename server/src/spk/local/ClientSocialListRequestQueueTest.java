package spk.local;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

public final class ClientSocialListRequestQueueTest {
    private static final int[] SEED={
        0x01020304,
        0x11223344,
        0x55667788,
        0x13572468
    };

    public static void main(String[] args)throws Exception{
        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();

        IsaacCipher encoder=
            new IsaacCipher(
                SEED.clone()
            );

        long addFriend=
            0x00123456789ABCDEFL;
        long removeFriend=
            0x0023456789ABCDEFL;
        long addIgnore=
            0x003456789ABCDEF0L;
        long removeIgnore=
            0x00456789ABCDEF01L;

        writeSocial(
            wire,
            encoder,
            188,
            addFriend
        );
        writeSocial(
            wire,
            encoder,
            215,
            removeFriend
        );
        writeSocial(
            wire,
            encoder,
            133,
            addIgnore
        );
        writeSocial(
            wire,
            encoder,
            74,
            removeIgnore
        );

        ClientPacketProbe probe=
            new ClientPacketProbe(
                new ByteArrayInputStream(
                    wire.toByteArray()
                ),
                new IsaacCipher(
                    SEED.clone()
                ),
                "[typed-social] "
            );

        for(int i=0;i<4;i++)
            if(!probe.readNextKnownPacket())
                throw new AssertionError(
                    "social decode stopped index="+
                    i
                );

        if(!probe.isAligned())
            throw new AssertionError(
                "decoder lost alignment"
            );

        if(probe.typedRequestCount()!=4)
            throw new AssertionError(
                "typed request count="+
                probe.typedRequestCount()
            );

        assertSocial(
            probe.takeTypedRequest(),
            188,
            SocialListClientRequest.Action.ADD_FRIEND,
            addFriend,
            "V308_CLIENT_ADD_FRIEND_WRITER"
        );
        assertSocial(
            probe.takeTypedRequest(),
            215,
            SocialListClientRequest.Action.REMOVE_FRIEND,
            removeFriend,
            "V308_CLIENT_REMOVE_FRIEND_WRITER"
        );
        assertSocial(
            probe.takeTypedRequest(),
            133,
            SocialListClientRequest.Action.ADD_IGNORE,
            addIgnore,
            "V308_CLIENT_ADD_IGNORE_WRITER"
        );
        SocialListClientRequest last=
            assertSocial(
                probe.takeTypedRequest(),
                74,
                SocialListClientRequest.Action.REMOVE_IGNORE,
                removeIgnore,
                "V308_CLIENT_REMOVE_IGNORE_WRITER"
            );

        if(probe.takeTypedRequest()!=null)
            throw new AssertionError(
                "typed request queue not empty"
            );

        for(int opcode:
                new int[]{
                    74,
                    133,
                    188,
                    215
                })
            if(ClientPacketProbe
                    .framingOnlyFixedLength(
                        opcode
                    )>=0)
                throw new AssertionError(
                    "social opcode remained framing-only opcode="+
                    opcode
                );

        String diagnostic=
            LocalPendingRequestDispatcher
                .socialListFailClosedDiagnostic(
                    last
                );

        if(!diagnostic.contains(
                "action=REMOVE_IGNORE")||
           !diagnostic.contains(
                "nameKey="+
                Long.toUnsignedString(
                    removeIgnore
                ))||
           !diagnostic.contains(
                "reason=NAME_KEY_ACCOUNT_MAPPING_UNPROVEN")||
           !diagnostic.contains(
                "stateMutation=false")||
           !diagnostic.contains(
                "authority=EXACT_CURRENT_CLIENT"))
            throw new AssertionError(
                "fail-closed diagnostic="+
                diagnostic
            );

        System.out.println(
            "CLIENT_SOCIAL_LIST_REQUEST_QUEUE_PASS "+
            "opcodes=188_215_133_74 "+
            "i64Preserved=true "+
            "fifo=true "+
            "metadata=true "+
            "framingOnlyRetired=true "+
            "domainMutationFailClosed=true"
        );
    }

    private static SocialListClientRequest
        assertSocial(
            ClientRequest request,
            int opcode,
            SocialListClientRequest.Action action,
            long nameKey,
            String source
        ){
        if(!(request instanceof
                SocialListClientRequest))
            throw new AssertionError(
                "request type="+
                request
            );

        SocialListClientRequest social=
            (SocialListClientRequest)request;

        if(social.action()!=action||
           social.nameKey()!=nameKey)
            throw new AssertionError(
                "social request expected action="+
                action+
                " key="+
                Long.toUnsignedString(nameKey)+
                " actual="+social
            );

        ClientRequestMetadata metadata=
            social.metadata();

        if(metadata.opcode!=opcode||
           !"FIXED8_NAME_KEY_I64_BE".equals(
                metadata.schema
           )||
           !source.equals(
                metadata.source
           )||
           metadata.provenance!=
                ClientRequestProvenance
                    .EXACT_CURRENT_CLIENT)
            throw new AssertionError(
                "social metadata="+
                metadata
            );

        return social;
    }

    private static void writeSocial(
        OutputStream output,
        IsaacCipher cipher,
        int opcode,
        long nameKey
    )throws Exception{
        output.write(
            (opcode+cipher.nextInt())&255
        );
        Binary.put64(
            output,
            nameKey
        );
    }

    private ClientSocialListRequestQueueTest(){}
}
