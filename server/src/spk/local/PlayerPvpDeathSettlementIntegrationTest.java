package spk.local;

import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.builtin.SuppliesMerchantDialogueContent;

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

        world.start();

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

            LocalTeleportDestinationCatalog.Destination
                pkDestination=
                    LocalTeleportDestinationCatalog.get(
                        TeleportNavigationService.EntryKind.PK
                    );
            Tile pkTile=
                WorldCollisionAuthority.safeTile(
                    pkDestination.regionId,
                    pkDestination.plane
                );

            if(pkTile==null)
                throw new AssertionError(
                    "PK destination has no safe tile"
                );

            int deathX=pkTile.x;
            int deathY=pkTile.y;
            int chunkX=deathX>>3;
            int chunkY=deathY>>3;
            int baseX=(chunkX-6)<<3;
            int baseY=(chunkY-6)<<3;

            LocalPlayerInteractionHandler
                targetNavigationInteractions=
                    new LocalPlayerInteractionHandler(
                        world,
                        target,
                        targetMovement,
                        targetEquipment,
                        target::generation,
                        LocalLabPvpRegionPolicy.INSTANCE
                    );

            LocalRegionDevCommandHandler
                targetNavigationRelocation=
                    new LocalRegionDevCommandHandler(
                        world,
                        target,
                        targetMovement,
                        targetNavigationInteractions,
                        combat,
                        npcs,
                        targetPetState,
                        home,
                        ()->{}
                    );

            LocalTeleportNavigationRuntime
                targetNavigation=
                    new LocalTeleportNavigationRuntime(
                        targetNavigationRelocation
                    );

            DevAuthorityWorkbench attackerDev=
                new DevAuthorityWorkbench();
            NpcRegistry attackerNpcs=
                new NpcRegistry(
                    attackerDev
                );
            HomeWorldRuntimePlan attackerHome=
                new HomeWorldRuntimePlan();
            CombatEngine attackerCombat=
                new CombatEngine(
                    attackerDev
                );

            LocalPlayerInteractionHandler
                attackerNavigationInteractions=
                    new LocalPlayerInteractionHandler(
                        world,
                        attacker,
                        attacker.movement(),
                        attacker.equipment(),
                        attacker::generation,
                        LocalLabPvpRegionPolicy.INSTANCE
                    );

            LocalRegionDevCommandHandler
                attackerNavigationRelocation=
                    new LocalRegionDevCommandHandler(
                        world,
                        attacker,
                        attacker.movement(),
                        attackerNavigationInteractions,
                        attackerCombat,
                        attackerNpcs,
                        attacker.petState(),
                        attackerHome,
                        ()->{}
                    );

            LocalTeleportNavigationRuntime
                attackerNavigation=
                    new LocalTeleportNavigationRuntime(
                        attackerNavigationRelocation
                    );

            SceneUpdatePublisher targetHomePublisher=
                new SceneUpdatePublisher(
                    targetWriter,
                    new SceneCoordinateContext(
                        MovementState.REGION_BASE_X,
                        MovementState.REGION_BASE_Y,
                        0
                    )
                );

            SceneUpdatePublisher attackerHomePublisher=
                new SceneUpdatePublisher(
                    attackerWriter,
                    new SceneCoordinateContext(
                        MovementState.REGION_BASE_X,
                        MovementState.REGION_BASE_Y,
                        0
                    )
                );

            bankFundedRegear(
                world,
                attacker,
                attackerWriter
            );

            if(attacker.equipment().weapon()!=
                    LocalLabShopRuntime.STARTER_WHIP||
               attacker.bank().inventoryCount(
                    LocalLabShopRuntime.COINS
               )!=0||
               attacker.bank().inventoryCount(
                    LocalLabShopRuntime.STARTER_WHIP
               )!=0)
                throw new AssertionError(
                    "bank-funded HOME regear postimage missing before PK navigation"
                );

            LocalTeleportNavigationRuntime.Result
                targetInitialPkNavigation=
                    targetNavigation.request(
                        "target",
                        TeleportNavigationService.EntryKind.PK,
                        targetHomePublisher,
                        targetWriter
                    );

            LocalTeleportNavigationRuntime.Result
                attackerInitialPkNavigation=
                    attackerNavigation.request(
                        "opensrc",
                        TeleportNavigationService.EntryKind.PK,
                        attackerHomePublisher,
                        attackerWriter
                    );

            if(!targetInitialPkNavigation.succeeded()||
               targetInitialPkNavigation.relocation==null||
               !attackerInitialPkNavigation.succeeded()||
               attackerInitialPkNavigation.relocation==null)
                throw new AssertionError(
                    "semantic PK navigation did not relocate both players"
                );

            if(WorldRegionAuthorityRepository.forTile(
                    targetMovement.x(),
                    targetMovement.y()
               ).regionId!=pkDestination.regionId||
               WorldRegionAuthorityRepository.forTile(
                    attacker.movement().x(),
                    attacker.movement().y()
               ).regionId!=pkDestination.regionId)
                throw new AssertionError(
                    "semantic PK navigation did not land in configured PK region"
                );

            /*
             * Both semantic requests deliberately land on the same recovered
             * collision-safe tile. Move only the attacker one cardinal tile
             * within the same PK region to make the live combat fixture
             * attackable without bypassing the navigation entrypoint.
             */
            attacker.movement().enterTransientRegion(
                deathX-1,
                deathY,
                pkDestination.plane,
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
                    CombatSystemHooks.forPlayer(attacker),
                    attacker::generation,
                    LocalLabPvpRegionPolicy.INSTANCE
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

            PlayerPvpEligibilityPolicy.Result
                livePkEligibility=
                    LocalLabPvpRegionPolicy.INSTANCE
                        .evaluate(
                            attacker,
                            target
                        );

            if(!livePkEligibility.eligible)
                throw new AssertionError(
                    "production PK region gate rejected navigated players "+
                    livePkEligibility.detail
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
                    pkDestination.plane,
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

            /*
             * Killer completes the ordinary owner-scoped ground Take instead
             * of directly mutating inventory/registry state.
             */
            String killerStep=
                attacker.movement().accept(
                    new MovementRequest(
                        164,
                        false,
                        new int[]{deathX},
                        new int[]{deathY},
                        new byte[0]
                    )
                );

            if(!killerStep.startsWith("ACCEPTED"))
                throw new AssertionError(
                    "killer move to death loot rejected "+
                    killerStep
                );

            attacker.movement().advance();

            SceneUpdatePublisher attackerPkPublisher=
                new SceneUpdatePublisher(
                    attackerWriter,
                    new SceneCoordinateContext(
                        baseX,
                        baseY,
                        pkDestination.plane
                    )
                );

            LocalGroundItemInteractionHandler
                killerGroundTake=
                    new LocalGroundItemInteractionHandler(
                        world,
                        attacker.bank(),
                        attacker.movement()
                    );

            int killerCoinsBefore=
                attacker.bank().inventoryCount(
                    995
                );

            LocalGroundItemInteractionHandler.Result
                killerTake=
                    killerGroundTake.handle(
                        new GroundItemInteraction(
                            236,
                            3,
                            995,
                            deathX,
                            deathY
                        ),
                        "opensrc",
                        attackerPkPublisher,
                        attackerWriter
                    );

            if(killerTake==null||
               !"GROUND_TAKE".equals(
                    killerTake.saveReason
               )||
               !killerTake.logText.contains(
                    "TAKE_ON_TILE_IMMEDIATE")||
               attacker.bank().inventoryCount(
                    995
               )!=killerCoinsBefore+10||
               world.groundItems().byId(
                    coinGroundId
               )!=null)
                throw new AssertionError(
                    "killer ordinary ground Take did not settle exact PK loot "+
                    (killerTake==null
                        ?"null"
                        :killerTake.logText)
                );

            /*
             * Respawn placed the victim back at HOME. Reuse the same semantic
             * navigation runtime to prove the regear/respawn loop is ready for
             * another PK trip.
             */
            SceneUpdatePublisher targetRespawnHomePublisher=
                new SceneUpdatePublisher(
                    targetWriter,
                    new SceneCoordinateContext(
                        MovementState.REGION_BASE_X,
                        MovementState.REGION_BASE_Y,
                        0
                    )
                );

            LocalTeleportNavigationRuntime.Result
                targetRepeatPkNavigation=
                    targetNavigation.request(
                        "target",
                        TeleportNavigationService.EntryKind.PK,
                        targetRespawnHomePublisher,
                        targetWriter
                    );

            WorldRegionAuthorityRepository.Region
                repeatRegion=
                    WorldRegionAuthorityRepository.forTile(
                        targetMovement.x(),
                        targetMovement.y()
                    );

            if(!targetRepeatPkNavigation.succeeded()||
               targetRepeatPkNavigation.relocation==null||
               repeatRegion==null||
               repeatRegion.regionId!=
                    pkDestination.regionId)
                throw new AssertionError(
                    "victim could not repeat semantic PK navigation after respawn/regear"
                );

            verifyPostLoopPersistence(
                attacker,
                target
            );

            System.out.println(
                "PLAYER_PVP_DEATH_SETTLEMENT_INTEGRATION_PASS "+
                "liveAttack=true "+
                "navigationToPk=true "+
                "pvpRegionGate=true "+
                "lethalCombat=true "+
                "pkRegionRisk=true "+
                "pkRiskLoss=true "+
                "deathSequence="+deathSequence+" "+
                "killerScopedLoot=true "+
                "killerOwnedLoot=true "+
                "groundPickup=true "+
                "carriedStateAtomic=true "+
                "groundDropExactlyOnce=true "+
                "deathSettlementPersistedOnce=true "+
                "respawnPersistedOnce=true "+
                "killerReward=true "+
                "killerRewardPersistRequestedOnce=true "+
                "defaultRegearApplied=true "+
                "respawnHome=true "+
                "defaultRegear=true "+
                "repeatNavigation=true "+
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

            System.out.println(
                "PLAYABLE_BANK_FUNDED_PK_LOOP_PASS "+
                "bankFundedRegear=true "+
                "bankObject26972=true "+
                "withdrawX=true "+
                "amountEntry208=true "+
                "merchantNpc410=true "+
                "whipPurchase=true "+
                "exactWield=true "+
                "navigationToPk=true "+
                "pvpRegionGate=true "+
                "lethalCombat=true "+
                "killerOwnedLoot=true "+
                "groundPickup=true "+
                "victimRespawnHome=true "+
                "defaultRegear=true "+
                "repeatNavigation=true "+
                "snapshotAuthorityPreserved=true "+
                "originalSpawnpkEconomyClaim=false"
            );

            System.out.println(
                "PLAYABLE_PK_LOOP_INTEGRATION_PASS "+
                "navigationToPk=true "+
                "pvpRegionGate=true "+
                "lethalCombat=true "+
                "pkRiskLoss=true "+
                "killerOwnedLoot=true "+
                "groundPickup=true "+
                "respawnHome=true "+
                "defaultRegear=true "+
                "repeatNavigation=true "+
                "authority="+
                LocalLabPvpRegionPolicy.AUTHORITY
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

    private static void verifyPostLoopPersistence(
        WorldPlayer attacker,
        WorldPlayer target
    )throws Exception{
        Path root=
            Files.createTempDirectory(
                "spawnpk-playable-pk-post-loop-"
            );

        FilePlayerRepository repository=
            new FilePlayerRepository(
                username->
                    root.resolve(
                        username+".properties"
                    )
            );

        World restarted=null;
        boolean temporaryRepositoryClean=false;

        try{
            repository.save(
                PlayerSnapshotCodec.capture(
                    "opensrc",
                    attacker
                )
            );
            repository.save(
                PlayerSnapshotCodec.capture(
                    "target",
                    target
                )
            );

            restarted=
                World.isolatedForTest(
                    60_000L,
                    repository
                );

            Optional<PlayerSnapshot> attackerLoaded=
                restarted.persistence()
                    .load(
                        "opensrc"
                    );
            Optional<PlayerSnapshot> targetLoaded=
                restarted.persistence()
                    .load(
                        "target"
                    );

            if(!attackerLoaded.isPresent()||
               !targetLoaded.isPresent())
                throw new AssertionError(
                    "fresh World persistence owner did not load both post-loop snapshots"
                );

            WorldPlayer restoredAttacker=
                new WorldPlayer();
            WorldPlayer restoredTarget=
                new WorldPlayer();

            PlayerSnapshotCodec.applyValidated(
                attackerLoaded.get(),
                restoredAttacker
            );
            PlayerSnapshotCodec.applyValidated(
                targetLoaded.get(),
                restoredTarget
            );

            PvpKillRewardService.Counters
                restoredReward=
                    PvpKillRewardService.counters(
                        restoredAttacker
                    );

            if(restoredAttacker.equipment().weapon()!=
                    LocalLabShopRuntime.STARTER_WHIP||
               restoredAttacker.bank().inventoryCount(
                    LocalLabShopRuntime.COINS
               )!=10||
               restoredAttacker.bank().inventoryCount(
                    LocalLabShopRuntime.STARTER_WHIP
               )!=0||
               restoredReward.kills!=1L||
               restoredReward.points!=1L)
                throw new AssertionError(
                    "fresh World attacker post-loop persistence mismatch weapon="+
                    restoredAttacker.equipment().weapon()+
                    " coins="+
                    restoredAttacker.bank().inventoryCount(
                        LocalLabShopRuntime.COINS
                    )+
                    " carriedWhip="+
                    restoredAttacker.bank().inventoryCount(
                        LocalLabShopRuntime.STARTER_WHIP
                    )+
                    " reward="+
                    restoredReward
                );

            if(!restoredTarget.lifecycle().alive()||
               restoredTarget.equipment().weapon()!=
                    G1DefaultLoadoutRegearService.STARTER_WEAPON||
               restoredTarget.bank().inventoryCount(
                    G1DefaultLoadoutRegearService.STARTER_FOOD
               )!=
                    G1DefaultLoadoutRegearService.STARTER_FOOD_COUNT||
               restoredTarget.bank().inventoryCount(
                    LocalLabShopRuntime.COINS
               )!=0)
                throw new AssertionError(
                    "fresh World victim post-loop persistence mismatch alive="+
                    restoredTarget.lifecycle().alive()+
                    " weapon="+
                    restoredTarget.equipment().weapon()+
                    " food="+
                    restoredTarget.bank().inventoryCount(
                        G1DefaultLoadoutRegearService.STARTER_FOOD
                    )+
                    " coins="+
                    restoredTarget.bank().inventoryCount(
                        LocalLabShopRuntime.COINS
                    )
                );
        }finally{
            if(restarted!=null)
                restarted.close();

            deleteTree(root);
            temporaryRepositoryClean=
                !Files.exists(root);
        }

        if(!temporaryRepositoryClean)
            throw new AssertionError(
                "temporary post-loop repository cleanup failed path="+
                root
            );

        System.out.println(
            "PLAYABLE_PK_POST_LOOP_PERSISTENCE_PASS "+
            "repositorySave=true "+
            "freshWorldLoad=true "+
            "attackerWeapon4151=true "+
            "attackerLootCoins10=true "+
            "attackerKillCounter1=true "+
            "attackerPointCounter1=true "+
            "victimAlive=true "+
            "victimStarterWeapon4151=true "+
            "victimStarterFood10=true "+
            "victimLostCoinsAbsent=true "+
            "snapshotValidated=true "+
            "temporaryRepositoryClean=true"
        );
    }

    private static void deleteTree(
        Path root
    )throws Exception{
        if(root==null||
           !Files.exists(root))
            return;

        try(java.util.stream.Stream<Path> paths=
                Files.walk(root)){
            Iterator<Path> iterator=
                paths.sorted(
                    Comparator.reverseOrder()
                ).iterator();

            while(iterator.hasNext())
                Files.deleteIfExists(
                    iterator.next()
                );
        }
    }

    private static void bankFundedRegear(
        World world,
        WorldPlayer attacker,
        ServerPacketWriter packets
    )throws Exception{
        BankState bank=attacker.bank();
        MovementState movement=attacker.movement();

        if(bank.inventorySlots()!=0||
           bank.inventoryCount(
                LocalLabShopRuntime.COINS
           )!=0||
           attacker.equipment().weapon()==
                LocalLabShopRuntime.STARTER_WHIP)
            throw new AssertionError(
                "bank-funded PK fixture must start unfunded and unequipped"
            );

        LocalBankObjectInteractionHandler bankObject=
            new LocalBankObjectInteractionHandler(
                bank,
                movement,
                world.content()
            );
        LocalBankRequestHandler bankRequests=
            new LocalBankRequestHandler(
                attacker,
                bank
            );
        LocalLabShopRuntime runtime=
            world.localLabShops();
        LocalSuppliesMerchantHandler merchant=
            new LocalSuppliesMerchantHandler(
                world,
                attacker,
                movement,
                null,
                null,
                runtime
            );
        LocalEquipmentItemActionHandler equipment=
            new LocalEquipmentItemActionHandler(
                bank,
                attacker.equipment(),
                attacker.playerState(),
                new PlayerPresentationService(
                    new DevAuthorityWorkbench()
                ),
                attacker.combatStyles()
            );

        ObjectInteraction bankClick=
            new ObjectInteraction(
                132,
                BankState.BANK_OBJECT_ID,
                movement.x()+1,
                movement.y()
            );

        String opened=
            onWorld(
                world,
                attacker,
                ()->bankObject.handle(
                    bankClick,
                    packets
                )
            );

        if(bankClick.opcode!=132||
           bankClick.objectId!=26972||
           opened==null||
           !opened.contains("V5_BANK_OPEN")||
           !bank.isOpen())
            throw new AssertionError(
                "bank-funded PK bank object path missing"
            );

        BankState.Stack coinStack=
            bank.bankAt(0);

        if(coinStack==null||
           coinStack.itemId!=
                LocalLabShopRuntime.COINS||
           coinStack.qty<100)
            throw new AssertionError(
                "bank-funded PK coin bank fixture missing"
            );

        int bankCoinsBefore=
            coinStack.qty;

        ItemContainerAction withdrawX=
            new ItemContainerAction(
                135,
                BankState.BANK_CONTAINER,
                0,
                LocalLabShopRuntime.COINS,
                0,
                "ITEM_ACTION_X"
            );

        String prompt=
            bank.apply(
                withdrawX,
                packets
            );

        if(withdrawX.opcode!=135||
           withdrawX.widgetId!=
                BankState.BANK_CONTAINER||
           BankState.BANK_CONTAINER!=5382||
           prompt==null||
           !prompt.contains(
                "WITHDRAW_X_PROMPT_SENT"
           ))
            throw new AssertionError(
                "bank-funded PK Withdraw-X prompt missing"
            );

        AmountEntryClientRequest amount208=
            new AmountEntryClientRequest(
                100,
                ClientRequestMetadata.exactCurrent(
                    208,
                    "i32 amount",
                    "G2_BANK_FUNDED_FULL_PK_LOOP"
                )
            );

        LocalBankRequestHandler.Result withdrew=
            bankRequests.handleAmount(
                amount208.amount(),
                packets
            );

        if(amount208.metadata().opcode!=208||
           amount208.metadata().provenance!=
                ClientRequestProvenance
                    .EXACT_CURRENT_CLIENT||
           !"BANK_AMOUNT".equals(
                withdrew.saveReason
           )||
           bank.inventoryCount(
                LocalLabShopRuntime.COINS
           )!=100||
           bank.bankAt(0)==null||
           bank.bankAt(0).qty!=
                bankCoinsBefore-100)
            throw new AssertionError(
                "bank-funded PK exact 100-coin withdrawal mismatch"
            );

        bank.close(packets);

        if(bank.isOpen()||
           bank.inventoryCount(
                LocalLabShopRuntime.COINS
           )!=100)
            throw new AssertionError(
                "bank-funded PK bank close lost carried coins"
            );

        long rocktailBefore=
            runtime.rocktailStock();

        NpcEntity npc=
            new NpcEntity(
                98,
                LocalSuppliesMerchantHandler.NPC_ID,
                movement.x()+1,
                movement.y()
            );

        if(!merchant.beginIfSupported(
                new NpcAction(
                    LocalSuppliesMerchantHandler
                        .TRADE_OPCODE,
                    npc.sceneIndex
                ),
                npc,
                packets,
                "[bank-funded-pk] "
           ))
            throw new AssertionError(
                "bank-funded PK NPC410 Trade missing"
            );

        LocalSuppliesMerchantHandler.Result buyMenu=
            merchant.handleOption(
                1,
                packets
            );

        if(!buyMenu.handled||
           !SuppliesMerchantDialogueContent
                .BUY_CATALOG_NODE
                .equals(
                    merchant
                        .semanticDialogueSnapshot()
                        .nodeKey
                ))
            throw new AssertionError(
                "bank-funded PK merchant catalog missing"
            );

        LocalSuppliesMerchantHandler.Result whipMenu=
            merchant.handleOption(
                2,
                packets
            );

        if(!whipMenu.handled||
           !SuppliesMerchantDialogueContent
                .WHIP_CONFIRM_NODE
                .equals(
                    merchant
                        .semanticDialogueSnapshot()
                        .nodeKey
                ))
            throw new AssertionError(
                "bank-funded PK whip confirmation missing"
            );

        LocalSuppliesMerchantHandler.Result bought=
            merchant.handleOption(
                1,
                packets
            );

        if(!LocalSuppliesMerchantHandler.SAVE_BUY
                .equals(
                    bought.saveReason
                )||
           bank.inventoryCount(
                LocalLabShopRuntime.COINS
           )!=0||
           bank.inventoryCount(
                LocalLabShopRuntime.STARTER_WHIP
           )!=1||
           runtime.rocktailStock()!=
                rocktailBefore)
            throw new AssertionError(
                "bank-funded PK merchant purchase mismatch"
            );

        int whipSlot=
            findInventorySlot(
                bank,
                LocalLabShopRuntime.STARTER_WHIP
            );

        if(whipSlot<0)
            throw new AssertionError(
                "bank-funded PK purchased whip slot missing"
            );

        ItemContainerAction wield=
            new ItemContainerAction(
                41,
                BankState.NORMAL_INVENTORY_CONTAINER,
                whipSlot,
                LocalLabShopRuntime.STARTER_WHIP,
                0,
                "INVENTORY_OPTION"
            );

        LocalEquipmentItemActionHandler.Result equipped=
            equipment.handle(
                wield,
                "opensrc",
                packets
            );

        if(wield.opcode!=41||
           wield.widgetId!=3214||
           equipped==null||
           !"EQUIP_FROM_INVENTORY".equals(
                equipped.saveReason
           )||
           attacker.equipment().weapon()!=
                LocalLabShopRuntime.STARTER_WHIP||
           bank.inventoryCount(
                LocalLabShopRuntime.STARTER_WHIP
           )!=0)
            throw new AssertionError(
                "bank-funded PK exact Wield mismatch"
            );

        PlayerSnapshot snapshot=
            PlayerSnapshotCodec.capture(
                "opensrc",
                attacker
            );
        WorldPlayer restored=
            new WorldPlayer();

        PlayerSnapshotCodec.applyValidated(
            snapshot,
            restored
        );

        if(restored.equipment().weapon()!=
                LocalLabShopRuntime.STARTER_WHIP||
           restored.bank().inventoryCount(
                LocalLabShopRuntime.COINS
           )!=0||
           restored.bank().inventoryCount(
                LocalLabShopRuntime.STARTER_WHIP
           )!=0)
            throw new AssertionError(
                "bank-funded PK regear snapshot authority mismatch"
            );
    }

    private static String onWorld(
        World world,
        WorldPlayer player,
        ThrowingString action
    )throws Exception{
        AtomicReference<String> result=
            new AtomicReference<>();
        AtomicReference<Throwable> failure=
            new AtomicReference<>();

        world.submitAndWait(
            player,
            ()->{
                try{
                    result.set(
                        action.run()
                    );
                }catch(Throwable error){
                    failure.set(error);
                }
            },
            5_000L
        );

        if(failure.get()!=null)
            throw new AssertionError(
                "world action failed",
                failure.get()
            );

        return result.get();
    }

    @FunctionalInterface
    private interface ThrowingString {
        String run()throws Exception;
    }

    private static int findInventorySlot(
        BankState bank,
        int itemId
    ){
        for(int slot=0;
            slot<BankState.INVENTORY_CAPACITY;
            slot++){
            BankState.InventorySlotSnapshot item=
                bank.inventorySlotSnapshot(slot);

            if(item.occupied&&
               item.itemId==itemId)
                return slot;
        }

        return -1;
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
