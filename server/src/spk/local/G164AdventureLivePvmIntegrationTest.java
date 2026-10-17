package spk.local;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public final class G164AdventureLivePvmIntegrationTest {
    private static final String PLAYER=
        "adventure-live-pvm";
    private static final int[] SEED={151,152,153,154};

    private static final class Bridge
        implements LocalCommandDispatcher.SessionBridge
    {
        final World world;
        final String playerRef;
        int activatedNowCount;
        int openCount;

        Bridge(
            World world,
            String playerRef
        ){
            this.world=world;
            this.playerRef=playerRef;
        }

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
            LocalLabAdventureRuntime.ActivationResult
                activation=
                    world.localLabAdventures()
                        .activate(
                            playerRef
                        );

            if(activation.activatedNow)
                activatedNowCount++;

            ObjectiveProgressService.Snapshot
                objective=
                    world.localLabAdventures()
                        .objective(
                            playerRef
                        );

            LocalLabAdventureBookPresentation.open(
                serverPackets,
                objective
            );

            openCount++;
            return true;
        }

        @Override public void applyPetDialog(
            LocalPetInventoryDialogHandler.Result result,
            String tag
        ){}
    }

    private static final class Projection {
        final boolean bookMode;
        final boolean primaryReset;
        final boolean secondaryReset;
        final int subjectType;
        final int definitionId;
        final String primaryText;
        final int rewardCount;
        final int current;
        final int target;
        final boolean claimed;
        final int chapterCurrent;
        final int chapterTarget;
        final boolean chapterRewardStatePresent;

        Projection(
            boolean bookMode,
            boolean primaryReset,
            boolean secondaryReset,
            int subjectType,
            int definitionId,
            String primaryText,
            int rewardCount,
            int current,
            int target,
            boolean claimed,
            int chapterCurrent,
            int chapterTarget,
            boolean chapterRewardStatePresent
        ){
            this.bookMode=bookMode;
            this.primaryReset=primaryReset;
            this.secondaryReset=secondaryReset;
            this.subjectType=subjectType;
            this.definitionId=definitionId;
            this.primaryText=primaryText;
            this.rewardCount=rewardCount;
            this.current=current;
            this.target=target;
            this.claimed=claimed;
            this.chapterCurrent=chapterCurrent;
            this.chapterTarget=chapterTarget;
            this.chapterRewardStatePresent=
                chapterRewardStatePresent;
        }
    }

    public static void main(String[] args)throws Exception{
        boolean activationOnOpen=false;
        boolean exactBookMode=false;
        boolean oneNpcObjective=false;
        boolean progress0=false;
        boolean progress2=false;
        boolean complete3=false;
        boolean emptyRewards=false;
        boolean chapterProgress=false;
        boolean repeatedOpenStable=false;
        boolean inputRouter=false;
        boolean chapterRewardStateClaim=false;

        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer player=
            new WorldPlayer();
        long generation=
            world.registerPlayer(
                player,
                PLAYER
            );

        try{
            Bridge bridge=
                new Bridge(
                    world,
                    PLAYER
                );

            Projection zero=
                openAndDecode(
                    bridge
                );

            ObjectiveProgressService.Snapshot
                zeroObjective=
                    world.localLabAdventures()
                        .objective(
                            PLAYER
                        );

            activationOnOpen=
                bridge.activatedNowCount==1&&
                bridge.openCount==1&&
                world.localLabAdventures()
                    .activePlayerCount()==1&&
                zeroObjective!=null;

            exactBookMode=
                zero.bookMode&&
                zero.primaryReset&&
                zero.secondaryReset;

            oneNpcObjective=
                zero.subjectType==
                    LocalLabAdventureBookPresentation
                        .SUBJECT_NPC_HEAD&&
                zero.definitionId==
                    LocalLabMonsterSpawnerProvisioning
                        .NPC_DEFINITION_ID&&
                LocalLabAdventureBookPresentation
                    .PRIMARY_TEXT.equals(
                        zero.primaryText
                    );

            progress0=
                zero.current==0&&
                zero.target==3&&
                !zero.claimed&&
                zeroObjective.progress==0L;

            emptyRewards=
                zero.rewardCount==0;

            chapterProgress=
                zero.chapterCurrent==0&&
                zero.chapterTarget==1;

            chapterRewardStateClaim=
                zero.chapterRewardStatePresent;

            LocalLabMonsterSpawnerProvisioning
                .creditTerminalProgression(
                    world,
                    PLAYER,
                    LocalLabMonsterSpawnerProvisioning
                        .NPC_DEFINITION_ID,
                    new Tile(
                        MovementState.INITIAL_X,
                        MovementState.INITIAL_Y,
                        0
                    ),
                    1L
                );
            LocalLabMonsterSpawnerProvisioning
                .creditTerminalProgression(
                    world,
                    PLAYER,
                    LocalLabMonsterSpawnerProvisioning
                        .NPC_DEFINITION_ID,
                    new Tile(
                        MovementState.INITIAL_X,
                        MovementState.INITIAL_Y,
                        0
                    ),
                    2L
                );

            Projection two=
                openAndDecode(
                    bridge
                );

            ObjectiveProgressService.Snapshot
                twoObjective=
                    world.localLabAdventures()
                        .objective(
                            PLAYER
                        );

            progress2=
                two.current==2&&
                two.target==3&&
                two.chapterCurrent==0&&
                two.chapterTarget==1&&
                twoObjective.progress==2L&&
                !twoObjective.complete;

            emptyRewards&=
                two.rewardCount==0;

            chapterRewardStateClaim|=
                two.chapterRewardStatePresent;

            LocalLabMonsterSpawnerProvisioning
                .creditTerminalProgression(
                    world,
                    PLAYER,
                    LocalLabMonsterSpawnerProvisioning
                        .NPC_DEFINITION_ID,
                    new Tile(
                        MovementState.INITIAL_X,
                        MovementState.INITIAL_Y,
                        0
                    ),
                    3L
                );

            Projection complete=
                openAndDecode(
                    bridge
                );

            ObjectiveProgressService.Snapshot
                completedObjective=
                    world.localLabAdventures()
                        .objective(
                            PLAYER
                        );

            complete3=
                complete.current==3&&
                complete.target==3&&
                !complete.claimed&&
                complete.chapterCurrent==1&&
                complete.chapterTarget==1&&
                completedObjective.progress==3L&&
                completedObjective.complete&&
                !completedObjective.claimed;

            emptyRewards&=
                complete.rewardCount==0;

            chapterProgress&=
                complete.chapterCurrent==1&&
                complete.chapterTarget==1;

            chapterRewardStateClaim|=
                complete.chapterRewardStatePresent;

            repeatedOpenStable=
                bridge.activatedNowCount==1&&
                bridge.openCount==3&&
                world.localLabAdventures()
                    .activePlayerCount()==1;

            inputRouter=
                hasAdventureInputRouter();

            require(
                activationOnOpen&&
                exactBookMode&&
                oneNpcObjective&&
                progress0&&
                progress2&&
                complete3&&
                emptyRewards&&
                chapterProgress&&
                repeatedOpenStable&&
                !inputRouter&&
                !chapterRewardStateClaim,
                "G16.4 acceptance"
            );

            System.out.println(
                "G164_ADVENTURE_LIVE_PVM_PASS"+
                " activationOnOpen="+
                    activationOnOpen+
                " exactBookMode="+exactBookMode+
                " oneNpcObjective="+oneNpcObjective+
                " progress0="+progress0+
                " progress2="+progress2+
                " complete3="+complete3+
                " emptyRewards="+emptyRewards+
                " chapterProgress="+chapterProgress+
                " repeatedOpenStable="+
                    repeatedOpenStable+
                " inputRouter="+inputRouter+
                " chapterRewardStateClaim="+
                    chapterRewardStateClaim+
                " rewardPolicyClaim=false"+
                " teleportPolicyClaim=false"+
                " persistenceClaim=false"+
                " originalServerPolicyClaim=false"
            );
        }finally{
            if(world.players().owns(
                    player,
                    generation))
                world.unregisterPlayer(
                    player,
                    generation
                );

            world.close();
        }
    }

    private static Projection openAndDecode(
        Bridge bridge
    )throws Exception{
        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();

        boolean handled=
            LocalCommandDispatcher
                .dispatchAdventureBookCommand(
                    new String[]{"adventurebook"},
                    bridge,
                    new ServerPacketWriter(
                        wire,
                        new IsaacCipher(
                            SEED.clone()
                        )
                    ),
                    "[g164] "
                );

        require(
            handled,
            "Adventure command not handled"
        );

        return decodeProjection(
            wire.toByteArray()
        );
    }

    private static Projection decodeProjection(
        byte[] wire
    ){
        IsaacCipher decode=
            new IsaacCipher(
                SEED.clone()
            );
        int[] offset={0};

        assert126(
            wire,
            offset,
            decode,
            "BEGIN_ADVENTURE_BOOK",
            1
        );

        boolean primary=
            read250Operation(
                wire,
                offset,
                decode,
                22
            )==0;

        boolean secondary=
            read250Operation(
                wire,
                offset,
                decode,
                22
            )==2;

        int cardStart=
            begin250(
                wire,
                offset,
                decode,
                22
            );
        int cardEnd=
            cardStart+
            current250BodyLength;

        int op=
            readU8(
                wire,
                offset
            );

        if(op!=3)
            throw new AssertionError(
                "expected chapter card op3 actual="+op
            );

        int subjectType=
            readU8(
                wire,
                offset
            );
        int definitionId=
            readI32(
                wire,
                offset
            );
        int textParts=
            readU8(
                wire,
                offset
            );

        if(textParts!=1)
            throw new AssertionError(
                "textParts="+textParts
            );

        String primaryText=
            readStringNl(
                wire,
                offset
            );

        int rewardCount=
            readU8(
                wire,
                offset
            );

        for(int i=0;i<rewardCount;i++){
            readI32(
                wire,
                offset
            );
            readI32(
                wire,
                offset
            );
        }

        int current=
            readU16(
                wire,
                offset
            );
        int target=
            readU16(
                wire,
                offset
            );
        boolean claimed=
            readU8(
                wire,
                offset
            )==1;

        if(offset[0]!=cardEnd)
            throw new AssertionError(
                "chapter card trailing="+
                (cardEnd-offset[0])
            );

        int scalarsStart=
            begin250(
                wire,
                offset,
                decode,
                22
            );
        int scalarsEnd=
            scalarsStart+
            current250BodyLength;

        int scalarsOp=
            readU8(
                wire,
                offset
            );

        if(scalarsOp!=8)
            throw new AssertionError(
                "expected chapter scalars op8 actual="+
                scalarsOp
            );

        int chapterCurrent=
            readU16(
                wire,
                offset
            );
        int chapterTarget=
            readU16(
                wire,
                offset
            );

        if(offset[0]!=scalarsEnd)
            throw new AssertionError(
                "chapter scalars trailing"
            );

        boolean rewardState=false;

        while(offset[0]<wire.length){
            int extraStart=
                begin250(
                    wire,
                    offset,
                    decode,
                    22
                );
            int extraEnd=
                extraStart+
                current250BodyLength;
            int extraOp=
                readU8(
                    wire,
                    offset
                );

            if(extraOp==7)
                rewardState=true;

            offset[0]=extraEnd;
        }

        return new Projection(
            true,
            primary,
            secondary,
            subjectType,
            definitionId,
            primaryText,
            rewardCount,
            current,
            target,
            claimed,
            chapterCurrent,
            chapterTarget,
            rewardState
        );
    }

    private static int current250BodyLength;

    private static int read250Operation(
        byte[] wire,
        int[] offset,
        IsaacCipher decode,
        int subtype
    ){
        int bodyStart=
            begin250(
                wire,
                offset,
                decode,
                subtype
            );
        int bodyEnd=
            bodyStart+
            current250BodyLength;

        int op=
            readU8(
                wire,
                offset
            );

        if(offset[0]!=bodyEnd)
            throw new AssertionError(
                "simple 250 body trailing"
            );

        return op;
    }

    private static int begin250(
        byte[] wire,
        int[] offset,
        IsaacCipher decode,
        int subtype
    ){
        int opcode=
            ((wire[offset[0]++]&255)-
                decode.nextInt())&
                255;

        if(opcode!=250)
            throw new AssertionError(
                "expected opcode250 actual="+
                opcode
            );

        int length=
            readU8(
                wire,
                offset
            );

        if(length<3)
            throw new AssertionError(
                "250 length="+length
            );

        int actualSubtype=
            readU16(
                wire,
                offset
            );

        if(actualSubtype!=subtype)
            throw new AssertionError(
                "subtype expected="+
                subtype+
                " actual="+
                actualSubtype
            );

        current250BodyLength=
            length-2;

        return offset[0];
    }

    private static void assert126(
        byte[] wire,
        int[] offset,
        IsaacCipher decode,
        String payload,
        int target
    ){
        int opcode=
            ((wire[offset[0]++]&255)-
                decode.nextInt())&
                255;

        if(opcode!=126)
            throw new AssertionError(
                "expected opcode126 actual="+
                opcode
            );

        int length=
            readU16(
                wire,
                offset
            );

        byte[] text=
            payload.getBytes(
                StandardCharsets.ISO_8859_1
            );

        if(length!=text.length+3)
            throw new AssertionError(
                "126 length"
            );

        for(byte value:text)
            if(readU8(
                    wire,
                    offset)!=(value&255))
                throw new AssertionError(
                    "126 text"
                );

        if(readU8(
                wire,
                offset)!=10)
            throw new AssertionError(
                "126 newline"
            );

        int high=
            readU8(
                wire,
                offset
            );
        int lowA=
            readU8(
                wire,
                offset
            );
        int actualTarget=
            (high<<8)|
            ((lowA-128)&255);

        if(actualTarget!=target)
            throw new AssertionError(
                "126 target expected="+
                target+
                " actual="+
                actualTarget
            );
    }

    private static String readStringNl(
        byte[] wire,
        int[] offset
    ){
        StringBuilder out=
            new StringBuilder();

        while(true){
            int value=
                readU8(
                    wire,
                    offset
                );

            if(value==10)
                return out.toString();

            out.append(
                (char)value
            );
        }
    }

    private static int readU8(
        byte[] wire,
        int[] offset
    ){
        if(offset[0]>=wire.length)
            throw new AssertionError(
                "wire underflow"
            );

        return wire[offset[0]++]&255;
    }

    private static int readU16(
        byte[] wire,
        int[] offset
    ){
        return
            (readU8(
                wire,
                offset
            )<<8)|
            readU8(
                wire,
                offset
            );
    }

    private static int readI32(
        byte[] wire,
        int[] offset
    ){
        return
            (readU8(
                wire,
                offset
            )<<24)|
            (readU8(
                wire,
                offset
            )<<16)|
            (readU8(
                wire,
                offset
            )<<8)|
            readU8(
                wire,
                offset
            );
    }

    private static boolean hasAdventureInputRouter(){
        for(Class<?> type:
                new Class<?>[]{
                    AdventureBookPresentation.class,
                    LocalLabAdventureBookPresentation.class
                })
            for(java.lang.reflect.Method method:
                    type.getDeclaredMethods()){
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

    private static void require(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(
                message
            );
    }

    private G164AdventureLivePvmIntegrationTest(){}
}
