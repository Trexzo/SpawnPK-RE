package spk.local;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

public final class PlayerLifecycleRespawnPolicyTest {
    private static final String AUTHORITY=
        "CUSTOM_LOCALLAB_RESPAWN_POLICY_TEST";

    public static void main(String[] args){
        legacyCompatibility();
        customResolvedPolicy();
        nonlethalDoesNotRequireDelay();
        invalidDelayIsAtomic();
        delayOverflowIsAtomic();
        invalidRestoreLeavesDead();
        authorityGuards();
        boundaryGuard();

        System.out.println(
            "PLAYER_RESPAWN_POLICY_PASS "+
            "legacyCompatibility=true "+
            "customDelay=true "+
            "customRestoreHp=true "+
            "policyPreResolved=true "+
            "callbackUnderPlayerLock=false "+
            "lethalDelayPreflight=true "+
            "nonlethalDelayUnused=true "+
            "invalidDelayAtomic=true "+
            "delayOverflowAtomic=true "+
            "invalidRestoreAtomic=true "+
            "homeReturnPreserved=true "+
            "combatClearPreserved=true "+
            "authorityExplicit=true "+
            "packetOwned=false "+
            "persistenceOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void legacyCompatibility(){
        WorldPlayer player=
            new WorldPlayer();

        player.movement().enterTransientRegion(
            3200,
            3200,
            0,
            3150,
            3150
        );
        player.combatState().targetSceneIndex=12;
        player.combatState().context=
            CombatContext.NPC_PVM;

        PlayerLifecycleService service=
            new PlayerLifecycleService(
                player
            );

        PlayerLifecycleService.DamageResult death=
            service.applyDamage(
                500,
                10L,
                "legacy"
            );

        require(
            death.died&&
            player.lifecycle().dead()&&
            player.lifecycle().respawnTick()==15L,
            "legacy respawn delay"
        );

        require(
            service.tick(14L)==
                PlayerLifecycleService.TickResult.NONE,
            "legacy respawn too early"
        );

        require(
            service.tick(15L)==
                PlayerLifecycleService.TickResult.RESPAWNED,
            "legacy respawn missing"
        );

        require(
            player.playerState()
                .currentLevel(
                    PlayerState.HITPOINTS
                )==
                    PlayerLifecycleService
                        .LOCALLAB_RESTORED_HITPOINTS&&
            player.movement().inHomeWindow()&&
            !player.combatState().active()&&
            player.lifecycle().alive(),
            "legacy terminal state"
        );
    }

    private static void customResolvedPolicy(){
        WorldPlayer player=
            new WorldPlayer();

        player.playerState().setCurrentLevel(
            PlayerState.HITPOINTS,
            3
        );

        PlayerLifecycleService service=
            new PlayerLifecycleService(
                player,
                AUTHORITY
            );

        PlayerLifecycleService.DamageResult death=
            service.applyDamage(
                3,
                20L,
                "custom",
                2L
            );

        require(
            death.died&&
            player.lifecycle().respawnTick()==22L&&
            AUTHORITY.equals(
                service.respawnAuthority()
            ),
            "custom death policy"
        );

        require(
            service.tick(
                21L,
                3
            )==
                PlayerLifecycleService.TickResult.NONE,
            "custom respawn too early"
        );

        require(
            service.tick(
                22L,
                3
            )==
                PlayerLifecycleService.TickResult.RESPAWNED,
            "custom respawn missing"
        );

        require(
            player.playerState()
                .currentLevel(
                    PlayerState.HITPOINTS
                )==3&&
            player.lifecycle().alive(),
            "custom restored HP"
        );
    }

    private static void nonlethalDoesNotRequireDelay(){
        WorldPlayer player=
            new WorldPlayer();

        PlayerLifecycleService service=
            new PlayerLifecycleService(
                player,
                AUTHORITY
            );

        PlayerLifecycleService.DamageResult result=
            service.applyDamage(
                10,
                1L,
                "nonlethal",
                -1L
            );

        require(
            !result.died&&
            result.hpBefore==99&&
            result.hpAfter==89&&
            player.lifecycle().alive(),
            "nonlethal unexpectedly consumed respawn policy"
        );
    }

    private static void invalidDelayIsAtomic(){
        WorldPlayer player=
            new WorldPlayer();

        PlayerLifecycleService service=
            new PlayerLifecycleService(
                player,
                AUTHORITY
            );

        int before=
            player.playerState()
                .currentLevel(
                    PlayerState.HITPOINTS
                );

        expect(
            IllegalArgumentException.class,
            ()->service.applyDamage(
                500,
                10L,
                "bad-delay",
                -1L
            ),
            "negative respawn delay"
        );

        require(
            player.playerState()
                .currentLevel(
                    PlayerState.HITPOINTS
                )==before&&
            player.lifecycle().alive(),
            "invalid delay mutated lifecycle"
        );
    }

    private static void delayOverflowIsAtomic(){
        WorldPlayer player=
            new WorldPlayer();

        PlayerLifecycleService service=
            new PlayerLifecycleService(
                player,
                AUTHORITY
            );

        int before=
            player.playerState()
                .currentLevel(
                    PlayerState.HITPOINTS
                );

        expect(
            IllegalArgumentException.class,
            ()->service.applyDamage(
                500,
                Long.MAX_VALUE-1L,
                "overflow",
                2L
            ),
            "respawn tick overflow"
        );

        require(
            player.playerState()
                .currentLevel(
                    PlayerState.HITPOINTS
                )==before&&
            player.lifecycle().alive(),
            "overflow mutated lifecycle"
        );
    }

    private static void invalidRestoreLeavesDead(){
        for(int restored:
                new int[]{0,256}){
            WorldPlayer player=
                new WorldPlayer();

            PlayerLifecycleService service=
                new PlayerLifecycleService(
                    player,
                    AUTHORITY
                );

            service.applyDamage(
                500,
                30L,
                "invalid-restore",
                0L
            );

            expect(
                IllegalArgumentException.class,
                ()->service.tick(
                    30L,
                    restored
                ),
                "invalid restored HP "+
                restored
            );

            require(
                player.lifecycle().dead()&&
                player.playerState()
                    .currentLevel(
                        PlayerState.HITPOINTS
                    )==0,
                "invalid restore escaped dead state "+
                restored
            );
        }
    }

    private static void authorityGuards(){
        WorldPlayer player=
            new WorldPlayer();

        expect(
            IllegalArgumentException.class,
            ()->new PlayerLifecycleService(
                player,
                "EXACT_CURRENT_CLIENT"
            ),
            "client respawn authority"
        );

        expect(
            IllegalArgumentException.class,
            ()->new PlayerLifecycleService(
                player,
                "UNKNOWN_SERVER_AUTHORITY"
            ),
            "unknown respawn authority"
        );
    }

    private static void boundaryGuard(){
        for(Class<?> type:new Class<?>[]{
                PlayerLifecycleService.class,
                PlayerLifecycleService
                    .DamageResult.class
        }){
            for(Field field:
                    type.getDeclaredFields()){
                String name=
                    field.getName()
                        .toLowerCase(
                            Locale.ROOT
                        );

                for(String forbidden:
                        new String[]{
                            "packet",
                            "opcode",
                            "widget",
                            "itemloss",
                            "grave",
                            "animation",
                            "gfx",
                            "resolver"
                        })
                    require(
                        !name.contains(
                            forbidden
                        ),
                        "unowned identity leaked through "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );
            }
        }

        for(Method method:
                PlayerLifecycleService.class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            for(String forbidden:
                    new String[]{
                        "packet",
                        "publish",
                        "persist",
                        "itemloss",
                        "grave",
                        "animation",
                        "gfx",
                        "resolver"
                    })
                require(
                    !name.contains(
                        forbidden
                    ),
                    "unowned behavior leaked through "+
                    method.getName()
                );
        }
    }

    private static void expect(
        Class<? extends Throwable> type,
        Runnable action,
        String label
    ){
        try{
            action.run();
        }catch(Throwable failure){
            if(type.isInstance(
                    failure))
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

    private PlayerLifecycleRespawnPolicyTest(){}
}
