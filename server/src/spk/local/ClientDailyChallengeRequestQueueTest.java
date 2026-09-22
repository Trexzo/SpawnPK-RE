package spk.local;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class ClientDailyChallengeRequestQueueTest {
    private static final int[] SEED={
        0x12345678,
        0x10293847,
        0x55667788,
        0x13579BDF
    };

    public static void main(String[] args)throws Exception{
        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();

        IsaacCipher encoder=
            new IsaacCipher(
                SEED.clone()
            );

        writeCommand(
            wire,
            encoder,
            "claimchallenge alpha_key"
        );
        writeCommand(
            wire,
            encoder,
            "::ordinary"
        );
        writeCommand(
            wire,
            encoder,
            "infochallenge Beta-Key"
        );

        ClientPacketProbe probe=
            new ClientPacketProbe(
                new ByteArrayInputStream(
                    wire.toByteArray()
                ),
                new IsaacCipher(
                    SEED.clone()
                ),
                "[typed-daily-challenge] "
            );

        for(int i=0;i<3;i++)
            if(!probe.readNextKnownPacket())
                throw new AssertionError(
                    "decode stopped index="+i
                );

        if(!probe.isAligned())
            throw new AssertionError(
                "decoder lost alignment"
            );

        if(probe.typedRequestCount()!=3)
            throw new AssertionError(
                "typed request count="+
                probe.typedRequestCount()
            );

        DailyChallengeClientRequest claim=
            assertDaily(
                probe.takeTypedRequest(),
                DailyChallengeClientRequest
                    .Action.CLAIM,
                "alpha_key"
            );

        assertGeneric(
            probe.takeTypedRequest(),
            "::ordinary"
        );

        DailyChallengeClientRequest info=
            assertDaily(
                probe.takeTypedRequest(),
                DailyChallengeClientRequest
                    .Action.INFO,
                "Beta-Key"
            );

        if(probe.takeTypedRequest()!=null)
            throw new AssertionError(
                "typed request queue not empty"
            );

        if(ClientPacketProbe
                .dailyChallengeRequest(
                    "claimchallenge "
                )!=null)
            throw new AssertionError(
                "empty challenge key was normalized"
            );

        if(ClientPacketProbe
                .dailyChallengeRequest(
                    "CLAIMCHALLENGE alpha_key"
                )!=null)
            throw new AssertionError(
                "non-exact compatibility prefix was normalized"
            );

        assertDiagnostic(
            LocalPendingRequestDispatcher
                .dailyChallengeFailClosedDiagnostic(
                    claim
                ),
            "CLAIM",
            "alpha_key"
        );

        assertDiagnostic(
            LocalPendingRequestDispatcher
                .dailyChallengeFailClosedDiagnostic(
                    info
                ),
            "INFO",
            "Beta-Key"
        );

        System.out.println(
            "CLIENT_DAILY_CHALLENGE_REQUEST_QUEUE_PASS "+
            "opcode=103 "+
            "claimTyped=true "+
            "infoTyped=true "+
            "stringKeyPreserved=true "+
            "genericCommandPreserved=true "+
            "fifo=true "+
            "metadata=true "+
            "domainMutationFailClosed=true"
        );
    }

    private static DailyChallengeClientRequest
        assertDaily(
            ClientRequest request,
            DailyChallengeClientRequest.Action action,
            String key
        ){
        if(!(request instanceof
                DailyChallengeClientRequest))
            throw new AssertionError(
                "request type="+request
            );

        DailyChallengeClientRequest typed=
            (DailyChallengeClientRequest)request;

        if(typed.action()!=action||
           !key.equals(
                typed.challengeKey()
           ))
            throw new AssertionError(
                "daily challenge request="+typed
            );

        ClientRequestMetadata metadata=
            typed.metadata();

        if(metadata.opcode!=103||
           !"VAR_BYTE_DAILY_CHALLENGE_ACTION_KEY_OPTIONAL_LF"
                .equals(metadata.schema)||
           !"V308_CLIENT_DAILY_CHALLENGE_COMPAT_COMMAND"
                .equals(metadata.source)||
           metadata.provenance!=
                ClientRequestProvenance
                    .EXACT_CURRENT_CLIENT)
            throw new AssertionError(
                "daily challenge metadata="+
                metadata
            );

        return typed;
    }

    private static void assertGeneric(
        ClientRequest request,
        String command
    ){
        if(!(request instanceof
                CommandClientRequest))
            throw new AssertionError(
                "ordinary command was normalized type="+
                request
            );

        CommandClientRequest typed=
            (CommandClientRequest)request;

        if(!command.equals(
                typed.command()))
            throw new AssertionError(
                "ordinary command expected="+
                command+
                " actual="+
                typed.command()
            );
    }

    private static void assertDiagnostic(
        String value,
        String action,
        String key
    ){
        if(value==null||
           !value.startsWith(
                "DAILY_CHALLENGE_REQUEST_FAIL_CLOSED"
           )||
           !value.contains(
                "action="+action
           )||
           !value.contains(
                "challengeKey="+key
           )||
           !value.contains(
                "reason=DAILY_CHALLENGE_KEY_ADAPTER_UNPROVEN"
           )||
           !value.contains(
                "stateMutation=false"
           )||
           !value.contains(
                "authority=EXACT_CURRENT_CLIENT"
           ))
            throw new AssertionError(
                "diagnostic="+value
            );
    }

    private static void writeCommand(
        OutputStream output,
        IsaacCipher cipher,
        String command
    )throws IOException{
        byte[] text=
            command.getBytes(
                StandardCharsets.ISO_8859_1
            );

        int length=
            text.length+1;

        if(length>255)
            throw new IllegalArgumentException(
                "test command too long"
            );

        output.write(
            (103+cipher.nextInt())&255
        );
        output.write(length);
        output.write(text);
        output.write(10);
    }

    private ClientDailyChallengeRequestQueueTest(){}
}
