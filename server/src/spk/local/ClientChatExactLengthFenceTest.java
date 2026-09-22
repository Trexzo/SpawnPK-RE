package spk.local;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Exact-v308 inbound authority fence for the rs.P encoder's hard 80-character
 * truncation boundary.
 */
public final class ClientChatExactLengthFenceTest {
    private static final int[] SEED={
        0x31415926,
        0x27182818,
        0x13572468,
        0x24681357
    };

    public static void main(String[] args)throws Exception{
        if(ClientChatTextCodec.MAX_ENCODED_CHARS!=80)
            throw new AssertionError(
                "chat max="+
                ClientChatTextCodec.MAX_ENCODED_CHARS
            );

        byte[] max=
            new byte[
                ClientChatTextCodec
                    .MAX_ENCODED_CHARS
            ];

        PublicChatClientRequest publicMax=
            requirePublic(
                decodePublic(max)
            );

        if(publicMax.message().length()!=80)
            throw new AssertionError(
                "public max message length="+
                publicMax.message().length()
            );

        PrivateMessageClientRequest privateMax=
            requirePrivate(
                decodePrivate(max)
            );

        if(privateMax.message().length()!=80)
            throw new AssertionError(
                "private max message length="+
                privateMax.message().length()
            );

        byte[] oversized=
            new byte[
                ClientChatTextCodec
                    .MAX_ENCODED_CHARS+1
            ];

        assertPublicRejected(oversized);
        assertPrivateRejected(oversized);

        System.out.println(
            "CLIENT_CHAT_EXACT_LENGTH_FENCE_PASS "+
            "maxChars=80 "+
            "public80Accepted=true "+
            "private80Accepted=true "+
            "public81Rejected=true "+
            "private81Rejected=true "+
            "queueUnchanged=true"
        );
    }

    private static ClientRequest decodePublic(
        byte[] encoded
    )throws Exception{
        ClientPacketProbe probe=
            publicProbe(encoded);

        if(!probe.readNextKnownPacket())
            throw new AssertionError(
                "public packet did not decode"
            );

        if(!probe.isAligned())
            throw new AssertionError(
                "public max packet lost alignment"
            );

        ClientRequest request=
            probe.takeTypedRequest();

        if(probe.takeTypedRequest()!=null)
            throw new AssertionError(
                "public typed queue had extra request"
            );

        return request;
    }

    private static ClientRequest decodePrivate(
        byte[] encoded
    )throws Exception{
        ClientPacketProbe probe=
            privateProbe(encoded);

        if(!probe.readNextKnownPacket())
            throw new AssertionError(
                "private packet did not decode"
            );

        if(!probe.isAligned())
            throw new AssertionError(
                "private max packet lost alignment"
            );

        ClientRequest request=
            probe.takeTypedRequest();

        if(probe.takeTypedRequest()!=null)
            throw new AssertionError(
                "private typed queue had extra request"
            );

        return request;
    }

    private static void assertPublicRejected(
        byte[] encoded
    )throws Exception{
        ClientPacketProbe probe=
            publicProbe(encoded);

        boolean rejected=false;

        try{
            probe.readNextKnownPacket();
        }catch(IOException expected){
            rejected=
                expected.getMessage()!=null&&
                expected.getMessage().contains(
                    "opcode4 invalid chat text"
                );
        }

        if(!rejected)
            throw new AssertionError(
                "public oversized payload accepted"
            );

        if(probe.typedRequestCount()!=0)
            throw new AssertionError(
                "public oversized payload mutated queue count="+
                probe.typedRequestCount()
            );
    }

    private static void assertPrivateRejected(
        byte[] encoded
    )throws Exception{
        ClientPacketProbe probe=
            privateProbe(encoded);

        boolean rejected=false;

        try{
            probe.readNextKnownPacket();
        }catch(IOException expected){
            rejected=
                expected.getMessage()!=null&&
                expected.getMessage().contains(
                    "opcode126 invalid chat text"
                );
        }

        if(!rejected)
            throw new AssertionError(
                "private oversized payload accepted"
            );

        if(probe.typedRequestCount()!=0)
            throw new AssertionError(
                "private oversized payload mutated queue count="+
                probe.typedRequestCount()
            );
    }

    private static PublicChatClientRequest
        requirePublic(
            ClientRequest request
        ){
        if(!(request instanceof
                PublicChatClientRequest))
            throw new AssertionError(
                "public request="+request
            );

        return (PublicChatClientRequest)request;
    }

    private static PrivateMessageClientRequest
        requirePrivate(
            ClientRequest request
        ){
        if(!(request instanceof
                PrivateMessageClientRequest))
            throw new AssertionError(
                "private request="+request
            );

        return (PrivateMessageClientRequest)request;
    }

    private static ClientPacketProbe publicProbe(
        byte[] encoded
    )throws Exception{
        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();
        IsaacCipher encoder=
            new IsaacCipher(
                SEED.clone()
            );

        writeOpcode(
            wire,
            encoder,
            4
        );
        wire.write(
            encoded.length+2
        );
        wire.write(128);
        wire.write(128);

        for(int i=encoded.length-1;
            i>=0;
            i--)
            wire.write(
                ((encoded[i]&255)+128)&255
            );

        return new ClientPacketProbe(
            new ByteArrayInputStream(
                wire.toByteArray()
            ),
            new IsaacCipher(
                SEED.clone()
            ),
            "[chat-length-public] "
        );
    }

    private static ClientPacketProbe privateProbe(
        byte[] encoded
    )throws Exception{
        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();
        IsaacCipher encoder=
            new IsaacCipher(
                SEED.clone()
            );

        writeOpcode(
            wire,
            encoder,
            126
        );
        wire.write(
            encoded.length+8
        );
        Binary.put64(
            wire,
            0x00123456789ABCDEFL
        );
        wire.write(encoded);

        return new ClientPacketProbe(
            new ByteArrayInputStream(
                wire.toByteArray()
            ),
            new IsaacCipher(
                SEED.clone()
            ),
            "[chat-length-private] "
        );
    }

    private static void writeOpcode(
        OutputStream output,
        IsaacCipher cipher,
        int opcode
    )throws Exception{
        output.write(
            (opcode+cipher.nextInt())&255
        );
    }

    private ClientChatExactLengthFenceTest(){}
}
