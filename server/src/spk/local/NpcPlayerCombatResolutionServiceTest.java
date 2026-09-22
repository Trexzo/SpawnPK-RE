package spk.local;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

public final class NpcPlayerCombatResolutionServiceTest {
    private static final String AUTHORITY=
        "CUSTOM_LOCALLAB_NPC_DAMAGE_TEST";
    private static final String FORMULA=
        "FIXTURE_DAMAGE";

    public static void main(String[] args){
        nonlethalAndContext();
        lethalAndAlreadyDead();
        staleTargetRejected();
        targetLostDuringResolverRejected();
        lostNpcRejected();
        npcLostDuringResolverRejected();
        resolverFailureAtomic();
        negativeDamageAtomic();
        authorityGuards();
        boundaryGuard();

        System.out.println(
            "NPC_PLAYER_COMBAT_RESOLUTION_PASS "+
            "canonicalNpcRequired=true "+
            "exactPlayerGeneration=true "+
            "resolverContext=true "+
            "callerDamagePolicy=true "+
            "nonlethalDamage=true "+
            "lethalDamage=true "+
            "alreadyDeadIgnored=true "+
            "staleTargetRejected=true "+
            "lostNpcRejected=true "+
            "resolverFailureAtomic=true "+
            "negativeDamageAtomic=true "+
            "damageAppliedOnce=true "+
            "aggroOwned=false "+
            "pathingOwned=false "+
            "cadenceOwned=false "+
            "combatOutcomeOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void nonlethalAndContext(){
        Fixture f=new Fixture();

        final int[] calls={0};

        NpcPlayerCombatResolutionService service=
            new NpcPlayerCombatResolutionService(
                f.npcs,
                f.players,
                new NpcPlayerCombatResolutionService
                    .DamageResolver(){
                    @Override public int resolve(
                        NpcPlayerCombatResolutionService
                            .DamageContext context
                    ){
                        calls[0]++;

                        require(
                            context.attackerId.equals(
                                f.npc.id
                            )&&
                            context.attackerDefinitionId==1488&&
                            context.attackerTile.equals(
                                new Tile(
                                    3200,
                                    3201,
                                    0
                                )
                            )&&
                            context.targetId.equals(
                                f.player.id()
                            )&&
                            context.targetGeneration==
                                f.generation&&
                            context.targetHitpoints==99&&
                            context.worldTick==10L,
                            "damage resolver context"
                        );

                        return 12;
                    }

                    @Override public String authority(){
                        return AUTHORITY;
                    }

                    @Override public String formula(){
                        return FORMULA;
                    }
                }
            );

        NpcPlayerCombatResolutionService.Result result=
            service.resolveImmediate(
                f.npc,
                f.player,
                10L
            );

        require(
            calls[0]==1&&
            result.resolvedDamage==12&&
            result.lifecycle.applied==12&&
            result.lifecycle.hpBefore==99&&
            result.lifecycle.hpAfter==87&&
            !result.lifecycle.died&&
            !result.lifecycle.ignoredDead&&
            f.player.playerState()
                .currentLevel(
                    PlayerState.HITPOINTS
                )==87&&
            result.attackerId.equals(
                f.npc.id
            )&&
            result.targetId.equals(
                f.player.id()
            )&&
            result.targetGeneration==
                f.generation&&
            AUTHORITY.equals(
                result.damageAuthority
            )&&
            FORMULA.equals(
                result.damageFormula
            ),
            "nonlethal NPC -> player damage"
        );

        require(
            AUTHORITY.equals(
                service.damageAuthority()
            )&&
            FORMULA.equals(
                service.damageFormula()
            ),
            "damage metadata"
        );
    }

    private static void lethalAndAlreadyDead(){
        Fixture f=new Fixture();

        final int[] calls={0};

        NpcPlayerCombatResolutionService service=
            new NpcPlayerCombatResolutionService(
                f.npcs,
                f.players,
                resolver(
                    calls,
                    500
                )
            );

        NpcPlayerCombatResolutionService.Result lethal=
            service.resolveImmediate(
                f.npc,
                f.player,
                20L
            );

        require(
            lethal.lifecycle.died&&
            lethal.lifecycle.applied==99&&
            lethal.lifecycle.hpAfter==0&&
            f.player.lifecycle().dead()&&
            calls[0]==1,
            "lethal NPC -> player damage"
        );

        NpcPlayerCombatResolutionService.Result duplicate=
            service.resolveImmediate(
                f.npc,
                f.player,
                21L
            );

        require(
            calls[0]==2&&
            duplicate.lifecycle.applied==0&&
            !duplicate.lifecycle.died&&
            duplicate.lifecycle.ignoredDead&&
            f.player.lifecycle().deathTick()==20L,
            "already-dead target duplicated death"
        );
    }

    private static void staleTargetRejected(){
        Fixture f=new Fixture();

        require(
            f.players.unregister(
                f.player
            ),
            "stale target fixture unregister"
        );

        final int[] calls={0};

        NpcPlayerCombatResolutionService service=
            new NpcPlayerCombatResolutionService(
                f.npcs,
                f.players,
                resolver(
                    calls,
                    10
                )
            );

        int before=f.player.playerState()
            .currentLevel(
                PlayerState.HITPOINTS
            );

        expect(
            IllegalStateException.class,
            ()->service.resolveImmediate(
                f.npc,
                f.player,
                30L
            ),
            "stale target"
        );

        require(
            calls[0]==0&&
            f.player.playerState()
                .currentLevel(
                    PlayerState.HITPOINTS
                )==before,
            "stale target mutated HP"
        );
    }

    private static void targetLostDuringResolverRejected(){
        Fixture f=new Fixture();

        final int[] calls={0};

        NpcPlayerCombatResolutionService service=
            new NpcPlayerCombatResolutionService(
                f.npcs,
                f.players,
                new NpcPlayerCombatResolutionService
                    .DamageResolver(){
                    @Override public int resolve(
                        NpcPlayerCombatResolutionService
                            .DamageContext context
                    ){
                        calls[0]++;
                        require(
                            f.players.unregister(
                                f.player
                            ),
                            "resolver target unregister"
                        );
                        return 10;
                    }

                    @Override public String authority(){
                        return AUTHORITY;
                    }

                    @Override public String formula(){
                        return FORMULA;
                    }
                }
            );

        int before=f.player.playerState()
            .currentLevel(
                PlayerState.HITPOINTS
            );

        expect(
            IllegalStateException.class,
            ()->service.resolveImmediate(
                f.npc,
                f.player,
                31L
            ),
            "target lost during resolver"
        );

        require(
            calls[0]==1&&
            f.player.playerState()
                .currentLevel(
                    PlayerState.HITPOINTS
                )==before,
            "target loss mutated HP"
        );
    }

    private static void lostNpcRejected(){
        Fixture f=new Fixture();

        require(
            f.npcs.remove(
                f.npc.id
            ),
            "lost NPC fixture removal"
        );

        final int[] calls={0};

        NpcPlayerCombatResolutionService service=
            new NpcPlayerCombatResolutionService(
                f.npcs,
                f.players,
                resolver(
                    calls,
                    10
                )
            );

        int before=f.player.playerState()
            .currentLevel(
                PlayerState.HITPOINTS
            );

        expect(
            IllegalStateException.class,
            ()->service.resolveImmediate(
                f.npc,
                f.player,
                40L
            ),
            "lost NPC"
        );

        require(
            calls[0]==0&&
            f.player.playerState()
                .currentLevel(
                    PlayerState.HITPOINTS
                )==before,
            "lost NPC mutated HP"
        );
    }

    private static void npcLostDuringResolverRejected(){
        Fixture f=new Fixture();

        final int[] calls={0};

        NpcPlayerCombatResolutionService service=
            new NpcPlayerCombatResolutionService(
                f.npcs,
                f.players,
                new NpcPlayerCombatResolutionService
                    .DamageResolver(){
                    @Override public int resolve(
                        NpcPlayerCombatResolutionService
                            .DamageContext context
                    ){
                        calls[0]++;
                        require(
                            f.npcs.remove(
                                f.npc.id
                            ),
                            "resolver NPC removal"
                        );
                        return 10;
                    }

                    @Override public String authority(){
                        return AUTHORITY;
                    }

                    @Override public String formula(){
                        return FORMULA;
                    }
                }
            );

        int before=f.player.playerState()
            .currentLevel(
                PlayerState.HITPOINTS
            );

        expect(
            IllegalStateException.class,
            ()->service.resolveImmediate(
                f.npc,
                f.player,
                41L
            ),
            "NPC lost during resolver"
        );

        require(
            calls[0]==1&&
            f.player.playerState()
                .currentLevel(
                    PlayerState.HITPOINTS
                )==before,
            "NPC loss mutated HP"
        );
    }

    private static void resolverFailureAtomic(){
        Fixture f=new Fixture();

        NpcPlayerCombatResolutionService service=
            new NpcPlayerCombatResolutionService(
                f.npcs,
                f.players,
                new NpcPlayerCombatResolutionService
                    .DamageResolver(){
                    @Override public int resolve(
                        NpcPlayerCombatResolutionService
                            .DamageContext context
                    ){
                        throw new IllegalStateException(
                            "resolver boom"
                        );
                    }

                    @Override public String authority(){
                        return AUTHORITY;
                    }

                    @Override public String formula(){
                        return FORMULA;
                    }
                }
            );

        int before=f.player.playerState()
            .currentLevel(
                PlayerState.HITPOINTS
            );

        expect(
            IllegalStateException.class,
            ()->service.resolveImmediate(
                f.npc,
                f.player,
                50L
            ),
            "resolver failure"
        );

        require(
            f.player.playerState()
                .currentLevel(
                    PlayerState.HITPOINTS
                )==before&&
            f.player.lifecycle().alive(),
            "resolver failure mutated target"
        );
    }

    private static void negativeDamageAtomic(){
        Fixture f=new Fixture();

        NpcPlayerCombatResolutionService service=
            new NpcPlayerCombatResolutionService(
                f.npcs,
                f.players,
                resolver(
                    new int[]{0},
                    -1
                )
            );

        int before=f.player.playerState()
            .currentLevel(
                PlayerState.HITPOINTS
            );

        expect(
            IllegalStateException.class,
            ()->service.resolveImmediate(
                f.npc,
                f.player,
                60L
            ),
            "negative damage"
        );

        require(
            f.player.playerState()
                .currentLevel(
                    PlayerState.HITPOINTS
                )==before&&
            f.player.lifecycle().alive(),
            "negative damage mutated target"
        );
    }

    private static void authorityGuards(){
        Fixture f=new Fixture();

        expect(
            IllegalArgumentException.class,
            ()->new NpcPlayerCombatResolutionService(
                f.npcs,
                f.players,
                resolver(
                    new int[]{0},
                    1,
                    "EXACT_CURRENT_CLIENT"
                )
            ),
            "client damage authority"
        );

        expect(
            IllegalArgumentException.class,
            ()->new NpcPlayerCombatResolutionService(
                f.npcs,
                f.players,
                resolver(
                    new int[]{0},
                    1,
                    "UNKNOWN_SERVER_AUTHORITY"
                )
            ),
            "unknown damage authority"
        );
    }

    private static NpcPlayerCombatResolutionService
        .DamageResolver resolver(
            int[] calls,
            int damage
        ){
        return resolver(
            calls,
            damage,
            AUTHORITY
        );
    }

    private static NpcPlayerCombatResolutionService
        .DamageResolver resolver(
            int[] calls,
            int damage,
            String authority
        ){
        return new NpcPlayerCombatResolutionService
            .DamageResolver(){
            @Override public int resolve(
                NpcPlayerCombatResolutionService
                    .DamageContext context
            ){
                calls[0]++;
                return damage;
            }

            @Override public String authority(){
                return authority;
            }

            @Override public String formula(){
                return FORMULA;
            }
        };
    }

    private static void boundaryGuard(){
        for(Class<?> type:new Class<?>[]{
                NpcPlayerCombatResolutionService.class,
                NpcPlayerCombatResolutionService.DamageContext.class,
                NpcPlayerCombatResolutionService.Result.class
        }){
            for(Field field:type.getDeclaredFields()){
                String name=field.getName()
                    .toLowerCase(Locale.ROOT);

                for(String forbidden:new String[]{
                        "packet","opcode","widget","sceneindex",
                        "aggro","path","cadence","animation",
                        "gfx","projectile","reward","drop"
                })
                    require(
                        !name.contains(forbidden),
                        "unowned identity leaked through "+
                        type.getSimpleName()+"."+field.getName()
                    );
            }
        }

        for(Method method:
                NpcPlayerCombatResolutionService.class
                    .getDeclaredMethods()){
            String name=method.getName()
                .toLowerCase(Locale.ROOT);

            for(String forbidden:new String[]{
                    "packet","publish","aggro","path",
                    "chase","cadence","animate",
                    "projectile","reward","drop"
            })
                require(
                    !name.contains(forbidden),
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
            if(type.isInstance(failure))
                return;

            throw new AssertionError(
                label+" wrong failure "+failure,
                failure
            );
        }

        throw new AssertionError(
            label+" did not fail"
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private static final class Fixture {
        final WorldNpcRegistry npcs=
            new WorldNpcRegistry();
        final PlayerRegistry players=
            new PlayerRegistry();
        final WorldNpc npc=
            npcs.spawn(
                1488,
                3200,
                3201,
                0
            );
        final WorldPlayer player=
            new WorldPlayer();
        final long generation;

        Fixture(){
            generation=players.register(
                player,
                "target"
            );
        }
    }

    private NpcPlayerCombatResolutionServiceTest(){}
}
