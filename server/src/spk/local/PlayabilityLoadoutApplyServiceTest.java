package spk.local;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public final class PlayabilityLoadoutApplyServiceTest {
    private static final int[] SEED={
        0x11223344,
        0x55667788,
        0x13572468,
        0x24681357
    };

    private static final String OWNER=
        "player:alice";

    public static void main(String[] args)throws Exception{
        successfulApplyAndSameVersionRegear();
        invalidSectionsFailBeforeMutation();
        packetFailureLeavesStateAndAckUntouched();
        openBankPublishesMirrors();
        revisionReplacementWaitsForApplyCommit();

        System.out.println(
            "PLAYABILITY_LOADOUT_APPLY_PASS "+
            "semanticMaterialized=true "+
            "stackableCompacted=true "+
            "nonStackableExpanded=true "+
            "inventoryEquipmentBatch=true "+
            "sameVersionRegear=true "+
            "invalidSectionsFailClosed=true "+
            "packetFailureAtomic=true "+
            "openBankMirrored=true "+
            "revisionStableThroughCommit=true "+
            "ackAfterCommit=true "+
            "authority="+
            PlayabilityLoadoutApplyService.AUTHORITY
        );
    }

    private static void successfulApplyAndSameVersionRegear()
        throws Exception{
        WorldPlayer player=
            new WorldPlayer();
        LoadoutService loadouts=
            new LoadoutService();
        PlayerLoadout v1=
            pvpLoadout(
                1L,
                4151,
                false
            );
        loadouts.create(v1);

        PlayabilityLoadoutApplyService service=
            new PlayabilityLoadoutApplyService(
                player,
                loadouts,
                OWNER
            );

        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();
        PlayabilityLoadoutApplyService.Result first=
            service.apply(
                v1.id,
                writer(wire)
            );

        require(
            first.newlyAcknowledged,
            "first revision acknowledgement"
        );
        require(
            first.inventorySlots==5&&
            first.equipmentSlots==1,
            "materialized slot counts"
        );
        assertAppliedPostimage(
            player,
            4151
        );
        require(
            countVarShort53(
                wire.toByteArray()
            )==2,
            "closed-bank apply packet count"
        );

        LoadoutService.Snapshot applied=
            loadouts.get(
                OWNER,
                v1.id
            );
        require(
            applied.lastAppliedVersion
                .equals(
                    PlayerLoadoutVersion.of(
                        1L
                    )
                ),
            "v1 acknowledgement recorded"
        );

        int[] emptyInventory=
            new int[
                BankState.INVENTORY_CAPACITY
            ];
        int[] emptyQuantities=
            new int[
                BankState.INVENTORY_CAPACITY
            ];
        Arrays.fill(
            emptyInventory,
            -1
        );

        synchronized(player.mutationLock()){
            player.bank()
                .replaceInventorySemantic(
                    emptyInventory,
                    emptyQuantities
                );
            player.equipment()
                .setWeapon(
                    EquipmentState
                        .BLOODREND_ID
                );
        }

        ByteArrayOutputStream regearWire=
            new ByteArrayOutputStream();
        PlayabilityLoadoutApplyService.Result
            regear=
                service.apply(
                    v1.id,
                    writer(regearWire)
                );

        require(
            !regear.newlyAcknowledged,
            "same revision acknowledgement must be idempotent"
        );
        assertAppliedPostimage(
            player,
            4151
        );
        require(
            countVarShort53(
                regearWire.toByteArray()
            )==2,
            "same-version regear packet count"
        );
    }

    private static void invalidSectionsFailBeforeMutation()
        throws Exception{
        WorldPlayer player=
            new WorldPlayer();
        seedSentinelState(player);

        LoadoutService loadouts=
            new LoadoutService();

        PlayerLoadout invalid=
            new PlayerLoadout(
                PlayerLoadoutId.of(
                    "playability:invalid-skills"
                ),
                PlayerLoadoutVersion.of(1L),
                OWNER,
                Collections.singletonList(
                    new PlayerLoadout.InventoryEntry(
                        PlayabilityLoadoutMaterializer
                            .itemKey(995),
                        500L
                    )
                ),
                Collections.singletonList(
                    new PlayerLoadout.EquipmentEntry(
                        PlayabilityLoadoutMaterializer
                            .slotKey(
                                EquipmentSlot.WEAPON
                            ),
                        PlayabilityLoadoutMaterializer
                            .itemKey(4151),
                        1L
                    )
                ),
                new PlayerLoadout.SkillProfile(
                    Collections.singletonList(
                        new PlayerLoadout.SkillValue(
                            "skill:attack",
                            99L
                        )
                    )
                ),
                null,
                PlayabilityLoadoutApplyService
                    .AUTHORITY
            );
        loadouts.create(invalid);

        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();

        expectFailure(
            IllegalStateException.class,
            ()->new PlayabilityLoadoutApplyService(
                player,
                loadouts,
                OWNER
            ).apply(
                invalid.id,
                writer(wire)
            ),
            "skill section must fail closed"
        );

        assertSentinelState(player);
        require(
            wire.size()==0,
            "invalid loadout emitted packets"
        );
        require(
            !loadouts.get(
                OWNER,
                invalid.id
            ).hasAppliedVersion(),
            "invalid loadout acknowledged"
        );
    }

    private static void packetFailureLeavesStateAndAckUntouched()
        throws Exception{
        WorldPlayer player=
            new WorldPlayer();
        seedSentinelState(player);

        LoadoutService loadouts=
            new LoadoutService();
        PlayerLoadout v1=
            pvpLoadout(
                1L,
                4151,
                false
            );
        loadouts.create(v1);

        ServerPacketWriter failing=
            new ServerPacketWriter(
                new AlwaysFailOutputStream(),
                new IsaacCipher(
                    SEED.clone()
                )
            );

        expectFailure(
            IOException.class,
            ()->new PlayabilityLoadoutApplyService(
                player,
                loadouts,
                OWNER
            ).apply(
                v1.id,
                failing
            ),
            "packet failure"
        );

        assertSentinelState(player);
        require(
            !loadouts.get(
                OWNER,
                v1.id
            ).hasAppliedVersion(),
            "failed packet apply acknowledged"
        );
    }

    private static void openBankPublishesMirrors()
        throws Exception{
        WorldPlayer player=
            new WorldPlayer();
        LoadoutService loadouts=
            new LoadoutService();
        PlayerLoadout v1=
            pvpLoadout(
                1L,
                4151,
                false
            );
        loadouts.create(v1);

        ByteArrayOutputStream openWire=
            new ByteArrayOutputStream();
        player.bank().open(
            writer(openWire)
        );

        ByteArrayOutputStream applyWire=
            new ByteArrayOutputStream();

        new PlayabilityLoadoutApplyService(
            player,
            loadouts,
            OWNER
        ).apply(
            v1.id,
            writer(applyWire)
        );

        require(
            countVarShort53(
                applyWire.toByteArray()
            )==4,
            "open bank must mirror bank+inventory postimage"
        );
        assertAppliedPostimage(
            player,
            4151
        );
    }

    private static void revisionReplacementWaitsForApplyCommit()
        throws Exception{
        WorldPlayer player=
            new WorldPlayer();
        LoadoutService loadouts=
            new LoadoutService();
        PlayerLoadout v1=
            pvpLoadout(
                1L,
                4151,
                false
            );
        PlayerLoadout v2=
            pvpLoadout(
                2L,
                21566,
                false
            );
        loadouts.create(v1);

        BlockingOutputStream blocked=
            new BlockingOutputStream();

        PlayabilityLoadoutApplyService service=
            new PlayabilityLoadoutApplyService(
                player,
                loadouts,
                OWNER
            );

        ExecutorService executor=
            Executors.newFixedThreadPool(2);

        try{
            Future<PlayabilityLoadoutApplyService.Result>
                applyFuture=
                    executor.submit(
                        ()->service.apply(
                            v1.id,
                            new ServerPacketWriter(
                                blocked,
                                new IsaacCipher(
                                    SEED.clone()
                                )
                            )
                        )
                    );

            if(!blocked.entered.await(
                    3,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "apply never reached transport commit"
                );

            Future<LoadoutService.Snapshot>
                replaceFuture=
                    executor.submit(
                        ()->loadouts.replace(
                            v2,
                            PlayerLoadoutVersion.of(
                                1L
                            )
                        )
                    );

            Thread.sleep(100L);

            require(
                !replaceFuture.isDone(),
                "loadout revision replaced during live apply"
            );

            blocked.release.countDown();

            PlayabilityLoadoutApplyService.Result
                applied=
                    applyFuture.get(
                        3,
                        TimeUnit.SECONDS
                    );

            LoadoutService.Snapshot replaced=
                replaceFuture.get(
                    3,
                    TimeUnit.SECONDS
                );

            require(
                applied.version.equals(
                    PlayerLoadoutVersion.of(
                        1L
                    )
                )&&
                applied.newlyAcknowledged,
                "blocked apply result"
            );
            require(
                replaced.loadout.version.equals(
                    PlayerLoadoutVersion.of(
                        2L
                    )
                ),
                "replacement did not advance after apply"
            );
            require(
                replaced.lastAppliedVersion.equals(
                    PlayerLoadoutVersion.of(
                        1L
                    )
                ),
                "v1 acknowledgement lost across replacement"
            );
            assertAppliedPostimage(
                player,
                4151
            );
        }finally{
            blocked.release.countDown();
            executor.shutdownNow();
        }
    }

    private static PlayerLoadout pvpLoadout(
        long version,
        int weapon,
        boolean skillProfile
    ){
        return new PlayerLoadout(
            PlayerLoadoutId.of(
                "playability:pvp-primary"
            ),
            PlayerLoadoutVersion.of(
                version
            ),
            OWNER,
            Arrays.asList(
                new PlayerLoadout.InventoryEntry(
                    PlayabilityLoadoutMaterializer
                        .itemKey(15272),
                    4L
                ),
                new PlayerLoadout.InventoryEntry(
                    PlayabilityLoadoutMaterializer
                        .itemKey(995),
                    1_000L
                )
            ),
            Collections.singletonList(
                new PlayerLoadout.EquipmentEntry(
                    PlayabilityLoadoutMaterializer
                        .slotKey(
                            EquipmentSlot.WEAPON
                        ),
                    PlayabilityLoadoutMaterializer
                        .itemKey(weapon),
                    1L
                )
            ),
            skillProfile
                ?new PlayerLoadout.SkillProfile(
                    Collections.singletonList(
                        new PlayerLoadout.SkillValue(
                            "skill:attack",
                            99L
                        )
                    )
                )
                :null,
            null,
            PlayabilityLoadoutApplyService
                .AUTHORITY
        );
    }

    private static void seedSentinelState(
        WorldPlayer player
    ){
        int[] inventory=
            new int[
                BankState.INVENTORY_CAPACITY
            ];
        int[] quantities=
            new int[
                BankState.INVENTORY_CAPACITY
            ];
        Arrays.fill(
            inventory,
            -1
        );
        inventory[0]=995;
        quantities[0]=7;

        synchronized(player.mutationLock()){
            player.bank()
                .replaceInventorySemantic(
                    inventory,
                    quantities
                );
            player.equipment()
                .setWeapon(
                    EquipmentState
                        .BLOODREND_ID
                );
        }
    }

    private static void assertSentinelState(
        WorldPlayer player
    ){
        BankState.InventorySlotSnapshot slot=
            player.bank()
                .inventorySlotSnapshot(0);

        require(
            slot.occupied&&
            slot.itemId==995&&
            slot.quantity==7&&
            player.bank().inventorySlots()==1&&
            player.equipment().weapon()==
                EquipmentState.BLOODREND_ID,
            "sentinel state changed"
        );
    }

    private static void assertAppliedPostimage(
        WorldPlayer player,
        int weapon
    ){
        require(
            player.equipment().weapon()==weapon,
            "applied weapon"
        );

        for(int slot=0;slot<4;slot++){
            BankState.InventorySlotSnapshot item=
                player.bank()
                    .inventorySlotSnapshot(
                        slot
                    );
            require(
                item.occupied&&
                item.itemId==15272&&
                item.quantity==1,
                "food materialization slot="+
                slot
            );
        }

        BankState.InventorySlotSnapshot coins=
            player.bank()
                .inventorySlotSnapshot(4);

        require(
            coins.occupied&&
            coins.itemId==995&&
            coins.quantity==1_000,
            "coin stack materialization"
        );

        require(
            player.bank().inventorySlots()==5,
            "unexpected materialized inventory slots"
        );
    }

    private static ServerPacketWriter writer(
        OutputStream output
    ){
        return new ServerPacketWriter(
            output,
            new IsaacCipher(
                SEED.clone()
            )
        );
    }

    private static int countVarShort53(
        byte[] wire
    )throws IOException{
        IsaacCipher cipher=
            new IsaacCipher(
                SEED.clone()
            );

        int offset=0;
        int count=0;

        while(offset<wire.length){
            int opcode=
                ((wire[offset++]&255)-
                 cipher.nextInt())&255;

            if(opcode!=53)
                throw new AssertionError(
                    "unexpected opcode="+
                    opcode
                );

            if(offset+2>wire.length)
                throw new EOFException(
                    "short var-short length"
                );

            int length=
                ((wire[offset++]&255)<<8)|
                (wire[offset++]&255);

            if(offset+length>wire.length)
                throw new EOFException(
                    "short var-short body"
                );

            offset+=length;
            count++;
        }

        return count;
    }

    private static void expectFailure(
        Class<? extends Throwable> type,
        Throwing action,
        String label
    )throws Exception{
        try{
            action.run();
        }catch(Throwable failure){
            if(type.isInstance(failure))
                return;

            throw new AssertionError(
                label+
                " wrong failure "+
                failure,
                failure
            );
        }

        throw new AssertionError(
            label+
            " did not fail"
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

    private interface Throwing {
        void run()throws Exception;
    }

    private static final class AlwaysFailOutputStream
        extends OutputStream {
        @Override public void write(int value)
            throws IOException{
            throw new IOException(
                "EXPECTED_LOADOUT_WRITE_FAILURE"
            );
        }

        @Override public void write(
            byte[] values,
            int offset,
            int length
        )throws IOException{
            throw new IOException(
                "EXPECTED_LOADOUT_WRITE_FAILURE"
            );
        }
    }

    private static final class BlockingOutputStream
        extends OutputStream {
        final CountDownLatch entered=
            new CountDownLatch(1);
        final CountDownLatch release=
            new CountDownLatch(1);
        final ByteArrayOutputStream delegate=
            new ByteArrayOutputStream();
        private boolean blocked;

        @Override public synchronized void write(
            int value
        )throws IOException{
            blockOnce();
            delegate.write(value);
        }

        @Override public synchronized void write(
            byte[] values,
            int offset,
            int length
        )throws IOException{
            blockOnce();
            delegate.write(
                values,
                offset,
                length
            );
        }

        private void blockOnce()
            throws IOException{
            if(blocked)
                return;

            blocked=true;
            entered.countDown();

            try{
                if(!release.await(
                        3,
                        TimeUnit.SECONDS))
                    throw new IOException(
                        "BLOCKING_OUTPUT_TIMEOUT"
                    );
            }catch(InterruptedException interrupted){
                Thread.currentThread()
                    .interrupt();
                throw new IOException(
                    "BLOCKING_OUTPUT_INTERRUPTED",
                    interrupted
                );
            }
        }
    }

    private PlayabilityLoadoutApplyServiceTest(){}
}
