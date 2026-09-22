package spk.local;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

public final class ClientFixedSemanticResidualRequestQueueTest {
    private static final int[] SEED={
        0x24681357,
        0x10293847,
        0x55667788,
        0x0BADF00D
    };

    public static void main(String[] args)throws Exception{
        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();

        IsaacCipher encoder=
            new IsaacCipher(
                SEED.clone()
            );

        long nameEntryKey=
            0x0011223344556677L;
        long reportKey=
            0x0077665544332211L;

        writeNameEntry(
            wire,
            encoder,
            nameEntryKey
        );
        writeChatModes(
            wire,
            encoder,
            2,
            1,
            0
        );
        writeReport(
            wire,
            encoder,
            reportKey,
            9,
            1
        );

        ClientPacketProbe probe=
            new ClientPacketProbe(
                new ByteArrayInputStream(
                    wire.toByteArray()
                ),
                new IsaacCipher(
                    SEED.clone()
                ),
                "[typed-fixed-residuals] "
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

        NameEntryClientRequest nameEntry=
            assertNameEntry(
                probe.takeTypedRequest(),
                nameEntryKey
            );

        ChatModeClientRequest chatMode=
            assertChatMode(
                probe.takeTypedRequest(),
                2,
                1,
                0
            );

        ReportAbuseClientRequest report=
            assertReport(
                probe.takeTypedRequest(),
                reportKey,
                9,
                1
            );

        if(probe.takeTypedRequest()!=null)
            throw new AssertionError(
                "typed request queue not empty"
            );

        for(int opcode:
                new int[]{
                    60,
                    95,
                    218
                })
            if(ClientPacketProbe
                    .framingOnlyFixedLength(
                        opcode
                    )>=0)
                throw new AssertionError(
                    "semantic residual remained framing-only opcode="+
                    opcode
                );

        assertDiagnostic(
            LocalPendingRequestDispatcher
                .nameEntryFailClosedDiagnostic(
                    nameEntry
                ),
            "NAME_ENTRY_REQUEST_FAIL_CLOSED",
            "ACTIVE_NAME_PROMPT_AND_ACCOUNT_MAPPING_UNPROVEN"
        );

        assertDiagnostic(
            LocalPendingRequestDispatcher
                .chatModeFailClosedDiagnostic(
                    chatMode
                ),
            "CHAT_MODE_REQUEST_FAIL_CLOSED",
            "SERVER_CHAT_MODE_POLICY_UNPROVEN"
        );

        assertDiagnostic(
            LocalPendingRequestDispatcher
                .reportAbuseFailClosedDiagnostic(
                    report
                ),
            "REPORT_ABUSE_REQUEST_FAIL_CLOSED",
            "ACCOUNT_MAPPING_AND_MODERATION_POLICY_UNPROVEN"
        );

        System.out.println(
            "CLIENT_FIXED_SEMANTIC_RESIDUAL_REQUEST_QUEUE_PASS "+
            "opcodes=60_95_218 "+
            "rawFieldsPreserved=true "+
            "fifo=true "+
            "metadata=true "+
            "framingOnlyRetired=true "+
            "domainMutationFailClosed=true"
        );
    }

    private static NameEntryClientRequest
        assertNameEntry(
            ClientRequest request,
            long expectedKey
        ){
        if(!(request instanceof
                NameEntryClientRequest))
            throw new AssertionError(
                "name-entry request="+request
            );

        NameEntryClientRequest typed=
            (NameEntryClientRequest)request;

        if(typed.nameKey()!=expectedKey)
            throw new AssertionError(
                "name-entry key expected="+
                Long.toUnsignedString(expectedKey)+
                " actual="+
                Long.toUnsignedString(
                    typed.nameKey()
                )
            );

        assertMetadata(
            typed.metadata(),
            60,
            "FIXED8_NAME_KEY_I64_BE",
            "V308_CLIENT_GENERIC_NAME_ENTRY_WRITER"
        );

        return typed;
    }

    private static ChatModeClientRequest
        assertChatMode(
            ClientRequest request,
            int mode0,
            int mode1,
            int mode2
        ){
        if(!(request instanceof
                ChatModeClientRequest))
            throw new AssertionError(
                "chat-mode request="+request
            );

        ChatModeClientRequest typed=
            (ChatModeClientRequest)request;

        if(typed.mode0()!=mode0||
           typed.mode1()!=mode1||
           typed.mode2()!=mode2)
            throw new AssertionError(
                "chat-mode values="+typed
            );

        assertMetadata(
            typed.metadata(),
            95,
            "FIXED3_CHAT_MODE_U8_U8_U8",
            "V308_CLIENT_CHAT_MODE_WRITER"
        );

        return typed;
    }

    private static ReportAbuseClientRequest
        assertReport(
            ClientRequest request,
            long expectedKey,
            int ruleIndex,
            int muteToggle
        ){
        if(!(request instanceof
                ReportAbuseClientRequest))
            throw new AssertionError(
                "report request="+request
            );

        ReportAbuseClientRequest typed=
            (ReportAbuseClientRequest)request;

        if(typed.nameKey()!=expectedKey||
           typed.ruleIndex()!=ruleIndex||
           typed.muteToggle()!=muteToggle)
            throw new AssertionError(
                "report values="+typed
            );

        assertMetadata(
            typed.metadata(),
            218,
            "FIXED10_NAME_KEY_I64_BE_RULE_U8_MUTE_U8",
            "V308_CLIENT_REPORT_ABUSE_WRITER"
        );

        return typed;
    }

    private static void assertMetadata(
        ClientRequestMetadata metadata,
        int opcode,
        String schema,
        String source
    ){
        if(metadata.opcode!=opcode||
           !schema.equals(metadata.schema)||
           !source.equals(metadata.source)||
           metadata.provenance!=
                ClientRequestProvenance
                    .EXACT_CURRENT_CLIENT)
            throw new AssertionError(
                "metadata="+metadata
            );
    }

    private static void assertDiagnostic(
        String value,
        String prefix,
        String reason
    ){
        if(value==null||
           !value.startsWith(prefix)||
           !value.contains(
                "reason="+reason
           )||
           !value.contains(
                "stateMutation=false"
           )||
           !value.contains(
                "authority=EXACT_CURRENT_CLIENT"
           ))
            throw new AssertionError(
                "fail-closed diagnostic="+value
            );
    }

    private static void writeNameEntry(
        OutputStream output,
        IsaacCipher cipher,
        long nameKey
    )throws Exception{
        writeOpcode(
            output,
            cipher,
            60
        );
        Binary.put64(
            output,
            nameKey
        );
    }

    private static void writeChatModes(
        OutputStream output,
        IsaacCipher cipher,
        int mode0,
        int mode1,
        int mode2
    )throws Exception{
        writeOpcode(
            output,
            cipher,
            95
        );
        output.write(mode0);
        output.write(mode1);
        output.write(mode2);
    }

    private static void writeReport(
        OutputStream output,
        IsaacCipher cipher,
        long nameKey,
        int ruleIndex,
        int muteToggle
    )throws Exception{
        writeOpcode(
            output,
            cipher,
            218
        );
        Binary.put64(
            output,
            nameKey
        );
        output.write(ruleIndex);
        output.write(muteToggle);
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

    private ClientFixedSemanticResidualRequestQueueTest(){}
}
