package spk.local;

import java.lang.reflect.*;
import java.util.*;

public final class EventChestServiceTest {
    private static final AtomicTransactionService.SourceAuthority
        POLICY=
            AtomicTransactionService
                .SourceAuthority
                .CUSTOM_LOCALLAB;

    public static void main(String[] args){
        ArrayList<String> calls=
            new ArrayList<>();

        EventChestService service=
            new EventChestService(
                (player,action,projection,authority)->{
                    calls.add(
                        player+"|"+
                        action+"|"+
                        authority+"|"+
                        projection.headingText
                    );

                    if(action==
                            EventChestService
                                .Action.RESET_EVENT_ITEMS)
                        return EventChestService
                            .ActionResult.failure(
                                action,
                                "caller-owned reset denied"
                            );

                    return EventChestService
                        .ActionResult.success(
                            action
                        );
                },
                POLICY
            );

        authorityFence();

        EventChestService.Snapshot empty=
            service.snapshot();

        require(
            !empty.configured()&&
            empty.revision==0L&&
            empty.policyAuthority==POLICY,
            "initial Event Chest state"
        );

        expect(
            IllegalStateException.class,
            ()->service.requestAction(
                "player:alice",
                EventChestService
                    .Action.EXCHANGE
            ),
            "Event Chest action before projection"
        );

        EventChestService.Projection firstProjection=
            projection(
                "Caller Event",
                "Caller progress",
                "Caller status",
                3
            );

        EventChestService.Snapshot first=
            service.replaceProjection(
                firstProjection
            );

        require(
            first.configured()&&
            first.revision==1L&&
            first.projection==
                firstProjection&&
            first.projection.mainEntries
                .size()==3&&
            first.projection.smallGrids
                .size()==3&&
            first.projection.smallGrids
                .get(0).size()==2&&
            EventChestService
                .PRESENTATION_AUTHORITY
                .equals(
                    first
                        .presentationAuthority
                ),
            "Event Chest projection replacement"
        );

        immutability(first);

        EventChestService.ActionResult exchange=
            service.requestAction(
                " Player:Alice ",
                EventChestService
                    .Action.EXCHANGE
            );

        EventChestService.ActionResult nextTier=
            service.requestAction(
                "PLAYER:ALICE",
                EventChestService
                    .Action.ENTER_NEXT_TIER
            );

        EventChestService.ActionResult reset=
            service.requestAction(
                "player:alice",
                EventChestService
                    .Action.RESET_EVENT_ITEMS
            );

        require(
            exchange.succeeded&&
            nextTier.succeeded&&
            !reset.succeeded&&
            "caller-owned reset denied"
                .equals(reset.detail)&&
            calls.equals(
                Arrays.asList(
                    "player:alice|EXCHANGE|"+
                        POLICY+
                        "|Caller Event",
                    "player:alice|ENTER_NEXT_TIER|"+
                        POLICY+
                        "|Caller Event",
                    "player:alice|RESET_EVENT_ITEMS|"+
                        POLICY+
                        "|Caller Event"
                )
            ),
            "Event Chest action delegation"
        );

        failureAtomicity(service);
        protocolBoundary();
        mechanicsBoundary();

        System.out.println(
            "EVENT_CHEST_SERVICE_PASS "+
            "mainGridCapacity=175 "+
            "smallGridCount=3 "+
            "smallGridCapacity=4 "+
            "exactActions=3 "+
            "exchangeDelegated=true "+
            "enterNextTierDelegated=true "+
            "resetEventItemsDelegated=true "+
            "projectionFailureAtomic=true "+
            "semanticDisplayEntries=true "+
            "rollMechanicsOwned=false "+
            "prizeTablesOwned=false "+
            "tokenEconomicsOwned=false "+
            "tierRulesOwned=false "+
            "rngOwned=false "+
            "resetSemanticsOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void authorityFence(){
        EventChestService.ActionExecutor executor=
            (player,action,projection,authority)->
                EventChestService
                    .ActionResult.success(
                        action
                    );

        expect(
            IllegalArgumentException.class,
            ()->new EventChestService(
                executor,
                AtomicTransactionService
                    .SourceAuthority
                    .EXACT_CURRENT_CLIENT
            ),
            "exact client used as Event Chest policy"
        );

        expect(
            IllegalArgumentException.class,
            ()->new EventChestService(
                executor,
                AtomicTransactionService
                    .SourceAuthority
                    .UNKNOWN_SERVER_AUTHORITY
            ),
            "unknown authority used as Event Chest policy"
        );
    }

    private static void failureAtomicity(
        EventChestService service
    ){
        EventChestService.Snapshot before=
            service.snapshot();

        EventChestService.Projection wrongAuthority=
            new EventChestService.Projection(
                "Wrong",
                "Wrong",
                "Wrong",
                Collections.emptyList(),
                emptySmallGrids(),
                AtomicTransactionService
                    .SourceAuthority
                    .EXACT_CURRENT_CLIENT
            );

        expect(
            IllegalArgumentException.class,
            ()->service.replaceProjection(
                wrongAuthority
            ),
            "Event Chest wrong-authority projection"
        );

        expect(
            IllegalArgumentException.class,
            ()->projection(
                "Overflow",
                "Overflow",
                "Overflow",
                176
            ),
            "Event Chest main-grid overflow"
        );

        ArrayList<Collection<EventChestService.DisplayEntry>>
            badSmall=
                new ArrayList<>();

        badSmall.add(
            entries(
                "a",
                5
            )
        );
        badSmall.add(
            Collections.emptyList()
        );
        badSmall.add(
            Collections.emptyList()
        );

        expect(
            IllegalArgumentException.class,
            ()->new EventChestService.Projection(
                "Bad small",
                "Bad small",
                "Bad small",
                Collections.emptyList(),
                badSmall,
                POLICY
            ),
            "Event Chest small-grid overflow"
        );

        EventChestService.Snapshot after=
            service.snapshot();

        require(
            after.revision==
                before.revision&&
            after.projection==
                before.projection,
            "failed Event Chest projection mutated state"
        );
    }

    private static EventChestService.Projection
        projection(
            String heading,
            String progress,
            String status,
            int mainCount
        )
    {
        ArrayList<EventChestService.DisplayEntry>
            main=
                new ArrayList<>();

        for(int i=0;i<mainCount;i++)
            main.add(
                new EventChestService
                    .DisplayEntry(
                        "main:"+i,
                        i+1L
                    )
            );

        ArrayList<Collection<EventChestService.DisplayEntry>>
            small=
                new ArrayList<>();

        small.add(
            entries(
                "small:a:",
                2
            )
        );
        small.add(
            entries(
                "small:b:",
                1
            )
        );
        small.add(
            Collections.emptyList()
        );

        return new EventChestService
            .Projection(
                heading,
                progress,
                status,
                main,
                small,
                POLICY
            );
    }

    private static List<Collection<EventChestService.DisplayEntry>>
        emptySmallGrids()
    {
        return Arrays.asList(
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList()
        );
    }

    private static Collection<EventChestService.DisplayEntry>
        entries(
            String prefix,
            int count
        )
    {
        ArrayList<EventChestService.DisplayEntry>
            entries=
                new ArrayList<>();

        for(int i=0;i<count;i++)
            entries.add(
                new EventChestService
                    .DisplayEntry(
                        prefix+i,
                        i+1L
                    )
            );

        return entries;
    }

    private static void immutability(
        EventChestService.Snapshot snapshot
    ){
        expect(
            UnsupportedOperationException.class,
            ()->snapshot.projection
                .mainEntries.clear(),
            "Event Chest main entries mutable"
        );

        expect(
            UnsupportedOperationException.class,
            ()->snapshot.projection
                .smallGrids.clear(),
            "Event Chest small-grid list mutable"
        );

        expect(
            UnsupportedOperationException.class,
            ()->snapshot.projection
                .smallGrids.get(0)
                .clear(),
            "Event Chest small-grid entries mutable"
        );
    }

    private static void protocolBoundary(){
        for(Class<?> type:new Class<?>[]{
                EventChestService.class,
                EventChestService
                    .Projection.class,
                EventChestService
                    .DisplayEntry.class,
                EventChestService
                    .Snapshot.class
        }){
            for(Field field:
                    type.getDeclaredFields()){
                String name=
                    field.getName()
                        .toLowerCase(
                            Locale.ROOT
                        );

                if(name.contains("widget")||
                   name.contains("opcode")||
                   name.contains("packet")||
                   name.contains("root")||
                   name.contains("itemid")||
                   name.contains("containerid"))
                    throw new AssertionError(
                        "protocol identity leaked into Event Chest "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );
            }
        }
    }

    private static void mechanicsBoundary(){
        for(Field field:
                EventChestService.class
                    .getDeclaredFields()){
            String name=
                field.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            if(name.contains("chance")||
               name.contains("rng")||
               name.contains("tokenprice")||
               name.contains("rollrequirement")||
               name.contains("rewardtable"))
                throw new AssertionError(
                    "unrecovered Event Chest mechanic leaked into field "+
                    field.getName()
                );
        }

        for(Method method:
                EventChestService.class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            if(name.contains("rollreward")||
               name.contains("calculateprize")||
               name.contains("consume")||
               name.contains("persist"))
                throw new AssertionError(
                    "unrecovered Event Chest mechanic leaked into method "+
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

    private EventChestServiceTest(){}
}
