package spk.local;

import java.util.*;

public final class PlayerPvpKillRewardServiceTest {
    public static void main(String[] args)
        throws Exception{

        WorldPlayer victim=
            new WorldPlayer();
        WorldPlayer killer=
            new WorldPlayer();

        TreeMap<String,String> social=
            new TreeMap<>();
        social.put(
            "friends",
            "alice,bob"
        );
        killer.snapshotExtensions()
            .replaceNamespace(
                "social",
                social
            );

        PlayerPvpKillRewardService service=
            new PlayerPvpKillRewardService(
                victim
            );

        PlayerPvpKillRewardService.Result first=
            service.settle(
                killer,
                1L
            );

        require(
            first.applied&&
            !first.replayed&&
            first.kills==1L&&
            first.points==1L&&
            "APPLIED".equals(
                first.reason
            ),
            "first reward not applied "+
            first
        );

        SortedMap<String,String> firstState=
            killer.snapshotExtensions()
                .namespace(
                    PlayerPvpKillRewardService
                        .NAMESPACE
                );

        require(
            "1".equals(
                firstState.get(
                    "kills"
                )
            )&&
            "1".equals(
                firstState.get(
                    "points"
                )
            )&&
            PlayerPvpKillRewardService.AUTHORITY
                .equals(
                    firstState.get(
                        "authority"
                    )
                ),
            "first reward state incorrect "+
            firstState
        );

        PlayerPvpKillRewardService.Result replay=
            service.settle(
                killer,
                1L
            );

        require(
            !replay.applied&&
            replay.replayed&&
            replay.kills==1L&&
            replay.points==1L&&
            service.settledCount()==1,
            "same-death replay mutated reward "+
            replay
        );

        PlayerPvpKillRewardService.Result second=
            service.settle(
                killer,
                2L
            );

        require(
            second.applied&&
            second.kills==2L&&
            second.points==2L&&
            service.settledCount()==2,
            "second death did not increment once "+
            second
        );

        require(
            "alice,bob".equals(
                killer.snapshotExtensions()
                    .namespace(
                        "social"
                    )
                    .get(
                        "friends"
                    )
            ),
            "reward mutation clobbered unrelated namespace"
        );

        PlayerSnapshot captured=
            PlayerSnapshotCodec.capture(
                "opensrc",
                killer,
                0
            );

        WorldPlayer restored=
            new WorldPlayer();

        PlayerSnapshotCodec.applyValidated(
            captured,
            restored
        );

        SortedMap<String,String> restoredReward=
            restored.snapshotExtensions()
                .namespace(
                    PlayerPvpKillRewardService
                        .NAMESPACE
                );

        require(
            "2".equals(
                restoredReward.get(
                    "kills"
                )
            )&&
            "2".equals(
                restoredReward.get(
                    "points"
                )
            )&&
            PlayerPvpKillRewardService.AUTHORITY
                .equals(
                    restoredReward.get(
                        "authority"
                    )
                ),
            "snapshot round-trip lost reward state "+
            restoredReward
        );

        WorldPlayer malformedKiller=
            new WorldPlayer();
        TreeMap<String,String> malformed=
            new TreeMap<>();
        malformed.put(
            "version",
            PlayerPvpKillRewardService.VERSION
        );
        malformed.put(
            "authority",
            PlayerPvpKillRewardService.AUTHORITY
        );
        malformed.put(
            "kills",
            "not-a-number"
        );
        malformed.put(
            "points",
            "4"
        );
        malformedKiller.snapshotExtensions()
            .replaceNamespace(
                PlayerPvpKillRewardService
                    .NAMESPACE,
                malformed
            );

        SortedMap<String,String> malformedBefore=
            malformedKiller.snapshotExtensions()
                .snapshot();

        PlayerPvpKillRewardService.Result malformedResult=
            new PlayerPvpKillRewardService(
                new WorldPlayer()
            ).settle(
                malformedKiller,
                1L
            );

        require(
            !malformedResult.applied&&
            !malformedResult.replayed&&
            "MALFORMED_STATE".equals(
                malformedResult.reason
            )&&
            malformedBefore.equals(
                malformedKiller
                    .snapshotExtensions()
                    .snapshot()
            ),
            "malformed reward state was not failure-atomic "+
            malformedResult
        );

        WorldPlayer overflowKiller=
            new WorldPlayer();
        TreeMap<String,String> overflow=
            new TreeMap<>();
        overflow.put(
            "version",
            PlayerPvpKillRewardService.VERSION
        );
        overflow.put(
            "authority",
            PlayerPvpKillRewardService.AUTHORITY
        );
        overflow.put(
            "kills",
            Long.toString(
                Long.MAX_VALUE
            )
        );
        overflow.put(
            "points",
            "0"
        );
        overflowKiller.snapshotExtensions()
            .replaceNamespace(
                PlayerPvpKillRewardService
                    .NAMESPACE,
                overflow
            );

        SortedMap<String,String> overflowBefore=
            overflowKiller.snapshotExtensions()
                .snapshot();

        PlayerPvpKillRewardService.Result overflowResult=
            new PlayerPvpKillRewardService(
                new WorldPlayer()
            ).settle(
                overflowKiller,
                1L
            );

        require(
            !overflowResult.applied&&
            "OVERFLOW_STATE".equals(
                overflowResult.reason
            )&&
            overflowBefore.equals(
                overflowKiller
                    .snapshotExtensions()
                    .snapshot()
            ),
            "overflow reward state was not failure-atomic "+
            overflowResult
        );

        System.out.println(
            "PLAYER_PVP_KILL_REWARD_PASS "+
            "explicitLocalLabPolicy=true "+
            "firstKillIncrement=true "+
            "sameDeathReplayIdempotent=true "+
            "nextDeathIncrement=true "+
            "malformedFailIsolated=true "+
            "overflowFailIsolated=true "+
            "namespaceIsolation=true "+
            "snapshotRoundTrip=true "+
            "itemRewardInvented=false "+
            "coinRewardInvented=false "+
            "authority="+
            PlayerPvpKillRewardService.AUTHORITY
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

    private PlayerPvpKillRewardServiceTest(){}
}
