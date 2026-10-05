package spk.local;

public final class SharedNpcRemoteRegionIsolationTest {
    public static void main(String[] args)
        throws Exception {

        World world=
            World.isolatedForTest(600L);
        WorldPlayer source=
            new WorldPlayer();
        WorldPlayer viewer=
            new WorldPlayer();

        long sourceGeneration=
            world.registerPlayer(
                source,
                "region-source"
            );
        long viewerGeneration=
            world.registerPlayer(
                viewer,
                "region-viewer"
            );

        OutboundPacketQueue sourceQueue=
            new OutboundPacketQueue();
        OutboundPacketQueue viewerQueue=
            new OutboundPacketQueue();

        ServerPacketWriter sourceWriter=
            new ServerPacketWriter(
                sourceQueue,
                new IsaacCipher(
                    new int[]{91,92,93,94}
                )
            );
        ServerPacketWriter viewerWriter=
            new ServerPacketWriter(
                viewerQueue,
                new IsaacCipher(
                    new int[]{95,96,97,98}
                )
            );

        NpcRegistry sourceNpcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );
        NpcRegistry viewerNpcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );

        Player81WorldSync.register(
            sourceWriter,
            world,
            source,
            new DevAuthorityWorkbench()
        );
        Player81WorldSync.register(
            viewerWriter,
            world,
            viewer,
            new DevAuthorityWorkbench()
        );

        SharedNpcWorldRelay.register(
            sourceWriter,
            world,
            source,
            sourceNpcs,
            source.movement()
        );
        SharedNpcWorldRelay.register(
            viewerWriter,
            world,
            viewer,
            viewerNpcs,
            viewer.movement()
        );

        try{
            sourceWriter.varShort(
                81,
                BootstrapPackets.player81Idle()
            );
            viewerWriter.varShort(
                81,
                BootstrapPackets.player81Idle()
            );

            int initialIndex=
                Player81WorldSync.clientIndexFor(
                    viewerWriter,
                    source
                );

            require(
                initialIndex>=0,
                "HOME source did not become visible to viewer"
            );

            PetDefinitionRepository.Def petDef=
                PetDefinitionRepository.get(
                    24019
                );

            require(
                petDef!=null,
                "pet fixture unavailable"
            );

            sourceNpcs.spawnPet(
                petDef,
                source.movement(),
                sourceWriter
            );

            SharedNpcWorldRelay.syncRemotePets(
                viewerWriter
            );

            require(
                hasDefinition(
                    viewerNpcs,
                    petDef.npcId
                ),
                "HOME remote pet mirror missing"
            );

            /*
             * Do not publish another packet-81 after changing source region.
             * The old Track therefore still exists internally. clientIndexFor
             * must nevertheless reject it against current spatial authority.
             */
            source.movement().enterTransientRegion(
                source.movement().x(),
                source.movement().y(),
                1,
                MovementState.REGION_BASE_X,
                MovementState.REGION_BASE_Y
            );

            require(
                Player81WorldSync.clientIndexFor(
                    viewerWriter,
                    source
                )<0,
                "wrong-plane stale player index remained usable"
            );

            SharedNpcWorldRelay.syncRemotePets(
                viewerWriter
            );

            require(
                !hasDefinition(
                    viewerNpcs,
                    petDef.npcId
                ),
                "wrong-plane remote pet mirror remained"
            );

            source.movement().returnHome();

            require(
                Player81WorldSync.clientIndexFor(
                    viewerWriter,
                    source
                )==initialIndex,
                "HOME visibility did not recover before viewer refresh"
            );

            SharedNpcWorldRelay.syncRemotePets(
                viewerWriter
            );

            require(
                hasDefinition(
                    viewerNpcs,
                    petDef.npcId
                ),
                "HOME remote pet mirror did not recover"
            );

            LocalTeleportDestinationCatalog.Destination boss=
                LocalTeleportDestinationCatalog.get(
                    TeleportNavigationService.EntryKind.BOSS
                );
            Tile far=
                WorldCollisionAuthority.safeTile(
                    boss.regionId,
                    boss.plane
                );
            int chunkX=far.x>>3;
            int chunkY=far.y>>3;

            source.movement().enterTransientRegion(
                far.x,
                far.y,
                far.plane,
                (chunkX-6)<<3,
                (chunkY-6)<<3
            );

            require(
                Player81WorldSync.clientIndexFor(
                    viewerWriter,
                    source
                )<0,
                "out-of-region stale player index remained usable"
            );

            SharedNpcWorldRelay.syncRemotePets(
                viewerWriter
            );

            require(
                !hasDefinition(
                    viewerNpcs,
                    petDef.npcId
                ),
                "out-of-region remote pet mirror remained"
            );

            require(
                viewer.movement().inHomeWindow()&&
                viewer.movement().x()==MovementState.INITIAL_X&&
                viewer.movement().y()==MovementState.INITIAL_Y,
                "viewer movement/window changed during source rebase"
            );

            System.out.println(
                "SHARED_NPC_REMOTE_REGION_ISOLATION_PASS "+
                "homeVisible=true "+
                "stalePlayerIndexRejected=true "+
                "wrongPlanePetRemoved=true "+
                "outOfRegionPetRemoved=true "+
                "homeRecovery=true "+
                "viewerStateUnchanged=true"
            );
        }finally{
            SharedNpcWorldRelay.unregister(
                sourceWriter
            );
            SharedNpcWorldRelay.unregister(
                viewerWriter
            );
            Player81WorldSync.unregister(
                sourceWriter
            );
            Player81WorldSync.unregister(
                viewerWriter
            );

            if(source.registered())
                world.unregisterPlayer(
                    source,
                    sourceGeneration
                );
            if(viewer.registered())
                world.unregisterPlayer(
                    viewer,
                    viewerGeneration
                );

            world.close();
        }
    }

    private static boolean hasDefinition(
        NpcRegistry registry,
        int definition
    ){
        for(NpcEntity entity:
                registry.snapshot())
            if(entity.definitionId==
                    definition)
                return true;

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

    private SharedNpcRemoteRegionIsolationTest(){}
}
