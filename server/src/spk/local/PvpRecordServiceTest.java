package spk.local;

import java.util.TreeMap;

public final class PvpRecordServiceTest {

    public static void main(String[] args)
        throws Exception {

        World world=
            World.isolatedForTest(600L);
        WorldPlayer alpha=
            new WorldPlayer();
        WorldPlayer beta=
            new WorldPlayer();

        world.registerPlayer(
            alpha,
            "alpha"
        );
        world.registerPlayer(
            beta,
            "beta"
        );

        try{
            PvpRecordService records=
                world.pvpRecords();

            assertRecord(
                records.snapshot(alpha),
                0L,0L,0L,0L,
                "initial alpha"
            );
            assertRecord(
                records.snapshot(beta),
                0L,0L,0L,0L,
                "initial beta"
            );

            records.onCombatOutcome(
                new CombatOutcome(
                    alpha.id().toString(),
                    beta.id().toString(),
                    CombatOutcomeType.NPC_KILL,
                    CombatOutcomeContext.NPC_PVM,
                    1L,
                    "CUSTOM_LOCALLAB"
                )
            );

            assertRecord(
                records.snapshot(alpha),
                0L,0L,0L,0L,
                "PvM ignored"
            );

            publishPvpDeath(
                records,
                alpha,
                beta,
                2L
            );
            publishPvpDeath(
                records,
                alpha,
                beta,
                3L
            );

            assertRecord(
                records.snapshot(alpha),
                2L,0L,2L,2L,
                "alpha two kills"
            );
            assertRecord(
                records.snapshot(beta),
                0L,2L,0L,0L,
                "beta two deaths"
            );

            publishPvpDeath(
                records,
                beta,
                alpha,
                4L
            );

            assertRecord(
                records.snapshot(alpha),
                2L,1L,0L,2L,
                "alpha death resets current"
            );
            assertRecord(
                records.snapshot(beta),
                1L,2L,1L,1L,
                "beta first kill"
            );

            PlayerSnapshot captured=
                PlayerSnapshotCodec.capture(
                    "alpha",
                    alpha,
                    0
                );

            require(
                "2".equals(
                    captured.value(
                        "extension.pvp-record.kills"
                    )
                ),
                "captured kills missing"
            );
            require(
                "1".equals(
                    captured.value(
                        "extension.pvp-record.deaths"
                    )
                ),
                "captured deaths missing"
            );
            require(
                "0".equals(
                    captured.value(
                        "extension.pvp-record.current-streak"
                    )
                ),
                "captured current streak missing"
            );
            require(
                "2".equals(
                    captured.value(
                        "extension.pvp-record.best-streak"
                    )
                ),
                "captured best streak missing"
            );

            WorldPlayer restored=
                new WorldPlayer();

            PlayerSnapshotCodec.applyValidated(
                captured,
                restored
            );

            assertRecord(
                records.snapshot(restored),
                2L,1L,0L,2L,
                "snapshot roundtrip"
            );

            TreeMap<String,String> malformed=
                new TreeMap<>();
            malformed.put("version","1");
            malformed.put("kills","not-a-number");
            malformed.put("deaths","-9");
            malformed.put("current-streak","7");
            malformed.put("best-streak","3");

            restored.snapshotExtensions()
                .replaceNamespace(
                    PvpRecordService.NAMESPACE,
                    malformed
                );

            assertRecord(
                records.snapshot(restored),
                0L,0L,7L,7L,
                "malformed tolerant"
            );

            TreeMap<String,String> future=
                new TreeMap<>();
            future.put("version","999");
            future.put("kills","123");
            future.put("deaths","456");
            future.put("current-streak","9");
            future.put("best-streak","10");

            restored.snapshotExtensions()
                .replaceNamespace(
                    PvpRecordService.NAMESPACE,
                    future
                );

            assertRecord(
                records.snapshot(restored),
                0L,0L,0L,0L,
                "future version fail closed"
            );

            System.out.println(
                "PVP_RECORD_SERVICE_PASS "+
                "pvpOnly=true "+
                "kills=true "+
                "deaths=true "+
                "currentStreak=true "+
                "bestStreak=true "+
                "snapshotExtension=true "+
                "roundTrip=true "+
                "malformedTolerant=true "+
                "futureVersionFailClosed=true "+
                "authority="+
                PvpRecordService.SOURCE_AUTHORITY
            );
        }finally{
            world.unregisterPlayer(alpha);
            world.unregisterPlayer(beta);
            world.close();
        }
    }

    private static void publishPvpDeath(
        PvpRecordService records,
        WorldPlayer attacker,
        WorldPlayer victim,
        long tick
    ){
        records.onCombatOutcome(
            new CombatOutcome(
                attacker.id().toString(),
                victim.id().toString(),
                CombatOutcomeType.PLAYER_KILL,
                CombatOutcomeContext.PLAYER_PVP,
                tick,
                PlayerLifecycleService.AUTHORITY
            )
        );
        records.onCombatOutcome(
            new CombatOutcome(
                attacker.id().toString(),
                victim.id().toString(),
                CombatOutcomeType.PLAYER_DEATH,
                CombatOutcomeContext.PLAYER_PVP,
                tick,
                PlayerLifecycleService.AUTHORITY
            )
        );
    }

    private static void assertRecord(
        PvpRecordService.Record record,
        long kills,
        long deaths,
        long currentStreak,
        long bestStreak,
        String label
    ){
        require(
            record.kills==kills&&
            record.deaths==deaths&&
            record.currentStreak==currentStreak&&
            record.bestStreak==bestStreak,
            label+" actual="+record
        );
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

    private PvpRecordServiceTest(){}
}
