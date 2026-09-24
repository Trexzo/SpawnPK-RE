package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;

public final class LocalDiagnosticContentOwnershipTest {
    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(20L);
        WorldPlayer player=
            new WorldPlayer();

        try{
            ContentRegistry registry=
                world.content();

            assertBinding(
                registry.commandBinding(
                    "contentregistry"
                ),
                "contentregistry"
            );
            assertBinding(
                registry.commandBinding(
                    "authority"
                ),
                "authority"
            );

            legacyAuthorityFallbackRemoved(
                world
            );

            world.registerPlayer(
                player,
                "diagnostic-content-owner"
            );
            world.start();

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            ServerPacketWriter packets=
                new ServerPacketWriter(
                    wire,
                    new IsaacCipher(
                        new int[]{141,142,143,144}
                    )
                );

            ContentResult authority=
                dispatch(
                    world,
                    player,
                    registry,
                    "::authority ignored",
                    packets
                );

            require(
                authority!=null&&
                authority.saveReason()==null&&
                LocalDiagnosticContentModule
                    .authoritySummary()
                    .equals(
                        authority.logText()),
                "authority result="+authority
            );

            ContentResult diagnostic=
                dispatch(
                    world,
                    player,
                    registry,
                    "::contentregistry ignored",
                    packets
                );

            String expected=
                "CONTENT_REGISTRY_DIAGNOSTIC "+
                registry.summary();

            require(
                diagnostic!=null&&
                diagnostic.saveReason()==null&&
                expected.equals(
                    diagnostic.logText()),
                "contentregistry result="+
                diagnostic+
                " expected="+expected
            );

            packets.flush();

            require(
                wire.size()==0,
                "diagnostic commands emitted wire bytes="+
                wire.size()
            );

            require(
                diagnostic.logText().contains(
                    "module="+
                    LocalDiagnosticContentModule
                        .MODULE_ID)&&
                diagnostic.logText().contains(
                    "provenance=CUSTOM_LOCALLAB"),
                "diagnostic omitted content binding provenance"
            );

            System.out.println(
                "LOCAL_DIAGNOSTIC_CONTENT_OWNERSHIP_PASS "+
                "contentRegistryOwned=true "+
                "authorityOwned=true "+
                "module="+
                LocalDiagnosticContentModule.MODULE_ID+
                " priority100=true "+
                "provenance=CUSTOM_LOCALLAB "+
                "dynamicSummary=true "+
                "legacyAuthorityFallback=false "+
                "wireBytes=0"
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(player);
            world.close();
        }
    }

    private static void legacyAuthorityFallbackRemoved(
        World world
    )throws Exception{
        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();

        ServerPacketWriter packets=
            new ServerPacketWriter(
                wire,
                new IsaacCipher(
                    new int[]{145,146,147,148}
                )
            );

        LocalDiagnosticCommandHandler legacy=
            new LocalDiagnosticCommandHandler(
                world,
                new EquipmentState(),
                new NativeItemLibraryService()
            );

        boolean claimed=
            legacy.handle(
                new String[]{"authority"},
                packets,
                "[diagnostic-content-test] ",
                "diagnostic-content-owner",
                "diagnostic-content-owner",
                false,
                null
            );

        packets.flush();

        require(
            !claimed,
            "legacy LocalDiagnosticCommandHandler still claims authority"
        );
        require(
            wire.size()==0,
            "legacy authority fallback emitted wire bytes="+
                wire.size()
        );
    }

    private static ContentResult dispatch(
        World world,
        WorldPlayer player,
        ContentRegistry registry,
        String command,
        ServerPacketWriter packets
    )throws Exception{
        AtomicReference<ContentResult>
            result=new AtomicReference<>();
        AtomicReference<Throwable>
            failure=new AtomicReference<>();

        world.submitAndWait(
            player,
            ()->{
                try{
                    result.set(
                        registry.dispatchCommand(
                            player,
                            command,
                            packets
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
                "diagnostic command failed "+
                command,
                failure.get()
            );

        return result.get();
    }

    private static void assertBinding(
        ContentRegistry.BindingInfo binding,
        String command
    ){
        require(
            binding!=null&&
            LocalDiagnosticContentModule
                .MODULE_ID
                .equals(binding.moduleId)&&
            binding.priority==100&&
            binding.provenance==
                ContentProvenance.CUSTOM_LOCALLAB,
            command+" binding="+binding
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private LocalDiagnosticContentOwnershipTest(){}
}
