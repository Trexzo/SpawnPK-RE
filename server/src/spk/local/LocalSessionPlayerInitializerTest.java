package spk.local;

import java.io.*;
import java.util.*;

public final class LocalSessionPlayerInitializerTest {
    public static void main(String[] args)throws Exception{
        testMissingSnapshotAppliesStarter();
        testLoadFailureRejectsBeforeRegistration();
        testLoadedSnapshotPreserved();

        System.out.println(
            "LOCAL_SESSION_PLAYER_INITIALIZER_PASS "+
            "arbitraryPersistent=true "+
            "missingStarterApplied=true "+
            "failedLoadRejected=true "+
            "failedLoadNotRegistered=true "+
            "loadedSnapshotPreserved=true"
        );
    }

    private static void testMissingSnapshotAppliesStarter()
        throws Exception{
        final String[] loadedUsername={null};
        String oldPlayability=
            System.getProperty(
                PlayabilityStarterAccountPolicy
                    .ENABLE_PROPERTY
            );

        System.setProperty(
            PlayabilityStarterAccountPolicy
                .ENABLE_PROPERTY,
            "true"
        );

        PlayerRepository repository=
            new PlayerRepository(){
                @Override public Optional<PlayerSnapshot> load(
                    String username
                ){
                    loadedUsername[0]=username;
                    return Optional.empty();
                }

                @Override public void save(
                    PlayerSnapshot snapshot
                ){}
            };

        World world=
            World.isolatedForTest(
                50L,
                repository
            );

        try{
            WorldPlayer player=
                new WorldPlayer();
            PetAccessoryState accessory=
                new PetAccessoryState();

            if(player.equipment().weapon()!=
                    EquipmentState.BLOODREND_ID)
                throw new AssertionError(
                    "fixture preimage no longer Bloodrend"
                );

            LocalSessionPlayerInitializer.Result result=
                initializer(
                    world,
                    player,
                    accessory
                ).initialize(
                    "testprofile",
                    "[player-init-new] "
                );

            if(!"testprofile".equals(
                    result.username))
                throw new AssertionError(
                    "arbitrary alias changed: "+
                    result.username
                );

            if(!result.persistentAccount||
               !result.newAccount)
                throw new AssertionError(
                    "missing arbitrary profile not classified new+persistent"
                );

            if(!"testprofile".equals(
                    loadedUsername[0]))
                throw new AssertionError(
                    "repository did not load arbitrary profile username="+
                    loadedUsername[0]
                );

            if(result.worldPlayerGeneration<=0L||
               world.players().byName(
                   "testprofile")!=player||
               !player.registered())
                throw new AssertionError(
                    "new account World registration missing"
                );

            assertStarterState(
                player
            );

            if(accessory.activeItem()!=0)
                throw new AssertionError(
                    "new account unexpectedly loaded accessory"
                );
        }finally{
            if(oldPlayability==null)
                System.clearProperty(
                    PlayabilityStarterAccountPolicy
                        .ENABLE_PROPERTY
                );
            else
                System.setProperty(
                    PlayabilityStarterAccountPolicy
                        .ENABLE_PROPERTY,
                    oldPlayability
                );

            world.close();
        }
    }

    private static void testLoadFailureRejectsBeforeRegistration()
        throws Exception{
        PlayerRepository repository=
            new PlayerRepository(){
                @Override public Optional<PlayerSnapshot> load(
                    String username
                )throws IOException{
                    throw new IOException(
                        "synthetic load failure"
                    );
                }

                @Override public void save(
                    PlayerSnapshot snapshot
                ){}
            };

        World world=
            World.isolatedForTest(
                50L,
                repository
            );

        try{
            WorldPlayer player=
                new WorldPlayer();
            PetAccessoryState accessory=
                new PetAccessoryState();

            boolean rejected=false;

            try{
                initializer(
                    world,
                    player,
                    accessory
                ).initialize(
                    "brokenprofile",
                    "[player-init-failed] "
                );
            }catch(IllegalStateException expected){
                rejected=
                    expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "ACCOUNT_LOAD_FAILED"
                    );
            }

            if(!rejected)
                throw new AssertionError(
                    "failed account load was not rejected"
                );

            if(player.registered()||
               world.players().size()!=0||
               world.players().byName(
                   "brokenprofile")!=null)
                throw new AssertionError(
                    "failed account load reached World registration"
                );

            if(player.equipment().weapon()!=
                    EquipmentState.BLOODREND_ID)
                throw new AssertionError(
                    "failed load incorrectly applied starter policy"
                );
        }finally{
            world.close();
        }
    }

    private static void testLoadedSnapshotPreserved()
        throws Exception{
        WorldPlayer source=
            new WorldPlayer();

        source.equipment().setWeapon(
            21566
        );
        source.movement().setPersistentRun(
            false
        );
        source.movement().setRunEnergy(
            37
        );

        PlayerSnapshot snapshot=
            PlayerSnapshotCodec.capture(
                "returning",
                source
            );

        PlayerRepository repository=
            new PlayerRepository(){
                @Override public Optional<PlayerSnapshot> load(
                    String username
                ){
                    return Optional.of(
                        snapshot
                    );
                }

                @Override public void save(
                    PlayerSnapshot ignored
                ){}
            };

        World world=
            World.isolatedForTest(
                50L,
                repository
            );

        try{
            WorldPlayer player=
                new WorldPlayer();

            LocalSessionPlayerInitializer.Result result=
                initializer(
                    world,
                    player,
                    new PetAccessoryState()
                ).initialize(
                    "returning",
                    "[player-init-returning] "
                );

            if(!result.persistentAccount||
               result.newAccount)
                throw new AssertionError(
                    "loaded account classification"
                );

            if(player.equipment().weapon()!=21566||
               player.movement().persistentRun()||
               player.movement().runEnergy()!=37||
               player.bank().inventorySlots()!=0)
                throw new AssertionError(
                    "loaded account overwritten by starter policy"
                );
        }finally{
            world.close();
        }
    }

    private static LocalSessionPlayerInitializer initializer(
        World world,
        WorldPlayer player,
        PetAccessoryState accessory
    ){
        return new LocalSessionPlayerInitializer(
            world,
            player,
            player.bank(),
            player.equipment(),
            player.movement(),
            player.petState(),
            player.playerState(),
            player.petEffects(),
            accessory
        );
    }

    private static void assertStarterState(
        WorldPlayer player
    ){
        if(player.equipment().weapon()!=
                PlayabilityStarterAccountPolicy
                    .STARTER_WEAPON)
            throw new AssertionError(
                "starter weapon="+
                player.equipment().weapon()
            );

        if(player.equipment().hasEquipped(
                EquipmentState.BLOODREND_ID))
            throw new AssertionError(
                "Bloodrend fixture leaked into starter equipment"
            );

        if(player.bank().inventorySlots()!=
                PlayabilityStarterAccountPolicy
                    .INVENTORY_FOOD)
            throw new AssertionError(
                "starter inventory slots="+
                player.bank().inventorySlots()
            );

        for(int slot=0;
            slot<
                PlayabilityStarterAccountPolicy
                    .INVENTORY_FOOD;
            slot++){
            BankState.Stack food=
                player.bank()
                    .inventoryAt(slot);

            if(food==null||
               food.itemId!=
                   PlayabilityStarterAccountPolicy
                       .FOOD_ITEM||
               food.qty!=1)
                throw new AssertionError(
                    "starter food slot="+
                    slot
                );
        }

        if(player.bank().bankSlots()!=4)
            throw new AssertionError(
                "starter bank slots="+
                player.bank().bankSlots()
            );

        assertBankStack(
            player,
            0,
            PlayabilityStarterAccountPolicy
                .COINS_ITEM,
            PlayabilityStarterAccountPolicy
                .BANK_COINS
        );
        assertBankStack(
            player,
            1,
            PlayabilityStarterAccountPolicy
                .DEATH_RUNE_ITEM,
            PlayabilityStarterAccountPolicy
                .BANK_DEATH_RUNES
        );
        assertBankStack(
            player,
            2,
            PlayabilityStarterAccountPolicy
                .BLOOD_RUNE_ITEM,
            PlayabilityStarterAccountPolicy
                .BANK_BLOOD_RUNES
        );
        assertBankStack(
            player,
            3,
            PlayabilityStarterAccountPolicy
                .FOOD_ITEM,
            PlayabilityStarterAccountPolicy
                .BANK_FOOD
        );

        if(!player.movement().persistentRun()||
           player.movement().runEnergy()!=100)
            throw new AssertionError(
                "starter run state"
            );

        for(int skill=0;
            skill<PlayerState.COMBAT_SKILL_COUNT;
            skill++)
            if(player.playerState()
                    .currentLevel(skill)!=99||
               player.playerState()
                    .xp(skill)!=PlayerState.XP_99)
                throw new AssertionError(
                    "starter combat skill="+
                    skill
                );
    }

    private static void assertBankStack(
        WorldPlayer player,
        int slot,
        int itemId,
        int quantity
    ){
        BankState.Stack stack=
            player.bank()
                .bankAt(slot);

        if(stack==null||
           stack.itemId!=itemId||
           stack.qty!=quantity)
            throw new AssertionError(
                "starter bank slot="+
                slot+
                " expected="+
                itemId+
                "x"+
                quantity+
                " actual="+
                stack
            );
    }
}
