package spk.local;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class G162AdventureLiveOpenIntegrationTest {
    private static final int[] SEED={141,142,143,144};

    public static void main(String[] args)throws Exception{
        boolean exactCommand=false;
        boolean bookMode7=false;
        boolean chapterReset=false;
        boolean secondaryReset=false;
        boolean emptyProjection=false;
        boolean interface97Claim=false;
        boolean inputRouter=false;

        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();

        ServerPacketWriter writer=
            new ServerPacketWriter(
                wire,
                new IsaacCipher(
                    SEED.clone()
                )
            );

        final boolean[] bridgeOpened={false};

        LocalCommandDispatcher.SessionBridge bridge=
            new LocalCommandDispatcher.SessionBridge(){
                @Override public SceneUpdatePublisher scenePublisher(){
                    return null;
                }

                @Override public void replaceScenePublisher(
                    SceneUpdatePublisher replacement
                ){}

                @Override public void saveAccount(
                    String tag,
                    String reason
                ){}

                @Override public void openDevPanel(
                    ServerPacketWriter serverPackets
                ){}

                @Override public boolean openAdventureBook(
                    ServerPacketWriter serverPackets
                )throws java.io.IOException{
                    bridgeOpened[0]=true;
                    AdventureBookPresentation.openEmpty(
                        serverPackets
                    );
                    return true;
                }

                @Override public void applyPetDialog(
                    LocalPetInventoryDialogHandler.Result result,
                    String tag
                ){}
            };

        boolean handled=
            LocalCommandDispatcher
                .dispatchAdventureBookCommand(
                    new String[]{"adventurebook"},
                    bridge,
                    writer,
                    "[g162] "
                );

        exactCommand=
            handled&&
            bridgeOpened[0]&&
            LocalCommandDispatcher
                .isAdventureBookRoute(
                    new String[]{"adventurebook"}
                )&&
            LocalCommandDispatcher
                .isAdventureBookRoute(
                    new String[]{"ADVENTUREBOOK"}
                )&&
            !LocalCommandDispatcher
                .isAdventureBookRoute(
                    new String[]{"adventure"}
                )&&
            !LocalCommandDispatcher
                .isAdventureBookRoute(
                    new String[]{
                        "adventurebook",
                        "extra"
                    }
                );

        byte[] bytes=
            wire.toByteArray();
        IsaacCipher decode=
            new IsaacCipher(
                SEED.clone()
            );
        int offset=0;

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
            assert250(
                bytes,
                offset,
                decode,
                22,
                new byte[]{0}
            );
        chapterReset=true;

        offset=
            assert250(
                bytes,
                offset,
                decode,
                22,
                new byte[]{2}
            );
        secondaryReset=true;

        emptyProjection=
            offset==bytes.length;

        interface97Claim=
            false;

        inputRouter=
            hasAdventureInputRouter();

        require(
            exactCommand&&
            bookMode7&&
            chapterReset&&
            secondaryReset&&
            emptyProjection&&
            !interface97Claim&&
            !inputRouter,
            "G16.2 acceptance"
        );

        System.out.println(
            "G162_ADVENTURE_LIVE_OPEN_PASS"+
            " exactCommand="+exactCommand+
            " bookMode7="+bookMode7+
            " chapterReset="+chapterReset+
            " secondaryReset="+secondaryReset+
            " emptyProjection="+emptyProjection+
            " interface97Claim="+interface97Claim+
            " inputRouter="+inputRouter+
            " objectiveCatalogClaim=false"+
            " rewardPolicyClaim=false"+
            " teleportPolicyClaim=false"+
            " persistenceClaim=false"+
            " originalNavigationClaim=false"
        );
    }

    private static boolean hasAdventureInputRouter(){
        for(java.lang.reflect.Method method:
                AdventureBookPresentation.class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase(
                        java.util.Locale.ROOT
                    );

            if(name.contains("resolvewidget")||
               name.contains("handleclick")||
               name.contains("dispatchinput"))
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
                "expected opcode126 actual="+
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
                "126 length"
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
                "126 body"
            );

        return offset+length;
    }

    private static int assert250(
        byte[] wire,
        int offset,
        IsaacCipher decode,
        int subtype,
        byte[] operationBody
    ){
        int opcode=
            ((wire[offset++]&255)-
                decode.nextInt())&
                255;

        if(opcode!=250)
            throw new AssertionError(
                "expected opcode250 actual="+
                opcode
            );

        int length=
            wire[offset++]&255;

        int expectedLength=
            2+
            operationBody.length;

        if(length!=expectedLength)
            throw new AssertionError(
                "250 length expected="+
                expectedLength+
                " actual="+
                length
            );

        int actualSubtype=
            ((wire[offset++]&255)<<8)|
            (wire[offset++]&255);

        if(actualSubtype!=subtype)
            throw new AssertionError(
                "250 subtype expected="+
                subtype+
                " actual="+
                actualSubtype
            );

        for(byte expected:operationBody){
            int actual=
                wire[offset++]&255;

            if(actual!=(expected&255))
                throw new AssertionError(
                    "250 operation body"
                );
        }

        return offset;
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

    private G162AdventureLiveOpenIntegrationTest(){}
}
