package spk.local;

import java.util.SortedMap;
import java.util.TreeMap;

public final class PvpKillRewardServiceTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer attacker=new WorldPlayer();
        WorldPlayer victim=new WorldPlayer();

        long attackerGeneration=
            world.registerPlayer(
                attacker,
                "opensrc"
            );
        long victimGeneration=
            world.registerPlayer(
                victim,
                "victim"
            );

        try{
            PvpKillRewardService service=
                new PvpKillRewardService(
                    victim
                );

            TreeMap<String,String> unrelated=
                new TreeMap<>();
            unrelated.put(
                "marker",
                "preserve-me"
            );
            attacker.snapshotExtensions()
                .replaceNamespace(
                    "unrelated_domain",
                    unrelated
                );

            long firstDeath=
                lethalPvpDeath(
                    victim,
                    attacker,
                    attackerGeneration,
                    10L
                );

            PvpKillRewardService.Receipt first=
                service.settle(
                    world,
                    attacker,
                    attackerGeneration,
                    firstDeath
                );

            assertGranted(
                first,
                1L,
                1L,
                false
            );

            PvpKillRewardService.Receipt replay=
                service.settle(
                    world,
                    attacker,
                    attackerGeneration,
                    firstDeath
                );

            assertGranted(
                replay,
                1L,
                1L,
                true
            );

            if(service.settledDeathCount()!=1)
                throw new AssertionError(
                    "same death created duplicate receipt"
                );

            PvpKillRewardService.Counters counters=
                PvpKillRewardService.counters(
                    attacker
                );
            if(counters.kills!=1L||
               counters.points!=1L)
                throw new AssertionError(
                    "first reward counters "+counters
                );

            victim.lifecycle().markRespawned();
            victim.playerState()
                .setCurrentLevel(
                    PlayerState.HITPOINTS,
                    1
                );

            long secondDeath=
                lethalPvpDeath(
                    victim,
                    attacker,
                    attackerGeneration,
                    20L
                );

            PvpKillRewardService.Receipt second=
                service.settle(
                    world,
                    attacker,
                    attackerGeneration,
                    secondDeath
                );

            assertGranted(
                second,
                2L,
                2L,
                false
            );

            SortedMap<String,String> unrelatedAfter=
                attacker.snapshotExtensions()
                    .namespace(
                        "unrelated_domain"
                    );
            if(!"preserve-me".equals(
                    unrelatedAfter.get(
                        "marker"
                    )))
                throw new AssertionError(
                    "unrelated extension namespace changed"
                );

            PlayerSnapshot snapshot=
                PlayerSnapshotCodec.capture(
                    "opensrc",
                    attacker
                );
            WorldPlayer restored=
                new WorldPlayer();

            PlayerSnapshotCodec.applyValidated(
                snapshot,
                restored
            );

            PvpKillRewardService.Counters restoredCounters=
                PvpKillRewardService.counters(
                    restored
                );
            if(restoredCounters.kills!=2L||
               restoredCounters.points!=2L)
                throw new AssertionError(
                    "snapshot reward round-trip failed "+
                    restoredCounters
                );

            testMalformedStateFailureAtomic(
                world
            );
            testOverflowFailureAtomic(
                world
            );
            testStaleGenerationRejected(
                world
            );

            System.out.println(
                "PVP_KILL_REWARD_SERVICE_PASS "+
                "firstKill=true "+
                "sameDeathReplay=true "+
                "nextDeath=true "+
                "kills=2 points=2 "+
                "malformedFailureAtomic=true "+
                "overflowFailureAtomic=true "+
                "unrelatedNamespacePreserved=true "+
                "snapshotRoundTrip=true "+
                "staleGenerationRejected=true "+
                "authority="+
                PvpKillRewardService.AUTHORITY
            );
        }finally{
            if(attacker.registered())
                world.unregisterPlayer(
                    attacker,
                    attackerGeneration
                );
            if(victim.registered())
                world.unregisterPlayer(
                    victim,
                    victimGeneration
                );
            world.close();
        }
    }

    private static void testMalformedStateFailureAtomic(
        World world
    ){
        WorldPlayer attacker=new WorldPlayer();
        WorldPlayer victim=new WorldPlayer();
        long attackerGeneration=
            world.registerPlayer(
                attacker,
                "malformed"
            );
        long victimGeneration=
            world.registerPlayer(
                victim,
                "malformed-victim"
            );

        try{
            TreeMap<String,String> bad=
                new TreeMap<>();
            bad.put("kills","not-a-number");
            bad.put("points","7");
            attacker.snapshotExtensions()
                .replaceNamespace(
                    PvpKillRewardService.NAMESPACE,
                    bad
                );

            long deathSequence=
                lethalPvpDeath(
                    victim,
                    attacker,
                    attackerGeneration,
                    30L
                );

            PvpKillRewardService service=
                new PvpKillRewardService(
                    victim
                );
            PvpKillRewardService.Receipt result=
                service.settle(
                    world,
                    attacker,
                    attackerGeneration,
                    deathSequence
                );

            if(result.granted||
               service.settledDeathCount()!=0)
                throw new AssertionError(
                    "malformed reward state granted "+
                    result
                );

            SortedMap<String,String> after=
                attacker.snapshotExtensions()
                    .namespace(
                        PvpKillRewardService.NAMESPACE
                    );
            if(!"not-a-number".equals(
                    after.get("kills"))||
               !"7".equals(
                    after.get("points")))
                throw new AssertionError(
                    "malformed failure mutated state "+
                    after
                );
        }finally{
            if(attacker.registered())
                world.unregisterPlayer(
                    attacker,
                    attackerGeneration
                );
            if(victim.registered())
                world.unregisterPlayer(
                    victim,
                    victimGeneration
                );
        }
    }

    private static void testOverflowFailureAtomic(
        World world
    ){
        WorldPlayer attacker=new WorldPlayer();
        WorldPlayer victim=new WorldPlayer();
        long attackerGeneration=
            world.registerPlayer(
                attacker,
                "overflow"
            );
        long victimGeneration=
            world.registerPlayer(
                victim,
                "overflow-victim"
            );

        try{
            TreeMap<String,String> max=
                new TreeMap<>();
            max.put(
                "kills",
                Long.toString(
                    Long.MAX_VALUE
                )
            );
            max.put(
                "points",
                "4"
            );
            attacker.snapshotExtensions()
                .replaceNamespace(
                    PvpKillRewardService.NAMESPACE,
                    max
                );

            long deathSequence=
                lethalPvpDeath(
                    victim,
                    attacker,
                    attackerGeneration,
                    40L
                );

            PvpKillRewardService service=
                new PvpKillRewardService(
                    victim
                );
            PvpKillRewardService.Receipt result=
                service.settle(
                    world,
                    attacker,
                    attackerGeneration,
                    deathSequence
                );

            if(result.granted)
                throw new AssertionError(
                    "overflow reward state granted "+
                    result
                );

            SortedMap<String,String> after=
                attacker.snapshotExtensions()
                    .namespace(
                        PvpKillRewardService.NAMESPACE
                    );
            if(!Long.toString(
                    Long.MAX_VALUE
               ).equals(
                    after.get("kills"))||
               !"4".equals(
                    after.get("points")))
                throw new AssertionError(
                    "overflow failure mutated state "+
                    after
                );
        }finally{
            if(attacker.registered())
                world.unregisterPlayer(
                    attacker,
                    attackerGeneration
                );
            if(victim.registered())
                world.unregisterPlayer(
                    victim,
                    victimGeneration
                );
        }
    }

    private static void testStaleGenerationRejected(
        World world
    ){
        WorldPlayer attacker=new WorldPlayer();
        WorldPlayer victim=new WorldPlayer();
        long attackerGeneration=
            world.registerPlayer(
                attacker,
                "stale"
            );
        long victimGeneration=
            world.registerPlayer(
                victim,
                "stale-victim"
            );

        long deathSequence=
            lethalPvpDeath(
                victim,
                attacker,
                attackerGeneration,
                50L
            );

        world.unregisterPlayer(
            attacker,
            attackerGeneration
        );

        try{
            PvpKillRewardService service=
                new PvpKillRewardService(
                    victim
                );
            PvpKillRewardService.Receipt result=
                service.settle(
                    world,
                    attacker,
                    attackerGeneration,
                    deathSequence
                );

            if(result.granted||
               service.settledDeathCount()!=0)
                throw new AssertionError(
                    "stale generation received reward "+
                    result
                );
        }finally{
            if(victim.registered())
                world.unregisterPlayer(
                    victim,
                    victimGeneration
                );
        }
    }

    private static long lethalPvpDeath(
        WorldPlayer victim,
        WorldPlayer attacker,
        long attackerGeneration,
        long tick
    ){
        victim.playerState()
            .setCurrentLevel(
                PlayerState.HITPOINTS,
                1
            );

        PlayerLifecycleService.DamageResult damage=
            new PlayerLifecycleService(
                victim
            ).applyDamage(
                1,
                tick,
                "PVP_TEST"
            );

        if(!damage.died)
            throw new AssertionError(
                "test lethal damage did not kill"
            );

        long deathSequence=
            victim.lifecycle()
                .deathSequence();

        victim.lifecycle()
            .attributeCurrentDeath(
                deathSequence,
                attacker.id(),
                attackerGeneration,
                "PLAYER_PVP"
            );

        return deathSequence;
    }

    private static void assertGranted(
        PvpKillRewardService.Receipt receipt,
        long kills,
        long points,
        boolean replay
    ){
        if(receipt==null||
           !receipt.granted||
           receipt.replay!=replay||
           receipt.kills!=kills||
           receipt.points!=points)
            throw new AssertionError(
                "unexpected reward receipt "+
                receipt
            );
    }

    private PvpKillRewardServiceTest(){}
}
