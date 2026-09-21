package spk.local;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class ApplicationControl126ServiceTest {
    private static final int[] SEED=
        new int[]{71,72,73,74};

    public static void main(String[] args)throws Exception{
        assertRecoveredTargets();
        assertRecoveredGrammars();
        assertExactFraming();
        assertValidation();
        assertProtocolSurfaceInternal();

        System.out.println(
            "APPLICATION_CONTROL_126_SERVICE_PASS "+
            "globalTarget=1 "+
            "collectionTargets=54315,54421,54422 "+
            "newline=true "+
            "targetTransform=shortA "+
            "typedControls=true "+
            "publicTargetLeak=false "+
            "authority=EXACT_CURRENT_CLIENT"
        );
    }

    private static void assertRecoveredTargets(){
        assertTarget(
            ApplicationControl126Command.Target
                .GLOBAL_CONTROL,
            1
        );
        assertTarget(
            ApplicationControl126Command.Target
                .COLLECTION_CLEAR_ROWS,
            54315
        );
        assertTarget(
            ApplicationControl126Command.Target
                .COLLECTION_SELECT_ROW,
            54421
        );
        assertTarget(
            ApplicationControl126Command.Target
                .COLLECTION_SELECT_CATEGORY,
            54422
        );
    }

    private static void assertRecoveredGrammars(){
        assertPayload(
            ApplicationControl126Command.token(
                ApplicationControl126Command.Token
                    .CONSTRUCTION_BUILD_ON
            ),
            "CONSTRUCTION_BUILD_ON",
            1
        );

        assertPayload(
            ApplicationControl126Command.token(
                ApplicationControl126Command.Token
                    .RAID_INSTANCE_OFF
            ),
            "RAID_INSTANCE_OFF",
            1
        );

        assertPayload(
            ApplicationControl126Command.token(
                ApplicationControl126Command.Token
                    .CLEAR_ACHIEVEMENT_TAB
            ),
            "CLEAR_ACHIEVEMENT_TAB",
            1
        );

        assertPayload(
            ApplicationControl126Command.addExchange(
                4151,
                27
            ),
            "add_exchange 4151,27",
            1
        );

        assertPayload(
            ApplicationControl126Command.setSellItem(
                4151
            ),
            "setsellitem,4151",
            1
        );

        assertPayload(
            ApplicationControl126Command.loginRewardIndex(
                7
            ),
            "LOGIN_REWARD_IDX 7",
            1
        );

        assertPayload(
            ApplicationControl126Command.itemGuideSelected(
                47505
            ),
            "ITEM_GUIDE_SELECTED_47505",
            1
        );

        assertPayload(
            ApplicationControl126Command.wikiSelected(
                46506
            ),
            "WIKI_SELECTED_46506",
            1
        );

        assertPayload(
            ApplicationControl126Command.clearClanChat(
                18144
            ),
            "clearcc 18144",
            1
        );

        assertPayload(
            ApplicationControl126Command.collectionClearRows(),
            "",
            54315
        );

        assertPayload(
            ApplicationControl126Command.collectionSelectRow(
                54314
            ),
            "54314",
            54421
        );

        assertPayload(
            ApplicationControl126Command
                .collectionSelectCategory(
                    54302
                ),
            "54302",
            54422
        );
    }

    private static void assertExactFraming()
        throws Exception{
        ByteArrayOutputStream out=
            new ByteArrayOutputStream();

        ServerPacketWriter writer=
            new ServerPacketWriter(
                out,
                new IsaacCipher(
                    SEED.clone()
                )
            );

        ApplicationControl126Service
            .constructionBuild(
                writer,
                true
            );

        ApplicationControl126Service
            .exchangeAdd(
                writer,
                4151,
                27
            );

        ApplicationControl126Service
            .publish(
                writer,
                ApplicationControl126Command
                    .collectionSelectRow(
                        54314
                    )
            );

        byte[] wire=
            out.toByteArray();

        IsaacCipher decode=
            new IsaacCipher(
                SEED.clone()
            );

        int offset=0;

        offset=assertPacket(
            wire,
            offset,
            decode,
            "CONSTRUCTION_BUILD_ON",
            1
        );

        offset=assertPacket(
            wire,
            offset,
            decode,
            "add_exchange 4151,27",
            1
        );

        offset=assertPacket(
            wire,
            offset,
            decode,
            "54314",
            54421
        );

        if(offset!=wire.length)
            throw new AssertionError(
                "trailing wire bytes="+
                (wire.length-offset)
            );
    }

    private static int assertPacket(
        byte[] wire,
        int offset,
        IsaacCipher decode,
        String payload,
        int target
    ){
        if(offset>=wire.length)
            throw new AssertionError(
                "missing packet"
            );

        int opcode=
            ((wire[offset++]&255)-
                decode.nextInt())&
                255;

        if(opcode!=126)
            throw new AssertionError(
                "opcode="+opcode
            );

        if(offset+2>wire.length)
            throw new AssertionError(
                "missing var-short length"
            );

        int length=
            ((wire[offset++]&255)<<8)|
            (wire[offset++]&255);

        byte[] expected=
            exactBody(
                payload,
                target
            );

        if(length!=expected.length)
            throw new AssertionError(
                "length expected="+
                expected.length+
                " actual="+length
            );

        if(offset+length>wire.length)
            throw new AssertionError(
                "truncated packet"
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
                "body mismatch expected="+
                hex(expected)+
                " actual="+
                hex(actual)
            );

        return offset+length;
    }

    private static byte[] exactBody(
        String payload,
        int target
    ){
        byte[] text=
            payload.getBytes(
                StandardCharsets.ISO_8859_1
            );

        byte[] out=
            Arrays.copyOf(
                text,
                text.length+3
            );

        out[text.length]=10;
        out[text.length+1]=
            (byte)(target>>>8);
        out[text.length+2]=
            (byte)((target+128)&255);

        return out;
    }

    private static void assertValidation(){
        expectInvalid(
            ()->ApplicationControl126Command
                .setSellItem(-1)
        );

        expectInvalid(
            ()->ApplicationControl126Command
                .itemGuideSelected(47504)
        );

        expectInvalid(
            ()->ApplicationControl126Command
                .wikiSelected(46606)
        );

        expectInvalid(
            ()->ApplicationControl126Command
                .collectionSelectRow(54315)
        );

        expectInvalid(
            ()->ApplicationControl126Command
                .collectionSelectCategory(54307)
        );
    }

    private static void assertProtocolSurfaceInternal(){
        if(java.lang.reflect.Modifier.isPublic(
                ApplicationControl126Command.class
                    .getModifiers())||
           java.lang.reflect.Modifier.isPublic(
                ApplicationControl126Service.class
                    .getModifiers())||
           java.lang.reflect.Modifier.isPublic(
                ApplicationControl126Command.Target.class
                    .getModifiers()))
            throw new AssertionError(
                "raw S2C126 protocol surface became public"
            );
    }

    private static void assertTarget(
        ApplicationControl126Command.Target target,
        int expected
    ){
        if(target.key()!=expected)
            throw new AssertionError(
                target+
                " expected="+
                expected+
                " actual="+
                target.key()
            );
    }

    private static void assertPayload(
        ApplicationControl126Command command,
        String payload,
        int target
    ){
        if(!payload.equals(
                command.payload())||
           command.target().key()!=target||
           command.authority()!=
                ApplicationControl126Command.Authority
                    .EXACT_CURRENT_CLIENT)
            throw new AssertionError(
                command+
                " payload="+
                command.payload()+
                " target="+
                command.target().key()
            );
    }

    private static void expectInvalid(
        Runnable action
    ){
        boolean failed=false;

        try{
            action.run();
        }catch(IllegalArgumentException expected){
            failed=true;
        }

        if(!failed)
            throw new AssertionError(
                "invalid S2C126 command accepted"
            );
    }

    private static String hex(byte[] data){
        StringBuilder out=
            new StringBuilder();

        for(byte value:data)
            out.append(
                String.format(
                    "%02X",
                    value&255
                )
            );

        return out.toString();
    }
}
