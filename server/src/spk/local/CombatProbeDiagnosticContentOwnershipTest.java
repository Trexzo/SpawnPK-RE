package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;

public final class CombatProbeDiagnosticContentOwnershipTest {
    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer player=
            new WorldPlayer();

        try{
            ContentRegistry registry=
                world.content();

            ContentRegistry.BindingInfo binding=
                registry.commandBinding(
                    "combatprobe"
                );

            require(
                binding!=null&&
                LocalDiagnosticContentModule
                    .MODULE_ID
                    .equals(
                        binding.moduleId)&&
                binding.priority==100&&
                binding.provenance==
                    ContentProvenance.CUSTOM_LOCALLAB,
                "combatprobe binding="+binding
            );

            assertInternalProjection();
            legacyRuntimeRouteRemoved(
                player
            );

            player.equipment()
                .setWeapon(
                    28526
                );

            world.registerPlayer(
                player,
                "combatprobe-diagnostic-owner"
            );
            world.start();

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            ServerPacketWriter packets=
                writer(
                    wire
                );

            ContentResult result=
                dispatch(
                    world,
                    player,
                    registry,
                    "::combatprobe ignored",
                    packets
                );

            packets.flush();

            require(
                result!=null&&
                result.saveReason()==null&&
                expected(
                    player
                ).equals(
                    result.logText()),
                "combatprobe result="+result+
                " expected="+
                expected(player)
            );

            require(
                wire.size()==0,
                "combatprobe emitted wire bytes="+
                wire.size()
            );

            System.out.println(
                "COMBAT_PROBE_DIAGNOSTIC_CONTENT_OWNERSHIP_PASS "+
                "binding=true "+
                "internalProjection=true "+
                "publicApiExpanded=false "+
                "textParity=true "+
                "wireBytes=0 "+
                "legacyRuntimeRoute=false"
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(
                    player
                );
            world.close();
        }
    }

    private static String expected(
        WorldPlayer player
    ){
        int weapon=
            player.equipment()
                .weapon();
        CombatWeaponProfile profile=
            CombatWeaponRepository.resolve(
                weapon
            );
        int combatRoot=
            CombatInterfaceRepository.forWeapon(
                weapon
            );
        CombatState state=
            player.combatState();

        return "V56_COMBAT_PROBE weapon="+
            weapon+
            " profile="+profile+
            " style={"+
            player.combatStyles()
                .summary(
                    combatRoot
                )+
            "}"+
            " targetScene="+
            state.targetSceneIndex+
            " targetDef="+
            state.targetDefinitionId+
            " context="+
            state.context+
            " formula=UNRESOLVED_NO_DAMAGE_GUESS";
    }

    private static void assertInternalProjection(){
        Class<?> type=
            LocalDiagnosticContentPlayer.class;

        require(
            !Modifier.isPublic(
                type.getModifiers()),
            "diagnostic projection became public"
        );

        for(java.lang.reflect.Method method:
                ContentPlayer.class
                    .getMethods()){
            String name=
                method.getName();

            require(
                !"combatWeaponProfileSummary".equals(
                    name)&&
                !"combatTargetSceneIndex".equals(
                    name)&&
                !"combatTargetDefinitionId".equals(
                    name)&&
                !"combatContextSummary".equals(
                    name),
                "combat diagnostic leaked to public ContentPlayer: "+
                name
            );
        }
    }

    private static void legacyRuntimeRouteRemoved(
        WorldPlayer player
    )throws Exception{
        DevAuthorityWorkbench dev=
            new DevAuthorityWorkbench();
        NpcRegistry npcs=
            new NpcRegistry(
                dev
            );
        CombatEngine combat=
            new CombatEngine(
                dev
            );
        LocalPetRuntimeCommandHandler petRuntime=
            new LocalPetRuntimeCommandHandler(
                player.petState(),
                player.petEffects(),
                npcs,
                player.movement()
            );

        LocalCombatCommandHandler handler=
            new LocalCombatCommandHandler(
                combat,
                player.equipment(),
                player.combatStyles(),
                npcs,
                petRuntime
            );

        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();
        ServerPacketWriter packets=
            writer(
                wire
            );

        require(
            handler.handle(
                new String[]{"combatprobe"},
                "combatprobe",
                packets
            )==null,
            "legacy combat handler still claims combatprobe"
        );

        packets.flush();

        require(
            wire.size()==0,
            "legacy combatprobe route emitted wire"
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
                "combatprobe diagnostic failed "+
                command,
                failure.get()
            );

        return result.get();
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream wire
    ){
        return new ServerPacketWriter(
            wire,
            new IsaacCipher(
                new int[]{241,242,243,244}
            )
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

    private CombatProbeDiagnosticContentOwnershipTest(){}
}
