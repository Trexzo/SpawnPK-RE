package spk.local;

import java.lang.reflect.Field;
import java.util.*;

/** Deterministic regressions for Issue #164 semantic global-event lifecycle. */
public final class GlobalEventLifecycleFoundationTest {
    public static void main(String[] args){
        GlobalEventService service=
            new GlobalEventService();

        WorldEventDefinition tournament=
            new WorldEventDefinition(
                WorldEventId.of(
                    "custom:world-tournament"
                ),
                10L,
                20L,
                Arrays.asList(
                    new WorldEventDefinition.PhaseDefinition(
                        "warmup",
                        10L
                    ),
                    new WorldEventDefinition.PhaseDefinition(
                        "live",
                        12L
                    ),
                    new WorldEventDefinition.PhaseDefinition(
                        "final",
                        15L
                    )
                ),
                "CUSTOM_LOCALLAB"
            );

        expect(
            UnsupportedOperationException.class,
            ()->tournament.phases.add(
                new WorldEventDefinition.PhaseDefinition(
                    "illegal",
                    18L
                )
            ),
            "definition phase immutability"
        );

        GlobalEventService.Snapshot created=
            service.register(tournament);

        eq(
            GlobalEventService.Lifecycle.SCHEDULED,
            created.lifecycle,
            "registered lifecycle"
        );
        eq(
            "CUSTOM_LOCALLAB",
            created.sourceAuthority,
            "definition authority"
        );
        check(!created.hasPhase(),"scheduled event has no runtime phase");

        check(!service.tick(9L).changed(),"before-start tick is stable");

        GlobalEventService.TickResult started=
            service.tick(10L);

        eq(1,started.changes.size(),"start change count");
        eq(
            GlobalEventService.ChangeCause.START_DEADLINE,
            started.changes.get(0).cause,
            "start cause"
        );
        eq(
            GlobalEventService.Lifecycle.ACTIVE,
            service.get(tournament.id).lifecycle,
            "active lifecycle"
        );
        eq(
            "warmup",
            service.get(tournament.id).phaseKey,
            "start phase"
        );

        GlobalEventService.TickResult live=
            service.tick(13L);

        eq(1,live.changes.size(),"live phase change count");
        eq(
            GlobalEventService.ChangeCause.PHASE_DEADLINE,
            live.changes.get(0).cause,
            "live phase cause"
        );
        eq(
            "live",
            service.get(tournament.id).phaseKey,
            "live phase"
        );
        eq(
            12L,
            service.get(tournament.id).lastTransitionTick,
            "phase transition uses phase deadline"
        );

        service.tick(16L);
        eq(
            "final",
            service.get(tournament.id).phaseKey,
            "final phase"
        );

        GlobalEventService.TickResult ended=
            service.tick(20L);

        eq(1,ended.changes.size(),"end change count");
        eq(
            GlobalEventService.ChangeCause.END_DEADLINE,
            ended.changes.get(0).cause,
            "end cause"
        );
        eq(
            GlobalEventService.Lifecycle.COMPLETED,
            service.get(tournament.id).lifecycle,
            "deadline completion"
        );
        check(
            !service.tick(20L).changed(),
            "repeated terminal tick idempotent"
        );

        expect(
            IllegalArgumentException.class,
            ()->service.tick(19L),
            "backwards world tick"
        );

        WorldEventDefinition cancelledDefinition=
            new WorldEventDefinition(
                WorldEventId.of("custom:cancelled-event"),
                30L,
                40L,
                Collections.emptyList(),
                "CUSTOM_LOCALLAB"
            );

        service.register(cancelledDefinition);

        GlobalEventService.MutationResult cancelled=
            service.cancel(
                cancelledDefinition.id,
                25L
            );

        check(cancelled.changed(),"first cancellation mutates");
        eq(
            GlobalEventService.Lifecycle.CANCELLED,
            cancelled.snapshot.lifecycle,
            "cancelled lifecycle"
        );
        check(
            !service.cancel(
                cancelledDefinition.id,
                25L
            ).changed(),
            "repeated cancellation idempotent"
        );

        expect(
            IllegalStateException.class,
            ()->service.complete(
                cancelledDefinition.id,
                25L
            ),
            "cancelled cannot complete"
        );

        WorldEventDefinition manualDefinition=
            new WorldEventDefinition(
                WorldEventId.of("custom:manual-completion"),
                50L,
                60L,
                Collections.singletonList(
                    new WorldEventDefinition.PhaseDefinition(
                        "active",
                        50L
                    )
                ),
                "CUSTOM_LOCALLAB"
            );

        service.register(manualDefinition);
        service.tick(50L);

        GlobalEventService.MutationResult completed=
            service.complete(
                manualDefinition.id,
                52L
            );

        eq(
            GlobalEventService.Lifecycle.COMPLETED,
            completed.snapshot.lifecycle,
            "explicit completion"
        );
        eq(
            GlobalEventService.ChangeCause.EXPLICIT_COMPLETE,
            completed.changes.get(
                completed.changes.size()-1
            ).cause,
            "explicit completion cause"
        );
        check(
            !service.complete(
                manualDefinition.id,
                52L
            ).changed(),
            "repeated completion idempotent"
        );

        expect(
            IllegalStateException.class,
            ()->service.cancel(
                manualDefinition.id,
                52L
            ),
            "completed cannot cancel"
        );

        WorldEventDefinition schedulerDefinition=
            new WorldEventDefinition(
                WorldEventId.of("custom:scheduler-separation"),
                70L,
                80L,
                Collections.emptyList(),
                "CUSTOM_LOCALLAB"
            );

        service.register(schedulerDefinition);

        WorldEventQueue scheduler=
            new WorldEventQueue();

        WorldEventQueue.Handle handle=
            scheduler.schedule(
                70L,
                ()->service.tick(70L)
            );

        handle.cancel();

        eq(
            0,
            scheduler.runDue(70L),
            "cancelled scheduler task does not execute"
        );
        eq(
            GlobalEventService.Lifecycle.SCHEDULED,
            service.get(schedulerDefinition.id).lifecycle,
            "scheduler task is not semantic state"
        );

        service.tick(70L);

        eq(
            GlobalEventService.Lifecycle.ACTIVE,
            service.get(schedulerDefinition.id).lifecycle,
            "domain state advances independently"
        );

        expect(
            UnsupportedOperationException.class,
            ()->service.snapshot().add(created),
            "snapshot list immutability"
        );

        expect(
            IllegalStateException.class,
            ()->service.register(tournament),
            "duplicate semantic event identity"
        );

        expect(
            IllegalArgumentException.class,
            ()->new WorldEventDefinition(
                WorldEventId.of("custom:bad-window"),
                90L,
                90L,
                Collections.emptyList(),
                "CUSTOM_LOCALLAB"
            ),
            "invalid event window"
        );

        expect(
            IllegalArgumentException.class,
            ()->new WorldEventDefinition(
                WorldEventId.of("custom:bad-phase"),
                90L,
                100L,
                Arrays.asList(
                    new WorldEventDefinition.PhaseDefinition(
                        "late",
                        95L
                    ),
                    new WorldEventDefinition.PhaseDefinition(
                        "early",
                        94L
                    )
                ),
                "CUSTOM_LOCALLAB"
            ),
            "non-monotonic phases"
        );

        protocolBoundaryGuard();

        System.out.println(
            "ISSUE164_GLOBAL_EVENT_LIFECYCLE_PASS "+
            "lifecycle=true "+
            "phaseDeadlines=true "+
            "schedulerSeparated=true "+
            "immutable=true "+
            "idempotent=true "+
            "protocolIndependent=true "+
            "authorityPreserved=true "+
            "events="+service.size()
        );
    }

    private static void protocolBoundaryGuard(){
        Class<?>[] classes={
            WorldEventId.class,
            WorldEventDefinition.class,
            WorldEventDefinition.PhaseDefinition.class,
            GlobalEventService.class,
            GlobalEventService.Snapshot.class,
            GlobalEventService.Change.class
        };

        String[] banned={
            "widget",
            "opcode",
            "subtype",
            "sprite",
            "packet",
            "clientclass",
            "schedulerhandle"
        };

        for(Class<?> type:classes){
            for(Field field:type.getDeclaredFields()){
                String name=field.getName().toLowerCase(
                    Locale.ROOT
                );
                String fieldType=field.getType().getName();

                for(String token:banned){
                    if(name.contains(token))
                        fail(
                            "protocol identity leaked "+
                            type.getName()+"."+
                            field.getName()
                        );
                }

                if(fieldType.contains(
                        "WorldEventQueue$Handle"))
                    fail(
                        "scheduler handle leaked "+
                        type.getName()+"."+
                        field.getName()
                    );
            }
        }
    }

    private static void check(
        boolean condition,
        String label
    ){
        if(!condition)
            fail(label);
    }

    private static void eq(
        Object expected,
        Object actual,
        String label
    ){
        if(!Objects.equals(expected,actual))
            fail(
                label+
                " expected="+expected+
                " actual="+actual
            );
    }

    private static void eq(
        long expected,
        long actual,
        String label
    ){
        if(expected!=actual)
            fail(
                label+
                " expected="+expected+
                " actual="+actual
            );
    }

    private static void expect(
        Class<? extends Throwable> type,
        Throwing action,
        String label
    ){
        try{
            action.run();
            fail(
                label+
                " did not throw "+
                type.getSimpleName()
            );
        }catch(Throwable error){
            if(!type.isInstance(error))
                fail(
                    label+
                    " threw "+
                    error
                );
        }
    }

    private static void fail(String message){
        throw new AssertionError(message);
    }

    private interface Throwing {
        void run() throws Exception;
    }
}
