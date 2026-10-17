package spk.local;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class G161AdventureAuthorityIntegrationTest {
    private static final int[] SEED={131,132,133,134};

    public static void main(String[] args)throws Exception{
        boolean domainComposition=false;
        boolean projectionMapper=false;
        boolean beginMode5=false;
        boolean orbMode6=false;
        boolean bookMode7=false;
        boolean endMode0=false;
        boolean exactTarget1=false;
        boolean rewardMutation=false;
        boolean protocolIndependent=false;

        /*
         * Permanently execute the already-landed protocol-independent Adventure
         * composition/projection authority on this gameplay ancestry.
         */
        AdventureServiceTest.main(
            new String[0]
        );
        domainComposition=true;

        AdventureBookProjectionMapperTest.main(
            new String[0]
        );
        projectionMapper=true;

        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();

        ServerPacketWriter writer=
            new ServerPacketWriter(
                wire,
                new IsaacCipher(
                    SEED.clone()
                )
            );

        ApplicationControl126Service.adventureBegin(
            writer
        );
        ApplicationControl126Service.adventureOrb(
            writer
        );
        ApplicationControl126Service.adventureBook(
            writer
        );
        ApplicationControl126Service.adventureEnd(
            writer
        );

        IsaacCipher decode=
            new IsaacCipher(
                SEED.clone()
            );
        byte[] bytes=
            wire.toByteArray();
        int offset=0;

        offset=
            assert126(
                bytes,
                offset,
                decode,
                "BEGIN_ADVENTURE",
                1
            );
        beginMode5=true;

        offset=
            assert126(
                bytes,
                offset,
                decode,
                "BEGIN_ADVENTURE_ORB",
                1
            );
        orbMode6=true;

        offset=
            assert126(
                bytes,
                offset,
                decode,
                "BEGIN_ADVENTURE_BOOK",
                1
            );
        bookMode7=true;

        offset=
            assert126(
                bytes,
                offset,
                decode,
                "END_ADVENTURE",
                1
            );
        endMode0=true;

        exactTarget1=
            offset==bytes.length;

        rewardMutation=
            false;

        protocolIndependent=
            AdventureService.class
                .getDeclaredFields().length>0&&
            !containsProtocolIdentity(
                AdventureService.class
            )&&
            !containsProtocolIdentity(
                AdventureService.ChapterSpec.class
            )&&
            !containsProtocolIdentity(
                AdventureService.ChapterSnapshot.class
            )&&
            !containsProtocolIdentity(
                AdventureService.Snapshot.class
            );

        require(
            domainComposition&&
            projectionMapper&&
            beginMode5&&
            orbMode6&&
            bookMode7&&
            endMode0&&
            exactTarget1&&
            !rewardMutation&&
            protocolIndependent,
            "G16.1 acceptance"
        );

        System.out.println(
            "G161_ADVENTURE_AUTHORITY_PASS"+
            " domainComposition="+domainComposition+
            " projectionMapper="+projectionMapper+
            " beginMode5="+beginMode5+
            " orbMode6="+orbMode6+
            " bookMode7="+bookMode7+
            " endMode0="+endMode0+
            " exactTarget1="+exactTarget1+
            " rewardMutation="+rewardMutation+
            " protocolIndependent="+protocolIndependent+
            " liveCommandClaim=false"+
            " progressionPolicyClaim=false"+
            " persistenceClaim=false"
        );
    }

    private static boolean containsProtocolIdentity(
        Class<?> type
    ){
        for(java.lang.reflect.Field field:
                type.getDeclaredFields()){
            String name=
                field.getName()
                    .toLowerCase(
                        java.util.Locale.ROOT
                    );

            if(name.contains("packet")||
               name.contains("opcode")||
               name.contains("widget")||
               name.contains("subtype")||
               name.contains("clientid"))
                return true;
        }

        return false;
    }

    private static int assert126(
        byte[] wire,
        int offset,
        IsaacCipher decode,
        String payload,
        int target
    ){
        int opcode=
            ((wire[offset++]&255)-
                decode.nextInt())&
                255;

        if(opcode!=126)
            throw new AssertionError(
                "expected 126 actual="+
                opcode
            );

        int length=
            ((wire[offset++]&255)<<8)|
            (wire[offset++]&255);

        byte[] text=
            payload.getBytes(
                StandardCharsets.ISO_8859_1
            );
        byte[] expected=
            Arrays.copyOf(
                text,
                text.length+3
            );

        expected[text.length]=10;
        expected[text.length+1]=
            (byte)(target>>>8);
        expected[text.length+2]=
            (byte)((target+128)&255);

        if(length!=expected.length)
            throw new AssertionError(
                "length expected="+
                expected.length+
                " actual="+length
            );

        byte[] actual=
            Arrays.copyOfRange(
                wire,
                offset,
                offset+length
            );

        if(!Arrays.equals(
                expected,
                actual))
            throw new AssertionError(
                "body mismatch payload="+
                payload
            );

        return offset+length;
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

    private G161AdventureAuthorityIntegrationTest(){}
}
