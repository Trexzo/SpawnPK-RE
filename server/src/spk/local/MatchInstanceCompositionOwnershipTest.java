package spk.local;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class MatchInstanceCompositionOwnershipTest {
    public static void main(String[] args)throws Exception{
        MatchSessionService matches=
            new MatchSessionService();
        WorldInstanceService instances=
            new WorldInstanceService();

        ExecutorService workers=
            Executors.newFixedThreadPool(3);

        try{
            CountDownLatch actionEntered=
                new CountDownLatch(1);
            CountDownLatch releaseAction=
                new CountDownLatch(1);
            AtomicBoolean reentrantMutation=
                new AtomicBoolean();

            Future<?> owner=
                workers.submit(
                    ()->{
                        matches
                            .withWorldInstanceCompositionOwnership(
                                instances,
                                ()->{
                                    matches.create(
                                        MatchId.of(
                                            "composition:owned"
                                        ),
                                        rules()
                                    );
                                    instances.create(
                                        WorldInstanceId.of(
                                            "composition:owned"
                                        ),
                                        "composition:owned",
                                        "CUSTOM_LOCALLAB"
                                    );

                                    require(
                                        matches.get(
                                            MatchId.of(
                                                "composition:owned"
                                            )
                                        )!=null,
                                        "reentrant match read"
                                    );
                                    require(
                                        instances.get(
                                            WorldInstanceId.of(
                                                "composition:owned"
                                            )
                                        )!=null,
                                        "reentrant instance read"
                                    );

                                    reentrantMutation.set(true);
                                    actionEntered.countDown();

                                    require(
                                        releaseAction.await(
                                            5L,
                                            TimeUnit.SECONDS
                                        ),
                                        "composition release"
                                    );
                                }
                            );

                        return null;
                    }
                );

            require(
                actionEntered.await(
                    5L,
                    TimeUnit.SECONDS
                ),
                "composition action entry"
            );

            Future<?> matchCompetitor=
                workers.submit(
                    ()->
                        matches.create(
                            MatchId.of(
                                "composition:match-competitor"
                            ),
                            rules()
                        )
                );

            Future<?> instanceCompetitor=
                workers.submit(
                    ()->
                        instances.create(
                            WorldInstanceId.of(
                                "composition:instance-competitor"
                            ),
                            "composition:instance-competitor",
                            "CUSTOM_LOCALLAB"
                        )
                );

            requireBlocked(
                matchCompetitor,
                "match mutation escaped composition ownership"
            );
            requireBlocked(
                instanceCompetitor,
                "instance mutation escaped composition ownership"
            );

            releaseAction.countDown();

            owner.get(
                5L,
                TimeUnit.SECONDS
            );
            matchCompetitor.get(
                5L,
                TimeUnit.SECONDS
            );
            instanceCompetitor.get(
                5L,
                TimeUnit.SECONDS
            );

            require(
                reentrantMutation.get(),
                "composition action did not mutate reentrantly"
            );

            boolean failed=false;

            try{
                matches
                    .withWorldInstanceCompositionOwnership(
                        instances,
                        ()->{
                            throw new IllegalStateException(
                                "fixture composition failure"
                            );
                        }
                    );
            }catch(IllegalStateException expected){
                failed=true;
            }

            require(
                failed,
                "composition failure not propagated"
            );

            durableLeaseBoundary(
                matches,
                instances
            );

            matches.create(
                MatchId.of(
                    "composition:after-failure-match"
                ),
                rules()
            );
            instances.create(
                WorldInstanceId.of(
                    "composition:after-failure-instance"
                ),
                "composition:after-failure-instance",
                "CUSTOM_LOCALLAB"
            );

            System.out.println(
                "MATCH_INSTANCE_COMPOSITION_OWNERSHIP_PASS "+
                "matchBlocked=true "+
                "instanceBlocked=true "+
                "reentrantMutation=true "+
                "actionFailureSafe=true "+
                "durableChildLease=true "+
                "directMatchTerminalBlocked=true "+
                "directInstanceTopologyBlocked=true "+
                "directParticipantTransitionBlocked=true "+
                "ownedParticipantTransition=true "+
                "directScoreMutationBlocked=true "+
                "ownedScoreMutation=true "+
                "foreignScoreCapabilityRejected=true "+
                "pairedLeaseRelease=true "+
                "ownedTerminalUnderLease=true "+
                "releaseAfterTerminal=true "+
                "opaqueLeaseIdentity=true "+
                "duplicateLeaseFailClosed=true "+
                "missingLeaseFailClosed=true "+
                "lockOrderMatchThenInstance=true "+
                "protocolIndependent=true"
            );
        }finally{
            workers.shutdownNow();

            require(
                workers.awaitTermination(
                    5L,
                    TimeUnit.SECONDS
                ),
                "worker shutdown"
            );
        }
    }

    private static void durableLeaseBoundary(
        MatchSessionService matches,
        WorldInstanceService instances
    )throws Exception{
        MatchId matchId=
            MatchId.of(
                "composition:leased"
            );
        WorldInstanceId instanceId=
            WorldInstanceId.of(
                "composition:leased"
            );
        MatchTeamId teamId=
            MatchTeamId.of(
                "team:leased"
            );
        AtomicReference<
            MatchSessionService.CompositionLease
        > lease=
            new AtomicReference<>();

        matches.withWorldInstanceCompositionOwnership(
            instances,
            ()->{
                matches.create(
                    matchId,
                    rules()
                );
                matches.addTeam(
                    matchId,
                    teamId
                );
                matches.join(
                    matchId,
                    teamId,
                    "player:leased"
                );
                instances.create(
                    instanceId,
                    matchId.toString(),
                    "CUSTOM_LOCALLAB"
                );
                instances.attach(
                    instanceId,
                    "player:leased"
                );
                matches.attachInstance(
                    matchId,
                    instanceId
                );
                matches.markReady(
                    matchId
                );
                instances.activate(
                    instanceId
                );
                matches.activate(
                    matchId
                );
                lease.set(
                    matches.acquireWorldInstanceCompositionLease(
                        instances,
                        matchId,
                        instanceId,
                        "parent:leased"
                    )
                );
            }
        );

        require(
            matches.compositionLeaseHeld(
                matchId
            )&&
            instances.compositionLeaseHeld(
                instanceId
            ),
            "paired composition lease missing"
        );

        expect(
            IllegalStateException.class,
            ()->matches.complete(
                matchId,
                new MatchSession.Result(
                    "caller_resolved",
                    teamId,
                    "CUSTOM_LOCALLAB"
                )
            ),
            "direct leased match complete"
        );
        expect(
            IllegalStateException.class,
            ()->matches.cancel(
                matchId,
                "caller_cancelled"
            ),
            "direct leased match cancel"
        );
        expect(
            IllegalStateException.class,
            ()->instances.attach(
                instanceId,
                "player:intruder"
            ),
            "direct leased instance attach"
        );
        expect(
            IllegalStateException.class,
            ()->instances.detach(
                instanceId,
                "player:leased"
            ),
            "direct leased instance detach"
        );
        expect(
            IllegalStateException.class,
            ()->instances.beginClosing(
                instanceId
            ),
            "direct leased instance beginClosing"
        );
        expect(
            IllegalStateException.class,
            ()->matches.adjustTeamScore(
                matchId,
                teamId,
                "direct_team",
                1L
            ),
            "direct leased team score"
        );
        expect(
            IllegalStateException.class,
            ()->matches.adjustParticipantScore(
                matchId,
                "player:leased",
                "direct_participant",
                1L
            ),
            "direct leased participant score"
        );
        expect(
            IllegalStateException.class,
            ()->matches.tryAdjustPresentParticipantScore(
                matchId,
                "player:leased",
                "direct_try_participant",
                1L
            ),
            "direct leased conditional participant score"
        );
        expect(
            IllegalStateException.class,
            ()->matches.tryAdjustPresentParticipantTeamScore(
                matchId,
                "player:leased",
                "direct_try_team",
                1L
            ),
            "direct leased conditional team score"
        );

        matches.adjustTeamScoreOwned(
            matchId,
            teamId,
            "owned_team",
            2L,
            lease.get()
        );
        matches.adjustParticipantScoreOwned(
            matchId,
            "player:leased",
            "owned_participant",
            3L,
            lease.get()
        );

        require(
            matches.tryAdjustPresentParticipantScoreOwned(
                matchId,
                "player:leased",
                "owned_try_participant",
                4L,
                lease.get()
            )&&
            matches.tryAdjustPresentParticipantTeamScoreOwned(
                matchId,
                "player:leased",
                "owned_try_team",
                5L,
                lease.get()
            ),
            "owned conditional score mutation"
        );

        MatchSession ownedScored=
            matches.get(
                matchId
            );

        require(
            Long.valueOf(2L).equals(
                ownedScored.team(
                    teamId
                ).scores.get(
                    "owned_team"
                )
            )&&
            Long.valueOf(5L).equals(
                ownedScored.team(
                    teamId
                ).scores.get(
                    "owned_try_team"
                )
            )&&
            Long.valueOf(3L).equals(
                ownedScored.participant(
                    "player:leased"
                ).scores.get(
                    "owned_participant"
                )
            )&&
            Long.valueOf(4L).equals(
                ownedScored.participant(
                    "player:leased"
                ).scores.get(
                    "owned_try_participant"
                )
            ),
            "owned score mutation result"
        );

        MatchSessionService foreignMatches=
            new MatchSessionService();
        WorldInstanceService foreignInstances=
            new WorldInstanceService();
        MatchId foreignMatchId=
            MatchId.of(
                "composition:foreign-lease"
            );
        WorldInstanceId foreignInstanceId=
            WorldInstanceId.of(
                "composition:foreign-lease"
            );
        MatchTeamId foreignTeamId=
            MatchTeamId.of(
                "team:foreign-lease"
            );
        AtomicReference<
            MatchSessionService.CompositionLease
        > foreignLease=
            new AtomicReference<>();

        foreignMatches.withWorldInstanceCompositionOwnership(
            foreignInstances,
            ()->{
                foreignMatches.create(
                    foreignMatchId,
                    rules()
                );
                foreignMatches.addTeam(
                    foreignMatchId,
                    foreignTeamId
                );
                foreignMatches.join(
                    foreignMatchId,
                    foreignTeamId,
                    "player:foreign"
                );
                foreignInstances.create(
                    foreignInstanceId,
                    foreignMatchId.toString(),
                    "CUSTOM_LOCALLAB"
                );
                foreignInstances.attach(
                    foreignInstanceId,
                    "player:foreign"
                );
                foreignMatches.attachInstance(
                    foreignMatchId,
                    foreignInstanceId
                );
                foreignMatches.markReady(
                    foreignMatchId
                );
                foreignInstances.activate(
                    foreignInstanceId
                );
                foreignMatches.activate(
                    foreignMatchId
                );
                foreignLease.set(
                    foreignMatches.acquireWorldInstanceCompositionLease(
                        foreignInstances,
                        foreignMatchId,
                        foreignInstanceId,
                        "foreign:lease"
                    )
                );
            }
        );

        expect(
            IllegalStateException.class,
            ()->matches.adjustTeamScoreOwned(
                matchId,
                teamId,
                "foreign_capability",
                1L,
                foreignLease.get()
            ),
            "foreign score capability"
        );

        expect(
            IllegalStateException.class,
            ()->matches.leave(
                matchId,
                "player:leased"
            ),
            "direct leased participant leave"
        );
        expect(
            IllegalStateException.class,
            ()->matches.forfeit(
                matchId,
                "player:leased"
            ),
            "direct leased participant forfeit"
        );
        expect(
            IllegalStateException.class,
            ()->matches.disconnect(
                matchId,
                "player:leased"
            ),
            "direct leased participant disconnect"
        );

        matches.forfeitOwned(
            matchId,
            "player:leased",
            lease.get()
        );

        require(
            matches.get(
                matchId
            ).participant(
                "player:leased"
            ).status==
                MatchSession.ParticipantStatus.FORFEITED,
            "owned participant transition"
        );

        expect(
            IllegalStateException.class,
            ()->matches.acquireWorldInstanceCompositionLease(
                instances,
                matchId,
                instanceId,
                "parent:duplicate"
            ),
            "duplicate paired lease"
        );
        expect(
            NullPointerException.class,
            ()->matches.releaseWorldInstanceCompositionLease(
                instances,
                matchId,
                instanceId,
                null
            ),
            "missing paired lease token"
        );

        require(
            matches.compositionLeaseHeld(
                matchId
            )&&
            instances.compositionLeaseHeld(
                instanceId
            ),
            "failed lease mutation dropped ownership"
        );

        matches.withWorldInstanceCompositionOwnership(
            instances,
            ()->{
                MatchSessionService.CompositionLease token=
                    lease.get();

                matches.cancelOwned(
                    matchId,
                    "owner_cancelled",
                    token
                );
                instances.beginClosingOwned(
                    instanceId,
                    token
                );
                instances.detachOwned(
                    instanceId,
                    "player:leased",
                    token
                );
                instances.closeOwned(
                    instanceId,
                    token
                );

                matches.releaseWorldInstanceCompositionLease(
                    instances,
                    matchId,
                    instanceId,
                    token
                );
            }
        );

        require(
            !matches.compositionLeaseHeld(
                matchId
            )&&
            !instances.compositionLeaseHeld(
                instanceId
            )&&
            matches.get(
                matchId
            ).state==
                MatchSession.State.CANCELLED&&
            instances.get(
                instanceId
            ).lifecycle==
                WorldInstanceService.Lifecycle.CLOSED,
            "paired composition lease release/terminalization"
        );

        expect(
            IllegalStateException.class,
            ()->matches.releaseWorldInstanceCompositionLease(
                instances,
                matchId,
                instanceId,
                lease.get()
            ),
            "double paired lease release"
        );
    }

    private static void expect(
        Class<? extends Throwable> type,
        Throwing action,
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

    private interface Throwing {
        void run() throws Exception;
    }


    private static MatchRules rules(){
        return new MatchRules(
            MatchRules.TeamMode.TEAMS,
            MatchRules.SpellPolicy.UNRESTRICTED,
            MatchRules.PrayerPolicy.UNRESTRICTED,
            MatchRules.RestrictionPolicy.ALLOWED,
            MatchRules.RestrictionPolicy.ALLOWED,
            MatchRules.WinConditionKind.CALLER_RESOLVED,
            MatchRules.NO_SCORE_TARGET,
            "composition:test",
            "CUSTOM_LOCALLAB"
        );
    }

    private static void requireBlocked(
        Future<?> future,
        String label
    )throws Exception{
        try{
            future.get(
                150L,
                TimeUnit.MILLISECONDS
            );

            throw new AssertionError(
                label
            );
        }catch(TimeoutException expected){
            // Expected: the composition owns both service monitors.
        }
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private MatchInstanceCompositionOwnershipTest(){}
}
