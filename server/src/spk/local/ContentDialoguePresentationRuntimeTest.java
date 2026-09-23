package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;

public final class ContentDialoguePresentationRuntimeTest {
    public static void main(String[] args)throws Exception{
        defaultCapabilityFailsClosed();
        runtimeContentWireParity();

        System.out.println(
            "CONTENT_DIALOGUE_PRESENTATION_RUNTIME_PASS "+
            "statement=true "+
            "namedNpc=true "+
            "twoOptions=true "+
            "close=true "+
            "wireParity=true "+
            "rawWidgetIdentity=false "+
            "defaultFailsClosed=true"
        );
    }

    private static void defaultCapabilityFailsClosed(){
        ContentPresentation alternate=
            new ContentPresentation(){
                @Override public void skill(
                    ContentSkill skill,
                    int experience,
                    int currentLevel
                ){}

                @Override public void runEnergy(
                    int energy
                ){}

                @Override public void specialEnergy(
                    int percent
                ){}

                @Override public void animationAndGfx(
                    int animationId,
                    int gfxId,
                    int gfxHeight,
                    int gfxDelay
                ){}
            };

        boolean rejected=false;

        try{
            alternate.dialogue();
        }catch(UnsupportedOperationException expected){
            rejected=
                expected.getMessage()!=null&&
                expected.getMessage().contains(
                    "dialogue presentation unavailable"
                );
        }

        if(!rejected)
            throw new AssertionError(
                "alternate ContentPresentation did not fail closed"
            );
    }

    private static void runtimeContentWireParity()
        throws Exception
    {
        World world=
            World.isolatedForTest(20L);
        WorldPlayer player=
            new WorldPlayer();

        try{
            world.content()
                .installCustom(
                    testModule()
                );

            world.registerPlayer(
                player,
                "testprofile"
            );
            world.start();

            assertStatement(world,player);
            assertNamedNpc(world,player);
            assertTwoOptions(world,player);
            assertClose(world,player);

            assertCustomBinding(
                world,
                "dialogstatement"
            );
            assertCustomBinding(
                world,
                "dialognpc"
            );
            assertCustomBinding(
                world,
                "dialogoptions"
            );
            assertCustomBinding(
                world,
                "dialogclose"
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(
                    player
                );
            world.close();
        }
    }

    private static void assertStatement(
        World world,
        WorldPlayer player
    )throws Exception{
        ByteArrayOutputStream actualWire=
            new ByteArrayOutputStream();
        ByteArrayOutputStream expectedWire=
            new ByteArrayOutputStream();

        ServerPacketWriter actual=
            writer(actualWire);
        ServerPacketWriter expected=
            writer(expectedWire);

        ContentResult result=
            dispatch(
                world,
                player,
                "::dialogstatement",
                actual
            );

        if(result==null||
           !"DIALOG_STATEMENT".equals(
                result.logText()))
            throw new AssertionError(
                "statement content result="+
                result
            );

        StandardDialoguePresentationAdapter
            .openStatement(
                expected,
                Arrays.asList(
                    "Alpha",
                    "Beta"
                )
            );

        actual.flush();
        expected.flush();

        requireWireParity(
            actualWire,
            expectedWire,
            "statement"
        );
    }

    private static void assertNamedNpc(
        World world,
        WorldPlayer player
    )throws Exception{
        ByteArrayOutputStream actualWire=
            new ByteArrayOutputStream();
        ByteArrayOutputStream expectedWire=
            new ByteArrayOutputStream();

        ServerPacketWriter actual=
            writer(actualWire);
        ServerPacketWriter expected=
            writer(expectedWire);

        ContentResult result=
            dispatch(
                world,
                player,
                "::dialognpc",
                actual
            );

        if(result==null||
           !"DIALOG_NPC".equals(
                result.logText()))
            throw new AssertionError(
                "named NPC content result="+
                result
            );

        StandardDialoguePresentationAdapter
            .openNamedNpc(
                expected,
                599,
                "Make-over Mage",
                Collections.singletonList(
                    "How may I help you?"
                )
            );

        actual.flush();
        expected.flush();

        requireWireParity(
            actualWire,
            expectedWire,
            "named NPC"
        );
    }

    private static void assertTwoOptions(
        World world,
        WorldPlayer player
    )throws Exception{
        ByteArrayOutputStream actualWire=
            new ByteArrayOutputStream();
        ByteArrayOutputStream expectedWire=
            new ByteArrayOutputStream();

        ServerPacketWriter actual=
            writer(actualWire);
        ServerPacketWriter expected=
            writer(expectedWire);

        ContentResult result=
            dispatch(
                world,
                player,
                "::dialogoptions",
                actual
            );

        if(result==null||
           !"DIALOG_OPTIONS".equals(
                result.logText()))
            throw new AssertionError(
                "two-option content result="+
                result
            );

        StandardDialoguePresentationAdapter
            .openTwoOptions(
                expected,
                "Select an Option",
                Arrays.asList(
                    "I'd like to change my look.",
                    "Nevermind."
                )
            );

        actual.flush();
        expected.flush();

        requireWireParity(
            actualWire,
            expectedWire,
            "two options"
        );
    }

    private static void assertClose(
        World world,
        WorldPlayer player
    )throws Exception{
        ByteArrayOutputStream actualWire=
            new ByteArrayOutputStream();
        ByteArrayOutputStream expectedWire=
            new ByteArrayOutputStream();

        ServerPacketWriter actual=
            writer(actualWire);
        ServerPacketWriter expected=
            writer(expectedWire);

        ContentResult result=
            dispatch(
                world,
                player,
                "::dialogclose",
                actual
            );

        if(result==null||
           !"DIALOG_CLOSE".equals(
                result.logText()))
            throw new AssertionError(
                "close content result="+
                result
            );

        StandardDialoguePresentationAdapter
            .close(
                expected
            );

        actual.flush();
        expected.flush();

        requireWireParity(
            actualWire,
            expectedWire,
            "close"
        );
    }

    private static ContentResult dispatch(
        World world,
        WorldPlayer player,
        String command,
        ServerPacketWriter writer
    )throws Exception{
        AtomicReference<ContentResult> result=
            new AtomicReference<>();
        AtomicReference<Throwable> failure=
            new AtomicReference<>();

        world.submitAndWait(
            player,
            ()->{
                try{
                    result.set(
                        world.content()
                            .dispatchCommand(
                                player,
                                command,
                                writer
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
                "content command failed "+
                command,
                failure.get()
            );

        return result.get();
    }

    private static ContentModule testModule(){
        return new ContentModule(){
            @Override public String id(){
                return "dialogue-presentation-api-test";
            }

            @Override public void register(
                ContentRegistrar registrar
            ){
                registrar.command(
                    "dialogstatement",
                    100,
                    context->{
                        context.presentation()
                            .dialogue()
                            .statement(
                                Arrays.asList(
                                    "Alpha",
                                    "Beta"
                                )
                            );

                        return ContentResult.handled(
                            "DIALOG_STATEMENT",
                            null
                        );
                    }
                );

                registrar.command(
                    "dialognpc",
                    100,
                    context->{
                        context.presentation()
                            .dialogue()
                            .namedNpc(
                                599,
                                "Make-over Mage",
                                Collections.singletonList(
                                    "How may I help you?"
                                )
                            );

                        return ContentResult.handled(
                            "DIALOG_NPC",
                            null
                        );
                    }
                );

                registrar.command(
                    "dialogoptions",
                    100,
                    context->{
                        context.presentation()
                            .dialogue()
                            .twoOptions(
                                "Select an Option",
                                Arrays.asList(
                                    "I'd like to change my look.",
                                    "Nevermind."
                                )
                            );

                        return ContentResult.handled(
                            "DIALOG_OPTIONS",
                            null
                        );
                    }
                );

                registrar.command(
                    "dialogclose",
                    100,
                    context->{
                        context.presentation()
                            .dialogue()
                            .close();

                        return ContentResult.handled(
                            "DIALOG_CLOSE",
                            null
                        );
                    }
                );
            }
        };
    }

    private static void assertCustomBinding(
        World world,
        String command
    ){
        ContentRegistry.BindingInfo binding=
            world.content()
                .commandBinding(command);

        if(binding==null||
           !"dialogue-presentation-api-test"
                .equals(binding.moduleId)||
           binding.provenance!=
                ContentProvenance
                    .CUSTOM_LOCALLAB)
            throw new AssertionError(
                "dialogue test binding "+
                command+
                " -> "+
                binding
            );
    }

    private static void requireWireParity(
        ByteArrayOutputStream actual,
        ByteArrayOutputStream expected,
        String phase
    ){
        if(!Arrays.equals(
                actual.toByteArray(),
                expected.toByteArray()))
            throw new AssertionError(
                phase+" wire mismatch"
            );
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream wire
    ){
        return new ServerPacketWriter(
            wire,
            new IsaacCipher(
                new int[]{51,52,53,54}
            )
        );
    }

    private ContentDialoguePresentationRuntimeTest(){}
}
