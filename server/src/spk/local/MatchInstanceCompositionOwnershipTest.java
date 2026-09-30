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

            durableChildHolds(
                matches,
                instances
            );

            System.out.println(
                "MATCH_INSTANCE_COMPOSITION_OWNERSHIP_PASS "+
                "matchBlocked=true "+
                "instanceBlocked=true "+
                "reentrantMutation=true "+
                "actionFailureSafe=true "+
                "lockOrderMatchThenInstance=true "+
                "durableChildHold=true "+
                "directMatchTerminalBlocked=true "+
                "directInstanceTopologyBlocked=true "+
                "independentHoldIdentity=true "+
                "holdReleaseAllowsOwnedTerminal=true "+
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

    private static void durableChildHolds(
        MatchSessionService matches,
        WorldInstanceService instances
    )throws Exception{
        MatchId firstMatch=
            MatchId.of(
                "composition:held:first"
            );
        WorldInstanceId firstInstance=
            WorldInstanceId.of(
                "composition:held:first"
            );
        String firstHold=
            "owner:first";

        matches.withWorldInstanceCompositionOwnership(
            instances,
            ()->{
                createActivePair(
                    matches,
                    instances,
                    firstMatch,
                    firstInstance,
                    "player:first"
                );
                matches.acquireTerminalHold(
                    firstMatch,
                    firstHold
                );
                instances.acquireStructuralHold(
                    firstInstance,
                    firstHold
                );
            }
        );

        require(
            matches.terminalHoldCount(
                firstMatch
            )==1&&
            instances.structuralHoldCount(
                firstInstance
            )==1,
            "durable child holds not published"
        );

        expectIllegal(
            ()->matches.complete(
                firstMatch,
                new MatchSession.Result(
                    "external_complete",
                    null,
                    "CUSTOM_LOCALLAB"
                )
            ),
            "direct match completion crossed durable hold"
        );
        expectIllegal(
            ()->matches.cancel(
                firstMatch,
                "external_cancel"
            ),
            "direct match cancellation crossed durable hold"
        );
        expectIllegal(
            ()->instances.attach(
                firstInstance,
                "player:foreign"
            ),
            "direct instance attach crossed durable hold"
        );
        expectIllegal(
            ()->instances.detach(
                firstInstance,
                "player:first"
            ),
            "direct instance detach crossed durable hold"
        );
        expectIllegal(
            ()->instances.beginClosing(
                firstInstance
            ),
            "direct instance close admission crossed durable hold"
        );

        expectIllegal(
            ()->matches.acquireTerminalHold(
                firstMatch,
                firstHold
            ),
            "duplicate match hold accepted"
        );
        expectIllegal(
            ()->instances.acquireStructuralHold(
                firstInstance,
                firstHold
            ),
            "duplicate instance hold accepted"
        );

        MatchId secondMatch=
            MatchId.of(
                "composition:held:second"
            );
        WorldInstanceId secondInstance=
            WorldInstanceId.of(
                "composition:held:second"
            );

        matches.withWorldInstanceCompositionOwnership(
            instances,
            ()->{
                createActivePair(
                    matches,
                    instances,
                    secondMatch,
                    secondInstance,
                    "player:second"
                );
                matches.acquireTerminalHold(
                    secondMatch,
                    "owner:second"
                );
                instances.acquireStructuralHold(
                    secondInstance,
                    "owner:second"
                );

                instances.releaseStructuralHold(
                    firstInstance,
                    firstHold
                );
                matches.releaseTerminalHold(
                    firstMatch,
                    firstHold
                );

                matches.cancel(
                    firstMatch,
                    "owner_cancelled"
                );
                instances.beginClosing(
                    firstInstance
                );
                instances.detach(
                    firstInstance,
                    "player:first"
                );
                instances.close(
                    firstInstance
                );
            }
        );

        require(
            matches.get(firstMatch).state==
                MatchSession.State.CANCELLED&&
            instances.get(firstInstance).lifecycle==
                WorldInstanceService
                    .Lifecycle.CLOSED,
            "released owner could not terminalize child pair"
        );

        require(
            matches.terminalHoldCount(
                secondMatch
            )==1&&
            instances.structuralHoldCount(
                secondInstance
            )==1&&
            matches.get(secondMatch).state==
                MatchSession.State.ACTIVE&&
            instances.get(secondInstance).lifecycle==
                WorldInstanceService
                    .Lifecycle.ACTIVE,
            "independent child hold disturbed"
        );

        expectIllegal(
            ()->matches.releaseTerminalHold(
                firstMatch,
                firstHold
            ),
            "double match hold release accepted"
        );
        expectIllegal(
            ()->instances.releaseStructuralHold(
                firstInstance,
                firstHold
            ),
            "double instance hold release accepted"
        );
    }

    private static void createActivePair(
        MatchSessionService matches,
        WorldInstanceService instances,
        MatchId matchId,
        WorldInstanceId instanceId,
        String participant
    ){
        MatchTeamId team=
            MatchTeamId.of(
                matchId.toString()+":team"
            );

        matches.create(
            matchId,
            rules()
        );
        matches.addTeam(
            matchId,
            team
        );
        matches.join(
            matchId,
            team,
            participant
        );

        instances.create(
            instanceId,
            matchId.toString(),
            "CUSTOM_LOCALLAB"
        );
        instances.attach(
            instanceId,
            participant
        );

        matches.attachInstance(
            matchId,
            instanceId
        );
        matches.markReady(matchId);
        instances.activate(instanceId);
        matches.activate(matchId);
    }

    private static void expectIllegal(
        ThrowingAction action,
        String label
    )throws Exception{
        try{
            action.run();
        }catch(IllegalStateException expected){
            return;
        }

        throw new AssertionError(label);
    }

    @FunctionalInterface
    private interface ThrowingAction {
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
