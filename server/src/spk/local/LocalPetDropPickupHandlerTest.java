package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalPetDropPickupHandlerTest {
    private static final class Bridge
        implements LocalPetDropPickupHandler.SessionBridge
    {
        SceneUpdatePublisher scenePublisher;
        String saveReason;
        long followResetCount;
        long followEnsureCount;
        final PlayerState playerState=
            new PlayerState();

        @Override public String username(){
            return "opensrc";
        }

        @Override public boolean persistentAccount(){
            return true;
        }

        @Override public long sessionWorldTick(){
            return 123L;
        }

        @Override public SceneUpdatePublisher scenePublisher(){
            return scenePublisher;
        }

        @Override public void saveAccount(
            String tag,
            String reason
        ){
            saveReason=reason;
        }

        @Override public PlayerState.PreparedScopesightMaintenance
            prepareScopesightPassive(
                boolean active
            ){
            return playerState
                .prepareScopesightMaintenance(
                    active
                );
        }

        @Override public void publishScopesightPassive(
            PlayerState.PreparedScopesightMaintenance prepared,
            ServerPacketWriter serverPackets
        )throws java.io.IOException{
            for(int skill=0;
                skill<PlayerState.COMBAT_SKILL_COUNT;
                skill++){
                if((prepared.changedMask&
                    (1<<skill))==0)
                    continue;

                serverPackets.fixed(
                    134,
                    BootstrapPackets.skill134(
                        skill,
                        playerState.xp(skill),
                        prepared.levelForSkill(
                            skill
                        )
                    )
                );
            }
        }

        @Override public void commitScopesightPassive(
            PlayerState.PreparedScopesightMaintenance prepared
        ){
            playerState
                .commitScopesightMaintenance(
                    prepared
                );
        }

        @Override public void resetPetFollowDeadline(){
            followResetCount++;
        }

        @Override public void ensurePetFollowScheduled(long now){
            followEnsureCount++;
        }
    }

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(50L);
        try{
            WorldPlayer player=new WorldPlayer();
            MovementState movement=player.movement();
            PetState petState=player.petState();
            DevAuthorityWorkbench dev=new DevAuthorityWorkbench();
            NpcRegistry npcs=new NpcRegistry(dev);

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            ServerPacketWriter w=
                new ServerPacketWriter(
                    wire,
                    new IsaacCipher(new int[]{1,2,3,4})
                );

            Bridge bridge=new Bridge();
            bridge.scenePublisher=
                new SceneUpdatePublisher(
                    w,
                    new SceneCoordinateContext(
                        MovementState.REGION_BASE_X,
                        MovementState.REGION_BASE_Y,
                        0
                    )
                );

            LocalPetDropPickupHandler h=
                new LocalPetDropPickupHandler(
                    world,
                    player.bank(),
                    movement,
                    petState,
                    player.petEffects(),
                    player.miniPets(),
                    npcs,
                    new VoidglassPetState(),
                    new PetAccessoryState(),
                    dev,
                    bridge
                );

            if(h.pickupPending())
                throw new AssertionError(
                    "new coordinator must start without pickup"
                );

            PetDefinitionRepository.Def def=
                PetDefinitionRepository.get(24019);
            if(def==null)
                throw new AssertionError(
                    "expected certified pet definition 24019"
                );

            String spawn=npcs.spawnPet(def,movement,w);
            if(!spawn.startsWith("PET_SPAWN_OK"))
                throw new AssertionError(
                    "pet spawn failed: "+spawn
                );
            petState.activate(def);

            NpcEntity pet=npcs.pet();
            if(pet==null)
                throw new AssertionError("spawned pet missing");

            NpcAction pickup=
                new NpcAction(155,pet.sceneIndex);

            if(!LocalPetDropPickupHandler.isPetPickupAction(
                pickup,
                pet,
                petState
            )){
                throw new AssertionError(
                    "opcode155 active-pet pickup classification changed"
                );
            }

            if(!h.handlePickupNpcAction(
                pickup,
                w,
                "[pet-pickup-test] "
            )){
                throw new AssertionError(
                    "pickup action not handled"
                );
            }

            if(!h.pickupPending())
                throw new AssertionError(
                    "pickup state was not owned by coordinator"
                );

            h.cancelDeferredForNewNpcAction(
                new NpcAction(17,pet.sceneIndex),
                "[pet-pickup-test] "
            );

            if(h.pickupPending())
                throw new AssertionError(
                    "new NPC action did not cancel deferred pickup"
                );

            if(LocalPetDropPickupHandler.isPetPickupAction(
                new NpcAction(72,pet.sceneIndex),
                pet,
                petState
            )){
                throw new AssertionError(
                    "attack opcode must not classify as pickup"
                );
            }

            if(LocalPetDropPickupHandler.chebyshev(
                10,10,12,11
            )!=2){
                throw new AssertionError(
                    "pickup distance metric changed"
                );
            }

            testOrdinaryGroundDropAtomicity();
            testMainPetTransactionAtomicity();

            System.out.println(
                "LOCAL_PET_DROP_PICKUP_HANDLER_PASS "+
                "pickupOwned=true npcCancel=true opcodeBoundary=true "+
                "ordinaryDropAtomic=true ordinaryDropMergeAtomic=true "+
                "groundSceneContextRollback=true "+
                "petFreshSummonAtomic=true petReplaceAtomic=true "+
                "petPickupCompleteAtomic=true petRetryExact=true"
            );
        }finally{
            world.close();
        }
    }

    private static void testOrdinaryGroundDropAtomicity()
        throws Exception
    {
        World world=
            World.isolatedForTest(51L);

        try{
            WorldPlayer player=
                new WorldPlayer();
            BankState bank=
                player.bank();
            MovementState movement=
                player.movement();
            DevAuthorityWorkbench dev=
                new DevAuthorityWorkbench();
            NpcRegistry npcs=
                new NpcRegistry(dev);
            Bridge bridge=
                new Bridge();

            ServerPacketWriter healthy=
                new ServerPacketWriter(
                    new ByteArrayOutputStream(),
                    new IsaacCipher(
                        new int[]{9,10,11,12}
                    )
                );

            bridge.scenePublisher=
                new SceneUpdatePublisher(
                    healthy,
                    new SceneCoordinateContext(
                        MovementState.REGION_BASE_X,
                        MovementState.REGION_BASE_Y,
                        0
                    )
                );

            LocalPetDropPickupHandler h=
                new LocalPetDropPickupHandler(
                    world,
                    bank,
                    movement,
                    player.petState(),
                    player.petEffects(),
                    player.miniPets(),
                    npcs,
                    new VoidglassPetState(),
                    new PetAccessoryState(),
                    dev,
                    bridge
                );

            bank.spawnItem(
                4151,
                1,
                healthy
            );
            int slot=
                findSlot(
                    bank,
                    4151
                );

            if(slot<0)
                throw new AssertionError(
                    "ordinary Drop fixture item missing"
                );

            Tile tile=
                new Tile(
                    movement.x(),
                    movement.y(),
                    0
                );

            OutboundPacketQueue failedQueue=
                fullQueue();
            ServerPacketWriter failedWriter=
                queueWriter(
                    failedQueue,
                    new int[]{13,14,15,16}
                );
            SceneCoordinateContext failedContext=
                new SceneCoordinateContext(
                    MovementState.REGION_BASE_X,
                    MovementState.REGION_BASE_Y,
                    0
                );
            bridge.scenePublisher=
                new SceneUpdatePublisher(
                    failedWriter,
                    failedContext
                );

            boolean failed=false;

            try{
                h.handleDrop(
                    new DropItemAction(
                        4151,
                        BankState.NORMAL_INVENTORY_CONTAINER,
                        slot
                    ),
                    failedWriter,
                    "[ground-drop-atomic] "
                );
            }catch(java.io.IOException expected){
                failed=true;
            }

            if(!failed||
               bank.inventoryCount(4151)!=1||
               world.groundItems().findOwned(
                    4151,
                    tile.x,
                    tile.y,
                    tile.plane,
                    "opensrc"
               )!=null||
               failedContext.currentChunkX()!=-1||
               failedContext.currentChunkY()!=-1||
               failedQueue.queuedBytes()!=1024)
                throw new AssertionError(
                    "failed new-stack Drop changed cross-domain preimage"
                );

            bridge.scenePublisher=
                new SceneUpdatePublisher(
                    healthy,
                    new SceneCoordinateContext(
                        MovementState.REGION_BASE_X,
                        MovementState.REGION_BASE_Y,
                        0
                    )
                );

            h.handleDrop(
                new DropItemAction(
                    4151,
                    BankState.NORMAL_INVENTORY_CONTAINER,
                    slot
                ),
                healthy,
                "[ground-drop-atomic] "
            );

            GroundItem first=
                world.groundItems().findOwned(
                    4151,
                    tile.x,
                    tile.y,
                    tile.plane,
                    "opensrc"
                );

            if(first==null||
               first.amount!=1||
               bank.inventoryCount(4151)!=0)
                throw new AssertionError(
                    "ordinary Drop retry did not commit exactly once"
                );

            bank.spawnItem(
                4151,
                1,
                healthy
            );
            int mergeSlot=
                findSlot(
                    bank,
                    4151
                );

            OutboundPacketQueue mergeFailQueue=
                fullQueue();
            ServerPacketWriter mergeFailWriter=
                queueWriter(
                    mergeFailQueue,
                    new int[]{17,18,19,20}
                );
            SceneCoordinateContext mergeContext=
                new SceneCoordinateContext(
                    MovementState.REGION_BASE_X,
                    MovementState.REGION_BASE_Y,
                    0
                );
            bridge.scenePublisher=
                new SceneUpdatePublisher(
                    mergeFailWriter,
                    mergeContext
                );

            boolean mergeFailed=false;

            try{
                h.handleDrop(
                    new DropItemAction(
                        4151,
                        BankState.NORMAL_INVENTORY_CONTAINER,
                        mergeSlot
                    ),
                    mergeFailWriter,
                    "[ground-drop-merge-atomic] "
                );
            }catch(java.io.IOException expected){
                mergeFailed=true;
            }

            if(!mergeFailed||
               bank.inventoryCount(4151)!=1||
               world.groundItems().byId(first.id)!=first||
               first.amount!=1||
               mergeContext.currentChunkX()!=-1||
               mergeContext.currentChunkY()!=-1)
                throw new AssertionError(
                    "failed merged Drop changed registry/inventory preimage"
                );

            bridge.scenePublisher=
                new SceneUpdatePublisher(
                    healthy,
                    new SceneCoordinateContext(
                        MovementState.REGION_BASE_X,
                        MovementState.REGION_BASE_Y,
                        0
                    )
                );

            h.handleDrop(
                new DropItemAction(
                    4151,
                    BankState.NORMAL_INVENTORY_CONTAINER,
                    mergeSlot
                ),
                healthy,
                "[ground-drop-merge-atomic] "
            );

            if(world.groundItems().byId(first.id)!=first||
               first.amount!=2||
               bank.inventoryCount(4151)!=0)
                throw new AssertionError(
                    "merged Drop retry did not preserve identity/exact amount"
                );
        }finally{
            world.close();
        }
    }

    private static void testMainPetTransactionAtomicity()
        throws Exception
    {
        World world=
            World.isolatedForTest(53L);

        try{
            WorldPlayer player=
                new WorldPlayer();
            BankState bank=
                player.bank();
            MovementState movement=
                player.movement();
            PetState petState=
                player.petState();
            PetEffectState effects=
                player.petEffects();
            DevAuthorityWorkbench dev=
                new DevAuthorityWorkbench();
            NpcRegistry npcs=
                new NpcRegistry(dev);
            VoidglassPetState voidglass=
                new VoidglassPetState();
            PetAccessoryState accessory=
                new PetAccessoryState();
            Bridge bridge=
                new Bridge();

            ServerPacketWriter healthy=
                new ServerPacketWriter(
                    new ByteArrayOutputStream(),
                    new IsaacCipher(
                        new int[]{29,30,31,32}
                    )
                );

            bridge.scenePublisher=
                new SceneUpdatePublisher(
                    healthy,
                    new SceneCoordinateContext(
                        MovementState.REGION_BASE_X,
                        MovementState.REGION_BASE_Y,
                        0
                    )
                );

            LocalPetDropPickupHandler handler=
                new LocalPetDropPickupHandler(
                    world,
                    bank,
                    movement,
                    petState,
                    effects,
                    player.miniPets(),
                    npcs,
                    voidglass,
                    accessory,
                    dev,
                    bridge
                );

            PetDefinitionRepository.Def first=
                PetDefinitionRepository.get(
                    24019
                );

            if(first==null)
                throw new AssertionError(
                    "expected certified pet 24019"
                );

            PetDefinitionRepository.Def second=null;
            for(PetDefinitionRepository.Def candidate:
                    PetDefinitionRepository.all()){
                if(candidate.itemId!=first.itemId){
                    second=candidate;
                    break;
                }
            }

            if(second==null)
                throw new AssertionError(
                    "expected second mapped pet"
                );

            bank.spawnItem(
                first.itemId,
                1,
                healthy
            );
            int firstSlot=
                findSlot(
                    bank,
                    first.itemId
                );

            OutboundPacketQueue summonFailQueue=
                fullQueue();
            ServerPacketWriter summonFail=
                queueWriter(
                    summonFailQueue,
                    new int[]{33,34,35,36}
                );

            boolean summonFailed=false;
            try{
                handler.handleDrop(
                    new DropItemAction(
                        first.itemId,
                        BankState.NORMAL_INVENTORY_CONTAINER,
                        firstSlot
                    ),
                    summonFail,
                    "[pet-summon-atomic] "
                );
            }catch(java.io.IOException expected){
                summonFailed=true;
            }

            if(!summonFailed||
               bank.inventoryCount(first.itemId)!=1||
               petState.active()||
               npcs.pet()!=null||
               bridge.saveReason!=null)
                throw new AssertionError(
                    "failed fresh pet summon changed canonical preimage"
                );

            handler.handleDrop(
                new DropItemAction(
                    first.itemId,
                    BankState.NORMAL_INVENTORY_CONTAINER,
                    firstSlot
                ),
                healthy,
                "[pet-summon-atomic] "
            );

            if(!petState.active()||
               petState.itemId()!=first.itemId||
               npcs.pet()==null||
               bank.inventoryCount(first.itemId)!=0||
               !"PET_DROP_SUMMON".equals(
                    bridge.saveReason
               ))
                throw new AssertionError(
                    "fresh pet summon retry did not commit"
                );

            NpcEntity oldActor=
                npcs.pet();

            bank.spawnItem(
                second.itemId,
                1,
                healthy
            );
            int secondSlot=
                findSlot(
                    bank,
                    second.itemId
                );

            bridge.saveReason=null;

            OutboundPacketQueue replaceFailQueue=
                fullQueue();
            ServerPacketWriter replaceFail=
                queueWriter(
                    replaceFailQueue,
                    new int[]{37,38,39,40}
                );

            boolean replaceFailed=false;
            try{
                handler.handleDrop(
                    new DropItemAction(
                        second.itemId,
                        BankState.NORMAL_INVENTORY_CONTAINER,
                        secondSlot
                    ),
                    replaceFail,
                    "[pet-replace-atomic] "
                );
            }catch(java.io.IOException expected){
                replaceFailed=true;
            }

            if(!replaceFailed||
               petState.itemId()!=first.itemId||
               npcs.pet()!=oldActor||
               bank.inventoryCount(second.itemId)!=1||
               bank.inventoryCount(first.itemId)!=0||
               bridge.saveReason!=null)
                throw new AssertionError(
                    "failed pet replacement changed old/new ownership"
                );

            handler.handleDrop(
                new DropItemAction(
                    second.itemId,
                    BankState.NORMAL_INVENTORY_CONTAINER,
                    secondSlot
                ),
                healthy,
                "[pet-replace-atomic] "
            );

            if(petState.itemId()!=second.itemId||
               npcs.pet()==null||
               npcs.pet()==oldActor||
               bank.inventoryCount(second.itemId)!=0||
               bank.inventoryCount(first.itemId)!=1||
               !"PET_REPLACE".equals(
                    bridge.saveReason
               ))
                throw new AssertionError(
                    "pet replacement retry did not commit exactly once"
                );

            NpcEntity replacementActor=
                npcs.pet();
            int inventoryBeforePickup=
                bank.inventoryCount(
                    second.itemId
                );

            java.lang.reflect.Method complete=
                LocalPetDropPickupHandler.class
                    .getDeclaredMethod(
                        "tryCompletePetPickup",
                        ServerPacketWriter.class,
                        String.class,
                        long.class
                    );
            complete.setAccessible(true);

            java.lang.reflect.Field completeAt=
                LocalPetDropPickupHandler.class
                    .getDeclaredField(
                        "pendingPetPickupCompleteAtMs"
                    );
            java.lang.reflect.Field pendingScene=
                LocalPetDropPickupHandler.class
                    .getDeclaredField(
                        "pendingPetPickupScene"
                    );
            java.lang.reflect.Field pendingItem=
                LocalPetDropPickupHandler.class
                    .getDeclaredField(
                        "pendingPetPickupItem"
                    );
            java.lang.reflect.Field pendingNpc=
                LocalPetDropPickupHandler.class
                    .getDeclaredField(
                        "pendingPetPickupNpc"
                    );
            java.lang.reflect.Field completeScene=
                LocalPetDropPickupHandler.class
                    .getDeclaredField(
                        "pendingPetPickupCompleteScene"
                    );
            java.lang.reflect.Field completeReason=
                LocalPetDropPickupHandler.class
                    .getDeclaredField(
                        "pendingPetPickupCompleteReason"
                    );

            for(java.lang.reflect.Field field:
                    new java.lang.reflect.Field[]{
                        completeAt,
                        pendingScene,
                        pendingItem,
                        pendingNpc,
                        completeScene,
                        completeReason
                    })
                field.setAccessible(true);

            long now=
                System.currentTimeMillis();
            completeAt.setLong(
                handler,
                now
            );
            pendingScene.set(
                handler,
                Integer.valueOf(
                    replacementActor.sceneIndex
                )
            );
            pendingItem.setInt(
                handler,
                second.itemId
            );
            pendingNpc.setInt(
                handler,
                second.npcId
            );
            completeScene.setInt(
                handler,
                replacementActor.sceneIndex
            );
            completeReason.set(
                handler,
                "TEST_ATOMIC_PICKUP"
            );

            bridge.saveReason=null;

            OutboundPacketQueue pickupFailQueue=
                fullQueue();
            ServerPacketWriter pickupFail=
                queueWriter(
                    pickupFailQueue,
                    new int[]{41,42,43,44}
                );

            boolean pickupFailed=false;
            try{
                complete.invoke(
                    handler,
                    pickupFail,
                    "[pet-pickup-atomic] ",
                    now
                );
            }catch(java.lang.reflect.InvocationTargetException wrapped){
                if(wrapped.getCause()
                        instanceof java.io.IOException)
                    pickupFailed=true;
                else
                    throw wrapped;
            }

            if(!pickupFailed||
               petState.itemId()!=second.itemId||
               npcs.pet()!=replacementActor||
               bank.inventoryCount(second.itemId)!=
                    inventoryBeforePickup||
               !handler.pickupPending()||
               bridge.saveReason!=null)
                throw new AssertionError(
                    "failed pickup completion changed canonical preimage"
                );

            complete.invoke(
                handler,
                healthy,
                "[pet-pickup-atomic] ",
                now
            );

            if(petState.active()||
               npcs.pet()!=null||
               bank.inventoryCount(second.itemId)!=
                    inventoryBeforePickup+1||
               handler.pickupPending()||
               !"PET_PICKUP".equals(
                    bridge.saveReason
               ))
                throw new AssertionError(
                    "pickup completion retry did not commit exactly once"
                );
        }finally{
            world.close();
        }
    }

    private static OutboundPacketQueue fullQueue()
        throws Exception
    {
        OutboundPacketQueue queue=
            new OutboundPacketQueue(1024);
        queue.offer(
            new byte[1024]
        );
        return queue;
    }

    private static ServerPacketWriter queueWriter(
        OutboundPacketQueue queue,
        int[] seed
    ){
        return new ServerPacketWriter(
            queue,
            new IsaacCipher(seed)
        );
    }

    private static int findSlot(
        BankState bank,
        int itemId
    ){
        for(int i=0;i<bank.inventoryCapacity();i++){
            BankState.Stack stack=
                bank.inventoryAt(i);
            if(stack!=null&&
               stack.itemId==itemId&&
               stack.qty>0)
                return i;
        }
        return -1;
    }

}
