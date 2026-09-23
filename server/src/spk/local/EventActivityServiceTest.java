package spk.local;

import java.lang.reflect.Field;
import java.util.*;

public final class EventActivityServiceTest {
    private static final String POLICY=
        "LOCAL_LAB_POLICY_EVENT_ACTIVITY";

    public static void main(String[] args){
        UsageQuotaService quotas=
            new UsageQuotaService();

        quotas.define(
            new UsageQuotaDefinition(
                "activity:finite:quota",
                10L,
                POLICY
            )
        );
        quotas.openWindow(
            "activity:finite:quota",
            "window:test",
            3L,
            123456L
        );

        EventActivityService service=
            new EventActivityService(
                quotas
            );

        EventActivityService.Snapshot initial=
            service.replaceRows(
                Arrays.asList(
                    new EventActivityService.RowSpec(
                        "activity:locked",
                        "Locked activity",
                        Collections.singletonList(
                            "Caller policy keeps this locked"
                        ),
                        EventActivityService.Mode.LOCKED,
                        null,
                        0L,
                        30_000L,
                        POLICY
                    ),
                    new EventActivityService.RowSpec(
                        "activity:unlimited",
                        "Unlimited activity",
                        Arrays.asList(
                            "No finite quota",
                            "Usage may still be observed"
                        ),
                        EventActivityService.Mode.UNLIMITED,
                        null,
                        4L,
                        60_000L,
                        POLICY
                    ),
                    new EventActivityService.RowSpec(
                        "activity:finite",
                        "Finite activity",
                        Collections.singletonList(
                            "Backed by UsageQuotaService"
                        ),
                        EventActivityService.Mode.FINITE,
                        "activity:finite:quota",
                        0L,
                        90_000L,
                        POLICY
                    )
                )
            );

        require(
            initial.rows.size()==3&&
            initial.row(0).locked()&&
            initial.row(0).presentationLimit==-1L&&
            initial.row(1).unlimited()&&
            initial.row(1).presentationLimit==0L&&
            initial.row(2).finite()&&
            initial.row(2).presentationLimit==10L,
            "Event Activity mode projection"
        );

        require(
            initial.row(2).currentUsage==3L&&
            "window:test".equals(
                initial.row(2).windowKey
            )&&
            initial.row(2).hasExternalTimestamp()&&
            initial.row(2).externalTimestamp==123456L,
            "finite quota projection"
        );

        require(
            EventActivityService
                .PRESENTATION_AUTHORITY
                .equals(
                    initial.row(2)
                        .presentationAuthority
                )&&
            POLICY.equals(
                initial.row(2)
                    .policyAuthority
            ),
            "Event Activity authority separation"
        );

        EventActivityService.ConsumeResult
            locked=
                service.consume(
                    "activity:locked",
                    1L
                );

        require(
            !locked.accepted&&
            locked.before.currentUsage==0L&&
            locked.after.currentUsage==0L,
            "locked Event Activity consumed"
        );

        EventActivityService.ConsumeResult
            unlimited=
                service.consume(
                    "activity:unlimited",
                    6L
                );

        require(
            unlimited.accepted&&
            unlimited.before.currentUsage==4L&&
            unlimited.after.currentUsage==10L&&
            unlimited.after.presentationLimit==0L,
            "unlimited Event Activity usage"
        );

        EventActivityService.ConsumeResult
            finiteAccepted=
                service.consume(
                    "activity:finite",
                    5L
                );

        require(
            finiteAccepted.accepted&&
            finiteAccepted.before.currentUsage==3L&&
            finiteAccepted.after.currentUsage==8L&&
            quotas.get(
                "activity:finite:quota"
            ).used==8L,
            "finite Event Activity did not delegate quota"
        );

        EventActivityService.ConsumeResult
            finiteRejected=
                service.consume(
                    "activity:finite",
                    3L
                );

        require(
            !finiteRejected.accepted&&
            finiteRejected.before.currentUsage==8L&&
            finiteRejected.after.currentUsage==8L&&
            quotas.get(
                "activity:finite:quota"
            ).used==8L,
            "finite Event Activity over-consume mutated quota"
        );

        expect(
            IllegalArgumentException.class,
            ()->new UsageQuotaDefinition(
                "activity:sentinel:locked",
                -1L,
                POLICY
            ),
            "locked sentinel entered UsageQuotaDefinition"
        );

        expect(
            IllegalArgumentException.class,
            ()->new UsageQuotaDefinition(
                "activity:sentinel:unlimited",
                0L,
                POLICY
            ),
            "unlimited sentinel entered UsageQuotaDefinition"
        );

        failureAtomicConfiguration(
            service,
            quotas
        );

        quotas.closeWindow(
            "activity:finite:quota"
        );

        expect(
            IllegalStateException.class,
            ()->service.activity(
                "activity:finite"
            ),
            "closed finite quota window still projected"
        );

        boolean immutable=false;

        try{
            initial.rows.clear();
        }catch(
            UnsupportedOperationException expected
        ){
            immutable=true;
        }

        require(
            immutable,
            "Event Activity rows mutable"
        );

        protocolBoundary();

        System.out.println(
            "EVENT_ACTIVITY_SERVICE_PASS "+
            "maxRows7=true "+
            "lockedSentinelProjection=true "+
            "unlimitedSentinelProjection=true "+
            "finiteQuotaComposition=true "+
            "finiteQuotaSingleSource=true "+
            "finiteOverconsumeFailClosed=true "+
            "unlimitedUsage=true "+
            "lockedUsageRejected=true "+
            "quotaSentinelsNotDefinitions=true "+
            "timerPresentationOnly=true "+
            "failedReplaceAtomic=true "+
            "closedFiniteWindowFailsClosed=true "+
            "authoritySeparated=true "+
            "rewardMutation=false "+
            "protocolIndependent=true"
        );
    }

    private static void failureAtomicConfiguration(
        EventActivityService service,
        UsageQuotaService quotas
    ){
        ArrayList<EventActivityService.RowSpec>
            tooMany=
                new ArrayList<>();

        for(int i=0;
            i<8;
            i++)
            tooMany.add(
                new EventActivityService.RowSpec(
                    "activity:too-many:"+i,
                    "row "+i,
                    Collections.emptyList(),
                    EventActivityService.Mode.UNLIMITED,
                    null,
                    0L,
                    0L,
                    POLICY
                )
            );

        expect(
            IllegalArgumentException.class,
            ()->service.replaceRows(
                tooMany
            ),
            "more than seven Event Activity rows"
        );

        expect(
            IllegalArgumentException.class,
            ()->service.replaceRows(
                Arrays.asList(
                    new EventActivityService.RowSpec(
                        "activity:duplicate",
                        "one",
                        Collections.emptyList(),
                        EventActivityService.Mode.UNLIMITED,
                        null,
                        0L,
                        0L,
                        POLICY
                    ),
                    new EventActivityService.RowSpec(
                        "ACTIVITY:DUPLICATE",
                        "two",
                        Collections.emptyList(),
                        EventActivityService.Mode.LOCKED,
                        null,
                        0L,
                        0L,
                        POLICY
                    )
                )
            ),
            "duplicate Event Activity key"
        );

        quotas.define(
            new UsageQuotaDefinition(
                "activity:closed:quota",
                5L,
                POLICY
            )
        );

        expect(
            IllegalStateException.class,
            ()->service.replaceRows(
                Collections.singletonList(
                    new EventActivityService.RowSpec(
                        "activity:closed",
                        "closed finite",
                        Collections.emptyList(),
                        EventActivityService.Mode.FINITE,
                        "activity:closed:quota",
                        0L,
                        0L,
                        POLICY
                    )
                )
            ),
            "finite row bound closed quota"
        );

        require(
            service.size()==3&&
            service.activity(
                "activity:locked"
            )!=null&&
            service.activity(
                "activity:finite"
            )!=null,
            "failed Event Activity replacement mutated live rows"
        );
    }

    private static void protocolBoundary(){
        for(Class<?> type:new Class<?>[]{
                EventActivityService.class,
                EventActivityService.RowSpec.class,
                EventActivityService.RowSnapshot.class
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
                   name.contains("subtype")||
                   name.contains("interface")||
                   name.contains("reward")||
                   name.contains("tokenvalue"))
                    throw new AssertionError(
                        "protocol/reward identity leaked into Event Activity "+
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

    private EventActivityServiceTest(){}
}
