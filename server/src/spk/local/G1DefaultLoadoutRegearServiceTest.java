package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

public final class G1DefaultLoadoutRegearServiceTest {
    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer player=
            new WorldPlayer();

        long generation=
            world.registerPlayer(
                player,
                "opensrc"
            );

        try{
            DefaultLoadoutService.Snapshot initial=
                G1DefaultLoadoutRegearService
                    .ensureStarterDefault(
                        world,
                        "opensrc"
                    );

            require(
                initial.hasDefault()&&
                initial.defaultLoadoutId.equals(
                    G1DefaultLoadoutRegearService.STARTER_ID
                )&&
                initial.selectionRevision==1L,
                "starter default not installed"
            );

            DefaultLoadoutService.Snapshot replayDefault=
                G1DefaultLoadoutRegearService
                    .ensureStarterDefault(
                        world,
                        "opensrc"
                    );

            require(
                replayDefault.selectionRevision==1L&&
                world.loadouts().size()==1,
                "starter registration/default replay was not idempotent"
            );

            G1DefaultLoadoutRegearService service=
                new G1DefaultLoadoutRegearService(
                    world,
                    player
                );

            G1DefaultLoadoutRegearService.Prepared first=
                service.prepareDefault(
                    "opensrc"
                );

            require(
                first!=null,
                "starter prepare missing"
            );

            OutboundPacketQueue queue=
                new OutboundPacketQueue(
                    1<<20
                );
            ServerPacketWriter writer=
                new ServerPacketWriter(
                    queue,
                    new IsaacCipher(
                        new int[]{91,92,93,94}
                    )
                );

            writer.beginBatch();
            service.publishPrepared(
                first,
                writer
            );
            writer.endBatch();

            G1DefaultLoadoutRegearService.Result
                firstResult=
                    service.commitPreparedAfterRespawn(
                        first
                    );

            requireStarterPostimage(
                player
            );
            require(
                firstResult.applied&&
                firstResult.firstApplyOfVersion,
                "first G1 regear not acknowledged "+
                firstResult
            );

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            queue.drainTo(
                wire,
                1<<20
            );
            require(
                wire.size()>0,
                "G1 regear published no container bytes"
            );

            clearCarriedState(player);

            G1DefaultLoadoutRegearService.Prepared second=
                service.prepareDefault(
                    "opensrc"
                );
            service.commitPreparedAfterRespawn(
                second
            );
            requireStarterPostimage(
                player
            );

            LoadoutService.Snapshot loadout=
                world.loadouts().get(
                    "opensrc",
                    G1DefaultLoadoutRegearService.STARTER_ID
                );
            require(
                loadout!=null&&
                loadout.hasAppliedVersion()&&
                loadout.lastAppliedVersion.equals(
                    PlayerLoadoutVersion.of(1L)
                ),
                "loadout apply version not retained"
            );

            G1DefaultLoadoutRegearService.Prepared stale=
                service.prepareDefault(
                    "opensrc"
                );

            PlayerLoadout replacement=
                new PlayerLoadout(
                    G1DefaultLoadoutRegearService.STARTER_ID,
                    PlayerLoadoutVersion.of(2L),
                    "opensrc",
                    Arrays.asList(
                        new PlayerLoadout.InventoryEntry(
                            "item:385",
                            5L
                        )
                    ),
                    Arrays.asList(
                        new PlayerLoadout.EquipmentEntry(
                            "slot:weapon",
                            "item:4151",
                            1L
                        )
                    ),
                    null,
                    null,
                    G1DefaultLoadoutRegearService.POLICY_AUTHORITY
                );

            world.loadouts().replace(
                replacement,
                PlayerLoadoutVersion.of(1L)
            );

            int weaponBeforeStale=
                player.equipment().weapon();
            int foodBeforeStale=
                player.bank().inventoryCount(
                    G1DefaultLoadoutRegearService.STARTER_FOOD
                );

            boolean staleRejected=false;
            try{
                service.commitPreparedAfterRespawn(
                    stale
                );
            }catch(IllegalStateException expected){
                staleRejected=true;
            }

            require(
                staleRejected&&
                player.equipment().weapon()==
                    weaponBeforeStale&&
                player.bank().inventoryCount(
                    G1DefaultLoadoutRegearService.STARTER_FOOD
                )==foodBeforeStale,
                "stale loadout plan mutated carried state"
            );

            G1DefaultLoadoutRegearService.Prepared
                current=
                    service.prepareDefault(
                        "opensrc"
                    );

            new PlayerLifecycleService(player)
                .applyDamage(
                    player.playerState()
                        .currentLevel(
                            PlayerState.HITPOINTS
                        ),
                    44L,
                    "G1_REGEAR_DEAD_GUARD"
                );

            boolean deadRejected=false;
            try{
                service.commitPreparedAfterRespawn(
                    current
                );
            }catch(IllegalStateException expected){
                deadRejected=true;
            }

            require(
                deadRejected,
                "dead player accepted regear commit"
            );

            player.lifecycle().markRespawned();

            PlayerSnapshot snapshot=
                PlayerSnapshotCodec.capture(
                    "opensrc",
                    player
                );
            WorldPlayer restored=
                new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(
                snapshot,
                restored
            );

            require(
                restored.equipment().weapon()==
                    player.equipment().weapon()&&
                restored.bank().inventoryCount(
                    G1DefaultLoadoutRegearService.STARTER_FOOD
                )==
                    player.bank().inventoryCount(
                        G1DefaultLoadoutRegearService.STARTER_FOOD
                    ),
                "regear postimage did not round-trip through snapshot"
            );

            System.out.println(
                "G1_DEFAULT_LOADOUT_REGEAR_PASS "+
                "starterRegistered=true "+
                "defaultSelectionIdempotent=true "+
                "weapon="+
                G1DefaultLoadoutRegearService.STARTER_WEAPON+
                " food="+
                G1DefaultLoadoutRegearService.STARTER_FOOD+
                "x"+
                G1DefaultLoadoutRegearService.STARTER_FOOD_COUNT+
                " semanticPostimage=true "+
                "containerPublication=true "+
                "repeatRegear=true "+
                "staleVersionRejected=true "+
                "deadCommitRejected=true "+
                "snapshotRoundTrip=true "+
                "originalSpawnpkEconomyClaim=false "+
                "authority="+
                G1DefaultLoadoutRegearService.POLICY_AUTHORITY
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(
                    player,
                    generation
                );
            world.close();
        }
    }

    private static void requireStarterPostimage(
        WorldPlayer player
    ){
        require(
            player.equipment().weapon()==
                G1DefaultLoadoutRegearService.STARTER_WEAPON,
            "starter weapon missing"
        );
        require(
            player.bank().inventoryCount(
                G1DefaultLoadoutRegearService.STARTER_FOOD
            )==
                G1DefaultLoadoutRegearService.STARTER_FOOD_COUNT,
            "starter food missing"
        );
        int occupied=0;
        for(int slot=0;
            slot<BankState.INVENTORY_CAPACITY;
            slot++)
            if(player.bank()
                    .inventorySlotSnapshot(slot)
                    .occupied)
                occupied++;

        require(
            occupied==
                G1DefaultLoadoutRegearService.STARTER_FOOD_COUNT,
            "starter non-stackable food did not expand by slot"
        );
    }

    private static void clearCarriedState(
        WorldPlayer player
    ){
        int[] emptyInventory=
            new int[BankState.INVENTORY_CAPACITY];
        int[] emptyInventoryQty=
            new int[BankState.INVENTORY_CAPACITY];
        Arrays.fill(
            emptyInventory,
            -1
        );

        int[] emptyEquipment=
            new int[EquipmentState.EQUIPMENT_SLOTS];
        int[] emptyEquipmentQty=
            new int[EquipmentState.EQUIPMENT_SLOTS];
        Arrays.fill(
            emptyEquipment,
            -1
        );

        synchronized(player.mutationLock()){
            player.bank().replaceInventorySemantic(
                emptyInventory,
                emptyInventoryQty
            );
            player.equipment().restoreAccountState(
                emptyEquipment,
                emptyEquipmentQty
            );
            player.playerState().syncEquipmentPresentation(
                player.equipment()
            );
        }
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

    private G1DefaultLoadoutRegearServiceTest(){}
}
