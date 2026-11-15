package spk.local;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import spk.event.DomainEventBus;

/**
 * R77 offline-only real DomainEventBus regression. The actual WorldPulse
 * drives scheduled publications; listeners use the real domain event
 * priority and cancellation machinery. No game socket or persistent data.
 */
public final class R77OfflineDomainPublicationQuiescenceTest {
    private static final class OwnedMutation
            implements R77OfflineOwnedDomainPublicationFence.OfflineOwnedEvent{
        final EntityId id;
        final long generation;
        OwnedMutation(WorldPlayer player,long generation){
            this.id=player.id();
            this.generation=generation;
        }
        @Override public EntityId offlineOwnerId(){return id;}
        @Override public long offlineOwnerGeneration(){return generation;}
    }

    private static final class OwnedQuiescenceDuringPublish
            implements R77OfflineOwnedDomainPublicationFence.OfflineOwnedEvent{
        final EntityId id;
        final long generation;
        OwnedQuiescenceDuringPublish(WorldPlayer player,long generation){
            this.id=player.id();
            this.generation=generation;
        }
        @Override public EntityId offlineOwnerId(){return id;}
        @Override public long offlineOwnerGeneration(){return generation;}
    }

    private static final class GlobalNotice
            implements DomainEventBus.Event {}

    public static void main(String[] args)throws Exception{
        try(World world=World.isolatedForTest(60_000L)){
            WorldPlayer retiring=new WorldPlayer();
            long retiringGeneration=
                world.registerPlayer(retiring,"opensrc");
            WorldPlayer unaffected=new WorldPlayer();
            long unaffectedGeneration=
                world.registerPlayer(unaffected,"other");

            R74OfflineCommandIngressFence commandFence=
                new R74OfflineCommandIngressFence(world);
            commandFence.installForTest();
            R77OfflineOwnedDomainPublicationFence eventFence=
                new R77OfflineOwnedDomainPublicationFence(
                    world,commandFence
                );
            eventFence.installForTest();
            try{
                eventFence.installForTest();
                throw new AssertionError(
                    "R77 permitted duplicate domain gate"
                );
            }catch(IllegalStateException rejected){
                require(rejected.getMessage().contains(
                    "already installed"
                ),"R77 repeated gate rejected for wrong reason");
            }

            AtomicInteger retiredHits=new AtomicInteger();
            AtomicInteger unrelatedHits=new AtomicInteger();
            AtomicInteger globalHits=new AtomicInteger();
            AtomicInteger deniedAfterQuiescence=new AtomicInteger();
            AtomicInteger deniedAfterUnregister=new AtomicInteger();
            AtomicInteger deniedMidPublication=new AtomicInteger();
            AtomicInteger midHighHits=new AtomicInteger();
            AtomicInteger midNormalHits=new AtomicInteger();

            world.domainEvents().subscribe(
                OwnedMutation.class,DomainEventBus.Priority.NORMAL,
                event->{
                    if(event.id.equals(retiring.id()))
                        retiredHits.incrementAndGet();
                    else if(event.id.equals(unaffected.id()))
                        unrelatedHits.incrementAndGet();
                    else
                        throw new AssertionError(
                            "R77 unrelated synthetic actor reached listener"
                        );
                }
            );
            world.domainEvents().subscribe(
                GlobalNotice.class,DomainEventBus.Priority.NORMAL,
                event->globalHits.incrementAndGet()
            );
            world.domainEvents().subscribe(
                OwnedQuiescenceDuringPublish.class,
                DomainEventBus.Priority.HIGH,
                event->{
                    midHighHits.incrementAndGet();
                    // This transition occurs inside the FIRST listener.
                    // Publication admission must recheck BEFORE the
                    // NEXT listener rather than at publication start.
                    commandFence.quiesceInsideWorldActor(
                        unaffected,unaffectedGeneration
                    );
                }
            );
            world.domainEvents().subscribe(
                OwnedQuiescenceDuringPublish.class,
                DomainEventBus.Priority.NORMAL,
                event->midNormalHits.incrementAndGet()
            );

            world.events().schedule(1L,()->{
                publishUnchecked(world,new OwnedMutation(
                    retiring,retiringGeneration
                ));
                publishUnchecked(world,new OwnedMutation(
                    unaffected,unaffectedGeneration
                ));
                publishUnchecked(world,new GlobalNotice());
            });
            require(world.observePulse(System.currentTimeMillis())==1L,
                "R77 first World tick mismatch");
            require(retiredHits.get()==1&&
                    unrelatedHits.get()==1&&globalHits.get()==1,
                "R77 normal DomainEventBus publication baseline failed");

            // These callbacks are put in the LEGACY untagged event
            // queue before quiescence. The event bus must still
            // revalidate their explicit player identity at execution.
            world.events().schedule(2L,()->{
                try{
                    world.domainEvents().publish(
                        new OwnedMutation(retiring,retiringGeneration)
                    );
                    throw new AssertionError(
                        "R77 retired-domain handler executed"
                    );
                }catch(IOException denied){
                    checkDenial(denied,"R77_DOMAIN_DENIED owner quiesced");
                    deniedAfterQuiescence.incrementAndGet();
                }catch(Exception other){
                    throw new IllegalStateException(other);
                }
            });
            world.events().schedule(2L,()->{
                publishUnchecked(world,new OwnedMutation(
                    unaffected,unaffectedGeneration
                ));
                publishUnchecked(world,new GlobalNotice());
            });

            // The actor flips to terminal BEFORE tick 2's event phase.
            java.util.concurrent.CompletableFuture<Void> quiesce=
                world.submit(retiring,retiringGeneration,()->{
                    commandFence.quiesceInsideWorldActor(
                        retiring,retiringGeneration
                    );
                });
            require(world.observePulse(System.currentTimeMillis())==2L,
                "R77 second World tick mismatch");
            quiesce.get(10L,TimeUnit.SECONDS);
            require(deniedAfterQuiescence.get()==1&&
                    retiredHits.get()==1&&
                    unrelatedHits.get()==2&&globalHits.get()==2,
                "R77 stale event bypassed publication gate");

            // The high-priority listener on the OTHER player itself
            // triggers terminal quiescence; the normal listener must
            // not execute because admission is rechecked per-listener.
            world.events().schedule(3L,()->{
                try{
                    world.domainEvents().publish(
                        new OwnedQuiescenceDuringPublish(
                            unaffected,unaffectedGeneration
                        )
                    );
                    throw new AssertionError(
                        "R77 continued listener delivery after quiescence"
                    );
                }catch(IOException denied){
                    checkDenial(denied,"R77_DOMAIN_DENIED owner quiesced");
                    deniedMidPublication.incrementAndGet();
                }catch(Exception other){
                    throw new IllegalStateException(other);
                }
            });
            world.events().schedule(3L,()->{
                publishUnchecked(world,new GlobalNotice());
            });
            require(world.observePulse(System.currentTimeMillis())==3L,
                "R77 third World tick mismatch");
            require(midHighHits.get()==1&&midNormalHits.get()==0&&
                    deniedMidPublication.get()==1&&globalHits.get()==3,
                "R77 high-priority quiescence did not stop later listener");

            require(world.unregisterPlayer(
                retiring,retiringGeneration),
                "R77 retiring World owner unregister failed");
            world.events().schedule(4L,()->{
                try{
                    world.domainEvents().publish(
                        new OwnedMutation(retiring,retiringGeneration)
                    );
                    throw new AssertionError(
                        "R77 unregistered actor published domain event"
                    );
                }catch(IOException denied){
                    checkDenial(denied,
                        "R77_DOMAIN_DENIED stale owner generation");
                    deniedAfterUnregister.incrementAndGet();
                }catch(Exception other){
                    throw new IllegalStateException(other);
                }
            });
            world.events().schedule(4L,()->{
                publishUnchecked(world,new GlobalNotice());
            });
            require(world.observePulse(System.currentTimeMillis())==4L,
                "R77 fourth World tick mismatch");
            require(deniedAfterUnregister.get()==1&&
                    retiredHits.get()==1&&
                    unrelatedHits.get()==2&&
                    globalHits.get()==4,
                "R77 domain events changed state after release");

            System.out.println(
                "R77_OFFLINE_DOMAIN_PUBLICATION_PASS "+
                "actualDomainBusListenerOrder=true "+
                "preQuiescenceOwnedPublicationAllowed=true "+
                "legacyScheduledOwnerEventDeniedAtListener=true "+
                "postQuiescenceListenerNotInvoked=true "+
                "unrelatedOwnerAllowedBeforeOwnQuiescence=true "+
                "globalEventsUnaffected=true "+
                "quiescenceDuringHighPriorityStopsNormalListener=true "+
                "staleOwnerGenerationDeniedAfterUnregister=true "+
                "actualWorldPulseExecutionContext=true "+
                "noProductionHooks=true"
            );
        }
    }

    private static void publishUnchecked(
        World world,DomainEventBus.Event event
    ){
        try{
            world.domainEvents().publish(event);
        }catch(Exception error){
            throw new IllegalStateException(
                "R77 unexpected domain publication denial",error
            );
        }
    }

    private static void checkDenial(
        IOException error,String expected
    ){
        require(error.getMessage()!=null&&
                error.getMessage().contains(expected),
            "R77 wrong refusal "+error);
    }

    private static void require(boolean valid,String why){
        if(!valid)throw new AssertionError(why);
    }

    private R77OfflineDomainPublicationQuiescenceTest(){}
}
