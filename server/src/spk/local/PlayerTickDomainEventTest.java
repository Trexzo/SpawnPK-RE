package spk.local;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import spk.event.DomainEventBus;
import spk.event.PlayerEvent;
import spk.event.PlayerTickEvent;

public final class PlayerTickDomainEventTest {
    public static void main(String[] args)
        throws Exception{
        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer alpha=
            new WorldPlayer();
        WorldPlayer beta=
            new WorldPlayer();

        world.registerPlayer(
            alpha,
            "Alpha"
        );
        long betaGeneration=
            world.registerPlayer(
                beta,
                "Beta"
            );

        List<String> baseEvents=
            new ArrayList<>();
        List<String> exactEvents=
            new ArrayList<>();
        boolean[] worldContext={true};

        DomainEventBus.Subscription base=
            world.domainEvents()
                .subscribe(
                    PlayerEvent.class,
                    DomainEventBus.Priority.NORMAL,
                    event->{
                        worldContext[0]&=
                            world.pulse()
                                .inExecutionContext();
                        baseEvents.add(
                            event.playerRef()
                        );
                    }
                );

        DomainEventBus.Subscription exact=
            world.domainEvents()
                .subscribe(
                    PlayerTickEvent.class,
                    DomainEventBus.Priority.NORMAL,
                    event->{
                        worldContext[0]&=
                            world.pulse()
                                .inExecutionContext();
                        exactEvents.add(
                            event.playerRef()+
                            "@"+
                            event.tick()
                        );
                    }
                );

        try{
            require(
                Modifier.isPublic(
                    PlayerEvent.class
                        .getModifiers()
                )&&
                Modifier.isPublic(
                    PlayerTickEvent.class
                        .getModifiers()
                ),
                "concrete player events are not public API"
            );

            world.observePulse(
                1L
            );

            require(
                world.clock().tick()==1L,
                "first logical tick did not advance"
            );

            require(
                baseEvents.size()==2&&
                baseEvents.contains("alpha")&&
                baseEvents.contains("beta"),
                "base PlayerEvent subscription did not observe exact current players "+
                    baseEvents
            );

            require(
                exactEvents.size()==2&&
                exactEvents.contains("alpha@1")&&
                exactEvents.contains("beta@1"),
                "PlayerTickEvent first publication mismatch "+
                    exactEvents
            );

            require(
                world.unregisterPlayer(
                    beta,
                    betaGeneration
                ),
                "beta unregister fixture failed"
            );

            world.observePulse(
                2L
            );

            require(
                world.clock().tick()==2L,
                "second logical tick did not advance"
            );

            require(
                baseEvents.size()==3&&
                "alpha".equals(
                    baseEvents.get(2)
                ),
                "unregistered player leaked into base event publication "+
                    baseEvents
            );

            require(
                exactEvents.size()==3&&
                "alpha@2".equals(
                    exactEvents.get(2)
                ),
                "unregistered player leaked into exact event publication "+
                    exactEvents
            );

            require(
                worldContext[0],
                "PlayerTickEvent published outside World execution context"
            );

            PlayerTickEvent normalized=
                new PlayerTickEvent(
                    "  MiXeD  ",
                    9L
                );

            require(
                "mixed".equals(
                    normalized.playerRef()
                )&&
                normalized.tick()==9L,
                "public event semantic normalization mismatch"
            );

            boolean invalidTickRejected=false;
            try{
                new PlayerTickEvent(
                    "alpha",
                    0L
                );
            }catch(
                IllegalArgumentException expected
            ){
                invalidTickRejected=true;
            }

            require(
                invalidTickRejected,
                "non-authoritative zero tick accepted"
            );

            System.out.println(
                "PLAYER_TICK_DOMAIN_EVENT_PASS"+
                " publicTaxonomy=true"+
                " worldContext=true"+
                " exactCurrentPlayers=true"+
                " onePerLogicalTick=true"+
                " baseSubscription=true"+
                " staleUnregisteredSuppressed=true"+
                " transportIdentityLeak=false"+
                " pluginApiArtifact=true"
            );
        }finally{
            base.close();
            exact.close();

            if(alpha.registered())
                world.unregisterPlayer(
                    alpha,
                    alpha.generation()
                );

            if(beta.registered())
                world.unregisterPlayer(
                    beta,
                    beta.generation()
                );

            world.close();
        }
    }

    private static void require(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(
                message
            );
    }

    private PlayerTickDomainEventTest(){}
}
