package spk.local;

import java.lang.reflect.Field;
import java.util.*;

public final class PvpHotspotServiceTest {
    private static final String POLICY=
        "LOCAL_LAB_POLICY_PVP_HOTSPOT";

    public static void main(String[] args)throws Exception{
        activePresenceAndKills();
        counterOverflowAtomic();
        completionLifecycle();
        cancellationLifecycle();
        authorityGuards();
        protocolBoundary();

        System.out.println(
            "PVP_HOTSPOT_SERVICE_PASS "+
            "globalEventComposition=true "+
            "activePresenceOnly=true "+
            "duplicateEnterRejected=true "+
            "absentLeaveRejected=true "+
            "validatedKillRequiresPresence=true "+
            "selfKillRejected=true "+
            "participantScopedCounters=true "+
            "counterOverflowAtomic=true "+
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
