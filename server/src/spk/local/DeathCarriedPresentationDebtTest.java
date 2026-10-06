package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

public final class DeathCarriedPresentationDebtTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        long generation=
            world.registerPlayer(
                player,
                "death-carried"
            );

        boolean settlementFirst=false;
        boolean immutablePostimage=false;
        boolean exactGeneration=false;
        boolean exactDeathSequence=false;
        boolean inventoryPublished=false;
        boolean equipmentPublished=false;
        boolean appearancePublished=false;
        boolean abortRetainsDebt=false;
        boolean retryCommitsOnce=false;
        boolean respawnSupersedesDebt=false;

        try{
            installPreDeathState(player);
            kill(player,50L);

            // Simulate the already-certified semantic death settlement postimage.
            installSettledState(player);

            DeathCarriedPresentationDebt debt=
                new DeathCarriedPresentationDebt();

            debt.captureAfterSettlement(
                player
            );

            settlementFirst=
                debt.pending()&&
                debt.pendingDeathSequence()==
                    player.lifecycle()
                        .deathSequence()&&
                player.bank()
                    .inventoryCount(995)==40&&
                player.equipment()
                    .weapon()==-1;

            exactDeathSequence=
                debt.pendingDeathSequence()==1L;

            // Change live carried state after capture: publication must still
            // use the immutable settled postimage.
            player.equipment()
                .setWeapon(4151);

            OutboundPacketQueue pressured=
                new OutboundPacketQueue(1024);
            pressured.offerBatch(
                new byte[1000]
            );
            ServerPacketWriter pressuredWriter=
                new ServerPacketWriter(
                    pressured,
                    new IsaacCipher(
                        new int[]{1,2,3,4}
                    )
                );

            final int[] appearanceCalls={0};
            final int[] publishedWeapon={Integer.MIN_VALUE};

            boolean pressuredCommit=
                debt.publishIfPending(
                    world,
                    player,
                    pressuredWriter,
                    (appearance,writer)->{
                        appearanceCalls[0]++;
                        publishedWeapon[0]=
                            appearance[
                                EquipmentSlot.WEAPON
                                    .appearanceIndex
                            ];
                    },
                    false,
                    "[g5.3-test] "
                );

            abortRetainsDebt=
                !pressuredCommit&&
                debt.pending()&&
                pressured.queuedBytes()==1000;

            ByteArrayOutputStream drain=
                new ByteArrayOutputStream();
            pressured.drainTo(
                drain,
                Integer.MAX_VALUE
            );

            int beforeRetry=
                pressured.queuedBytes();

            boolean retryCommit=
                debt.publishIfPending(
                    world,
                    player,
                    pressuredWriter,
                    (appearance,writer)->{
                        appearanceCalls[0]++;
                        publishedWeapon[0]=
                            appearance[
                                EquipmentSlot.WEAPON
                                    .appearanceIndex
                            ];
                    },
                    false,
                    "[g5.3-test] "
                );

            inventoryPublished=
                retryCommit&&
                pressured.queuedBytes()>beforeRetry;
            equipmentPublished=
                inventoryPublished;
            appearancePublished=
                appearanceCalls[0]>=2;
            immutablePostimage=
                publishedWeapon[0]==-1;

            int bytesAfterRetry=
                pressured.queuedBytes();

            boolean replay=
                debt.publishIfPending(
                    world,
                    player,
                    pressuredWriter,
                    (appearance,writer)->{
                        throw new AssertionError(
                            "cleared debt replayed appearance"
                        );
                    },
                    false,
                    "[g5.3-test] "
                );

            retryCommitsOnce=
                !replay&&
                !debt.pending()&&
                pressured.queuedBytes()==
                    bytesAfterRetry;

            DeathCarriedPresentationDebt superseded=
                new DeathCarriedPresentationDebt();
            installSettledState(player);
            superseded.captureAfterSettlement(
                player
            );

            int beforeSupersede=
                pressured.queuedBytes();

            boolean supersedePublish=
                superseded.publishIfPending(
                    world,
                    player,
                    pressuredWriter,
                    (appearance,writer)->{
                        throw new AssertionError(
                            "respawn-superseded debt published"
                        );
                    },
                    true,
                    "[g5.3-test] "
                );

            respawnSupersedesDebt=
                !supersedePublish&&
                !superseded.pending()&&
                pressured.queuedBytes()==
                    beforeSupersede;

            DeathCarriedPresentationDebt stale=
                new DeathCarriedPresentationDebt();
            installSettledState(player);
            stale.captureAfterSettlement(
                player
            );

            world.unregisterPlayer(
                player,
                generation
            );

            WorldPlayer replacement=
                new WorldPlayer();
            long replacementGeneration=
                world.registerPlayer(
                    replacement,
                    "death-carried"
                );

            try{
                boolean stalePublish=
                    stale.publishIfPending(
                        world,
                        player,
                        pressuredWriter,
                        (appearance,writer)->{
                            throw new AssertionError(
                                "stale generation published"
                            );
                        },
                        false,
                        "[g5.3-test] "
                    );

                exactGeneration=
                    !stalePublish&&
                    !stale.pending()&&
                    world.players().owns(
                        replacement,
                        replacementGeneration
                    );
            }finally{
                world.unregisterPlayer(
                    replacement,
                    replacementGeneration
                );
            }

            require(
                settlementFirst&&
                immutablePostimage&&
                exactGeneration&&
                exactDeathSequence&&
                inventoryPublished&&
                equipmentPublished&&
                appearancePublished&&
                abortRetainsDebt&&
                retryCommitsOnce&&
                respawnSupersedesDebt,
                "G5.3 death carried presentation invariant"
            );

            System.out.println(
                "G5_DEATH_CARRIED_PRESENTATION_PASS"+
                " settlementFirst="+settlementFirst+
                " immutablePostimage="+immutablePostimage+
                " exactGeneration="+exactGeneration+
                " exactDeathSequence="+exactDeathSequence+
                " inventoryPublished="+inventoryPublished+
                " equipmentPublished="+equipmentPublished+
                " appearancePublished="+appearancePublished+
                " abortRetainsDebt="+abortRetainsDebt+
                " retryCommitsOnce="+retryCommitsOnce+
                " respawnSupersedesDebt="+respawnSupersedesDebt+
                " gameplayMutation=false"+
                " originalSpawnpkPresentationClaim=false"
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(
                    player,
                    player.generation()
                );
            world.close();
        }
    }

    private static void installPreDeathState(
        WorldPlayer player
    ){
        int[] inventoryItems=
            new int[
                BankState.INVENTORY_CAPACITY
            ];
        int[] inventoryQuantities=
            new int[
                BankState.INVENTORY_CAPACITY
            ];
        Arrays.fill(
            inventoryItems,
            -1
        );
        inventoryItems[0]=995;
        inventoryQuantities[0]=100;

        player.bank()
            .replaceInventorySemantic(
                inventoryItems,
                inventoryQuantities
            );
        player.equipment()
            .setWeapon(4151);
    }

    private static void installSettledState(
        WorldPlayer player
    ){
        int[] inventoryItems=
            new int[
                BankState.INVENTORY_CAPACITY
            ];
        int[] inventoryQuantities=
            new int[
                BankState.INVENTORY_CAPACITY
            ];
        Arrays.fill(
            inventoryItems,
            -1
        );
        inventoryItems[0]=995;
        inventoryQuantities[0]=40;

        player.bank()
            .replaceInventorySemantic(
                inventoryItems,
                inventoryQuantities
            );

        int[] equipmentItems=
            new int[
                EquipmentState.EQUIPMENT_SLOTS
            ];
        int[] equipmentQuantities=
            new int[
                EquipmentState.EQUIPMENT_SLOTS
            ];
        Arrays.fill(
            equipmentItems,
            -1
        );

        player.equipment()
            .restoreAccountState(
                equipmentItems,
                equipmentQuantities
            );
    }

    private static void kill(
        WorldPlayer player,
        long tick
    ){
        PlayerLifecycleService.DamageResult result=
            new PlayerLifecycleService(
                player,
                "CUSTOM_LOCALLAB_G5_DEATH_CARRIED_TEST"
            ).applyDamage(
                500,
                tick,
                "G5_DEATH_CARRIED_TEST",
                5L
            );

        require(
            result.died&&
            player.lifecycle().dead(),
            "death fixture"
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

    private DeathCarriedPresentationDebtTest(){}
}
