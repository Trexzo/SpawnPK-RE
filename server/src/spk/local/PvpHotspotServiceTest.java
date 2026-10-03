package spk.local;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;

public final class PvpHotspotServiceTest {
    private static final String POLICY=
        "LOCAL_LAB_POLICY_PVP_HOTSPOT";

    public static void main(String[] args)throws Exception{
        activePresenceAndKills();
        counterOverflowAtomic();
        globalEventOwnershipLinearized();
        terminalPresenceReconciliation();
        completionLifecycle();
        cancellationLifecycle();
        authorityGuards();
        protocolBoundary();

        System.out.println(
            "PVP_HOTSPOT_SERVICE_PASS "+
            "globalEventComposition=true "+
            "registrationComposition=true "+
            "activePresenceOnly=true "+
            "duplicateEnterRejected=true "+
            "absentLeaveRejected=true "+
            "validatedKillRequiresPresence=true "+
            "selfKillRejected=true "+
            "participantScopedCounters=true "+
            "counterOverflowAtomic=true "+
            "globalEventOwnershipLinearized=true "+
            "terminalPresenceReconciled=true "+
            "deadlineCompletionClearsPresence=true "+
            "externalTerminalClearsPresence=true "+
            "staleWorldTickRejected=true "+
            "leaveBlocksKillAttribution=true "+
            "completionClearsPresence=true "+
            "cancellationClearsPresence=true "+
            "terminalMutationRejected=true "+
            "rewardMutation=false "+
            "teleportMutation=false "+
            "protocolIndependent=true"
        );
    }

    private static void activePresenceAndKills(){
        GlobalEventService events=
            new GlobalEventService();
        PvpHotspotService service=
            new PvpHotspotService(
                events
            );

        WorldEventId id=
            WorldEventId.of(
                "pvp-hotspot:1"
            );

        PvpHotspotService.Snapshot created=
            service.registerHotspot(
                definition(
                    id,
                    100L,
                    200L
                ),
                "zone:wilderness:test",
                POLICY
            );

        require(
            created.lifecycle==
                GlobalEventService
                    .Lifecycle.SCHEDULED&&
            "zone:wilderness:test".equals(
                created.zoneKey
            ),
            "PvP Hotspot registration"
        );

        expect(
            IllegalStateException.class,
            ()->service.enter(
                id,
                "player:a",
                90L
            ),
            "scheduled hotspot enter"
        );

        PvpHotspotService.Snapshot enteredA=
            service.enter(
                id,
                "player:a",
                100L
            );

        require(
            enteredA.lifecycle==
                GlobalEventService
                    .Lifecycle.ACTIVE&&
            enteredA.presentCount()==1&&
            enteredA.participant(
                "player:a"
            ).present,
            "PvP Hotspot first enter"
        );

        expect(
            IllegalStateException.class,
            ()->service.enter(
                id,
                "player:a",
                100L
            ),
            "duplicate hotspot enter"
        );

        service.enter(
            id,
            "player:b",
            101L
        );

        expect(
            IllegalArgumentException.class,
            ()->service.recordValidatedKill(
                id,
                "player:a",
                "player:a",
                102L
            ),
            "hotspot self kill"
        );

        PvpHotspotService.KillResult kill=
            service.recordValidatedKill(
                id,
                "player:a",
                "player:b",
                102L
            );

        require(
            kill.attackerKills==1L&&
            kill.victimDeaths==1L&&
            service.get(id)
                .participant(
                    "player:a"
                ).kills==1L&&
            service.get(id)
                .participant(
                    "player:b"
                ).deaths==1L,
            "PvP Hotspot validated kill"
        );

        expect(
            IllegalArgumentException.class,
            ()->service.recordValidatedKill(
                id,
                "player:a",
                "player:b",
                101L
            ),
            "stale hotspot kill tick"
        );

        service.leave(
            id,
            "player:b",
            103L
        );

        expect(
            IllegalStateException.class,
            ()->service.recordValidatedKill(
                id,
                "player:a",
                "player:b",
                104L
            ),
            "left victim hotspot kill"
        );

        expect(
            IllegalStateException.class,
            ()->service.leave(
                id,
                "player:b",
                105L
            ),
            "absent hotspot leave"
        );

        // Re-entry is allowed; historical counters stay with the participant.
        PvpHotspotService.Snapshot reentered=
            service.enter(
                id,
                "player:b",
                106L
            );

        require(
            reentered.participant(
                "player:b"
            ).present&&
            reentered.participant(
                "player:b"
            ).deaths==1L,
            "PvP Hotspot re-entry history"
        );
    }

    private static void counterOverflowAtomic()throws Exception{
        GlobalEventService events=
            new GlobalEventService();
        PvpHotspotService service=
            new PvpHotspotService(
                events
            );

        WorldEventId id=
            WorldEventId.of(
                "pvp-hotspot:overflow"
            );

        service.registerHotspot(
            definition(
                id,
                10L,
                100L
            ),
            "zone:wilderness:overflow",
            POLICY
        );

        service.enter(
            id,
            "player:a",
            10L
        );
        service.enter(
            id,
            "player:b",
            10L
        );

        setParticipantCounters(
            service,
            id,
            "player:a",
            Long.MAX_VALUE,
            0L
        );
        setParticipantCounters(
            service,
            id,
            "player:b",
            0L,
            7L
        );

        expect(
            IllegalStateException.class,
            ()->service.recordValidatedKill(
                id,
                "player:a",
                "player:b",
                11L
            ),
            "attacker kill overflow"
        );

        require(
            service.get(id)
                .participant(
                    "player:a"
                ).kills==
                    Long.MAX_VALUE&&
            service.get(id)
                .participant(
                    "player:b"
                ).deaths==7L,
            "attacker overflow partially mutated counters"
        );

        setParticipantCounters(
            service,
            id,
            "player:a",
            4L,
            0L
        );
        setParticipantCounters(
            service,
            id,
            "player:b",
            0L,
            Long.MAX_VALUE
        );

        expect(
            IllegalStateException.class,
            ()->service.recordValidatedKill(
                id,
                "player:a",
                "player:b",
                12L
            ),
            "victim death overflow"
        );

        require(
            service.get(id)
                .participant(
                    "player:a"
                ).kills==4L&&
            service.get(id)
                .participant(
                    "player:b"
                ).deaths==
                    Long.MAX_VALUE,
            "victim overflow partially mutated counters"
        );

        setParticipantCounters(
            service,
            id,
            "player:b",
            0L,
            5L
        );

        PvpHotspotService.KillResult result=
            service.recordValidatedKill(
                id,
                "player:a",
                "player:b",
                13L
            );

        require(
            result.attackerKills==5L&&
            result.victimDeaths==6L&&
            service.get(id)
                .participant(
                    "player:a"
                ).kills==5L&&
            service.get(id)
                .participant(
                    "player:b"
                ).deaths==6L,
            "normal counters after overflow fixtures"
        );
    }

    @SuppressWarnings("unchecked")
    private static void setParticipantCounters(
        PvpHotspotService service,
        WorldEventId eventId,
        String participantRef,
        long kills,
        long deaths
    )throws Exception{
        Field hotspotsField=
            PvpHotspotService.class
                .getDeclaredField(
                    "hotspots"
                );
        hotspotsField.setAccessible(true);

        Map<WorldEventId,Object> hotspots=
            (Map<WorldEventId,Object>)
                hotspotsField.get(
                    service
                );
        Object entry=
            Objects.requireNonNull(
                hotspots.get(
                    eventId
                ),
                "hotspot entry"
            );

        Field participantsField=
            entry.getClass()
                .getDeclaredField(
                    "participants"
                );
        participantsField.setAccessible(true);

        Map<String,Object> participants=
            (Map<String,Object>)
                participantsField.get(
                    entry
                );
        Object participant=
            Objects.requireNonNull(
                participants.get(
                    participantRef
                ),
                "hotspot participant"
            );

        Field killsField=
            participant.getClass()
                .getDeclaredField(
                    "kills"
                );
        Field deathsField=
            participant.getClass()
                .getDeclaredField(
                    "deaths"
                );
        killsField.setAccessible(true);
        deathsField.setAccessible(true);
        killsField.setLong(
            participant,
            kills
        );
        deathsField.setLong(
            participant,
            deaths
        );
    }


    private static void globalEventOwnershipLinearized()
        throws Exception{
        GlobalEventService events=
            new GlobalEventService();
        PvpHotspotService service=
            new PvpHotspotService(
                events
            );

        WorldEventId id=
            WorldEventId.of(
                "pvp-hotspot:owned"
            );

        service.registerHotspot(
            definition(
                id,
                10L,
                100L
            ),
            "zone:owned",
            POLICY
        );

        events.tick(10L);

        ExecutorService workers=
            Executors.newFixedThreadPool(2);
        CountDownLatch eventOwned=
            new CountDownLatch(1);
        CountDownLatch allowTerminal=
            new CountDownLatch(1);

        try{
            Future<?> terminal=
                workers.submit(
                    ()->{
                        events.withEventCompositionOwnership(
                            id,
                            ()->{
                                eventOwned.countDown();

                                if(!allowTerminal.await(
                                        5L,
                                        TimeUnit.SECONDS))
                                    throw new AssertionError(
                                        "terminal ownership release timeout"
                                    );

                                events.complete(
                                    id,
                                    20L
                                );
                            }
                        );

                        return null;
                    }
                );

            require(
                eventOwned.await(
                    5L,
                    TimeUnit.SECONDS
                ),
                "GlobalEvent owner did not enter"
            );

            Future<PvpHotspotService.Snapshot> enter=
                workers.submit(
                    ()->service.enter(
                        id,
                        "player:blocked",
                        20L
                    )
                );

            try{
                enter.get(
                    200L,
                    TimeUnit.MILLISECONDS
                );
                throw new AssertionError(
                    "hotspot enter crossed GlobalEvent ownership"
                );
            }catch(TimeoutException expected){
                // Expected: service holds its own monitor and blocks on
                // the backing GlobalEvent ownership boundary.
            }

            allowTerminal.countDown();

            terminal.get(
                5L,
                TimeUnit.SECONDS
            );

            try{
                enter.get(
                    5L,
                    TimeUnit.SECONDS
                );
                throw new AssertionError(
                    "terminal backing event allowed participant mutation"
                );
            }catch(ExecutionException failure){
                require(
                    failure.getCause() instanceof
                        IllegalStateException,
                    "blocked enter wrong terminal failure "+
                    failure.getCause()
                );
            }

            PvpHotspotService.Snapshot after=
                service.get(id);

            require(
                after.lifecycle==
                    GlobalEventService
                        .Lifecycle.COMPLETED&&
                after.participant(
                    "player:blocked"
                )==null,
                "terminal transition crossed hotspot mutation"
            );
        }finally{
            allowTerminal.countDown();
            workers.shutdownNow();
            workers.awaitTermination(
                5L,
                TimeUnit.SECONDS
            );
        }
    }


    private static void terminalPresenceReconciliation(){
        GlobalEventService deadlineEvents=
            new GlobalEventService();
        PvpHotspotService deadlineService=
            new PvpHotspotService(
                deadlineEvents
            );

        WorldEventId deadlineId=
            WorldEventId.of(
                "pvp-hotspot:deadline-terminal"
            );

        deadlineService.registerHotspot(
            definition(
                deadlineId,
                10L,
                20L
            ),
            "zone:deadline-terminal",
            POLICY
        );

        deadlineService.enter(
            deadlineId,
            "player:a",
            10L
        );
        deadlineService.enter(
            deadlineId,
            "player:b",
            11L
        );
        deadlineService.recordValidatedKill(
            deadlineId,
            "player:a",
            "player:b",
            12L
        );

        expect(
            IllegalStateException.class,
            ()->deadlineService.leave(
                deadlineId,
                "player:a",
                20L
            ),
            "deadline-completed hotspot leave"
        );

        PvpHotspotService.Snapshot deadlineTerminal=
            deadlineService.get(
                deadlineId
            );

        require(
            deadlineTerminal.lifecycle==
                GlobalEventService
                    .Lifecycle.COMPLETED&&
            deadlineTerminal.presentCount()==0&&
            !deadlineTerminal.participant(
                "player:a"
            ).present&&
            !deadlineTerminal.participant(
                "player:b"
            ).present&&
            deadlineTerminal.participant(
                "player:a"
            ).kills==1L&&
            deadlineTerminal.participant(
                "player:b"
            ).deaths==1L,
            "deadline terminal presence reconciliation"
        );

        GlobalEventService completedEvents=
            new GlobalEventService();
        PvpHotspotService completedService=
            new PvpHotspotService(
                completedEvents
            );

        WorldEventId completedId=
            WorldEventId.of(
                "pvp-hotspot:external-complete"
            );

        completedService.registerHotspot(
            definition(
                completedId,
                30L,
                60L
            ),
            "zone:external-complete",
            POLICY
        );
        completedService.enter(
            completedId,
            "player:c",
            30L
        );

        completedEvents.complete(
            completedId,
            40L
        );

        PvpHotspotService.Snapshot externallyCompleted=
            completedService.get(
                completedId
            );

        require(
            externallyCompleted.lifecycle==
                GlobalEventService
                    .Lifecycle.COMPLETED&&
            externallyCompleted.presentCount()==0&&
            !externallyCompleted.participant(
                "player:c"
            ).present,
            "external completion presence reconciliation"
        );

        GlobalEventService cancelledEvents=
            new GlobalEventService();
        PvpHotspotService cancelledService=
            new PvpHotspotService(
                cancelledEvents
            );

        WorldEventId cancelledId=
            WorldEventId.of(
                "pvp-hotspot:external-cancel"
            );

        cancelledService.registerHotspot(
            definition(
                cancelledId,
                70L,
                100L
            ),
            "zone:external-cancel",
            POLICY
        );
        cancelledService.enter(
            cancelledId,
            "player:d",
            70L
        );

        cancelledEvents.cancel(
            cancelledId,
            80L
        );

        PvpHotspotService.Snapshot externallyCancelled=
            cancelledService.get(
                cancelledId
            );

        require(
            externallyCancelled.lifecycle==
                GlobalEventService
                    .Lifecycle.CANCELLED&&
            externallyCancelled.presentCount()==0&&
            !externallyCancelled.participant(
                "player:d"
            ).present,
            "external cancellation presence reconciliation"
        );
    }


    private static void completionLifecycle(){
        GlobalEventService events=
            new GlobalEventService();
        PvpHotspotService service=
            new PvpHotspotService(events);

        WorldEventId id=
            WorldEventId.of(
                "pvp-hotspot:complete"
            );

        service.registerHotspot(
            definition(
                id,
                10L,
                50L
            ),
            "zone:complete",
            POLICY
        );

        service.enter(
            id,
            "player:c",
            10L
        );
        service.enter(
            id,
            "player:d",
            11L
        );
        service.recordValidatedKill(
            id,
            "player:c",
            "player:d",
            12L
        );

        PvpHotspotService.Snapshot completed=
            service.complete(
                id,
                20L
            );

        require(
            completed.lifecycle==
                GlobalEventService
                    .Lifecycle.COMPLETED&&
            completed.terminal()&&
            completed.presentCount()==0&&
            completed.participant(
                "player:c"
            ).kills==1L&&
            completed.participant(
                "player:d"
            ).deaths==1L,
            "PvP Hotspot completion"
        );

        expect(
            IllegalStateException.class,
            ()->service.enter(
                id,
                "player:e",
                21L
            ),
            "terminal hotspot enter"
        );

        expect(
            IllegalStateException.class,
            ()->service.recordValidatedKill(
                id,
                "player:c",
                "player:d",
                22L
            ),
            "terminal hotspot kill"
        );
    }

    private static void cancellationLifecycle(){
        GlobalEventService events=
            new GlobalEventService();
        PvpHotspotService service=
            new PvpHotspotService(events);

        WorldEventId id=
            WorldEventId.of(
                "pvp-hotspot:cancel"
            );

        service.registerHotspot(
            definition(
                id,
                20L,
                60L
            ),
            "zone:cancel",
            POLICY
        );

        PvpHotspotService.Snapshot cancelled=
            service.cancel(
                id,
                5L
            );

        require(
            cancelled.lifecycle==
                GlobalEventService
                    .Lifecycle.CANCELLED&&
            cancelled.presentCount()==0,
            "scheduled PvP Hotspot cancellation"
        );
    }

    private static void authorityGuards(){
        GlobalEventService events=
            new GlobalEventService();
        PvpHotspotService service=
            new PvpHotspotService(events);

        WorldEventId id=
            WorldEventId.of(
                "pvp-hotspot:authority"
            );

        expect(
            IllegalArgumentException.class,
            ()->service.registerHotspot(
                new WorldEventDefinition(
                    id,
                    10L,
                    20L,
                    Collections.emptyList(),
                    "EXACT_CURRENT_CLIENT"
                ),
                "zone:authority",
                POLICY
            ),
            "PvP Hotspot authority mismatch"
        );

        require(
            events.size()==0&&
            service.size()==0,
            "authority mismatch mutated hotspot state"
        );
    }

    private static WorldEventDefinition definition(
        WorldEventId id,
        long startTick,
        long endTick
    ){
        return new WorldEventDefinition(
            id,
            startTick,
            endTick,
            Collections.singletonList(
                new WorldEventDefinition
                    .PhaseDefinition(
                        "active",
                        startTick
                    )
            ),
            POLICY
        );
    }

    private static void protocolBoundary(){
        for(Class<?> type:new Class<?>[]{
                PvpHotspotService.class,
                PvpHotspotService.Snapshot.class,
                PvpHotspotService.ParticipantSnapshot.class,
                PvpHotspotService.KillResult.class
        }){
            for(Field field:
                    type.getDeclaredFields()){
                String name=
                    field.getName()
                        .toLowerCase(
                            Locale.ROOT
                        );

                if(name.contains("packet")||
                   name.contains("opcode")||
                   name.contains("widget")||
                   name.contains("interface")||
                   name.contains("reward")||
                   name.contains("teleport")||
                   name.contains("multiplier"))
                    throw new AssertionError(
                        "protocol/reward/teleport state leaked into Hotspot "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );
            }
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
            throw new AssertionError(label);
    }

    private PvpHotspotServiceTest(){}
}
