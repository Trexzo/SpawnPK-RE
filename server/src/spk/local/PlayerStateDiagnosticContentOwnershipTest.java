package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;

public final class PlayerStateDiagnosticContentOwnershipTest {
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

            assertBinding(
                registry.commandBinding(
                    "prayerinfo"
                ),
                "prayerinfo"
            );
            assertBinding(
                registry.commandBinding(
                    "magicinfo"
                ),
                "magicinfo"
            );
            assertBinding(
                registry.commandBinding(
                    "styleinfo"
                ),
                "styleinfo"
            );

            assertInternalProjection();
            legacyFallbackRemoved(
                world
            );

            AtomicReference<ContentRegistration>
                override=new AtomicReference<>();

            registry.installCustom(
                new ContentModule(){
                    @Override public String id(){
                        return "prayerinfo-override";
                    }

                    @Override public void register(
                        ContentRegistrar registrar
                    ){
                        override.set(
                            registrar.command(
                                "prayerinfo",
                                200,
                                context->
                                    ContentResult.handled(
                                        "PRAYER_INFO_OVERRIDE",
                                        null
                                    )
                            )
                        );
                    }
                }
            );

            ContentRegistry.BindingInfo overridden=
                registry.commandBinding(
                    "prayerinfo"
                );

            require(
                overridden!=null&&
                "prayerinfo-override".equals(
                    overridden.moduleId)&&
                overridden.priority==200&&
                overridden.provenance==
                    ContentProvenance.CUSTOM_LOCALLAB,
                "override binding="+
                overridden
            );

            world.registerPlayer(
                player,
                "player-state-diagnostic-owner"
            );
            world.start();

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            ServerPacketWriter packets=
                writer(
                    wire
                );

            ContentResult overrideResult=
                dispatch(
                    world,
                    player,
                    registry,
                    "::prayerinfo",
                    packets
                );

            require(
                overrideResult!=null&&
                "PRAYER_INFO_OVERRIDE".equals(
                    overrideResult.logText()),
                "prayerinfo override result="+
                overrideResult
            );

            ContentRegistration handle=
                override.get();

            require(
                handle!=null&&
                handle.unregister(),
                "prayerinfo override unregister failed"
            );

            assertBinding(
                registry.commandBinding(
                    "prayerinfo"
                ),
                "prayerinfo"
            );

            ContentResult prayer=
                dispatch(
                    world,
                    player,
                    registry,
                    "::prayerinfo ignored",
                    packets
                );

            require(
                prayer!=null&&
                prayer.saveReason()==null&&
                prayerExpected(
                    player
                ).equals(
                    prayer.logText()),
                "prayerinfo result="+
                prayer
            );

            ContentResult magic=
                dispatch(
                    world,
                    player,
                    registry,
                    "::magicinfo ignored",
                    packets
                );

            require(
                magic!=null&&
                magic.saveReason()==null&&
                magicExpected(
                    player
                ).equals(
                    magic.logText()),
                "magicinfo result="+
                magic
            );

            ContentResult style=
                dispatch(
                    world,
                    player,
                    registry,
                    "::styleinfo ignored",
                    packets
                );

            require(
                style!=null&&
                style.saveReason()==null&&
                styleExpected(
                    player
                ).equals(
                    style.logText()),
                "styleinfo result="+
                style
            );

            packets.flush();

            require(
                wire.size()==0,
                "player-state diagnostics emitted wire bytes="+
                wire.size()
            );

            System.out.println(
                "PLAYER_STATE_DIAGNOSTIC_CONTENT_OWNERSHIP_PASS "+
                "prayerinfo=true "+
                "magicinfo=true "+
                "styleinfo=true "+
                "internalProjection=true "+
                "publicApiExpanded=false "+
                "priorityOverride=true "+
                "restore=true "+
                "textParity=true "+
                "legacyFallback=false "+
                "wireBytes=0"
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(
                    player
                );
            world.close();
        }
    }

    private static void assertInternalProjection(){
        Class<?> type=
            LocalDiagnosticContentPlayer.class;

        require(
            !Modifier.isPublic(
                type.getModifiers()),
            "diagnostic projection became public"
        );

        require(
            "spk.local".equals(
                type.getPackage()
                    .getName()),
            "diagnostic projection package="+
            type.getPackage()
                .getName()
        );

        for(java.lang.reflect.Method method:
                ContentPlayer.class
                    .getMethods()){
            String name=
                method.getName();

            require(
                !"prayerStateSummary".equals(
                    name)&&
                !"magicStateSummary".equals(
                    name)&&
                !"weaponItemId".equals(
                    name)&&
                !"combatStyleStateSummary".equals(
                    name),
                "diagnostic method leaked to public ContentPlayer: "+
                name
            );
        }
    }

    private static void legacyFallbackRemoved(
        World world
    )throws Exception{
        LocalDiagnosticCommandHandler legacy=
            new LocalDiagnosticCommandHandler(
                world,
                new EquipmentState(),
                new NativeItemLibraryService()
            );

        for(String command:
                new String[]{
                    "prayerinfo",
                    "magicinfo",
                    "styleinfo"
                }){
            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            ServerPacketWriter packets=
                writer(
                    wire
                );

            boolean claimed=
                legacy.handle(
                    new String[]{command},
                    packets,
                    "[player-state-diagnostic-test] ",
                    "player-state-diagnostic-owner",
                    "player-state-diagnostic-owner",
                    false,
                    null
                );

            packets.flush();

            require(
                !claimed&&
                wire.size()==0,
                "legacy diagnostic still claimed "+
                command+
                " wire="+
                wire.size()
            );
        }
    }

    private static String prayerExpected(
        WorldPlayer player
    ){
        return "V510_PRAYER_INFO "+
            player.prayers()
                .summary()+
            " definitions="+
            PrayerDefinitionRepository.count();
    }

    private static String magicExpected(
        WorldPlayer player
    ){
        return "V510_MAGIC_INFO "+
            player.magic()
                .summary()+
            " definitions="+
            SpellDefinitionRepository.count();
    }

    private static String styleExpected(
        WorldPlayer player
    ){
        int weapon=
            player.equipment()
                .weapon();
        int root=
            CombatInterfaceRepository.forWeapon(
                weapon
            );

        return "V510_STYLE_INFO weapon="+
            weapon+" "+
            player.combatStyles()
                .summary(
                    root
                )+
            " roots="+
            CombatStyleRepository.rootCount()+
            " styles="+
            CombatStyleRepository.countStyles();
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
                "player-state diagnostic failed "+
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
                .equals(
                    binding.moduleId)&&
            binding.priority==100&&
            binding.provenance==
                ContentProvenance.CUSTOM_LOCALLAB,
            command+
            " binding="+
            binding
        );
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream wire
    ){
        return new ServerPacketWriter(
            wire,
            new IsaacCipher(
                new int[]{191,192,193,194}
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

    private PlayerStateDiagnosticContentOwnershipTest(){}
}
