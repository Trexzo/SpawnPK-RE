package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;

public final class MakeoverSemanticDialogueSessionTest {
    public static void main(String[] args)throws Exception{
        designerHandoffUsesSemanticDialogue();
        nevermindEndsSemanticDialogue();
        serverCancellationUsesAbortRevision();
        noLegacyStageState();

        System.out.println(
            "MAKEOVER_SEMANTIC_DIALOGUE_SESSION_PASS "+
            "intro=true "+
            "options=true "+
            "designerHandoff=true "+
            "nevermind=true "+
            "serverAbortRevision=true "+
            "legacyStage=false"
        );
    }

    private static void designerHandoffUsesSemanticDialogue()
        throws Exception
    {
        WorldPlayer player=
            new WorldPlayer();
        LocalMakeoverMageHandler handler=
            handler(player);
        ServerPacketWriter packets=
            writer();

        NpcEntity mage=
            adjacentMage(player,30);

        require(
            !handler.semanticDialogueSnapshot().active&&
            !handler.designActive(),
            "initial Make-over state"
        );

        require(
            handler.beginIfSupported(
                new NpcAction(
                    155,
                    mage.sceneIndex
                ),
                mage,
                packets,
                "[makeover-semantic-test] "
            ),
            "begin"
        );

        DialogueSessionService.Snapshot intro=
            handler.semanticDialogueSnapshot();

        require(
            intro.active&&
            "dialogue:makeover-mage".equals(
                intro.dialogueKey)&&
            "node:intro".equals(
                intro.nodeKey)&&
            intro.inputMode==
                DialogueSessionService
                    .InputMode.CONTINUE&&
            intro.revision==1L&&
            !handler.designActive(),
            "intro semantic state"
        );

        require(
            handler.handleContinue(
                StandardDialoguePresentationAdapter
                    .namedNpcContinueWidget(1),
                packets,
                "[makeover-semantic-test] "
            ),
            "continue"
        );

        DialogueSessionService.Snapshot options=
            handler.semanticDialogueSnapshot();

        require(
            options.active&&
            "node:options".equals(
                options.nodeKey)&&
            options.inputMode==
                DialogueSessionService
                    .InputMode.OPTIONS&&
            options.optionCount==2&&
            options.revision==2L,
            "options semantic state"
        );

        require(
            handler.handleOption(
                1,
                packets,
                "[makeover-semantic-test] "
            ),
            "designer option"
        );

        DialogueSessionService.Snapshot ended=
            handler.semanticDialogueSnapshot();

        require(
            !ended.active&&
            ended.revision==3L&&
            handler.designActive()&&
            handler.active(),
            "designer handoff"
        );

        require(
            handler.cancel(),
            "designer cancel"
        );

        require(
            !handler.active()&&
            !handler.designActive()&&
            !handler.semanticDialogueSnapshot()
                .active,
            "designer cancel state"
        );
    }

    private static void nevermindEndsSemanticDialogue()
        throws Exception
    {
        WorldPlayer player=
            new WorldPlayer();
        LocalMakeoverMageHandler handler=
            handler(player);
        ServerPacketWriter packets=
            writer();
        NpcEntity mage=
            adjacentMage(player,31);

        handler.beginIfSupported(
            new NpcAction(
                155,
                mage.sceneIndex
            ),
            mage,
            packets,
            "[makeover-semantic-test] "
        );
        handler.handleContinue(
            StandardDialoguePresentationAdapter
                .KEYBOARD_CONTINUE_WIDGET,
            packets,
            "[makeover-semantic-test] "
        );

        require(
            handler.handleOption(
                2,
                packets,
                "[makeover-semantic-test] "
            ),
            "Nevermind option"
        );

        DialogueSessionService.Snapshot ended=
            handler.semanticDialogueSnapshot();

        require(
            !ended.active&&
            ended.revision==3L&&
            !handler.designActive()&&
            !handler.active(),
            "Nevermind end state"
        );
    }

    private static void serverCancellationUsesAbortRevision()
        throws Exception
    {
        WorldPlayer player=
            new WorldPlayer();
        LocalMakeoverMageHandler handler=
            handler(player);
        ServerPacketWriter packets=
            writer();
        NpcEntity mage=
            adjacentMage(player,32);

        handler.beginIfSupported(
            new NpcAction(
                155,
                mage.sceneIndex
            ),
            mage,
            packets,
            "[makeover-semantic-test] "
        );

        DialogueSessionService.Snapshot before=
            handler.semanticDialogueSnapshot();

        require(
            before.active&&
            before.revision==1L,
            "pre-abort state"
        );

        require(
            handler.cancelForManualMovement(
                packets,
                "[makeover-semantic-test] "
            ),
            "manual movement cancellation"
        );

        DialogueSessionService.Snapshot after=
            handler.semanticDialogueSnapshot();

        require(
            !after.active&&
            after.revision==2L&&
            !handler.active(),
            "server abort revision"
        );

        require(
            !handler.cancelForManualMovement(
                packets,
                "[makeover-semantic-test] "
            ),
            "inactive manual movement should not cancel again"
        );

        require(
            handler.semanticDialogueSnapshot()
                .revision==2L,
            "inactive cancellation changed revision"
        );
    }

    private static void noLegacyStageState(){
        for(Field field:
                LocalMakeoverMageHandler.class
                    .getDeclaredFields())
            if("stage".equals(
                    field.getName())||
               field.getType()
                    .getSimpleName()
                    .equals("Stage"))
                throw new AssertionError(
                    "legacy Make-over Stage field remains "+
                    field
                );

        boolean dialogue=false;

        for(Field field:
                LocalMakeoverMageHandler.class
                    .getDeclaredFields())
            if(field.getType()==
                    DialogueSessionService.class)
                dialogue=true;

        require(
            dialogue,
            "Make-over handler has no DialogueSessionService"
        );
    }

    private static LocalMakeoverMageHandler
        handler(
            WorldPlayer player
        ){
        return new LocalMakeoverMageHandler(
            player,
            player.equipment()
        );
    }

    private static NpcEntity adjacentMage(
        WorldPlayer player,
        int scene
    ){
        return new NpcEntity(
            scene,
            LocalMakeoverMageHandler.NPC_ID,
            player.movement().x()+1,
            player.movement().y()
        );
    }

    private static ServerPacketWriter writer(){
        return new ServerPacketWriter(
            new ByteArrayOutputStream(),
            new IsaacCipher(
                new int[]{61,62,63,64}
            )
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private MakeoverSemanticDialogueSessionTest(){}
}
