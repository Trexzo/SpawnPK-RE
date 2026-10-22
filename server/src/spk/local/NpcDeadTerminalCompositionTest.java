package spk.local;

public final class NpcDeadTerminalCompositionTest {
    public static void main(String[] args)throws Exception{
        successfulTerminalConsumption();
        aliveRejected();
        staleRejected();
        precommitFailurePreservesDeath();
        returnWithoutRemovalPreservesDeath();
        postRemovalFailureRetiresLifecycle();
        duplicateConsumptionFailsClosed();

        System.out.println(
            "NPC_DEAD_TERMINAL_COMPOSITION_PASS "+
            "precommitFailureAtomic=true "+
            "commitRequired=true "+
            "canonicalRemoved=true "+
            "lifecycleRemoved=true "+
            "exactDeathIdentity=true "+
            "duplicateCommitBlocked=true "+
            "resurrection=false "+
            "protocolIndependent=true"
        );
    }

    private static void successfulTerminalConsumption()
        throws Exception{
        Fixture f=deadFixture(17L);

        final NpcLifecycleService.Snapshot[] seen={null};

        boolean consumed=
            f.lifecycle.consumeDeadCanonical(
                f.npc,
                snapshot->{
                    seen[0]=snapshot;
                    require(
                        snapshot.dead()&&
                        snapshot.deathTick==17L&&
                        snapshot.hitpoints==0&&
                        snapshot.maxHitpoints==12&&
                        "CUSTOM_LOCALLAB_HP".equals(
                            snapshot.sourceAuthority
                        ),
                        "terminal snapshot identity"
                    );

                    require(
                        f.registry.remove(
                            f.npc.id
                        ),
                        "terminal canonical removal"
                    );
                }
            );

        require(
            consumed&&
            seen[0]!=null&&
            f.registry.byId(f.npc.id)==null&&
            f.lifecycle.get(f.npc.id)==null,
            "successful terminal consumption"
        );
    }

    private static void aliveRejected()
        throws Exception{
        WorldNpcRegistry registry=
            new WorldNpcRegistry();
        NpcLifecycleService lifecycle=
            new NpcLifecycleService(registry);
        WorldNpc npc=
            registry.spawn(
                1488,
                3200,
                3200,
                0
            );

        lifecycle.register(
            npc,
            10,
            "CUSTOM_LOCALLAB_HP"
        );

        expect(
            IllegalStateException.class,
            ()->lifecycle.consumeDeadCanonical(
                npc,
                snapshot->
                    registry.remove(
                        npc.id
                    )
            ),
            "alive terminal consumption"
        );

        require(
            registry.byId(npc.id)==npc&&
            lifecycle.get(npc.id)!=null&&
            lifecycle.get(npc.id).alive(),
            "alive rejection mutated state"
        );
    }

    private static void staleRejected()
        throws Exception{
        Fixture f=deadFixture(19L);

        require(
            f.registry.remove(
                f.npc.id
            ),
            "stale fixture canonical removal"
        );

        expect(
            IllegalStateException.class,
            ()->f.lifecycle.consumeDeadCanonical(
                f.npc,
                snapshot->{}
            ),
            "stale terminal consumption"
        );

        require(
            f.lifecycle.get(f.npc.id)!=null&&
            f.lifecycle.get(f.npc.id).dead(),
            "stale rejection removed lifecycle"
        );
    }

    private static void precommitFailurePreservesDeath()
        throws Exception{
        Fixture f=deadFixture(23L);
        RuntimeException failure=
            new RuntimeException(
                "precommit"
            );

        try{
            f.lifecycle.consumeDeadCanonical(
                f.npc,
                snapshot->{
                    throw failure;
                }
            );
            throw new AssertionError(
                "precommit failure not propagated"
            );
        }catch(RuntimeException actual){
            require(
                actual==failure,
                "precommit exact failure identity"
            );
        }

        NpcLifecycleService.Snapshot state=
            f.lifecycle.get(
                f.npc.id
            );

        require(
            f.registry.byId(f.npc.id)==f.npc&&
            state!=null&&
            state.dead()&&
            state.deathTick==23L,
            "precommit failure did not preserve exact death"
        );
    }

    private static void returnWithoutRemovalPreservesDeath()
        throws Exception{
        Fixture f=deadFixture(29L);

        expect(
            IllegalStateException.class,
            ()->f.lifecycle.consumeDeadCanonical(
                f.npc,
                snapshot->{}
            ),
            "terminal action without canonical removal"
        );

        NpcLifecycleService.Snapshot state=
            f.lifecycle.get(
                f.npc.id
            );

        require(
            f.registry.byId(f.npc.id)==f.npc&&
            state!=null&&
            state.dead()&&
            state.deathTick==29L,
            "missing commit changed death state"
        );
    }

    private static void postRemovalFailureRetiresLifecycle()
        throws Exception{
        Fixture f=deadFixture(31L);
        RuntimeException failure=
            new RuntimeException(
                "after-removal"
            );

        try{
            f.lifecycle.consumeDeadCanonical(
                f.npc,
                snapshot->{
                    require(
                        f.registry.remove(
                            f.npc.id
                        ),
                        "post-removal fixture canonical removal"
                    );
                    throw failure;
                }
            );
            throw new AssertionError(
                "post-removal failure not propagated"
            );
        }catch(RuntimeException actual){
            require(
                actual==failure,
                "post-removal exact failure identity"
            );
        }

        require(
            f.registry.byId(f.npc.id)==null&&
            f.lifecycle.get(f.npc.id)==null,
            "post-removal failure left ghost lifecycle"
        );
    }

    private static void duplicateConsumptionFailsClosed()
        throws Exception{
        Fixture f=deadFixture(37L);

        require(
            f.lifecycle.consumeDeadCanonical(
                f.npc,
                snapshot->{
                    require(
                        f.registry.remove(
                            f.npc.id
                        ),
                        "duplicate fixture first removal"
                    );
                }
            ),
            "first terminal consumption"
        );

        expect(
            IllegalStateException.class,
            ()->f.lifecycle.consumeDeadCanonical(
                f.npc,
                snapshot->{}
            ),
            "duplicate terminal consumption"
        );

        require(
            f.registry.byId(f.npc.id)==null&&
            f.lifecycle.get(f.npc.id)==null,
            "duplicate consumption recreated state"
        );
    }

    private static Fixture deadFixture(
        long deathTick
    ){
        WorldNpcRegistry registry=
            new WorldNpcRegistry();
        NpcLifecycleService lifecycle=
            new NpcLifecycleService(registry);
        WorldNpc npc=
            registry.spawn(
                1488,
                3200,
                3200,
                0
            );

        lifecycle.register(
            npc,
            12,
            "CUSTOM_LOCALLAB_HP"
        );

        NpcLifecycleService.DamageResult lethal=
            lifecycle.applyDamage(
                npc.id,
                99,
                deathTick
            );

        require(
            lethal.newlyDied,
            "dead fixture lethal transition"
        );

        return new Fixture(
            registry,
            lifecycle,
            npc
        );
    }

    private static final class Fixture {
        final WorldNpcRegistry registry;
        final NpcLifecycleService lifecycle;
        final WorldNpc npc;

        Fixture(
            WorldNpcRegistry registry,
            NpcLifecycleService lifecycle,
            WorldNpc npc
        ){
            this.registry=registry;
            this.lifecycle=lifecycle;
            this.npc=npc;
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
            throw new AssertionError(
                label
            );
    }

    private NpcDeadTerminalCompositionTest(){}
}
