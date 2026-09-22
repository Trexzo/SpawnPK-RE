package spk.local;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

public final class PlayerLifecycleRespawnPolicyTest {
    private static final String AUTHORITY=
        "CUSTOM_LOCALLAB_RESPAWN_POLICY_TEST";

    public static void main(String[] args){
        legacyCompatibility();
        customProgressionCompatibleHp();
        nonlethalDoesNotResolvePolicy();
        invalidDelayIsAtomic();
        delayOverflowIsAtomic();
        invalidRestoreLeavesDead();
        restoreFailureLeavesDead();
        authorityGuards();
        boundaryGuard();

        System.out.println(
            "PLAYER_RESPAWN_POLICY_PASS "+
            "legacyCompatibility=true "+
            "customDelay=true "+
            "progressionCompatibleHp=true "+
            "lethalPolicyPreflight=true "+
            "nonlethalNoPolicyCall=true "+
            "invalidDelayAtomic=true "+
            "delayOverflowAtomic=true "+
            "invalidRestoreAtomic=true "+
            "restoreFailureLeavesDead=true "+
            "exactRestoreHp=true "+
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
                )==99,
            "legacy respawn HP"
        );

        require(
            player.movement().inHomeWindow()&&
            player.movement().x()==
                MovementState.INITIAL_X&&
            player.movement().y()==
                MovementState.INITIAL_Y,
            "legacy home return"
        );

        require(
            !player.combatState().active()&&
            player.lifecycle().alive(),
            "legacy terminal cleanup"
        );

        require(
            PlayerLifecycleService.AUTHORITY
                .equals(
                    service.respawnAuthority()
                ),
            "legacy authority"
        );
    }

    private static void customProgressionCompatibleHp(){
        WorldPlayer player=
            new WorldPlayer();

        player.playerState().setXp(
            PlayerState.HITPOINTS,
            275
        );
        player.playerState().setCurrentLevel(
            PlayerState.HITPOINTS,
            3
        );

        CombatSkillProgressionService progression=
            new CombatSkillProgressionService(
                player.playerState(),
                new TestCurve()
            );

        final int[] delayCalls={0};
        final int[] restoreCalls={0};

        PlayerLifecycleService service=
            new PlayerLifecycleService(
                player,
                new PlayerLifecycleService
                    .RespawnPolicy(){
                    @Override public long respawnDelayTicks(
                        WorldPlayer owner,
                        long deathTick,
                        String cause
                    ){
                        require(
                            owner==player,
                            "wrong policy player"
                        );
                        require(
                            deathTick==20L&&
                            "custom".equals(cause),
                            "death policy facts"
                        );
                        delayCalls[0]++;
                        return 2L;
                    }

                    @Override public int restoredHitpoints(
                        WorldPlayer owner
                    ){
                        require(
                            owner==player,
                            "wrong restore player"
                        );
                        restoreCalls[0]++;
                        return progression
                            .snapshot(
                                CombatSkillProgressionService
                                    .Skill.HITPOINTS
                            )
                            .baseLevel;
                    }

                    @Override public String authority(){
                        return AUTHORITY;
                    }
                }
            );

        PlayerLifecycleService.DamageResult death=
            service.applyDamage(
                3,
                20L,
                "custom"
            );

        require(
            death.died&&
            delayCalls[0]==1&&
            restoreCalls[0]==0&&
            player.lifecycle().respawnTick()==22L,
            "custom death policy"
        );

        require(
            service.tick(21L)==
                PlayerLifecycleService.TickResult.NONE,
            "custom respawn too early"
        );

        require(
            service.tick(22L)==
                PlayerLifecycleService.TickResult.RESPAWNED,
            "custom respawn missing"
        );

        require(
            restoreCalls[0]==1&&
            player.playerState()
                .currentLevel(
                    PlayerState.HITPOINTS
                )==3&&
            player.lifecycle().alive(),
            "progression-compatible restored HP"
        );

        require(
            AUTHORITY.equals(
                service.respawnAuthority()
            ),
            "custom respawn authority"
        );
    }

    private static void nonlethalDoesNotResolvePolicy(){
        WorldPlayer player=
            new WorldPlayer();

        final int[] delayCalls={0};

        PlayerLifecycleService service=
            new PlayerLifecycleService(
                player,
                new PlayerLifecycleService
                    .RespawnPolicy(){
                    @Override public long respawnDelayTicks(
                        WorldPlayer owner,
                        long deathTick,
                        String cause
                    ){
                        delayCalls[0]++;
                        return 1L;
                    }

                    @Override public int restoredHitpoints(
                        WorldPlayer owner
                    ){
                        throw new AssertionError(
                            "restore invoked before death"
                        );
                    }

                    @Override public String authority(){
                        return AUTHORITY;
                    }
                }
            );

        PlayerLifecycleService.DamageResult result=
            service.applyDamage(
                10,
                1L,
                "nonlethal"
            );

        require(
            !result.died&&
            result.hpBefore==99&&
            result.hpAfter==89&&
            delayCalls[0]==0&&
            player.lifecycle().alive(),
            "nonlethal policy invocation"
        );
    }

    private static void invalidDelayIsAtomic(){
        WorldPlayer player=
            new WorldPlayer();

        PlayerLifecycleService service=
            new PlayerLifecycleService(
                player,
                policy(
                    -1L,
                    99
                )
            );

        int before=
            player.playerState()
                .currentLevel(
                    PlayerState.HITPOINTS
                );

        expect(
            IllegalStateException.class,
            ()->service.applyDamage(
                500,
                10L,
                "bad-delay"
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
                policy(
                    2L,
                    99
                )
            );

        int before=
            player.playerState()
                .currentLevel(
                    PlayerState.HITPOINTS
                );

        expect(
            IllegalStateException.class,
            ()->service.applyDamage(
                500,
                Long.MAX_VALUE-1L,
                "overflow"
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
                    policy(
                        0L,
                        restored
                    )
                );

            service.applyDamage(
                500,
                30L,
                "invalid-restore"
            );

            expect(
                IllegalStateException.class,
                ()->service.tick(
                    30L
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

    private static void restoreFailureLeavesDead(){
        WorldPlayer player=
            new WorldPlayer();

        PlayerLifecycleService service=
            new PlayerLifecycleService(
                player,
                new PlayerLifecycleService
                    .RespawnPolicy(){
                    @Override public long respawnDelayTicks(
                        WorldPlayer owner,
                        long deathTick,
                        String cause
                    ){
                        return 0L;
                    }

                    @Override public int restoredHitpoints(
                        WorldPlayer owner
                    ){
                        throw new IllegalStateException(
                            "restore boom"
                        );
                    }

                    @Override public String authority(){
                        return AUTHORITY;
                    }
                }
            );

        service.applyDamage(
            500,
            40L,
            "restore-failure"
        );

        expect(
            IllegalStateException.class,
            ()->service.tick(
                40L
            ),
            "restore resolver failure"
        );

        require(
            player.lifecycle().dead()&&
            player.playerState()
                .currentLevel(
                    PlayerState.HITPOINTS
                )==0,
            "restore failure escaped dead state"
        );
    }

    private static void authorityGuards(){
        WorldPlayer player=
            new WorldPlayer();

        expect(
            IllegalArgumentException.class,
            ()->new PlayerLifecycleService(
                player,
                policy(
                    5L,
                    99,
                    "EXACT_CURRENT_CLIENT"
                )
            ),
            "client respawn authority"
        );

        expect(
            IllegalArgumentException.class,
            ()->new PlayerLifecycleService(
                player,
                policy(
                    5L,
                    99,
                    "UNKNOWN_SERVER_AUTHORITY"
                )
            ),
            "unknown respawn authority"
        );
    }

    private static PlayerLifecycleService
        .RespawnPolicy policy(
            long delay,
            int restored
        ){
        return policy(
            delay,
            restored,
            AUTHORITY
        );
    }

    private static PlayerLifecycleService
        .RespawnPolicy policy(
            long delay,
            int restored,
            String authority
        ){
        return new PlayerLifecycleService
            .RespawnPolicy(){
            @Override public long respawnDelayTicks(
                WorldPlayer player,
                long deathTick,
                String cause
            ){
                return delay;
            }

            @Override public int restoredHitpoints(
                WorldPlayer player
            ){
                return restored;
            }

            @Override public String authority(){
                return authority;
            }
        };
    }

    private static void boundaryGuard(){
        for(Class<?> type:new Class<?>[]{
                PlayerLifecycleService.RespawnPolicy.class,
                PlayerLifecycleService.class
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
                            "gfx"
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
                        "gfx"
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

    private static final class TestCurve
        implements CombatSkillProgressionService.LevelCurve {

        private static final int[] THRESHOLDS={
            0,
            100,
            250,
            500
        };

        @Override public int maxLevel(){
            return THRESHOLDS.length;
        }

        @Override public int minimumXpForLevel(
            int level
        ){
            return THRESHOLDS[
                level-1
            ];
        }

        @Override public String authority(){
            return "CUSTOM_LOCALLAB_RESPAWN_XP_CURVE";
        }
    }

    private PlayerLifecycleRespawnPolicyTest(){}
}
