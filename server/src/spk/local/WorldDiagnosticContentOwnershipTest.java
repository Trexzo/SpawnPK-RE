package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;

public final class WorldDiagnosticContentOwnershipTest {
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
                    "worldauth"
                ),
                "worldauth"
            );
            assertBinding(
                registry.commandBinding(
                    "collisionauth"
                ),
                "collisionauth"
            );

            assertPositionApi(
                player
            );

            legacyFallbackRemoved(
                world
            );

            AtomicReference<ContentRegistration>
                override=new AtomicReference<>();

            registry.installCustom(
                new ContentModule(){
                    @Override public String id(){
                        return "worldauth-override";
                    }

                    @Override public void register(
                        ContentRegistrar registrar
                    ){
                        override.set(
                            registrar.command(
                                "worldauth",
                                200,
                                context->
                                    ContentResult.handled(
                                        "WORLD_AUTH_OVERRIDE",
                                        null
                                    )
                            )
                        );
                    }
                }
            );

            ContentRegistry.BindingInfo overridden=
                registry.commandBinding(
                    "worldauth"
                );

            require(
                overridden!=null&&
                "worldauth-override".equals(
                    overridden.moduleId)&&
                overridden.priority==200&&
                overridden.provenance==
                    ContentProvenance.CUSTOM_LOCALLAB,
                "override binding="+
                overridden
            );

            world.registerPlayer(
                player,
                "world-diagnostic-content-owner"
            );
            world.start();

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            ServerPacketWriter packets=
                writer(
                    wire
                );

            ContentResult overriddenResult=
                dispatch(
                    world,
                    player,
                    registry,
                    "::worldauth",
                    packets
                );

            require(
                overriddenResult!=null&&
                "WORLD_AUTH_OVERRIDE".equals(
                    overriddenResult.logText()),
                "worldauth override result="+
                overriddenResult
            );

            ContentRegistration handle=
                override.get();

            require(
                handle!=null&&
                handle.unregister(),
                "worldauth override unregister failed"
            );

            assertBinding(
                registry.commandBinding(
                    "worldauth"
                ),
                "worldauth"
            );

            MovementState movement=
                player.movement();

            ContentResult currentRegion=
                dispatch(
                    world,
                    player,
                    registry,
                    "::worldauth",
                    packets
                );

            int currentRegionId=
                regionId(
                    movement.x(),
                    movement.y()
                );

            require(
                currentRegion!=null&&
                currentRegion.saveReason()==null&&
                worldAuthorityExpected(
                    currentRegionId
                ).equals(
                    currentRegion.logText()),
                "current worldauth="+
                currentRegion
            );

            ContentResult explicitRegion=
                dispatch(
                    world,
                    player,
                    registry,
                    "::worldauth 12850",
                    packets
                );

            require(
                explicitRegion!=null&&
                worldAuthorityExpected(
                    12850
                ).equals(
                    explicitRegion.logText()),
                "explicit worldauth="+
                explicitRegion
            );

            ContentResult currentCollision=
                dispatch(
                    world,
                    player,
                    registry,
                    "::collisionauth",
                    packets
                );

            String expectedCurrentCollision=
                collisionExpected(
                    movement.x(),
                    movement.y(),
                    movement.plane()
                );

            require(
                currentCollision!=null&&
                currentCollision.saveReason()==null&&
                expectedCurrentCollision.equals(
                    currentCollision.logText()),
                "current collisionauth="+
                currentCollision
            );

            ContentResult loneArgument=
                dispatch(
                    world,
                    player,
                    registry,
                    "::collisionauth 999",
                    packets
                );

            require(
                loneArgument!=null&&
                expectedCurrentCollision.equals(
                    loneArgument.logText()),
                "legacy lone-argument behavior changed="+
                loneArgument
            );

            ContentResult explicitCollision=
                dispatch(
                    world,
                    player,
                    registry,
                    "::collisionauth 3200 3201 0",
                    packets
                );

            require(
                explicitCollision!=null&&
                collisionExpected(
                    3200,
                    3201,
                    0
                ).equals(
                    explicitCollision.logText()),
                "explicit collisionauth="+
                explicitCollision
            );

            packets.flush();

            require(
                wire.size()==0,
                "world diagnostic content emitted wire bytes="+
                wire.size()
            );

            System.out.println(
                "WORLD_DIAGNOSTIC_CONTENT_OWNERSHIP_PASS "+
                "worldauth=true "+
                "collisionauth=true "+
                "semanticPosition=true "+
                "positionDefaults=true "+
                "priorityOverride=true "+
                "restore=true "+
                "explicitRegionParity=true "+
                "collisionDefaultParity=true "+
                "collisionLoneArgParity=true "+
                "explicitCollisionParity=true "+
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

    private static void assertPositionApi(
        WorldPlayer player
    )throws Exception{
        ContentPlayer content=
            ContentRuntimeAdapters.player(
                player
            );

        require(
            content.worldX()==
                player.movement().x()&&
            content.worldY()==
                player.movement().y()&&
            content.plane()==
                player.movement().plane(),
            "runtime semantic position mismatch"
        );

        for(String methodName:
                new String[]{
                    "worldX",
                    "worldY",
                    "plane"
                }){
            Method method=
                ContentPlayer.class.getMethod(
                    methodName
                );

            require(
                method.isDefault(),
                methodName+
                " must remain a default-compatible API addition"
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
                new MovementState(),
                new PrayerState(),
                new MagicState(),
                new CombatStyleState(),
                new NativeItemLibraryService()
            );

        for(String[] tokens:
                new String[][]{
                    {"worldauth"},
                    {"collisionauth"}
                }){
            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            ServerPacketWriter packets=
                writer(
                    wire
                );

            boolean claimed=
                legacy.handle(
                    tokens,
                    packets,
                    "[world-diagnostic-content-test] ",
                    "world-diagnostic-content-owner",
                    "world-diagnostic-content-owner",
                    false,
                    null
                );

            packets.flush();

            require(
                !claimed&&
                wire.size()==0,
                "legacy diagnostic still claimed "+
                tokens[0]+
                " wire="+
                wire.size()
            );
        }
    }

    private static String worldAuthorityExpected(
        int region
    ){
        WorldRegionAuthorityRepository.Region authority=
            WorldRegionAuthorityRepository.get(
                region
            );

        return "V5150_WORLD_AUTHORITY region="+
            region+
            " result="+
            (authority==null
                ?"UNKNOWN"
                :authority.toString())+
            " repositoryRegions="+
            WorldRegionAuthorityRepository.count()+
            " decoded="+
            WorldRegionAuthorityRepository
                .fullyDecodedCount()+
            " productionConfirmed="+
            WorldRegionAuthorityRepository
                .productionConfirmedCount()+
            " behavior=DATA_ONLY_NO_TELEPORT";
    }

    private static String collisionExpected(
        int x,
        int y,
        int plane
    ){
        int region=
            regionId(
                x,
                y
            );

        return "V5160_COLLISION_AUTH world="+
            x+","+y+","+plane+
            " region="+region+
            " mask="+
            WorldCollisionAuthority.maskAt(
                x,
                y,
                plane
            )+
            " blocked="+
            WorldCollisionAuthority.blockedTile(
                x,
                y,
                plane
            )+
            " repositoryRegions="+
            WorldCollisionAuthority.regionCount()+
            " entries="+
            WorldCollisionAuthority.entryCount();
    }

    private static int regionId(
        int x,
        int y
    ){
        return ((x>>6)<<8)|
            (y>>6);
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
                "world diagnostic command failed "+
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
                new int[]{181,182,183,184}
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

    private WorldDiagnosticContentOwnershipTest(){}
}
