package spk.local;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class CombatOutcomeObserverGroupTest {
    public static void main(String[] args){
        constructionGuards();

        ArrayList<String> calls=
            new ArrayList<>();
        ArrayList<String> failures=
            new ArrayList<>();

        CombatOutcomeObserver first=
            outcome->calls.add(
                "first:"+outcome.type()
            );

        CombatOutcomeObserver failing=
            outcome->{
                calls.add(
                    "failing:"+outcome.type()
                );
                throw new IllegalStateException(
                    "consumer boom"
                );
            };

        CombatOutcomeObserver later=
            outcome->calls.add(
                "later:"+outcome.type()
            );

        CombatOutcomeObserverGroup group=
            new CombatOutcomeObserverGroup(
                Arrays.asList(
                    first,
                    failing,
                    later
                ),
                (index,observer,outcome,failure)->
                    failures.add(
                        index+"|"+
                        outcome.type()+"|"+
                        failure.getClass()
                            .getSimpleName()+"|"+
                        failure.getMessage()
                    )
            );

        CombatOutcome fact=
            new CombatOutcome(
                "player:a",
                "npc:1",
                CombatOutcomeType.NPC_KILL,
                CombatOutcomeContext.NPC_PVM,
                42L,
                "CUSTOM_LOCALLAB"
            );

        group.onCombatOutcome(fact);

        require(
            calls.equals(
                Arrays.asList(
                    "first:NPC_KILL",
                    "failing:NPC_KILL",
                    "later:NPC_KILL"
                )
            ),
            "ordered failure-isolated fanout"
        );

        require(
            failures.equals(
                Collections.singletonList(
                    "1|NPC_KILL|IllegalStateException|consumer boom"
                )
            ),
            "consumer failure report"
        );

        require(
            group.size()==3&&
            group.observers().get(0)==first&&
            group.observers().get(1)==failing&&
            group.observers().get(2)==later,
            "observer snapshot order"
        );

        expect(
            UnsupportedOperationException.class,
            ()->group.observers().clear(),
            "observer snapshot mutable"
        );

        failureHandlerFailureIsolation(fact);
        protocolAndAsyncBoundary();

        System.out.println(
            "COMBAT_OUTCOME_OBSERVER_GROUP_PASS "+
            "orderedFanout=true "+
            "consumerFailureIsolated=true "+
            "laterConsumerRuns=true "+
            "failureReported=true "+
            "failureHandlerFailureIsolated=true "+
            "immutableObservers=true "+
            "emptyRejected=true "+
            "protocolIndependent=true "+
            "asyncOwned=false "+
            "retryOwned=false "+
            "persistenceOwned=false"
        );
    }

    private static void constructionGuards(){
        CombatOutcomeObserver noop=
            outcome->{};

        expect(
            IllegalArgumentException.class,
            ()->new CombatOutcomeObserverGroup(
                Collections.emptyList(),
                (index,observer,outcome,failure)->{}
            ),
            "empty observer group"
        );

        expect(
            NullPointerException.class,
            ()->new CombatOutcomeObserverGroup(
                Arrays.asList(
                    noop,
                    null
                ),
                (index,observer,outcome,failure)->{}
            ),
            "null observer"
        );

        expect(
            NullPointerException.class,
            ()->new CombatOutcomeObserverGroup(
                Collections.singletonList(
                    noop
                ),
                null
            ),
            "null failure handler"
        );
    }

    private static void failureHandlerFailureIsolation(
        CombatOutcome fact
    ){
        ArrayList<String> calls=
            new ArrayList<>();

        CombatOutcomeObserverGroup group=
            new CombatOutcomeObserverGroup(
                Arrays.asList(
                    outcome->{
                        calls.add("before");
                        throw new IllegalArgumentException(
                            "observer failure"
                        );
                    },
                    outcome->
                        calls.add("after")
                ),
                (index,observer,outcome,failure)->{
                    throw new IllegalStateException(
                        "failure handler failure"
                    );
                }
            );

        group.onCombatOutcome(fact);

        require(
            calls.equals(
                Arrays.asList(
                    "before",
                    "after"
                )
            ),
            "failure handler blocked later observer"
        );
    }

    private static void protocolAndAsyncBoundary(){
        for(Field field:
                CombatOutcomeObserverGroup.class
                    .getDeclaredFields()){
            String name=
                field.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            if(name.contains("packet")||
               name.contains("opcode")||
               name.contains("widget")||
               name.contains("queue")||
               name.contains("executor")||
               name.contains("thread")||
               name.contains("retry")||
               name.contains("persist"))
                throw new AssertionError(
                    "transport/async identity leaked into observer group field "+
                    field.getName()
                );
        }

        for(Method method:
                CombatOutcomeObserverGroup.class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            if(name.contains("enqueue")||
               name.contains("retry")||
               name.contains("replay")||
               name.contains("persist")||
               name.contains("async"))
                throw new AssertionError(
                    "event-bus behavior leaked into observer group method "+
                    method.getName()
                );
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

    private CombatOutcomeObserverGroupTest(){}
}
