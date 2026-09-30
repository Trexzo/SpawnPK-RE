package spk.local;

import java.lang.reflect.Field;
import java.util.Locale;

public final class NpcCombatEngagementServiceTest {
    public static void main(String[] args)throws Exception{
        waitingAttackAndDelegation();
        oneEngagementAndCancel();
        staleNpcAndTargetCancel();
        cadenceAndExecutorFailureNoAdvance();
        overflowAtomic();
        revisionOverflowAtomic();
        authorityAndBoundary();

        System.out.println(
            "NPC_COMBAT_ENGAGEMENT_PASS "+
            "explicitTarget=true "+
            "exactTargetGeneration=true "+
            "oneEngagementPerNpc=true "+
            "firstAttackTick=true "+
            "waiting=true "+
            "cadenceCallerOwned=true "+
            "cadenceOverflowAtomic=true "+
            "revisionOverflowAtomic=true "+
            "executorDelegated=true "+
            "executorFailureNoAdvance=true "+
            "staleNpcCancels=true "+
            "staleTargetCancels=true "+
            "cancelIdempotent=true "+
            "revisioned=true "+
            "aggroOwned=false "+
            "pathingOwned=false "+
            "damageOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void waitingAttackAndDelegation()throws Exception{
        Fixture f=new Fixture("engage-main");
        try{
            NpcPlayerCombatResolutionService damage=
                new NpcPlayerCombatResolutionService(
                    fixedDamage(5)
                );

            NpcCombatEngagementService service=
                new NpcCombatEngagementService(
                    f.world,
                    cadence(3),
                    (npc,target,generation,tick)->
                        damage.resolveImmediateOwned(
                            f.world,
                            npc,
                            target,
                            generation,
                            tick
                        )
                );

            NpcCombatEngagementService.Snapshot begun=
                service.begin(
                    f.npc,
                    f.player,
                    f.generation,
                    10L
                );

            require(
                begun.nextAttackTick==10L&&
                begun.revision==0L,
                "begin schedule"
            );

            NpcCombatEngagementService.TickResult wait=
                service.tick(f.npc.id,9L);

            require(
                wait.status==
                    NpcCombatEngagementService.TickStatus.WAITING&&
                hp(f.player)==99,
                "waiting tick"
            );

            NpcCombatEngagementService.TickResult hit=
                service.tick(f.npc.id,10L);

            require(
                hit.status==
                    NpcCombatEngagementService.TickStatus.ATTACKED&&
                hp(f.player)==94&&
                hit.snapshot.nextAttackTick==13L&&
                hit.snapshot.revision==1L,
                "delegated attack"
            );
        }finally{
            f.close();
        }
    }

    private static void oneEngagementAndCancel()throws Exception{
        Fixture f=new Fixture("engage-cancel");
        try{
            NpcCombatEngagementService service=
                new NpcCombatEngagementService(
                    f.world,
                    cadence(4),
                    (a,t,g,w)->{}
                );

            service.begin(f.npc,f.player,f.generation,0L);

            expect(
                IllegalStateException.class,
                ()->service.begin(
                    f.npc,
                    f.player,
                    f.generation,
                    1L
                ),
                "duplicate engagement"
            );

            require(
                service.cancel(f.npc)&&
                !service.cancel(f.npc)&&
                service.size()==0,
                "cancel idempotency"
            );
        }finally{
            f.close();
        }
    }

    private static void staleNpcAndTargetCancel()throws Exception{
        Fixture a=new Fixture("engage-stale-npc");
        try{
            int[] cadenceCalls={0};
            int[] attackCalls={0};

            NpcCombatEngagementService service=
                new NpcCombatEngagementService(
                    a.world,
                    countingCadence(cadenceCalls,2),
                    (n,t,g,w)->attackCalls[0]++
                );

            service.begin(a.npc,a.player,a.generation,0L);
            require(
                a.world.npcs().remove(a.npc.id),
                "remove stale npc fixture"
            );

            NpcCombatEngagementService.TickResult result=
                service.tick(a.npc.id,0L);

            require(
                result.status==
                    NpcCombatEngagementService.TickStatus.STALE_ATTACKER&&
                service.size()==0&&
                cadenceCalls[0]==0&&
                attackCalls[0]==0,
                "stale npc did not cancel"
            );
        }finally{
            a.close();
        }

        Fixture b=new Fixture("engage-stale-target");
        try{
            int[] cadenceCalls={0};
            int[] attackCalls={0};

            NpcCombatEngagementService service=
                new NpcCombatEngagementService(
                    b.world,
                    countingCadence(cadenceCalls,2),
                    (n,t,g,w)->attackCalls[0]++
                );

            service.begin(b.npc,b.player,b.generation,0L);

            require(
                b.world.unregisterPlayer(
                    b.player,
                    b.generation
                ),
                "remove stale target fixture"
            );
            b.generation=
                b.world.registerPlayer(
                    b.player,
                    "engage-stale-target"
                );

            NpcCombatEngagementService.TickResult result=
                service.tick(b.npc.id,0L);

            require(
                result.status==
                    NpcCombatEngagementService.TickStatus.STALE_TARGET&&
                service.size()==0&&
                cadenceCalls[0]==0&&
                attackCalls[0]==0,
                "stale target did not cancel"
            );
        }finally{
            b.close();
        }
    }

    private static void cadenceAndExecutorFailureNoAdvance()
        throws Exception{
        Fixture f=new Fixture("engage-failure");
        try{
            NpcCombatEngagementService cadenceFail=
                new NpcCombatEngagementService(
                    f.world,
                    new NpcCombatEngagementService
                        .CadenceResolver(){
                        public int nextDelayTicks(
                            NpcCombatEngagementService.Context c
                        ){
                            throw new IllegalStateException(
                                "cadence boom"
                            );
                        }
                        public String authority(){
                            return "CUSTOM_LOCALLAB_CADENCE";
                        }
                        public String policy(){
                            return "TEST";
                        }
                    },
                    (n,t,g,w)->{}
                );

            cadenceFail.begin(f.npc,f.player,f.generation,5L);

            expect(
                IllegalStateException.class,
                ()->cadenceFail.tick(f.npc.id,5L),
                "cadence failure"
            );

            require(
                cadenceFail.get(f.npc.id).nextAttackTick==5L&&
                cadenceFail.get(f.npc.id).revision==0L,
                "cadence failure advanced schedule"
            );

            cadenceFail.cancel(f.npc);

            NpcCombatEngagementService executorFail=
                new NpcCombatEngagementService(
                    f.world,
                    cadence(2),
                    (n,t,g,w)->{
                        throw new IllegalStateException(
                            "executor boom"
                        );
                    }
                );

            executorFail.begin(f.npc,f.player,f.generation,7L);

            expect(
                IllegalStateException.class,
                ()->executorFail.tick(f.npc.id,7L),
                "executor failure"
            );

            require(
                executorFail.get(f.npc.id).nextAttackTick==7L&&
                executorFail.get(f.npc.id).revision==0L,
                "executor failure advanced schedule"
            );
        }finally{
            f.close();
        }
    }

    private static void overflowAtomic()throws Exception{
        Fixture f=new Fixture("engage-overflow");
        try{
            int[] attacks={0};

            NpcCombatEngagementService service=
                new NpcCombatEngagementService(
                    f.world,
                    cadence(2),
                    (n,t,g,w)->attacks[0]++
                );

            service.begin(
                f.npc,
                f.player,
                f.generation,
                Long.MAX_VALUE-1L
            );

            expect(
                IllegalStateException.class,
                ()->service.tick(
                    f.npc.id,
                    Long.MAX_VALUE-1L
                ),
                "cadence overflow"
            );

            require(
                attacks[0]==0&&
                service.get(f.npc.id).revision==0L,
                "overflow invoked executor/advanced"
            );
        }finally{
            f.close();
        }
    }

    private static void revisionOverflowAtomic()throws Exception{
        Fixture f=new Fixture(
            "engage-revision-overflow"
        );
        try{
            int[] attacks={0};

            NpcCombatEngagementService service=
                new NpcCombatEngagementService(
                    f.world,
                    cadence(2),
                    (n,t,g,w)->attacks[0]++
                );

            service.begin(
                f.npc,
                f.player,
                f.generation,
                7L
            );

            setEngagementRevision(
                service,
                f.npc.id,
                Long.MAX_VALUE
            );

            expect(
                IllegalStateException.class,
                ()->service.tick(
                    f.npc.id,
                    7L
                ),
                "revision overflow"
            );

            NpcCombatEngagementService.Snapshot
                after=
                    service.get(
                        f.npc.id
                    );

            require(
                attacks[0]==0&&
                after.nextAttackTick==7L&&
                after.revision==
                    Long.MAX_VALUE,
                "revision overflow invoked executor/advanced schedule"
            );
        }finally{
            f.close();
        }
    }

    @SuppressWarnings("unchecked")
    private static void setEngagementRevision(
        NpcCombatEngagementService service,
        EntityId attackerId,
        long revision
    )throws Exception{
        Field engagementsField=
            NpcCombatEngagementService.class
                .getDeclaredField(
                    "engagements"
                );
        engagementsField.setAccessible(true);

        java.util.Map<EntityId,Object>
            engagements=
                (java.util.Map<EntityId,Object>)
                    engagementsField.get(
                        service
                    );

        Object engagement=
            java.util.Objects.requireNonNull(
                engagements.get(
                    attackerId
                ),
                "engagement"
            );

        Field revisionField=
            engagement.getClass()
                .getDeclaredField(
                    "revision"
                );
        revisionField.setAccessible(true);
        revisionField.setLong(
            engagement,
            revision
        );
    }


    private static void authorityAndBoundary(){
        Fixture f=new Fixture("engage-boundary");
        try{
            expect(
                IllegalArgumentException.class,
                ()->new NpcCombatEngagementService(
                    f.world,
                    cadenceWithAuthority(
                        2,
                        "EXACT_CURRENT_CLIENT"
                    ),
                    (n,t,g,w)->{}
                ),
                "client cadence authority"
            );

            for(Field field:
                    NpcCombatEngagementService.class
                        .getDeclaredFields()){
                String name=field.getName()
                    .toLowerCase(Locale.ROOT);

                for(String forbidden:new String[]{
                        "packet","opcode","widget","sceneindex",
                        "aggro","path","damage","animation",
                        "gfx","projectile","reward","drop"
                })
                    require(
                        !name.contains(forbidden),
                        "unowned policy leaked through field "+
                        field.getName()
                    );
            }
        }finally{
            f.close();
        }
    }

    private static NpcCombatEngagementService.CadenceResolver
        cadence(int delay){
        return cadenceWithAuthority(
            delay,
            "CUSTOM_LOCALLAB_CADENCE"
        );
    }

    private static NpcCombatEngagementService.CadenceResolver
        countingCadence(
            int[] calls,
            int delay
        ){
        return new NpcCombatEngagementService.CadenceResolver(){
            public int nextDelayTicks(
                NpcCombatEngagementService.Context c
            ){
                calls[0]++;
                return delay;
            }
            public String authority(){
                return "CUSTOM_LOCALLAB_CADENCE";
            }
            public String policy(){
                return "TEST_FIXED_DELAY";
            }
        };
    }

    private static NpcCombatEngagementService.CadenceResolver
        cadenceWithAuthority(
            int delay,
            String authority
        ){
        return new NpcCombatEngagementService.CadenceResolver(){
            public int nextDelayTicks(
                NpcCombatEngagementService.Context c
            ){
                return delay;
            }
            public String authority(){
                return authority;
            }
            public String policy(){
                return "TEST_FIXED_DELAY";
            }
        };
    }

    private static NpcPlayerCombatResolutionService.DamageResolver
        fixedDamage(int damage){
        return new NpcPlayerCombatResolutionService.DamageResolver(){
            public int resolve(
                NpcPlayerCombatResolutionService.DamageContext c
            ){
                return damage;
            }
            public String authority(){
                return "CUSTOM_LOCALLAB_NPC_DAMAGE";
            }
            public String formula(){
                return "TEST_FIXED_DAMAGE";
            }
        };
    }

    private static int hp(WorldPlayer player){
        return player.playerState()
            .currentLevel(PlayerState.HITPOINTS);
    }

    private static final class Fixture {
        final World world=World.isolatedForTest(600L);
        final WorldPlayer player=new WorldPlayer();
        long generation;
        final WorldNpc npc;

        Fixture(String username){
            generation=world.registerPlayer(
                player,
                username
            );
            npc=world.npcs().spawn(
                1488,
                3200,
                3200,
                0
            );
        }

        void close(){
            for(WorldPlayer current:
                    world.players().snapshot())
                world.unregisterPlayer(current);
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
            throw new AssertionError(label);
    }

    private NpcCombatEngagementServiceTest(){}
}
