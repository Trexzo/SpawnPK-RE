package spk.local;

import java.io.ByteArrayOutputStream;

public final class PlayerPvpDeathSettlementIntegrationTest {
    private static final int QUEUE_CAPACITY=1<<20;

    private static final class PetBridge
        implements LocalPetDropPickupHandler.SessionBridge
    {
        SceneUpdatePublisher publisher;
        @Override public String username(){return "target";}
        @Override public boolean persistentAccount(){return false;}
        @Override public long sessionWorldTick(){return 1L;}
        @Override public SceneUpdatePublisher scenePublisher(){return publisher;}
        @Override public void saveAccount(String tag,String reason){}
        @Override public int syncScopesightPassive(ServerPacketWriter writer){return 0;}
        @Override public void resetPetFollowDeadline(){}
        @Override public void ensurePetFollowScheduled(long now){}
    }

    private static final class RegionBridge
        implements LocalRegionStreamHandler.SessionBridge
    {
        SceneUpdatePublisher publisher;
        @Override public String username(){return "target";}
        @Override public SceneUpdatePublisher scenePublisher(){return publisher;}
        @Override public void replaceScenePublisher(SceneUpdatePublisher replacement){
            publisher=replacement;
        }
        @Override public void resetPetFollowRuntime(){}
    }

    private static final class TickBridge
        implements LocalWorldTickCoordinator.SessionBridge
    {
        SceneUpdatePublisher publisher;
        int deathSettlementSaves;
        int respawnSaves;
        int killerRewardSaves;
        int respawnAppearancePublishes;
        int[] respawnAppearanceItems;

        @Override public Player81WorldSync.Context player81Sync(){return null;}
        @Override public SceneUpdatePublisher scenePublisher(){return publisher;}
        @Override public void publishPlayerAppearanceSnapshot(
            int[] appearanceItems,
            ServerPacketWriter writer
        ){
            respawnAppearancePublishes++;
            respawnAppearanceItems=
                appearanceItems.clone();
        }
        @Override public void saveAccount(String tag,String reason){
            if("PLAYER_DEATH_SETTLEMENT".equals(reason))
                deathSettlementSaves++;
            if("PLAYER_RESPAWN".equals(reason))
                respawnSaves++;
        }
        @Override public void savePlayerAccount(
            WorldPlayer player,
            long expectedGeneration,
            String tag,
            String reason
        ){
            if("PVP_KILL_REWARD".equals(reason))
                killerRewardSaves++;
        }
        @Override public void publishOpponentOverlay(
            NpcEntity target,
            ServerPacketWriter writer,
            String tag,
            String reason
        ){}
        @Override public void clearOpponentOverlay(
            ServerPacketWriter writer,
            String tag,
            String reason
        ){}
        @Override public long petFollowDeadline(){return Long.MAX_VALUE;}
        @Override public void setPetFollowDeadline(long value){}
        @Override public void ensurePetFollowScheduled(long now){}
        @Override public void ensurePetTestSequenceScheduled(long now){}
    }

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer attacker=new WorldPlayer();
        WorldPlayer target=new WorldPlayer();

        long attackerGeneration=
            world.registerPlayer(attacker,"opensrc");
        long targetGeneration=
            world.registerPlayer(target,"target");

        G1DefaultLoadoutRegearService
            .ensureStarterDefault(
                world,
                "target"
            );

        OutboundPacketQueue attackerQueue=
            new OutboundPacketQueue(QUEUE_CAPACITY);
        OutboundPacketQueue targetQueue=
            new OutboundPacketQueue(QUEUE_CAPACITY);

        ServerPacketWriter attackerWriter=
            new ServerPacketWriter(
                attackerQueue,
                new IsaacCipher(new int[]{71,72,73,74})
            );
        ServerPacketWriter targetWriter=
            new ServerPacketWriter(
                targetQueue,
                new IsaacCipher(new int[]{81,82,83,84})
            );

        try{
            MovementState targetMovement=target.movement();
            EquipmentState targetEquipment=target.equipment();
            CombatStyleState targetCombatStyles=target.combatStyles();
            PetEffectState targetPetEffects=target.petEffects();
            PetState targetPetState=target.petState();

            DevAuthorityWorkbench dev=new DevAuthorityWorkbench();
            NpcRegistry npcs=new NpcRegistry(dev);
            HomeWorldRuntimePlan home=new HomeWorldRuntimePlan();
            CombatEngine combat=new CombatEngine(dev);

            npcs.bootstrapHome(
                targetWriter,
                targetMovement,
                targetPetState,
                home
            );
            drain(targetQueue);

            int deathX=3200;
            int deathY=3200;
            int baseX=3150;
            int baseY=3150;

            targetMovement.enterTransientRegion(
                deathX,
                deathY,
                0,
                baseX,
                baseY
            );
            attacker.movement().enterTransientRegion(
                deathX-1,
                deathY,
                0,
                baseX,
                baseY
            );

            npcs.detachRegionViewPreservingFollowers(
                targetWriter
            );
            drain(targetQueue);

            SceneUpdatePublisher publisher=
                new SceneUpdatePublisher(
                    targetWriter,
                    new SceneCoordinateContext(
                        baseX,
                        baseY,
                        0
                    )
                );

            LocalPlayerInteractionHandler targetInteractions=
                new LocalPlayerInteractionHandler(
                    world,
                    target,
                    targetMovement,
                    targetEquipment
                );
            LocalBankObjectInteractionHandler bankObjects=
                new LocalBankObjectInteractionHandler(
                    target.bank(),
                    targetMovement
                );
            LocalRoutedNpcInteractionHandler routedNpcs=
                new LocalRoutedNpcInteractionHandler(
                    npcs,
                    target.bank(),
                    targetMovement,
                    null,
                    target,
                    targetEquipment
                );
            LocalGroundItemInteractionHandler groundItems=
                new LocalGroundItemInteractionHandler(
                    world,
                    target.bank(),
                    targetMovement
                );

            PetBridge petBridge=new PetBridge();
            petBridge.publisher=publisher;

            LocalPetDropPickupHandler petDropPickup=
                new LocalPetDropPickupHandler(
                    world,
                    target.bank(),
                    targetMovement,
                    targetPetState,
                    targetPetEffects,
                    target.miniPets(),
                    npcs,
                    new VoidglassPetState(),
                    new PetAccessoryState(),
                    dev,
                    petBridge
                );

            LocalPetRuntimeCommandHandler petRuntime=
                new LocalPetRuntimeCommandHandler(
                    targetPetState,
                    targetPetEffects,
                    npcs,
                    targetMovement
                );

            RegionBridge regionBridge=new RegionBridge();
            regionBridge.publisher=publisher;

            LocalRegionStreamHandler regionStreams=
                new LocalRegionStreamHandler(
                    true,
                    world,
                    target,
                    targetMovement,
                    home,
                    npcs,
                    targetInteractions,
                    combat,
                    regionBridge
                );

            TickBridge tickBridge=new TickBridge();
            tickBridge.publisher=publisher;

            LocalWorldTickCoordinator coordinator=
                new LocalWorldTickCoordinator(
                    true,
                    world,
                    target,
                    targetMovement,
                    targetEquipment,
                    targetCombatStyles,
                    targetPetEffects,
                    npcs,
                    home,
                    combat,
                    regionStreams,
                    targetInteractions,
                    bankObjects,
                    routedNpcs,
                    groundItems,
                    petDropPickup,
                    petRuntime,
                    tickBridge
                );

            if(DeathPolicyRepository.get(995).kind!=
                    DeathPolicyRepository.Kind.STANDARD_UNRESOLVED)
                throw new AssertionError(
                    "coin fixture must use explicit LocalLab standard-loss policy"
                );

            BankState.PreparedInventoryMutation coins=
                target.bank().prepareAddInventoryAmount(
                    995,
                    10
                );
            if(!coins.accepted())
                throw new AssertionError(
                    "target coin fixture rejected "+
                    coins.result
                );
            target.bank().commitPreparedInventoryMutation(
                coins
            );

            attacker.equipment().setWeapon(4151);
            if(!target.playerState().setCurrentLevel(
                    PlayerState.HITPOINTS,
                    9))
                throw new AssertionError(
                    "target HP fixture rejected"
                );

            Player81WorldSync.Context attackerSync=
                Player81WorldSync.register(
                    attackerWriter,
                    world,
                    attacker,
                    new DevAuthorityWorkbench()
                );
            Player81WorldSync.Context targetSync=
                Player81WorldSync.register(
                    targetWriter,
                    world,
                    target,
                    new DevAuthorityWorkbench()
                );

            Player81WorldSync.transformForTest(
                attackerSync,
                BootstrapPackets.player81Idle()
            );
            Player81WorldSync.transformForTest(
                targetSync,
                BootstrapPackets.player81Idle()
            );

            int targetIndex=
                attackerSync.clientIndexFor(target);
            if(targetIndex<0)
                throw new AssertionError(
                    "target not visible to attacker"
                );

            LocalPlayerInteractionHandler attackerInteractions=
                new LocalPlayerInteractionHandler(
                    world,
                    attacker,
                    attacker.movement(),
                    attacker.equipment(),
                    attacker.combatStyles(),
                    CombatDamageRules.localLabFallback(),
                    CombatAttackTimingRules.recoveredCompatibility(),
                    CombatSystemHooks.forPlayer(attacker)
                );

            String request=
                attackerInteractions.handleResolved(
                    new PlayerAction(
                        128,
                        1,
                        targetIndex,
                        "Attack"
                    ),
                    target,
                    attackerSync
                );
            if(request==null||
               !request.contains("PLAYER_ATTACK_REQUEST"))
                throw new AssertionError(
                    "live PvP request missing "+
                    request
                );

            String attack=
                attackerInteractions.tickAttack(
                    20L,
                    attackerWriter,
                    attackerSync
                );
            if(attack==null||
               !attack.contains("PLAYER_ATTACK_RESOLVED")||
               !attack.contains("targetDied=true"))
                throw new AssertionError(
                    "live PvP lethal resolution missing "+
                    attack
                );

            if(!target.lifecycle().dead()||
               target.playerState().currentLevel(
                    PlayerState.HITPOINTS)!=0)
                throw new AssertionError(
                    "live PvP did not enter canonical death"
                );

            PlayerLifecycleState.DeathAttribution attribution=
                target.lifecycle().deathAttribution();
            if(attribution==null||
               !attacker.id().equals(
                    attribution.attackerId
               )||
               attribution.attackerGeneration!=
                    attackerGeneration||
               !"PLAYER_PVP".equals(
                    attribution.context))
                throw new AssertionError(
                    "live lethal attribution missing "+
                    attribution
                );

            long deathSequence=
                target.lifecycle().deathSequence();

            targetWriter.beginBatch();
            coordinator.tick(
                target.lifecycle().respawnTick(),
                10_000L,
                targetWriter,
                "[g1.1-live-pvp] "
            );
            LocalSession.endWorldTickBatch(
                targetWriter
            );
            coordinator.commitRegionStreamBatch();
            coordinator.commitHomePresentationBatch();
            coordinator.commitGroundPresentationBatch(
                10_000L
            );

            if(!coordinator.deferredRespawnEligible()||
               !coordinator.deferredDeathSettlementEligible())
                throw new AssertionError(
                    "live PvP death did not prepare settlement+respawn"
                );

            coordinator
                .settleDeferredRespawnAfterWorldTick(
                    targetWriter,
                    "[g1.1-live-pvp] "
                );

            GroundItem drop=
                world.groundItems().findOwned(
                    995,
                    deathX,
                    deathY,
                    0,
                    "opensrc"
                );

            if(drop==null||
               drop.amount!=10||
               target.bank().inventoryCount(995)!=0)
                throw new AssertionError(
                    "live PvP death did not create exact killer-scoped ground postimage"
                );

            if(!target.lifecycle().alive()||
               target.lifecycle().deathAttribution()!=null||
               targetMovement.x()!=MovementState.INITIAL_X||
               targetMovement.y()!=MovementState.INITIAL_Y)
                throw new AssertionError(
                    "live PvP respawn postimage incorrect"
                );

            if(target.equipment().weapon()!=
                    G1DefaultLoadoutRegearService.STARTER_WEAPON||
               target.bank().inventoryCount(
                    G1DefaultLoadoutRegearService.STARTER_FOOD
                )!=
                    G1DefaultLoadoutRegearService.STARTER_FOOD_COUNT)
                throw new AssertionError(
                    "live PvP respawn did not apply G1 default loadout"
                );

            int trackedDeathLoot=
                world.deathLootLifecycle()
                    .trackedCount();

            if(trackedDeathLoot<=0||
               trackedDeathLoot!=
                    world.groundItems().size())
                throw new AssertionError(
                    "live PvP settlement did not register exact death-loot lifecycle tracked="+
                    trackedDeathLoot+
                    " ground="+
                    world.groundItems().size()
                );

            int weaponAppearanceIndex=
                EquipmentSlot.WEAPON.appearanceIndex;

            if(tickBridge.respawnAppearancePublishes!=1||
               tickBridge.respawnAppearanceItems==null||
               weaponAppearanceIndex<0||
               tickBridge.respawnAppearanceItems[
                    weaponAppearanceIndex
               ]!=
                    G1DefaultLoadoutRegearService.STARTER_WEAPON)
                throw new AssertionError(
                    "live PvP respawn did not publish exact starter appearance"
                );

            PvpKillRewardService.Counters killerReward=
                PvpKillRewardService.counters(
                    attacker
                );

            if(killerReward.kills!=1L||
               killerReward.points!=1L)
                throw new AssertionError(
                    "live PvP killer reward mismatch "+
                    killerReward
                );

            if(tickBridge.deathSettlementSaves!=1||
               tickBridge.respawnSaves!=1||
               tickBridge.killerRewardSaves!=1)
                throw new AssertionError(
                    "live PvP persistence count mismatch death="+
                    tickBridge.deathSettlementSaves+
                    " respawn="+
                    tickBridge.respawnSaves+
                    " killerReward="+
                    tickBridge.killerRewardSaves
                );

            int groundStacksBeforeReplay=
                world.groundItems().size();
            long coinGroundId=drop.id;
            int coinAmountBeforeReplay=drop.amount;

            coordinator.settleCurrentDeathForSessionTeardown(
                "[g1.1-live-pvp-replay] "
            );

            GroundItem replayDrop=
                world.groundItems().findOwned(
                    995,
                    deathX,
                    deathY,
                    0,
                    "opensrc"
                );
            if(replayDrop==null||
               replayDrop.id!=coinGroundId||
               replayDrop.amount!=coinAmountBeforeReplay||
               world.groundItems().size()!=groundStacksBeforeReplay||
               tickBridge.deathSettlementSaves!=1||
               tickBridge.respawnSaves!=1||
               tickBridge.killerRewardSaves!=1||
               PvpKillRewardService.counters(attacker).kills!=1L||
               PvpKillRewardService.counters(attacker).points!=1L)
                throw new AssertionError(
                    "post-respawn settlement replay changed settled postimage"
                );

            System.out.println(
                "PLAYER_PVP_DEATH_SETTLEMENT_INTEGRATION_PASS "+
                "liveAttack=true "+
                "deathSequence="+deathSequence+" "+
                "killerScopedLoot=true "+
                "carriedStateAtomic=true "+
                "groundDropExactlyOnce=true "+
                "deathSettlementPersistedOnce=true "+
                "respawnPersistedOnce=true "+
                "killerReward=true "+
                "killerRewardPersistRequestedOnce=true "+
                "defaultRegearApplied=true "+
                "starterWeapon="+
                G1DefaultLoadoutRegearService.STARTER_WEAPON+" "+
                "starterFood="+
                G1DefaultLoadoutRegearService.STARTER_FOOD+"x"+
                G1DefaultLoadoutRegearService.STARTER_FOOD_COUNT+" "+
                "respawnAppearancePublished=true "+
                "deathLootLifecycleRegistered=true "+
                "attributionClearedOnRespawn=true "+
                "replayIdempotent=true "+
                "authority="+
                LocalLabDeathDispositionPolicy.AUTHORITY
            );

            Player81WorldSync.unregister(
                attackerWriter
            );
            Player81WorldSync.unregister(
                targetWriter
            );
        }finally{
            try{Player81WorldSync.unregister(attackerWriter);}
            catch(Exception ignored){}
            try{Player81WorldSync.unregister(targetWriter);}
            catch(Exception ignored){}

            if(attacker.registered())
                world.unregisterPlayer(
                    attacker,
                    attackerGeneration
                );
            if(target.registered())
                world.unregisterPlayer(
                    target,
                    targetGeneration
                );
            world.close();
        }
    }

    private static void drain(
        OutboundPacketQueue queue
    )throws Exception{
        ByteArrayOutputStream out=
            new ByteArrayOutputStream();
        queue.drainTo(
            out,
            1<<20
        );
    }

    private PlayerPvpDeathSettlementIntegrationTest(){}
}
