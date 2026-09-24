package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;
import spk.content.builtin.LocalLabCoreContentModule;
import spk.content.builtin.MakeoverMageDialogueContent;

public final class ContentDialogueOutcomeOwnershipTest {
    public static void main(String[] args)throws Exception{
        transitionOutcomeContract();
        builtInOutcomePolicy();
        unsupportedRuntimeOutcomeFailsClosed();

        System.out.println(
            "CONTENT_DIALOGUE_OUTCOME_OWNERSHIP_PASS "+
            "semanticOutcome=true "+
            "option1ContentOwned=true "+
            "option2ContentOwned=true "+
            "closeContentOwned=true "+
            "unsupportedFailsClosed=true "+
            "partialWire=false "+
            "builtInRestore=true"
        );
    }

    private static void transitionOutcomeContract(){
        ContentDialogueTransition plain=
            ContentDialogueTransition.end();

        require(
            plain.kind()==
                ContentDialogueTransition.Kind.END&&
            plain.outcomeKey()==null,
            "plain END outcome"
        );

        ContentDialogueTransition keyed=
            ContentDialogueTransition.end(
                "  MakeOver:Open-Designer  "
            );

        require(
            keyed.kind()==
                ContentDialogueTransition.Kind.END&&
            "makeover:open-designer".equals(
                keyed.outcomeKey()),
            "semantic outcome normalization"
        );

        expect(
            IllegalArgumentException.class,
            ()->ContentDialogueTransition.end(
                "bad outcome key"
            ),
            "unsafe outcome key"
        );
    }

    private static void builtInOutcomePolicy(){
        MakeoverMageDialogueContent policy=
            new MakeoverMageDialogueContent();

        ContentDialogueTransition option1=
            policy.handle(
                context(
                    MakeoverMageDialogueContent
                        .OPTIONS_NODE,
                    ContentDialogueIntent.option(1)
                )
            );
        ContentDialogueTransition option2=
            policy.handle(
                context(
                    MakeoverMageDialogueContent
                        .OPTIONS_NODE,
                    ContentDialogueIntent.option(2)
                )
            );
        ContentDialogueTransition close=
            policy.handle(
                context(
                    MakeoverMageDialogueContent
                        .OPTIONS_NODE,
                    ContentDialogueIntent.closeIntent()
                )
            );

        require(
            option1.kind()==
                ContentDialogueTransition.Kind.END&&
            MakeoverMageDialogueContent
                .OUTCOME_OPEN_DESIGNER
                .equals(option1.outcomeKey()),
            "option 1 outcome"
        );

        require(
            option2.kind()==
                ContentDialogueTransition.Kind.END&&
            MakeoverMageDialogueContent
                .OUTCOME_CANCEL
                .equals(option2.outcomeKey()),
            "option 2 outcome"
        );

        require(
            close.kind()==
                ContentDialogueTransition.Kind.END&&
            MakeoverMageDialogueContent
                .OUTCOME_CLIENT_CLOSE
                .equals(close.outcomeKey()),
            "client close outcome"
        );
    }

    private static void unsupportedRuntimeOutcomeFailsClosed()
        throws Exception
    {
        World world=
            World.isolatedForTest(20L);
        WorldPlayer player=
            new WorldPlayer();

        try{
            AtomicReference<ContentRegistration>
                override=
                    new AtomicReference<>();

            world.content().installCustom(
                new ContentModule(){
                    @Override public String id(){
                        return "dialogue-outcome-override";
                    }

                    @Override public void register(
                        ContentRegistrar registrar
                    ){
                        override.set(
                            registrar.dialogue(
                                MakeoverMageDialogueContent
                                    .DIALOGUE_KEY,
                                250,
                                context->{
                                    if(MakeoverMageDialogueContent
                                            .INTRO_NODE
                                            .equals(
                                                context.nodeKey())&&
                                       context.intent().kind()==
                                            ContentDialogueIntent
                                                .Kind.CONTINUE)
                                        return ContentDialogueTransition.move(
                                            MakeoverMageDialogueContent
                                                .OPTIONS_NODE
                                        );

                                    if(MakeoverMageDialogueContent
                                            .OPTIONS_NODE
                                            .equals(
                                                context.nodeKey())&&
                                       context.intent().kind()==
                                            ContentDialogueIntent
                                                .Kind.OPTION)
                                        return ContentDialogueTransition.end(
                                            "custom:unsupported"
                                        );

                                    if(MakeoverMageDialogueContent
                                            .OPTIONS_NODE
                                            .equals(
                                                context.nodeKey())&&
                                       context.intent().kind()==
                                            ContentDialogueIntent
                                                .Kind.CLOSE)
                                        return ContentDialogueTransition.end(
                                            "custom:unsupported"
                                        );

                                    throw new IllegalStateException(
                                        "unsupported override transition"
                                    );
                                }
                            )
                        );
                    }
                }
            );

            world.registerPlayer(
                player,
                "dialogue-outcome-owner"
            );
            world.start();

            LocalRoutedNpcInteractionHandler routed=
                routed(
                    world,
                    player
                );

            begin(
                world,
                player,
                routed,
                adjacentMage(
                    player,
                    61
                )
            );
            continueDialogue(
                world,
                player,
                routed
            );

            ByteArrayOutputStream rejectedWire=
                new ByteArrayOutputStream();
            AtomicReference<Throwable> rejection=
                new AtomicReference<>();

            world.submitAndWait(
                player,
                ()->{
                    try{
                        routed.makeoverMage()
                            .handleOption(
                                1,
                                writer(rejectedWire),
                                "[dialogue-outcome-test] "
                            );
                    }catch(Throwable failure){
                        rejection.set(failure);
                    }
                },
                5_000L
            );

            require(
                rejection.get() instanceof
                    IllegalStateException&&
                rejection.get().getMessage()!=null&&
                rejection.get().getMessage().contains(
                    "unsupported Make-over option outcome=custom:unsupported"
                ),
                "unsupported outcome failure "+
                rejection.get()
            );

            require(
                rejectedWire.size()==0,
                "unsupported outcome emitted bytes="+
                rejectedWire.size()
            );

            require(
                !routed.makeoverMage()
                    .semanticDialogueSnapshot()
                    .active&&
                !routed.makeoverMage()
                    .designActive(),
                "unsupported outcome mutated runtime effect state"
            );

            require(
                override.get()!=null&&
                override.get().unregister(),
                "outcome override unregister"
            );

            begin(
                world,
                player,
                routed,
                adjacentMage(
                    player,
                    62
                )
            );
            continueDialogue(
                world,
                player,
                routed
            );

            ByteArrayOutputStream restoredWire=
                new ByteArrayOutputStream();
            AtomicReference<Boolean> handled=
                new AtomicReference<>();

            world.submitAndWait(
                player,
                ()->{
                    try{
                        ServerPacketWriter packets=
                            writer(restoredWire);

                        handled.set(
                            routed.makeoverMage()
                                .handleOption(
                                    1,
                                    packets,
                                    "[dialogue-outcome-test] "
                                )
                        );
                        packets.flush();
                    }catch(Throwable failure){
                        throw new RuntimeException(
                            failure
                        );
                    }
                },
                5_000L
            );

            require(
                Boolean.TRUE.equals(
                    handled.get())&&
                routed.makeoverMage()
                    .designActive()&&
                restoredWire.size()>0,
                "built-in outcome did not restore designer effect"
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(player);
            world.close();
        }
    }

    private static ContentDialogueContext context(
        String nodeKey,
        ContentDialogueIntent intent
    ){
        return new ContentDialogueContext(){
            @Override public String dialogueKey(){
                return MakeoverMageDialogueContent
                    .DIALOGUE_KEY;
            }

            @Override public String nodeKey(){
                return nodeKey;
            }

            @Override public ContentDialogueIntent intent(){
                return intent;
            }

            @Override public ContentPlayer player(){
                return new ContentPlayer(){
                    @Override public int skillLevel(
                        ContentSkill skill
                    ){return 1;}

                    @Override public int skillExperience(
                        ContentSkill skill
                    ){return 0;}

                    @Override public java.util.Set<ContentSkill>
                        restoreCombatSkillsAndSpecial()
                    {
                        return java.util.Collections.emptySet();
                    }

                    @Override public void clearTimedStatuses(){}

                    @Override public void setRunEnergy(
                        int value
                    ){}

                    @Override public java.util.Set<ContentSkill>
                        syncMaintainedPetEffects()
                    {
                        return java.util.Collections.emptySet();
                    }

                    @Override public boolean
                        maintainedPetEffectActive()
                    {return false;}

                    @Override public int runEnergy(){return 100;}
                    @Override public int specialEnergy(){return 100;}
                    @Override public int poison(){return 0;}
                    @Override public int venom(){return 0;}
                    @Override public int sicken(){return 0;}
                };
            }
        };
    }

    private static void begin(
        World world,
        WorldPlayer player,
        LocalRoutedNpcInteractionHandler routed,
        NpcEntity mage
    )throws Exception{
        AtomicReference<Throwable> failure=
            new AtomicReference<>();

        world.submitAndWait(
            player,
            ()->{
                try{
                    String residual=
                        routed.handle(
                            new NpcAction(
                                155,
                                mage.sceneIndex
                            ),
                            mage,
                            writer(
                                new ByteArrayOutputStream()
                            ),
                            "[dialogue-outcome-test] "
                        );

                    if(residual!=null)
                        failure.set(
                            new AssertionError(
                                "begin residual="+
                                residual
                            )
                        );
                }catch(Throwable error){
                    failure.set(error);
                }
            },
            5_000L
        );

        if(failure.get()!=null)
            throw new AssertionError(
                "begin failed",
                failure.get()
            );
    }

    private static void continueDialogue(
        World world,
        WorldPlayer player,
        LocalRoutedNpcInteractionHandler routed
    )throws Exception{
        AtomicReference<Throwable> failure=
            new AtomicReference<>();

        world.submitAndWait(
            player,
            ()->{
                try{
                    if(!routed.makeoverMage()
                            .handleContinue(
                                StandardDialoguePresentationAdapter
                                    .namedNpcContinueWidget(1),
                                writer(
                                    new ByteArrayOutputStream()
                                ),
                                "[dialogue-outcome-test] "
                            ))
                        failure.set(
                            new AssertionError(
                                "Continue not handled"
                            )
                        );
                }catch(Throwable error){
                    failure.set(error);
                }
            },
            5_000L
        );

        if(failure.get()!=null)
            throw new AssertionError(
                "Continue failed",
                failure.get()
            );
    }

    private static LocalRoutedNpcInteractionHandler
        routed(
            World world,
            WorldPlayer player
        ){
        return new LocalRoutedNpcInteractionHandler(
            new NpcRegistry(),
            player.bank(),
            player.movement(),
            world.content(),
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
            LocalLabCoreContentModule
                .MAKEOVER_MAGE_NPC,
            player.movement().x()+1,
            player.movement().y()
        );
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream wire
    ){
        return new ServerPacketWriter(
            wire,
            new IsaacCipher(
                new int[]{111,112,113,114}
            )
        );
    }

    private static void expect(
        Class<? extends Throwable> type,
        Runnable action,
        String label
    ){
        try{
            action.run();
        }catch(Throwable failure){
            if(type.isInstance(failure))
                return;

            throw new AssertionError(
                label+" wrong failure "+failure,
                failure
            );
        }

        throw new AssertionError(
            label+" did not fail"
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private ContentDialogueOutcomeOwnershipTest(){}
}
