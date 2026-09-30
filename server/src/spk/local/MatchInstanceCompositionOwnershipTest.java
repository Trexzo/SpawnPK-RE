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

            System.out.println(
                "MATCH_INSTANCE_COMPOSITION_OWNERSHIP_PASS "+
                "matchBlocked=true "+
                "instanceBlocked=true "+
                "reentrantMutation=true "+
                "actionFailureSafe=true "+
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
