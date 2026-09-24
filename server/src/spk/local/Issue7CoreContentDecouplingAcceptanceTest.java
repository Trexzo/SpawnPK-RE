package spk.local;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import spk.content.api.*;
import spk.plugin.api.*;

public final class Issue7CoreContentDecouplingAcceptanceTest {
    private static final String COMMAND=
        "issue7pluginprobe";

    public static void main(String[] args)throws Exception{
        decoderBoundary();
        devControlBoundary();
        pluginCommandDispatch();

        System.out.println(
            "ISSUE7_CORE_CONTENT_DECOUPLING_ACCEPTANCE_PASS "+
            "productionDecoders=2 "+
            "hardcodedCommandStrings=false "+
            "dialogueTrees=false "+
            "devControlStateOnly=true "+
            "pluginCommandRegistered=true "+
            "pluginCommandDispatched=true "+
            "customProvenance=true "+
            "zeroWire=true "+
            "disableClean=true "+
            "scriptRuntimeDelegatedToIssue5=true"
        );
    }

    private static void decoderBoundary()
        throws Exception{
        Path root=
            Paths.get(
                "server/src/spk/local"
            );

        ArrayList<Path> decoders=
            new ArrayList<>();

        try(Stream<Path> files=
                Files.walk(root)){
            files.filter(
                    Files::isRegularFile
                )
                .filter(path->{
                    String name=
                        path.getFileName()
                            .toString();

                    return name.endsWith(
                            "Decoder.java")||
                        name.endsWith(
                            "PacketProbe.java");
                })
                .filter(path->
                    !path.getFileName()
                        .toString()
                        .endsWith(
                            "Test.java"))
                .forEach(
                    decoders::add
                );
        }

        Collections.sort(
            decoders
        );

        require(
            decoders.size()==2,
            "production decoder inventory="+
            decoders
        );

        require(
            endsWith(
                decoders,
                "ClientPacketProbe.java")&&
            endsWith(
                decoders,
                "GenericInteractionPacketDecoder.java"),
            "unexpected decoder inventory="+
            decoders
        );

        for(Path decoder:decoders){
            String source=
                source(decoder);

            require(
                !source.contains(
                    "\"::"),
                decoder+
                " contains hardcoded :: command text"
            );

            require(
                !source.toLowerCase(
                    Locale.ROOT
                ).contains(
                    "dialogueoption"),
                decoder+
                " contains dialogue-option semantics"
            );

            require(
                !source.contains(
                    "DialogueOptionClientRequest"),
                decoder+
                " contains semantic dialogue request type"
            );

            require(
                !source.contains(
                    "ClientCommandSemanticRouter"),
                decoder+
                " calls downstream semantic router"
            );

            require(
                !source.contains(
                    "ContentRegistry"),
                decoder+
                " owns content routing"
            );

            require(
                !source.contains(
                    "LocalCommandDispatcher"),
                decoder+
                " owns command dispatch"
            );

            require(
                !source.contains(
                    "registrar.command("),
                decoder+
                " registers commands"
            );

            require(
                !source.contains(
                    "equalsIgnoreCase("),
                decoder+
                " performs text-command classification"
            );
        }

        String probe=
            source(
                Paths.get(
                    "server/src/spk/local/ClientPacketProbe.java"
                )
            );

        require(
            probe.contains(
                "new CommandClientRequest(")&&
            probe.contains(
                "\"VAR_BYTE_ISO_8859_1_OPTIONAL_LF\""),
            "opcode103 transport command framing missing"
        );

        String router=
            source(
                Paths.get(
                    "server/src/spk/local/ClientCommandSemanticRouter.java"
                )
            );

        require(
            router.contains(
                "\"dialogueoption \"")&&
            router.contains(
                "dialogueOptionIndex("),
            "dialogue semantic alias not downstream"
        );

        String pending=
            source(
                Paths.get(
                    "server/src/spk/local/LocalPendingRequestDispatcher.java"
                )
            );

        int semantic=
            pending.indexOf(
                "ClientCommandSemanticRouter"
            );
        int generic=
            pending.indexOf(
                "commandDispatcher.handle("
            );

        require(
            semantic>=0&&
            generic>semantic,
            "semantic command classification ordering"
        );
    }

    private static void devControlBoundary()
        throws Exception{
        String source=
            source(
                Paths.get(
                    "server/src/spk/local/DevControlCenter.java"
                )
            );

        String[] forbidden={
            "\"::",
            "equalsIgnoreCase(",
            "ContentResult",
            "ContentRegistry",
            "LocalCommandDispatcher",
            "ServerPacketWriter",
            "DialogueSession",
            "registrar.command("
        };

        for(String token:forbidden)
            require(
                !source.contains(token),
                "DevControlCenter owns runtime/content behavior token="+
                token
            );

        require(
            source.contains(
                "enum Page")&&
            source.contains(
                "enum PendingAmount"),
            "DevControlCenter state model missing"
        );
    }

    private static void pluginCommandDispatch()
        throws Exception{
        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer player=
            new WorldPlayer();
        AcceptancePlugin plugin=
            new AcceptancePlugin();

        try{
            require(
                world.content()
                    .commandBinding(
                        COMMAND
                    )==null,
                "probe command unexpectedly exists before plugin"
            );

            PluginHandle handle=
                world.plugins()
                    .enable(
                        plugin
                    );

            require(
                handle!=null&&
                handle.enabled(),
                "plugin did not enable"
            );

            ContentRegistry.BindingInfo binding=
                world.content()
                    .commandBinding(
                        COMMAND
                    );

            require(
                binding!=null&&
                "plugin:issue7.acceptance"
                    .equals(
                        binding.moduleId)&&
                binding.priority==100&&
                binding.provenance==
                    ContentProvenance.CUSTOM_LOCALLAB,
                "plugin command binding="+
                binding
            );

            world.registerPlayer(
                player,
                "issue7-acceptance"
            );
            world.start();

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            ServerPacketWriter packets=
                new ServerPacketWriter(
                    wire,
                    new IsaacCipher(
                        new int[]{
                            351,
                            352,
                            353,
                            354
                        }
                    )
                );

            AtomicReference<ContentResult>
                result=
                    new AtomicReference<>();
            AtomicReference<Throwable>
                failure=
                    new AtomicReference<>();

            world.submitAndWait(
                player,
                ()->{
                    try{
                        result.set(
                            world.content()
                                .dispatchCommand(
                                    player,
                                    "::"+
                                    COMMAND+
                                    " alpha beta",
                                    packets
                                )
                        );
                    }catch(Throwable error){
                        failure.set(
                            error
                        );
                    }
                },
                5_000L
            );

            if(failure.get()!=null)
                throw new AssertionError(
                    "plugin command dispatch failed",
                    failure.get()
                );

            packets.flush();

            require(
                result.get()!=null&&
                !result.get().hasAction()&&
                result.get().saveReason()==null&&
                "ISSUE7_PLUGIN_COMMAND args=2"
                    .equals(
                        result.get()
                            .logText()),
                "plugin command result="+
                result.get()
            );

            require(
                wire.size()==0,
                "plugin command emitted wire bytes="+
                wire.size()
            );

            require(
                world.plugins()
                    .disable(
                        "issue7.acceptance"),
                "plugin disable failed"
            );

            require(
                !handle.enabled()&&
                plugin.disableCount.get()==1,
                "plugin disable callback/handle mismatch"
            );

            require(
                world.content()
                    .commandBinding(
                        COMMAND
                    )==null,
                "plugin command remained after disable"
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(
                    player
                );

            world.close();
        }
    }

    private static boolean endsWith(
        List<Path> paths,
        String file
    ){
        for(Path path:paths)
            if(path.getFileName()
                    .toString()
                    .equals(file))
                return true;

        return false;
    }

    private static String source(
        Path path
    )throws Exception{
        return new String(
            Files.readAllBytes(path),
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

    private static final class AcceptancePlugin
        implements Plugin {

        private final AtomicInteger
            disableCount=
                new AtomicInteger();

        @Override public PluginManifest manifest(){
            return new PluginManifest(
                "issue7.acceptance",
                "1.0.0"
            );
        }

        @Override public void enable(
            PluginContext context
        ){
            context.content()
                .command(
                    COMMAND,
                    100,
                    command->
                        ContentResult.handled(
                            "ISSUE7_PLUGIN_COMMAND args="+
                            command.arguments()
                                .size(),
                            null
                        )
                );
        }

        @Override public void disable(){
            disableCount.incrementAndGet();
        }
    }

    private Issue7CoreContentDecouplingAcceptanceTest(){}
}
