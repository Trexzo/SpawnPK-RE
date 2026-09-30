package spk.local;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public final class NpcTargetAcquisitionServiceTest {
    private static final String AUTHORITY=
        "CUSTOM_LOCALLAB_NPC_TARGETING";
    private static final String POLICY=
        "TEST_CALLER_PRIORITY";

    public static void main(String[] args)throws Exception{
        exactCanonicalNpcRequired();
        callerEligibilityAndPriority();
        deterministicTieBreak();
        deadPlayersExcluded();
        staleBestFallsThrough();
        attackerLossFailsClosed();
        playerThenNpcFinalizationLockOrder();
        policyFailureNoWorldMutation();
        authorityAndBoundary();

        System.out.println(
            "NPC_TARGET_ACQUISITION_PASS "+
            "exactNpc=true "+
            "exactPlayerGeneration=true "+
            "deadExcluded=true "+
            "callerEligibility=true "+
            "callerPriority=true "+
            "deterministicTieBreak=true "+
            "staleCandidateFallback=true "+
            "lockOrderPlayerThenNpc=true "+
            "noImplicitEngagement=true "+
            "aggroPolicyOwned=false "+
            "presentationOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void exactCanonicalNpcRequired()
        throws Exception{
        Fixture f=new Fixture();

        try{
            require(
                f.world.npcs().remove(f.npc.id),
                "remove canonical NPC"
            );

            NpcTargetAcquisitionService service=
                f.service(
                    context->
                        NpcTargetAcquisitionService.Decision
                            .eligible(0L)
                );

            NpcTargetAcquisitionService.Result result=
                service.acquire(f.npc);

            require(
                result.status==
                    NpcTargetAcquisitionService.Status
                        .STALE_ATTACKER&&
                result.target==null,
                "stale attacker accepted"
            );
        }finally{
            f.close();
        }
    }

    private static void callerEligibilityAndPriority()
        throws Exception{
        Fixture f=new Fixture();

        try{
            PlayerRef far=
                f.addPlayer(
                    "target-far",
                    3092,
                    3495,
                    0
                );
            PlayerRef near=
                f.addPlayer(
                    "target-near",
                    3089,
                    3495,
                    0
                );
            PlayerRef excluded=
                f.addPlayer(
                    "target-excluded",
                    3088,
                    3495,
                    0
                );

            final int[] calls={0};

            NpcTargetAcquisitionService service=
                f.service(
                    context->{
                        calls[0]++;

                        require(
                            context.attackerId.equals(
                                f.npc.id
                            )&&
                            context.attackerDefinitionId==
                                f.npc.definitionId,
                            "attacker semantic context"
                        );

                        if(context.candidateId.equals(
                                excluded.player.id()))
                            return NpcTargetAcquisitionService
                                .Decision.ineligible();

                        require(
                            context.samePlane&&
                            context.chebyshevDistance>=0,
                            "same-plane distance context"
                        );

                        return NpcTargetAcquisitionService
                            .Decision.eligible(
                                context.chebyshevDistance
                            );
                    }
                );

            NpcTargetAcquisitionService.Result result=
                service.acquire(f.npc);

            require(
                result.status==
                    NpcTargetAcquisitionService.Status.ACQUIRED&&
                result.target!=null&&
                result.target.player==near.player&&
                result.target.playerId.equals(
                    near.player.id()
                )&&
                result.target.generation==
                    near.generation&&
                result.target.priority==2L&&
                result.target.tile.equals(
                    new Tile(3089,3495,0)
                )&&
                calls[0]==3&&
                far.player!=result.target.player&&
                excluded.player!=result.target.player,
                "caller eligibility/priority selection"
            );
        }finally{
            f.close();
        }
    }

    private static void deterministicTieBreak()
        throws Exception{
        Fixture f=new Fixture();

        try{
            PlayerRef first=
                f.addPlayer(
                    "tie-first",
                    3088,
                    3496,
                    0
                );
            PlayerRef second=
                f.addPlayer(
                    "tie-second",
                    3088,
                    3494,
                    0
                );

            require(
                first.player.id().value<
                    second.player.id().value,
                "fixture EntityId order"
            );

            NpcTargetAcquisitionService service=
                f.service(
                    context->
                        NpcTargetAcquisitionService.Decision
                            .eligible(7L)
                );

            NpcTargetAcquisitionService.Result result=
                service.acquire(f.npc);

            require(
                result.status==
                    NpcTargetAcquisitionService.Status.ACQUIRED&&
                result.target.player==first.player&&
                result.target.priority==7L,
                "deterministic EntityId tie-break"
            );
        }finally{
            f.close();
        }
    }

    private static void deadPlayersExcluded()
        throws Exception{
        Fixture f=new Fixture();

        try{
            PlayerRef dead=
                f.addPlayer(
                    "dead-best",
                    3088,
                    3495,
                    0
                );
            PlayerRef alive=
                f.addPlayer(
                    "alive-next",
                    3090,
                    3495,
                    0
                );

            PlayerLifecycleService.DamageResult lethal=
                new PlayerLifecycleService(
                    dead.player
                ).applyDamage(
                    999,
                    0L,
                    "TARGET_ACQUISITION_DEAD_FIXTURE"
                );

            require(
                lethal.died&&
                dead.player.lifecycle().dead(),
                "dead candidate fixture"
            );

            final ArrayList<EntityId> evaluated=
                new ArrayList<>();

            NpcTargetAcquisitionService service=
                f.service(
                    context->{
                        evaluated.add(
                            context.candidateId
                        );
                        return NpcTargetAcquisitionService
                            .Decision.eligible(
                                context.chebyshevDistance
                            );
                    }
                );

            NpcTargetAcquisitionService.Result result=
                service.acquire(f.npc);

            require(
                result.status==
                    NpcTargetAcquisitionService.Status.ACQUIRED&&
                result.target.player==alive.player&&
                !evaluated.contains(
                    dead.player.id()
                )&&
                evaluated.size()==1,
                "dead player reached policy/selection"
            );
        }finally{
            f.close();
        }
    }

    private static void staleBestFallsThrough()
        throws Exception{
        Fixture f=new Fixture();

        try{
            PlayerRef best=
                f.addPlayer(
                    "stale-best",
                    3088,
                    3495,
                    0
                );
            PlayerRef fallback=
                f.addPlayer(
                    "fallback",
                    3090,
                    3495,
                    0
                );

            final boolean[] removed={false};

            NpcTargetAcquisitionService service=
                f.service(
                    context->{
                        if(context.candidateId.equals(
                                best.player.id())&&
                           !removed[0]){
                            removed[0]=true;
                            require(
                                f.world.unregisterPlayer(
                                    best.player,
                                    best.generation
                                ),
                                "policy stale-best removal"
                            );
                        }

                        return NpcTargetAcquisitionService
                            .Decision.eligible(
                                context.candidateId.equals(
                                    best.player.id())
                                    ?0L
                                    :1L
                            );
                    }
                );

            NpcTargetAcquisitionService.Result result=
                service.acquire(f.npc);

            require(
                removed[0]&&
                result.status==
                    NpcTargetAcquisitionService.Status.ACQUIRED&&
                result.target.player==
                    fallback.player&&
                result.target.generation==
                    fallback.generation,
                "stale best did not fall through"
            );
        }finally{
            f.close();
        }
    }

    private static void attackerLossFailsClosed()
        throws Exception{
        Fixture f=new Fixture();

        try{
            f.addPlayer(
                "attacker-loss-target",
                3088,
                3495,
                0
            );

            final boolean[] removed={false};

            NpcTargetAcquisitionService service=
                f.service(
                    context->{
                        if(!removed[0]){
                            removed[0]=true;
                            require(
                                f.world.npcs().remove(
                                    f.npc.id
                                ),
                                "policy attacker removal"
                            );
                        }

                        return NpcTargetAcquisitionService
                            .Decision.eligible(0L);
                    }
                );

            NpcTargetAcquisitionService.Result result=
                service.acquire(f.npc);

            require(
                removed[0]&&
                result.status==
                    NpcTargetAcquisitionService.Status
                        .STALE_ATTACKER&&
                result.target==null,
                "lost attacker returned target"
            );
        }finally{
            f.close();
        }
    }

    private static void playerThenNpcFinalizationLockOrder()
        throws Exception{
        Fixture f=new Fixture();
        PlayerRef target=
            f.addPlayer(
                "lock-order-target",
                3088,
                3495,
                0
            );
        ExecutorService workers=
            Executors.newFixedThreadPool(3);
        CountDownLatch policyEntered=
            new CountDownLatch(1);
        CountDownLatch allowPolicyReturn=
            new CountDownLatch(1);
        CountDownLatch npcLockHeld=
            new CountDownLatch(1);
        CountDownLatch releaseNpcLock=
            new CountDownLatch(1);
        AtomicReference<Thread> acquireThread=
            new AtomicReference<>();

        try{
            NpcTargetAcquisitionService service=
                f.service(
                    context->{
                        policyEntered.countDown();

                        if(!allowPolicyReturn.await(
                                5L,
                                TimeUnit.SECONDS))
                            throw new AssertionError(
                                "policy return timeout"
                            );

                        return NpcTargetAcquisitionService
                            .Decision.eligible(0L);
                    }
                );

            Future<NpcTargetAcquisitionService.Result>
                acquire=
                    workers.submit(
                        ()->{
                            acquireThread.set(
                                Thread.currentThread()
                            );
                            return service.acquire(
                                f.npc
                            );
                        }
                    );

            require(
                policyEntered.await(
                    5L,
                    TimeUnit.SECONDS
                ),
                "policy did not enter"
            );

            Future<Boolean> npcBlocker=
                workers.submit(
                    ()->f.world.npcs()
                        .withCurrentMutationOwnershipIfCurrent(
                            f.npc,
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
                "NPC lock blocker start"
            );

            allowPolicyReturn.countDown();

            awaitBlocked(
                acquireThread,
                "acquisition did not block on NPC finalization"
            );

            Future<Boolean> unregister=
                workers.submit(
                    ()->f.world.unregisterPlayer(
                        target.player,
                        target.generation
                    )
                );

            requireBlocked(
                unregister,
                "unregister crossed player -> NPC finalization ownership"
            );

            releaseNpcLock.countDown();

            require(
                npcBlocker.get(
                    5L,
                    TimeUnit.SECONDS
                ),
                "NPC blocker lost ownership"
            );

            NpcTargetAcquisitionService.Result result=
                acquire.get(
                    5L,
                    TimeUnit.SECONDS
                );

            require(
                result.status==
                    NpcTargetAcquisitionService.Status.ACQUIRED&&
                result.target.player==target.player,
                "owned finalization result"
            );

            require(
                unregister.get(
                    5L,
                    TimeUnit.SECONDS
                ),
                "unregister after finalization"
            );
        }finally{
            allowPolicyReturn.countDown();
            releaseNpcLock.countDown();
            workers.shutdownNow();
            workers.awaitTermination(
                5L,
                TimeUnit.SECONDS
            );
            f.close();
        }
    }

    private static void policyFailureNoWorldMutation()
        throws Exception{
        Fixture f=new Fixture();

        try{
            PlayerRef target=
                f.addPlayer(
                    "policy-failure",
                    3088,
                    3495,
                    0
                );

            Tile npcBefore=f.npc.tile();
            Tile playerBefore=
                new Tile(
                    target.player.movement().x(),
                    target.player.movement().y(),
                    target.player.movement().plane()
                );

            NpcTargetAcquisitionService service=
                f.service(
                    context->{
                        throw new IllegalStateException(
                            "POLICY_FAILURE"
                        );
                    }
                );

            expect(
                IllegalStateException.class,
                ()->service.acquire(f.npc),
                "policy failure"
            );

            require(
                f.world.npcs().byId(
                    f.npc.id
                )==f.npc&&
                f.npc.tile().equals(
                    npcBefore
                )&&
                f.world.players().owns(
                    target.player,
                    target.generation
                )&&
                new Tile(
                    target.player.movement().x(),
                    target.player.movement().y(),
                    target.player.movement().plane()
                ).equals(playerBefore),
                "policy failure mutated world"
            );
        }finally{
            f.close();
        }
    }

    private static void authorityAndBoundary()
        throws Exception{
        Fixture f=new Fixture();

        try{
            expect(
                IllegalArgumentException.class,
                ()->new NpcTargetAcquisitionService(
                    f.world,
                    context->
                        NpcTargetAcquisitionService.Decision
                            .ineligible(),
                    "EXACT_CURRENT_CLIENT",
                    POLICY
                ),
                "client target authority"
            );

            expect(
                IllegalArgumentException.class,
                ()->new NpcTargetAcquisitionService(
                    f.world,
                    context->
                        NpcTargetAcquisitionService.Decision
                            .ineligible(),
                    "UNKNOWN_SERVER_AUTHORITY",
                    POLICY
                ),
                "unknown target authority"
            );

            NpcTargetAcquisitionService service=
                f.service(
                    context->
                        NpcTargetAcquisitionService.Decision
                            .ineligible()
                );

            require(
                AUTHORITY.equals(
                    service.authority()
                )&&
                POLICY.equals(
                    service.policy()
                ),
                "target provenance"
            );

            for(Class<?> type:new Class<?>[]{
                    NpcTargetAcquisitionService.class,
                    NpcTargetAcquisitionService.Context.class,
                    NpcTargetAcquisitionService.Target.class,
                    NpcTargetAcquisitionService.Result.class
                }){
                for(Field field:
                        type.getDeclaredFields()){
                    String name=
                        field.getName()
                            .toLowerCase(
                                Locale.ROOT
                            );

                    for(String forbidden:new String[]{
                            "packet",
                            "opcode",
                            "widget",
                            "sceneindex",
                            "socket",
                            "isaac",
                            "hitsplat",
                            "projectile",
                            "reward",
                            "drop",
                            "xp"
                        })
                        require(
                            !name.contains(
                                forbidden
                            ),
                            "presentation/combat identity leaked "+
                            type.getSimpleName()+
                            "."+
                            field.getName()
                        );
                }
            }

            for(Method method:
                    NpcTargetAcquisitionService.class
                        .getDeclaredMethods()){
                String name=
                    method.getName()
                        .toLowerCase(
                            Locale.ROOT
                        );

                for(String forbidden:new String[]{
                        "attack",
                        "engage",
                        "damage",
                        "packet",
                        "publish",
                        "reward",
                        "drop"
                    })
                    require(
                        !name.contains(
                            forbidden
                        ),
                        "implicit combat behavior leaked "+
                        method.getName()
                    );
            }
        }finally{
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
               current.getState()==
                    Thread.State.BLOCKED)
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
            // Expected while acquisition owns the player mutation lock.
        }
    }

    private static final class PlayerRef {
        final WorldPlayer player;
        final long generation;

        PlayerRef(
            WorldPlayer player,
            long generation
        ){
            this.player=player;
            this.generation=generation;
        }
    }

    private static final class Fixture {
        final World world=
            World.isolatedForTest(600L);
        final WorldNpc npc=
            world.npcs().spawn(
                1505,
                3087,
                3495,
                0
            );
        final ArrayList<PlayerRef> players=
            new ArrayList<>();

        PlayerRef addPlayer(
            String username,
            int x,
            int y,
            int plane
        ){
            WorldPlayer player=
                new WorldPlayer();
            long generation=
                world.registerPlayer(
                    player,
                    username
                );

            player.movement()
                .restoreAccountState(
                    false,
                    100,
                    x,
                    y,
                    plane
                );

            PlayerRef ref=
                new PlayerRef(
                    player,
                    generation
                );
            players.add(ref);
            return ref;
        }

        NpcTargetAcquisitionService service(
            NpcTargetAcquisitionService.AcquisitionPolicy policy
        ){
            return new NpcTargetAcquisitionService(
                world,
                policy,
                AUTHORITY,
                POLICY
            );
        }

        void close(){
            for(WorldPlayer player:
                    world.players().snapshot())
                world.unregisterPlayer(
                    player
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

    private NpcTargetAcquisitionServiceTest(){}
}
