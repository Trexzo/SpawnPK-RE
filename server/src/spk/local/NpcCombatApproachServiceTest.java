package spk.local;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class NpcCombatApproachServiceTest {
    public static void main(String[] args)throws Exception{
        alreadyInRangeNoMove();
        homeBlockerAvoidanceOneStep();
        worldStaticOneStep();
        blockedRouteNoMove();
        differentPlaneNoMove();
        staleTargetNoMove();
        staleAttackerNoMove();
        playerThenNpcLockOrder();
        reentrantPolicyOwnershipLossNoMove();
        invalidPolicyAtomic();
        authorityAndBoundary();

        System.out.println(
            "NPC_COMBAT_APPROACH_PASS "+
            "exactNpcOwnership=true "+
            "exactPlayerGeneration=true "+
            "lockOrderPlayerThenNpc=true "+
            "callerStopRange=true "+
            "callerRoutePolicy=true "+
            "inRangeNoMove=true "+
            "oneStep=true "+
            "collisionSafe=true "+
            "worldStaticRoute=true "+
            "blockedNoMove=true "+
            "differentPlaneNoMove=true "+
            "staleNpcNoMove=true "+
            "staleTargetNoMove=true "+
            "aggroOwned=false "+
            "targetSelectionOwned=false "+
            "damageOwned=false "+
            "presentationOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void alreadyInRangeNoMove()
        throws Exception{
        Fixture f=new Fixture(
            "approach-range",
            3088,
            3495
        );

        try{
            WorldNpc npc=
                f.spawn(
                    1488,
                    3087,
                    3495,
                    0
                );

            NpcCombatApproachService service=
                new NpcCombatApproachService(
                    f.world,
                    policy(
                        1,
                        RouteRequest.Policy
                            .HOME_RECOVERED_STATIC_AUTHORITY
                    )
                );

            Tile before=npc.tile();

            NpcCombatApproachService.Result result=
                service.step(
                    npc,
                    f.player,
                    f.generation
                );

            require(
                result.status==
                    NpcCombatApproachService.Status.IN_RANGE,
                "already-in-range status"
            );
            require(
                before.equals(npc.tile())&&
                before.equals(result.before)&&
                before.equals(result.after),
                "already-in-range moved"
            );
        }finally{
            f.close();
        }
    }

    private static void homeBlockerAvoidanceOneStep()
        throws Exception{
        Fixture f=new Fixture(
            "approach-home-blocker",
            3085,
            3495
        );

        try{
            WorldNpc npc=
                f.spawn(
                    1488,
                    3083,
                    3495,
                    0
                );

            NpcCombatApproachService service=
                new NpcCombatApproachService(
                    f.world,
                    policy(
                        1,
                        RouteRequest.Policy
                            .HOME_RECOVERED_STATIC_AUTHORITY
                    )
                );

            Tile before=npc.tile();

            NpcCombatApproachService.Result result=
                service.step(
                    npc,
                    f.player,
                    f.generation
                );

            Tile after=npc.tile();

            require(
                result.status==
                    NpcCombatApproachService.Status.MOVED,
                "HOME blocker approach did not move"
            );
            require(
                MovementState.direction(
                    before.x,
                    before.y,
                    after.x,
                    after.y
                )>=0,
                "HOME approach moved more than one step"
            );
            require(
                !(after.x==3084&&after.y==3495),
                "HOME approach entered known blocked tile"
            );
            require(
                result.routeAuthority!=null&&
                result.routeAuthority.contains(
                    "HOME_RECOVERED_STATIC"
                ),
                "HOME route authority"
            );
        }finally{
            f.close();
        }
    }

    private static void worldStaticOneStep()
        throws Exception{
        World world=
            World.isolatedForTest(600L);
        WorldPlayer player=
            new WorldPlayer();
        long generation=
            world.registerPlayer(
                player,
                "approach-world-static"
            );

        try{
            player.movement()
                .enterTransientRegion(
                    1408,
                    8961,
                    0,
                    1400,
                    8950
                );

            WorldNpc npc=
                world.npcs().spawn(
                    1488,
                    1408,
                    8959,
                    0
                );

            NpcCombatApproachService service=
                new NpcCombatApproachService(
                    world,
                    policy(
                        1,
                        RouteRequest.Policy
                            .WORLD_STATIC_AUTHORITY
                    )
                );

            NpcCombatApproachService.Result result=
                service.step(
                    npc,
                    player,
                    generation
                );

            require(
                result.status==
                    NpcCombatApproachService.Status.MOVED,
                "WORLD_STATIC approach did not move"
            );
            require(
                npc.x()==1408&&
                npc.y()==8960&&
                npc.plane()==0,
                "WORLD_STATIC exact open cross-region step"
            );
            require(
                "WORLD_STATIC_COLLISION".equals(
                    result.routeAuthority
                ),
                "WORLD_STATIC route authority"
            );
        }finally{
            close(world);
        }
    }

    private static void blockedRouteNoMove()
        throws Exception{
        World world=
            World.isolatedForTest(600L);
        WorldPlayer player=
            new WorldPlayer();
        long generation=
            world.registerPlayer(
                player,
                "approach-blocked"
            );

        try{
            player.movement()
                .enterTransientRegion(
                    5000,
                    5000,
                    0,
                    5000,
                    5000
                );

            WorldNpc npc=
                world.npcs().spawn(
                    1488,
                    3087,
                    3495,
                    0
                );

            Tile before=npc.tile();

            NpcCombatApproachService service=
                new NpcCombatApproachService(
                    world,
                    policy(
                        1,
                        RouteRequest.Policy
                            .HOME_RECOVERED_STATIC_AUTHORITY
                    )
                );

            NpcCombatApproachService.Result result=
                service.step(
                    npc,
                    player,
                    generation
                );

            require(
                result.status==
                    NpcCombatApproachService.Status.BLOCKED,
                "unreachable HOME target not blocked"
            );
            require(
                before.equals(npc.tile()),
                "blocked route moved NPC"
            );
        }finally{
            close(world);
        }
    }

    private static void differentPlaneNoMove()
        throws Exception{
        World world=
            World.isolatedForTest(600L);
        WorldPlayer player=
            new WorldPlayer();
        long generation=
            world.registerPlayer(
                player,
                "approach-plane"
            );

        try{
            player.movement()
                .enterTransientRegion(
                    3201,
                    3200,
                    1,
                    3150,
                    3150
                );

            WorldNpc npc=
                world.npcs().spawn(
                    1488,
                    3200,
                    3200,
                    0
                );

            Tile before=npc.tile();

            NpcCombatApproachService service=
                new NpcCombatApproachService(
                    world,
                    policy(
                        1,
                        RouteRequest.Policy
                            .WORLD_STATIC_AUTHORITY
                    )
                );

            NpcCombatApproachService.Result result=
                service.step(
                    npc,
                    player,
                    generation
                );

            require(
                result.status==
                    NpcCombatApproachService.Status.DIFFERENT_PLANE,
                "different-plane status"
            );
            require(
                before.equals(npc.tile()),
                "different-plane approach moved"
            );
        }finally{
            close(world);
        }
    }

    private static void staleTargetNoMove()
        throws Exception{
        Fixture f=new Fixture(
            "approach-stale-target",
            3089,
            3495
        );

        try{
            WorldNpc npc=
                f.spawn(
                    1488,
                    3087,
                    3495,
                    0
                );
            Tile before=npc.tile();

            require(
                f.world.unregisterPlayer(
                    f.player,
                    f.generation
                ),
                "stale target fixture unregister"
            );

            NpcCombatApproachService service=
                new NpcCombatApproachService(
                    f.world,
                    policy(
                        1,
                        RouteRequest.Policy
                            .HOME_RECOVERED_STATIC_AUTHORITY
                    )
                );

            NpcCombatApproachService.Result result=
                service.step(
                    npc,
                    f.player,
                    f.generation
                );

            require(
                result.status==
                    NpcCombatApproachService.Status.STALE_TARGET,
                "stale target status"
            );
            require(
                before.equals(npc.tile()),
                "stale target moved NPC"
            );
        }finally{
            f.close();
        }
    }

    private static void staleAttackerNoMove()
        throws Exception{
        Fixture f=new Fixture(
            "approach-stale-attacker",
            3089,
            3495
        );

        try{
            WorldNpc npc=
                f.spawn(
                    1488,
                    3087,
                    3495,
                    0
                );
            Tile before=npc.tile();

            require(
                f.world.npcs().remove(
                    npc.id
                ),
                "stale attacker fixture removal"
            );

            NpcCombatApproachService service=
                new NpcCombatApproachService(
                    f.world,
                    policy(
                        1,
                        RouteRequest.Policy
                            .HOME_RECOVERED_STATIC_AUTHORITY
                    )
                );

            NpcCombatApproachService.Result result=
                service.step(
                    npc,
                    f.player,
                    f.generation
                );

            require(
                result.status==
                    NpcCombatApproachService.Status.STALE_ATTACKER,
                "stale attacker status"
            );
            require(
                before.equals(npc.tile()),
                "stale attacker object mutated"
            );
        }finally{
            f.close();
        }
    }

    private static void playerThenNpcLockOrder()
        throws Exception{
        Fixture f=new Fixture(
            "approach-lock-order",
            3089,
            3495
        );
        ExecutorService workers=
            Executors.newFixedThreadPool(3);
        CountDownLatch npcLockHeld=
            new CountDownLatch(1);
        CountDownLatch releaseNpcLock=
            new CountDownLatch(1);
        AtomicReference<Thread> stepThread=
            new AtomicReference<>();

        try{
            WorldNpc npc=
                f.spawn(
                    1488,
                    3087,
                    3495,
                    0
                );

            Future<Boolean> blocker=
                workers.submit(
                    ()->f.world.npcs()
                        .withCurrentMutationOwnershipIfCurrent(
                            npc,
                            ()->{
                                npcLockHeld.countDown();

                                if(!releaseNpcLock.await(
                                        5L,
                                        TimeUnit.SECONDS))
                                    throw new AssertionError(
                                        "NPC lock release timeout"
                                    );
                            }
                        )
                );

            require(
                npcLockHeld.await(
                    5L,
                    TimeUnit.SECONDS
                ),
                "NPC lock blocker did not start"
            );

            NpcCombatApproachService service=
                new NpcCombatApproachService(
                    f.world,
                    policy(
                        1,
                        RouteRequest.Policy
                            .HOME_RECOVERED_STATIC_AUTHORITY
                    )
                );

            Future<NpcCombatApproachService.Result>
                approach=
                    workers.submit(
                        ()->{
                            stepThread.set(
                                Thread.currentThread()
                            );
                            return service.step(
                                npc,
                                f.player,
                                f.generation
                            );
                        }
                    );

            awaitBlocked(
                stepThread,
                "approach did not block on NPC ownership"
            );

            Future<Boolean> unregister=
                workers.submit(
                    ()->f.world.unregisterPlayer(
                        f.player,
                        f.generation
                    )
                );

            requireBlocked(
                unregister,
                "player unregister crossed approach lock order"
            );

            releaseNpcLock.countDown();

            require(
                blocker.get(
                    5L,
                    TimeUnit.SECONDS
                ),
                "NPC lock blocker lost ownership"
            );

            NpcCombatApproachService.Result result=
                approach.get(
                    5L,
                    TimeUnit.SECONDS
                );

            require(
                result.status==
                    NpcCombatApproachService.Status.MOVED,
                "owned approach result"
            );

            require(
                unregister.get(
                    5L,
                    TimeUnit.SECONDS
                ),
                "player unregister after approach"
            );
        }finally{
            releaseNpcLock.countDown();
            workers.shutdownNow();
            workers.awaitTermination(
                5L,
                TimeUnit.SECONDS
            );
            f.close();
        }
    }

    private static void awaitBlocked(
        AtomicReference<Thread> thread,
        String label
    )throws Exception{
        long deadline=
            System.nanoTime()+
            TimeUnit.SECONDS.toNanos(5L);

        while(System.nanoTime()<deadline){
            Thread current=thread.get();

            if(current!=null&&
               current.getState()==Thread.State.BLOCKED)
                return;

            Thread.sleep(5L);
        }

        throw new AssertionError(label);
    }

    private static void requireBlocked(
        Future<?> future,
        String label
    )throws Exception{
        try{
            future.get(
                150L,
                TimeUnit.MILLISECONDS
            );
            throw new AssertionError(label);
        }catch(TimeoutException expected){
            // Expected while approach owns the player mutation lock.
        }
    }


    private static void reentrantPolicyOwnershipLossNoMove()
        throws Exception{
        Fixture targetFixture=
            new Fixture(
                "approach-reentrant-target",
                3089,
                3495
            );

        try{
            WorldNpc npc=
                targetFixture.spawn(
                    1488,
                    3087,
                    3495,
                    0
                );
            Tile before=npc.tile();
            AtomicInteger routeCalls=
                new AtomicInteger();

            NpcCombatApproachService service=
                new NpcCombatApproachService(
                    targetFixture.world,
                    new NpcCombatApproachService
                        .ApproachPolicy(){
                        public int stopRange(
                            NpcCombatApproachService.Context context
                        ){
                            require(
                                targetFixture.world.unregisterPlayer(
                                    targetFixture.player,
                                    targetFixture.generation
                                ),
                                "reentrant target unregister"
                            );
                            return 1;
                        }

                        public RouteRequest.Policy routePolicy(
                            NpcCombatApproachService.Context context
                        ){
                            routeCalls.incrementAndGet();
                            return RouteRequest.Policy
                                .HOME_RECOVERED_STATIC_AUTHORITY;
                        }

                        public String authority(){
                            return "CUSTOM_LOCALLAB_NPC_APPROACH";
                        }

                        public String policy(){
                            return "REENTRANT_TARGET_UNREGISTER";
                        }
                    }
                );

            NpcCombatApproachService.Result result=
                service.step(
                    npc,
                    targetFixture.player,
                    targetFixture.generation
                );

            require(
                result.status==
                    NpcCombatApproachService.Status.STALE_TARGET&&
                routeCalls.get()==0&&
                before.equals(npc.tile()),
                "stopRange target loss crossed ownership recheck"
            );
        }finally{
            targetFixture.close();
        }

        Fixture attackerFixture=
            new Fixture(
                "approach-reentrant-attacker",
                3089,
                3495
            );

        try{
            WorldNpc npc=
                attackerFixture.spawn(
                    1488,
                    3087,
                    3495,
                    0
                );
            Tile before=npc.tile();

            NpcCombatApproachService service=
                new NpcCombatApproachService(
                    attackerFixture.world,
                    new NpcCombatApproachService
                        .ApproachPolicy(){
                        public int stopRange(
                            NpcCombatApproachService.Context context
                        ){
                            return 1;
                        }

                        public RouteRequest.Policy routePolicy(
                            NpcCombatApproachService.Context context
                        ){
                            require(
                                attackerFixture.world.npcs()
                                    .remove(npc.id),
                                "reentrant attacker removal"
                            );
                            return RouteRequest.Policy
                                .HOME_RECOVERED_STATIC_AUTHORITY;
                        }

                        public String authority(){
                            return "CUSTOM_LOCALLAB_NPC_APPROACH";
                        }

                        public String policy(){
                            return "REENTRANT_ATTACKER_REMOVAL";
                        }
                    }
                );

            NpcCombatApproachService.Result result=
                service.step(
                    npc,
                    attackerFixture.player,
                    attackerFixture.generation
                );

            require(
                result.status==
                    NpcCombatApproachService.Status.STALE_ATTACKER&&
                before.equals(npc.tile()),
                "routePolicy attacker loss crossed ownership recheck"
            );
        }finally{
            attackerFixture.close();
        }
    }


    private static void invalidPolicyAtomic()
        throws Exception{
        Fixture f=new Fixture(
            "approach-invalid-policy",
            3089,
            3495
        );

        try{
            WorldNpc npc=
                f.spawn(
                    1488,
                    3087,
                    3495,
                    0
                );
            Tile before=npc.tile();

            NpcCombatApproachService invalidRange=
                new NpcCombatApproachService(
                    f.world,
                    new NpcCombatApproachService
                        .ApproachPolicy(){
                        public int stopRange(
                            NpcCombatApproachService.Context c
                        ){
                            return 0;
                        }
                        public RouteRequest.Policy routePolicy(
                            NpcCombatApproachService.Context c
                        ){
                            return RouteRequest.Policy
                                .HOME_RECOVERED_STATIC_AUTHORITY;
                        }
                        public String authority(){
                            return "CUSTOM_LOCALLAB_NPC_APPROACH";
                        }
                        public String policy(){
                            return "INVALID_RANGE_TEST";
                        }
                    }
                );

            expect(
                IllegalArgumentException.class,
                ()->invalidRange.step(
                    npc,
                    f.player,
                    f.generation
                ),
                "invalid stop range"
            );

            require(
                before.equals(npc.tile()),
                "invalid stop range mutated NPC"
            );

            NpcCombatApproachService nullRoute=
                new NpcCombatApproachService(
                    f.world,
                    new NpcCombatApproachService
                        .ApproachPolicy(){
                        public int stopRange(
                            NpcCombatApproachService.Context c
                        ){
                            return 1;
                        }
                        public RouteRequest.Policy routePolicy(
                            NpcCombatApproachService.Context c
                        ){
                            return null;
                        }
                        public String authority(){
                            return "CUSTOM_LOCALLAB_NPC_APPROACH";
                        }
                        public String policy(){
                            return "NULL_ROUTE_TEST";
                        }
                    }
                );

            expect(
                NullPointerException.class,
                ()->nullRoute.step(
                    npc,
                    f.player,
                    f.generation
                ),
                "null route policy"
            );

            require(
                before.equals(npc.tile()),
                "null route policy mutated NPC"
            );
        }finally{
            f.close();
        }
    }

    private static void authorityAndBoundary(){
        World world=
            World.isolatedForTest(600L);

        try{
            expect(
                IllegalArgumentException.class,
                ()->new NpcCombatApproachService(
                    world,
                    policyWithAuthority(
                        1,
                        RouteRequest.Policy
                            .HOME_RECOVERED_STATIC_AUTHORITY,
                        "EXACT_CURRENT_CLIENT"
                    )
                ),
                "client approach authority"
            );

            expect(
                IllegalArgumentException.class,
                ()->new NpcCombatApproachService(
                    world,
                    policyWithAuthority(
                        1,
                        RouteRequest.Policy
                            .HOME_RECOVERED_STATIC_AUTHORITY,
                        "UNKNOWN_SERVER_AUTHORITY"
                    )
                ),
                "unknown approach authority"
            );

            for(Field field:
                    NpcCombatApproachService.class
                        .getDeclaredFields()){
                String name=
                    field.getName()
                        .toLowerCase(Locale.ROOT);

                for(String forbidden:
                        new String[]{
                            "packet",
                            "opcode",
                            "widget",
                            "sceneindex",
                            "aggro",
                            "targetselector",
                            "damage",
                            "animation",
                            "gfx",
                            "projectile",
                            "reward",
                            "drop"
                        })
                    require(
                        !name.contains(forbidden),
                        "unowned policy leaked through field "+
                        field.getName()
                    );
            }
        }finally{
            world.close();
        }
    }

    private static NpcCombatApproachService.ApproachPolicy
        policy(
            int range,
            RouteRequest.Policy routePolicy
        ){
        return policyWithAuthority(
            range,
            routePolicy,
            "CUSTOM_LOCALLAB_NPC_APPROACH"
        );
    }

    private static NpcCombatApproachService.ApproachPolicy
        policyWithAuthority(
            int range,
            RouteRequest.Policy routePolicy,
            String authority
        ){
        return new NpcCombatApproachService.ApproachPolicy(){
            public int stopRange(
                NpcCombatApproachService.Context c
            ){
                return range;
            }

            public RouteRequest.Policy routePolicy(
                NpcCombatApproachService.Context c
            ){
                return routePolicy;
            }

            public String authority(){
                return authority;
            }

            public String policy(){
                return "TEST_FIXED_APPROACH";
            }
        };
    }

    private static final class Fixture {
        final World world=
            World.isolatedForTest(600L);
        final WorldPlayer player=
            new WorldPlayer();
        final long generation;

        Fixture(
            String username,
            int playerX,
            int playerY
        ){
            generation=
                world.registerPlayer(
                    player,
                    username
                );

            player.movement()
                .restoreAccountState(
                    false,
                    100,
                    playerX,
                    playerY,
                    0
                );
        }

        WorldNpc spawn(
            int definitionId,
            int x,
            int y,
            int plane
        ){
            return world.npcs().spawn(
                definitionId,
                x,
                y,
                plane
            );
        }

        void close(){
            NpcCombatApproachServiceTest
                .close(world);
        }
    }

    private static void close(World world){
        for(WorldPlayer current:
                world.players().snapshot())
            world.unregisterPlayer(current);
        world.close();
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
            throw new AssertionError(label);
    }

    private NpcCombatApproachServiceTest(){}
}
