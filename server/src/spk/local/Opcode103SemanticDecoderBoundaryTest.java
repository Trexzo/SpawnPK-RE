package spk.local;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public final class Opcode103SemanticDecoderBoundaryTest {
    public static void main(String[] args)throws Exception{
        exactRouterCompatibility();
        transportOnlyDecode();
        sourceBoundary();

        System.out.println(
            "OPCODE103_SEMANTIC_DECODER_BOUNDARY_PASS "+
            "transportCommandOnly=true "+
            "dialogueAliasDownstream=true "+
            "exactAliasCompatibility=true "+
            "invalidAliasFallsThrough=true "+
            "decoderLiteralAbsent=true "+
            "decoderSemanticTypeAbsent=true "+
            "pendingRouterPresent=true"
        );
    }

    private static void exactRouterCompatibility(){
        require(
            ClientCommandSemanticRouter
                .dialogueOptionIndex(
                    "dialogueoption 1"
                )==1,
            "option 1"
        );

        require(
            ClientCommandSemanticRouter
                .dialogueOptionIndex(
                    "dialogueoption 5"
                )==5,
            "option 5"
        );

        String[] invalid={
            null,
            "",
            "dialogueoption",
            "dialogueoption ",
            "dialogueoption 0",
            "dialogueoption 6",
            "dialogueoption 1 extra",
            "DialogueOption 1",
            " dialogueoption 1"
        };

        for(String value:invalid)
            require(
                ClientCommandSemanticRouter
                    .dialogueOptionIndex(
                        value
                    )==-1,
                "invalid alias="+value
            );
    }

    private static void transportOnlyDecode()
        throws Exception{
        int[] seed={31,32,33,34};
        IsaacCipher encoder=
            new IsaacCipher(
                seed.clone()
            );
        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();

        int encoded=
            (103+
                encoder.nextInt())&
            255;
        wire.write(encoded);

        byte[] text=
            "dialogueoption 3\n".getBytes(
                StandardCharsets.ISO_8859_1
            );

        wire.write(
            text.length
        );
        wire.write(text);

        ClientPacketProbe probe=
            new ClientPacketProbe(
                new ByteArrayInputStream(
                    wire.toByteArray()
                ),
                new IsaacCipher(
                    seed.clone()
                ),
                "[opcode103-boundary] "
            );

        require(
            probe.readNextKnownPacket(),
            "opcode103 decode"
        );

        ClientRequest request=
            probe.takeTypedRequest();

        require(
            request instanceof
                CommandClientRequest,
            "opcode103 request type="+
            request
        );

        CommandClientRequest command=
            (CommandClientRequest)
                request;

        require(
            "dialogueoption 3".equals(
                command.command()),
            "command text="+
            command.command()
        );

        ClientRequestMetadata metadata=
            command.metadata();

        require(
            metadata.opcode==103&&
            "VAR_BYTE_ISO_8859_1_OPTIONAL_LF"
                .equals(
                    metadata.schema)&&
            metadata.provenance==
                ClientRequestProvenance
                    .EXACT_CURRENT_CLIENT,
            "metadata="+metadata
        );

        require(
            probe.typedRequestCount()==0&&
            probe.isAligned(),
            "probe state"
        );
    }

    private static void sourceBoundary()
        throws Exception{
        String decoder=
            source(
                "server/src/spk/local/ClientPacketProbe.java"
            );

        require(
            !decoder.contains(
                "\"dialogueoption \""),
            "dialogue command literal remains in packet decoder"
        );

        require(
            !decoder.contains(
                "dialogueOptionIndex("),
            "dialogue semantic parser remains in packet decoder"
        );

        require(
            !decoder.contains(
                "DialogueOptionClientRequest"),
            "dialogue semantic request remains in packet decoder"
        );

        String pending=
            source(
                "server/src/spk/local/LocalPendingRequestDispatcher.java"
            );

        int semantic=
            pending.indexOf(
                "ClientCommandSemanticRouter"
            );
        int commandDispatch=
            pending.indexOf(
                "commandDispatcher.handle("
            );

        require(
            semantic>=0&&
            commandDispatch>semantic,
            "semantic classification must precede generic command dispatch"
        );

        require(
            pending.contains(
                "makeoverMage.handleOption("),
            "dialogue option runtime route missing"
        );
    }

    private static String source(
        String path
    )throws Exception{
        return new String(
            java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(
                    path
                )
            ),
            StandardCharsets.UTF_8
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(
                label
            );
    }

    private Opcode103SemanticDecoderBoundaryTest(){}
}
