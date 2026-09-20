package spk.local;

public final class CanonicalRemotePetProjectionTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer source=new WorldPlayer();
        WorldPlayer viewer=new WorldPlayer();

        world.registerPlayer(source,"opensrc");
        world.registerPlayer(viewer,"src");

        OutboundPacketQueue sourceQueue=
            new OutboundPacketQueue();
        OutboundPacketQueue viewerQueue=
            new OutboundPacketQueue();

        ServerPacketWriter sourceWriter=
            new ServerPacketWriter(
                sourceQueue,
                new IsaacCipher(new int[]{1,2,3,4})
            );
        ServerPacketWriter viewerWriter=
            new ServerPacketWriter(
                viewerQueue,
                new IsaacCipher(new int[]{5,6,7,8})
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

        DevAuthorityWorkbench sourceDev=
            new DevAuthorityWorkbench();
        DevAuthorityWorkbench viewerDev=
            new DevAuthorityWorkbench();

        NpcRegistry sourceNpcs=
            new NpcRegistry(
                sourceDev,
                world.petNpcs(),
                source.id()
            );
        NpcRegistry viewerNpcs=
            new NpcRegistry(
                viewerDev,
                world.petNpcs(),
                viewer.id()
            );

        MovementState sourceMovement=
            source.movement();
        MovementState viewerMovement=
            viewer.movement();

        SharedNpcWorldRelay.register(
            sourceWriter,
            world,
            source,
            sourceNpcs,
            sourceMovement
        );
        SharedNpcWorldRelay.register(
            viewerWriter,
            world,
            viewer,
            viewerNpcs,
            viewerMovement
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

            PetDefinitionRepository.Def mainDef=
                PetDefinitionRepository.get(24019);
            if(mainDef==null)
                throw new AssertionError(
                    "missing main pet 24019"
                );

            String mainSpawn=
                sourceNpcs.spawnPet(
                    mainDef,
                    sourceMovement,
                    sourceWriter
                );
            if(!mainSpawn.startsWith("PET_SPAWN_OK"))
                throw new AssertionError(mainSpawn);

            MiniPetDefinitionRepository.Def miniDef=
                MiniPetDefinitionRepository.get(23988);
            if(miniDef==null)
                throw new AssertionError(
                    "missing mini pet 23988"
                );

            String miniSpawn=
                sourceNpcs.spawnOrReplaceMiniPet(
                    miniDef,
                    sourceMovement,
                    sourceWriter
                );
            if(!miniSpawn.startsWith("MINIPET_SPAWN_OK"))
                throw new AssertionError(miniSpawn);

            WorldNpc canonicalMain=
                world.petNpcs().main(source.id());
            WorldNpc canonicalMini=
                world.petNpcs().mini(source.id());

            if(canonicalMain==null||
               canonicalMini==null)
                throw new AssertionError(
                    "canonical pet actors missing"
                );

            int canonicalMainX=canonicalMain.x();
            int canonicalMainY=canonicalMain.y();
            int canonicalMiniX=canonicalMini.x();
            int canonicalMiniY=canonicalMini.y();

            // Deliberately corrupt only the source session's presentation copies.
            // A canonical remote projection must ignore this stale duplicate truth.
            sourceNpcs.pet().x+=4;
            sourceNpcs.pet().y+=1;
            sourceNpcs.miniPet().x+=3;
            sourceNpcs.miniPet().y+=1;

            if(sourceNpcs.pet().x==canonicalMainX&&
               sourceNpcs.pet().y==canonicalMainY)
                throw new AssertionError(
                    "main presentation drift setup failed"
                );

            if(sourceNpcs.miniPet().x==canonicalMiniX&&
               sourceNpcs.miniPet().y==canonicalMiniY)
                throw new AssertionError(
                    "mini presentation drift setup failed"
                );

            SharedNpcWorldRelay.syncRemotePets(
                viewerWriter
            );

            NpcEntity remoteMain=
                findDef(
                    viewerNpcs,
                    mainDef.npcId
                );
            NpcEntity remoteMini=
                findDef(
                    viewerNpcs,
                    miniDef.npcId
                );

            if(remoteMain==null||
               remoteMini==null)
                throw new AssertionError(
                    "canonical remote actors missing"
                );

            if(remoteMain.x!=canonicalMainX||
               remoteMain.y!=canonicalMainY)
                throw new AssertionError(
                    "remote main followed stale session copy "+
                    remoteMain.x+","+remoteMain.y+
                    " canonical="+
                    canonicalMainX+","+canonicalMainY
                );

            if(remoteMini.x!=canonicalMiniX||
               remoteMini.y!=canonicalMiniY)
                throw new AssertionError(
                    "remote mini followed stale session copy "+
                    remoteMini.x+","+remoteMini.y+
                    " canonical="+
                    canonicalMiniX+","+canonicalMiniY
                );

            sourceWriter.varShort(
                81,
                CombatSync.player81AnimationOnly(15552)
            );

            int before=viewerQueue.queuedBytes();

            String nativeState=
                sourceNpcs.setPetNativeState(
                    2,
                    sourceWriter
                );

            if(!nativeState.startsWith(
                    "PET_NATIVE_STATE_OK"))
                throw new AssertionError(nativeState);

            if(viewerQueue.queuedBytes()!=before)
                throw new AssertionError(
                    "canonical pet mask overtook packet81 barrier"
                );

            viewerWriter.varShort(
                81,
                BootstrapPackets.player81Idle()
            );

            if(viewerQueue.queuedBytes()<=before)
                throw new AssertionError(
                    "canonical-targeted pet mask not delivered"
                );

            System.out.println(
                "CANONICAL_REMOTE_PET_PROJECTION_PASS "+
                "mainEntity="+canonicalMain.id+
                " miniEntity="+canonicalMini.id+
                " staleSourceIgnored=true"+
                " packet81Barrier=true"+
                " canonicalMaskTarget=true"
            );
        }finally{
            SharedNpcWorldRelay.unregister(sourceWriter);
            SharedNpcWorldRelay.unregister(viewerWriter);
            Player81WorldSync.unregister(sourceWriter);
            Player81WorldSync.unregister(viewerWriter);
            world.unregisterPlayer(source);
            world.unregisterPlayer(viewer);
            world.close();
        }
    }

    private static NpcEntity findDef(
        NpcRegistry registry,
        int definitionId
    ){
        for(NpcEntity npc:registry.snapshot())
            if(npc.definitionId==definitionId)
                return npc;
        return null;
    }
}
