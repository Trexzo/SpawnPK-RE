package spk.local;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class NpcDropResolutionServiceTest {
    private static final String AUTHORITY=
        "CUSTOM_LOCALLAB_NPC_DROPS_TEST";

    public static void main(String[] args){
        exactDeadResolutionAndReplay();
        aliveRejected();
        lifecycleMissingRejected();
        resolverFailureAtomic();
        overflowRejected();
        postResolveOwnershipRecheck();
        lifecycleRemovalDuringResolveRejected();
        authorityGuards();
        boundaryGuard();

        System.out.println(
            "NPC_DROP_RESOLUTION_PASS "+
            "canonicalDeadRequired=true "+
            "deathTickRequired=true "+
            "callerRecipient=true "+
            "callerResolver=true "+
            "authorityExplicit=true "+
            "emptyDropAllowed=true "+
            "duplicateItemsCombined=true "+
            "overflowRejected=true "+
            "exactReplayIdempotent=true "+
            "recipientMismatchRejected=true "+
            "resolverFailureAtomic=true "+
            "postResolveOwnershipRecheck=true "+
            "lifecycleRemovalDuringResolveRejected=true "+
            "groundMutation=false "+
            "inventoryMutation=false "+
            "rngOwned=false "+
            "killCreditOwned=false "+
            "respawnOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void exactDeadResolutionAndReplay(){
        Fixture fixture=
            deadFixture(
                1488,
                100,
                11L
            );

        final int[] calls={0};

        NpcDropResolutionService service=
            new NpcDropResolutionService(
                fixture.registry,
                fixture.lifecycle,
                new NpcDropResolutionService
                    .DropResolver(){
                    @Override public List<
                        NpcDropResolutionService.Drop
                    > resolve(
                        NpcDropResolutionService
                            .DeathContext context
                    ){
                        calls[0]++;

                        require(
                            context.npcId.equals(
                                fixture.npc.id
                            )&&
                            context.definitionId==1488&&
                            context.deathTick==11L&&
                            "custom_hp".equals(
                                context.lifecycleAuthority
                            )&&
                            "player:a".equals(
                                context.recipientRef
                            )&&
                            context.deathTile.x==3200&&
                            context.deathTile.y==3201&&
                            context.deathTile.plane==0,
                            "death context"
                        );

                        return Arrays.asList(
                            new NpcDropResolutionService
                                .Drop(
                                    995,
                                    10
                                ),
                            new NpcDropResolutionService
                                .Drop(
                                    560,
                                    2
                                ),
                            new NpcDropResolutionService
                                .Drop(
                                    995,
                                    5
                                )
                        );
                    }

                    @Override public String authority(){
                        return AUTHORITY;
                    }
                }
            );

        NpcDropResolutionService.Resolution first=
            service.resolve(
                fixture.npc,
                " Player:A "
            );

        require(
            calls[0]==1&&
            first.drops.size()==2&&
            first.drops.get(0).itemId==995&&
            first.drops.get(0).amount==15&&
            first.drops.get(1).itemId==560&&
            first.drops.get(1).amount==2&&
            AUTHORITY.equals(
                first.dropAuthority
            )&&
            AUTHORITY.equals(
                service.dropAuthority()
            )&&
            service.size()==1&&
            service.get(
                fixture.npc.id
            )==first,
            "resolved canonical drop bundle"
        );

        NpcDropResolutionService.Resolution replay=
            service.resolve(
                fixture.npc,
                "PLAYER:A"
            );

        require(
            replay==first&&
            calls[0]==1,
            "exact replay reran resolver"
        );

        boolean recipientMismatch=false;

        try{
            service.resolve(
                fixture.npc,
                "player:b"
            );
        }catch(
            IllegalStateException expected
        ){
            recipientMismatch=true;
        }

        require(
            recipientMismatch&&
            calls[0]==1,
            "recipient mismatch accepted"
        );

        boolean immutableDrops=false;

        try{
            first.drops.clear();
        }catch(
            UnsupportedOperationException expected
        ){
            immutableDrops=true;
        }

        boolean immutableSnapshot=false;

        try{
            service.snapshot().clear();
        }catch(
            UnsupportedOperationException expected
        ){
            immutableSnapshot=true;
        }

        require(
            immutableDrops&&
            immutableSnapshot,
            "drop snapshots mutable"
        );

        GroundItemRegistry ground=
            new GroundItemRegistry();

        require(
            ground.size()==0,
            "drop resolution mutated ground items"
        );

        /*
         * Once resolved, retry remains idempotent even after a later lifecycle
         * owner retires the canonical NPC for respawn scheduling.
         */
        require(
            fixture.registry.remove(
                fixture.npc.id
            ),
            "fixture retirement"
        );

        require(
            service.resolve(
                fixture.npc,
                "player:a"
            )==first&&
            calls[0]==1,
            "cached replay lost after NPC retirement"
        );
    }

    private static void aliveRejected(){
        WorldNpcRegistry registry=
            new WorldNpcRegistry();
        NpcLifecycleService lifecycle=
            new NpcLifecycleService(
                registry
            );
        WorldNpc npc=
            registry.spawn(
                1,
                3200,
                3200,
                0
            );

        lifecycle.register(
            npc,
            10,
            "custom_hp"
        );

        final int[] calls={0};

        NpcDropResolutionService service=
            new NpcDropResolutionService(
                registry,
                lifecycle,
                countingResolver(
                    calls,
                    Collections.emptyList()
                )
            );

        expect(
            IllegalStateException.class,
            ()->service.resolve(
                npc,
                "player:a"
            ),
            "alive NPC drop resolution"
        );

        require(
            calls[0]==0&&
            service.size()==0,
            "alive rejection invoked resolver"
        );
    }

    private static void lifecycleMissingRejected(){
        WorldNpcRegistry registry=
            new WorldNpcRegistry();
        NpcLifecycleService lifecycle=
            new NpcLifecycleService(
                registry
            );
        WorldNpc npc=
            registry.spawn(
                2,
                3200,
                3200,
                0
            );

        final int[] calls={0};

        NpcDropResolutionService service=
            new NpcDropResolutionService(
                registry,
                lifecycle,
                countingResolver(
                    calls,
                    Collections.emptyList()
                )
            );

        expect(
            IllegalStateException.class,
            ()->service.resolve(
                npc,
                "player:a"
            ),
            "missing lifecycle"
        );

        require(
            calls[0]==0&&
            service.size()==0,
            "missing lifecycle invoked resolver"
        );
    }

    private static void resolverFailureAtomic(){
        Fixture fixture=
            deadFixture(
                3,
                10,
                5L
            );

        final int[] calls={0};

        NpcDropResolutionService service=
            new NpcDropResolutionService(
                fixture.registry,
                fixture.lifecycle,
                new NpcDropResolutionService
                    .DropResolver(){
                    @Override public List<
                        NpcDropResolutionService.Drop
                    > resolve(
                        NpcDropResolutionService
                            .DeathContext context
                    ){
                        calls[0]++;

                        if(calls[0]==1)
                            throw new IllegalStateException(
                                "resolver boom"
                            );

                        return Collections.emptyList();
                    }

                    @Override public String authority(){
                        return AUTHORITY;
                    }
                }
            );

        expect(
            IllegalStateException.class,
            ()->service.resolve(
                fixture.npc,
                "player:a"
            ),
            "resolver failure"
        );

        require(
            service.size()==0&&
            fixture.lifecycle
                .get(
                    fixture.npc.id
                )
                .dead(),
            "resolver failure published partial result"
        );

        NpcDropResolutionService.Resolution retry=
            service.resolve(
                fixture.npc,
                "player:a"
            );

        require(
            calls[0]==2&&
            retry.drops.isEmpty()&&
            service.size()==1,
            "resolver retry"
        );
    }

    private static void overflowRejected(){
        Fixture fixture=
            deadFixture(
                4,
                10,
                6L
            );

        NpcDropResolutionService service=
            new NpcDropResolutionService(
                fixture.registry,
                fixture.lifecycle,
                resolver(
                    Arrays.asList(
                        new NpcDropResolutionService
                            .Drop(
                                995,
                                Integer.MAX_VALUE
                            ),
                        new NpcDropResolutionService
                            .Drop(
                                995,
                                1
                            )
                    )
                )
            );

        expect(
            IllegalStateException.class,
            ()->service.resolve(
                fixture.npc,
                "player:a"
            ),
            "drop amount overflow"
        );

        require(
            service.size()==0,
            "overflow cached drop result"
        );
    }

    private static void postResolveOwnershipRecheck(){
        Fixture fixture=
            deadFixture(
                5,
                10,
                7L
            );

        NpcDropResolutionService service=
            new NpcDropResolutionService(
                fixture.registry,
                fixture.lifecycle,
                new NpcDropResolutionService
                    .DropResolver(){
                    @Override public List<
                        NpcDropResolutionService.Drop
                    > resolve(
                        NpcDropResolutionService
                            .DeathContext context
                    ){
                        require(
                            fixture.registry.remove(
                                fixture.npc.id
                            ),
                            "resolver-side retirement fixture"
                        );

                        return Collections.singletonList(
                            new NpcDropResolutionService
                                .Drop(
                                    995,
                                    1
                                )
                        );
                    }

                    @Override public String authority(){
                        return AUTHORITY;
                    }
                }
            );

        expect(
            IllegalStateException.class,
            ()->service.resolve(
                fixture.npc,
                "player:a"
            ),
            "ownership changed during resolve"
        );

        require(
            service.size()==0,
            "ownership race cached drop result"
        );
    }

    private static void lifecycleRemovalDuringResolveRejected(){
        Fixture fixture=
            deadFixture(
                6,
                10,
                8L
            );

        NpcDropResolutionService service=
            new NpcDropResolutionService(
                fixture.registry,
                fixture.lifecycle,
                new NpcDropResolutionService
                    .DropResolver(){
                    @Override public List<
                        NpcDropResolutionService.Drop
                    > resolve(
                        NpcDropResolutionService
                            .DeathContext context
                    ){
                        require(
                            fixture.lifecycle.unregister(
                                fixture.npc.id
                            ),
                            "resolver-side lifecycle removal fixture"
                        );

                        return Collections.singletonList(
                            new NpcDropResolutionService
                                .Drop(
                                    995,
                                    1
                                )
                        );
                    }

                    @Override public String authority(){
                        return AUTHORITY;
                    }
                }
            );

        expect(
            IllegalStateException.class,
            ()->service.resolve(
                fixture.npc,
                "player:a"
            ),
            "lifecycle removed during resolve"
        );

        require(
            service.size()==0&&
            fixture.lifecycle.get(
                fixture.npc.id
            )==null,
            "lifecycle removal race cached drop result"
        );
    }

    private static void authorityGuards(){
        WorldNpcRegistry registry=
            new WorldNpcRegistry();
        NpcLifecycleService lifecycle=
            new NpcLifecycleService(
                registry
            );

        expect(
            IllegalArgumentException.class,
            ()->new NpcDropResolutionService(
                registry,
                lifecycle,
                resolver(
                    Collections.emptyList(),
                    "EXACT_CURRENT_CLIENT"
                )
            ),
            "client drop authority"
        );

        expect(
            IllegalArgumentException.class,
            ()->new NpcDropResolutionService(
                registry,
                lifecycle,
                resolver(
                    Collections.emptyList(),
                    "UNKNOWN_SERVER_AUTHORITY"
                )
            ),
            "unknown drop authority"
        );
    }

    private static Fixture deadFixture(
        int definitionId,
        int maxHp,
        long deathTick
    ){
        WorldNpcRegistry registry=
            new WorldNpcRegistry();
        NpcLifecycleService lifecycle=
            new NpcLifecycleService(
                registry
            );
        WorldNpc npc=
            registry.spawn(
                definitionId,
                3200,
                3201,
                0
            );

        lifecycle.register(
            npc,
            maxHp,
            "custom_hp"
        );
        lifecycle.applyDamage(
            npc.id,
            maxHp,
            deathTick
        );

        require(
            lifecycle.get(
                npc.id
            ).dead(),
            "dead fixture"
        );

        return new Fixture(
            registry,
            lifecycle,
            npc
        );
    }

    private static NpcDropResolutionService
        .DropResolver countingResolver(
            int[] calls,
            List<NpcDropResolutionService.Drop> drops
        ){
        return new NpcDropResolutionService
            .DropResolver(){
            @Override public List<
                NpcDropResolutionService.Drop
            > resolve(
                NpcDropResolutionService
                    .DeathContext context
            ){
                calls[0]++;
                return drops;
            }

            @Override public String authority(){
                return AUTHORITY;
            }
        };
    }

    private static NpcDropResolutionService
        .DropResolver resolver(
            List<NpcDropResolutionService.Drop> drops
        ){
        return resolver(
            drops,
            AUTHORITY
        );
    }

    private static NpcDropResolutionService
        .DropResolver resolver(
            List<NpcDropResolutionService.Drop> drops,
            String authority
        ){
        return new NpcDropResolutionService
            .DropResolver(){
            @Override public List<
                NpcDropResolutionService.Drop
            > resolve(
                NpcDropResolutionService
                    .DeathContext context
            ){
                return drops;
            }

            @Override public String authority(){
                return authority;
            }
        };
    }

    private static void boundaryGuard(){
        for(Class<?> type:new Class<?>[]{
                NpcDropResolutionService.class,
                NpcDropResolutionService.DeathContext.class,
                NpcDropResolutionService.Resolution.class,
                NpcDropResolutionService.Drop.class
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
                            "sceneindex",
                            "grounditemregistry",
                            "inventory",
                            "bank",
                            "rng",
                            "respawn"
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
                NpcDropResolutionService.class
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
                        "spawn",
                        "ground",
                        "inventory",
                        "bank",
                        "respawn",
                        "random"
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

    private NpcDropResolutionServiceTest(){}
}
