package spk.local;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

public final class NpcPlayerCombatResolutionServiceTest {
    private static final String AUTHORITY="CUSTOM_LOCALLAB_NPC_DAMAGE";
    private static final String FORMULA="TEST_FIXED_DAMAGE";

    public static void main(String[] args)throws Exception{
        canonicalDamageAndDeath();
        staleTargetRejectedBeforePolicy();
        foreignNpcRejectedBeforePolicy();
        targetGenerationTransferDuringResolverAtomic();
        npcRemovalDuringResolverAtomic();
        resolverFailureAndNegativeDamageAtomic();
        authorityGuards();
        boundaryGuard();

        System.out.println(
            "NPC_PLAYER_COMBAT_RESOLUTION_PASS "+
            "canonicalNpcOwned=true "+
            "exactTargetGeneration=true "+
            "resolverContext=true "+
            "callerDamagePolicy=true "+
            "nonlethalDamage=true "+
            "lethalDamage=true "+
            "alreadyDeadIgnored=true "+
            "staleTargetRejectedBeforePolicy=true "+
            "foreignNpcRejectedBeforePolicy=true "+
            "targetOwnershipLossAtomic=true "+
            "npcOwnershipLossAtomic=true "+
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

    private static void canonicalDamageAndDeath()
        throws Exception{
        Fixture f=new Fixture("npc-player-canonical");

        try{
            final int[] calls={0};

            NpcPlayerCombatResolutionService service=
                new NpcPlayerCombatResolutionService(
                    resolver(calls,12)
                );

            NpcPlayerCombatResolutionService.Result first=
                service.resolveImmediateOwned(
                    f.world,
                    f.npc,
                    f.player,
                    f.generation,
                    10L
                );

            require(
                calls[0]==1&&
                first.resolvedDamage==12&&
                first.lifecycle.applied==12&&
                first.lifecycle.hpBefore==99&&
                first.lifecycle.hpAfter==87&&
                !first.lifecycle.died&&
                !first.lifecycle.ignoredDead&&
                first.attackerId.equals(f.npc.id)&&
                first.targetId.equals(f.player.id())&&
                first.targetGeneration==f.generation&&
                AUTHORITY.equals(first.damageAuthority)&&
                FORMULA.equals(first.damageFormula),
                "canonical nonlethal NPC -> player damage"
            );

            NpcPlayerCombatResolutionService lethalService=
                new NpcPlayerCombatResolutionService(
                    resolver(calls,500)
                );

            NpcPlayerCombatResolutionService.Result lethal=
                lethalService.resolveImmediateOwned(
                    f.world,
                    f.npc,
                    f.player,
                    f.generation,
                    20L
                );

            require(
                lethal.lifecycle.died&&
                lethal.lifecycle.applied==87&&
                lethal.lifecycle.hpAfter==0&&
                f.player.lifecycle().dead(),
                "canonical lethal NPC -> player damage"
            );

            NpcPlayerCombatResolutionService.Result duplicate=
                lethalService.resolveImmediateOwned(
                    f.world,
                    f.npc,
                    f.player,
                    f.generation,
                    21L
                );

            require(
                duplicate.lifecycle.applied==0&&
                !duplicate.lifecycle.died&&
                duplicate.lifecycle.ignoredDead&&
                f.player.lifecycle().deathTick()==20L,
                "already-dead target duplicated death"
            );

            require(
                AUTHORITY.equals(
                    service.damageAuthority()
                )&&
                FORMULA.equals(
                    service.damageFormula()
                ),
                "damage provenance"
            );
        }finally{
            f.close();
        }
    }

    private static void staleTargetRejectedBeforePolicy()
        throws Exception{
        Fixture f=new Fixture("npc-player-stale");

        try{
            final int[] calls={0};
            NpcPlayerCombatResolutionService service=
                new NpcPlayerCombatResolutionService(
                    resolver(calls,10)
                );

            int before=hp(f.player);

            expect(
                IllegalStateException.class,
                ()->service.resolveImmediateOwned(
                    f.world,
                    f.npc,
                    f.player,
                    f.generation+1L,
                    30L
                ),
                "stale target generation"
            );

            require(
                calls[0]==0&&
                hp(f.player)==before,
                "stale target reached policy/mutated HP"
            );
        }finally{
            f.close();
        }
    }

    private static void foreignNpcRejectedBeforePolicy()
        throws Exception{
        Fixture f=new Fixture("npc-player-foreign");

        try{
            WorldNpc foreign=
                new WorldNpc(
                    f.npc.id,
                    f.npc.definitionId,
                    f.npc.x(),
                    f.npc.y(),
                    f.npc.plane(),
                    f.npc.ownerId,
                    f.npc.sourceItemId
                );

            final int[] calls={0};
            NpcPlayerCombatResolutionService service=
                new NpcPlayerCombatResolutionService(
                    resolver(calls,10)
                );

            int before=hp(f.player);

            expect(
                IllegalStateException.class,
                ()->service.resolveImmediateOwned(
                    f.world,
                    foreign,
                    f.player,
                    f.generation,
                    40L
                ),
                "same-id foreign NPC"
            );

            require(
                calls[0]==0&&
                hp(f.player)==before,
                "foreign NPC reached policy/mutated HP"
            );
        }finally{
            f.close();
        }
    }

    private static void targetGenerationTransferDuringResolverAtomic()
        throws Exception{
        Fixture f=new Fixture("npc-player-generation");

        try{
            final long[] replacementGeneration={-1L};
            final int[] calls={0};

            NpcPlayerCombatResolutionService service=
                new NpcPlayerCombatResolutionService(
                    new NpcPlayerCombatResolutionService
                        .DamageResolver(){
                        @Override public int resolve(
                            NpcPlayerCombatResolutionService
                                .DamageContext context
                        ){
                            calls[0]++;

                            if(!f.world.unregisterPlayer(
                                    f.player,
                                    f.generation
                                ))
                                throw new AssertionError(
                                    "target unregister fixture"
                                );

                            replacementGeneration[0]=
                                f.world.registerPlayer(
                                    f.player,
                                    "npc-player-generation"
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

            int before=hp(f.player);

            expect(
                IllegalStateException.class,
                ()->service.resolveImmediateOwned(
                    f.world,
                    f.npc,
                    f.player,
                    f.generation,
                    50L
                ),
                "target generation transfer"
            );

            require(
                calls[0]==1&&
                replacementGeneration[0]>f.generation&&
                f.world.players().owns(
                    f.player,
                    replacementGeneration[0]
                )&&
                hp(f.player)==before,
                "target generation loss mutated HP"
            );

            f.generation=replacementGeneration[0];
        }finally{
            f.close();
        }
    }

    private static void npcRemovalDuringResolverAtomic()
        throws Exception{
        Fixture f=new Fixture("npc-player-npc-loss");

        try{
            final int[] calls={0};
            NpcPlayerCombatResolutionService service=
                new NpcPlayerCombatResolutionService(
                    new NpcPlayerCombatResolutionService
                        .DamageResolver(){
                        @Override public int resolve(
                            NpcPlayerCombatResolutionService
                                .DamageContext context
                        ){
                            calls[0]++;
                            require(
                                f.world.npcs().remove(
                                    f.npc.id
                                ),
                                "NPC removal fixture"
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

            int before=hp(f.player);

            expect(
                IllegalStateException.class,
                ()->service.resolveImmediateOwned(
                    f.world,
                    f.npc,
                    f.player,
                    f.generation,
                    60L
                ),
                "NPC ownership loss"
            );

            require(
                calls[0]==1&&
                hp(f.player)==before,
                "NPC ownership loss mutated HP"
            );
        }finally{
            f.close();
        }
    }

    private static void resolverFailureAndNegativeDamageAtomic()
        throws Exception{
        Fixture f=new Fixture("npc-player-failure");

        try{
            int before=hp(f.player);

            NpcPlayerCombatResolutionService throwing=
                new NpcPlayerCombatResolutionService(
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

            expect(
                IllegalStateException.class,
                ()->throwing.resolveImmediateOwned(
                    f.world,
                    f.npc,
                    f.player,
                    f.generation,
                    70L
                ),
                "resolver failure"
            );

            require(
                hp(f.player)==before&&
                f.player.lifecycle().alive(),
                "resolver failure mutated target"
            );

            NpcPlayerCombatResolutionService negative=
                new NpcPlayerCombatResolutionService(
                    resolver(new int[]{0},-1)
                );

            expect(
                IllegalStateException.class,
                ()->negative.resolveImmediateOwned(
                    f.world,
                    f.npc,
                    f.player,
                    f.generation,
                    71L
                ),
                "negative damage"
            );

            require(
                hp(f.player)==before&&
                f.player.lifecycle().alive(),
                "negative damage mutated target"
            );
        }finally{
            f.close();
        }
    }

    private static void authorityGuards(){
        expect(
            IllegalArgumentException.class,
            ()->new NpcPlayerCombatResolutionService(
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
                        type.getSimpleName()+"."+
                        field.getName()
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

    private static int hp(WorldPlayer player){
        return player.playerState()
            .currentLevel(
                PlayerState.HITPOINTS
            );
    }

    private static final class Fixture {
        final World world=
            World.isolatedForTest(600L);
        final WorldPlayer player=
            new WorldPlayer();
        long generation;
        final WorldNpc npc;

        Fixture(String username){
            generation=
                world.registerPlayer(
                    player,
                    username
                );
            npc=
                world.npcs().spawn(
                    1488,
                    3200,
                    3200,
                    0
                );
        }

        void close(){
            for(WorldPlayer current:
                    world.players().snapshot())
                world.unregisterPlayer(
                    current
                );
            world.close();
        }
    }

    private static void expect(
        Class<? extends Throwable> type,
        ThrowingRunnable action,
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

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
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

    private NpcPlayerCombatResolutionServiceTest(){}
}
