package spk.local;

import java.lang.reflect.Field;
import java.util.*;

public final class UsageQuotaFoundationTest {
    public static void main(String[] args){
        assertLifecycle();
        assertFailClosedBoundaries();
        assertNoProtocolIdentity();

        System.out.println(
            "USAGE_QUOTA_FOUNDATION_PASS "+
            "semanticKeys=true "+
            "explicitWindows=true "+
            "atomicConsume=true "+
            "overLimitFailClosed=true "+
            "externalTimestampUninterpreted=true "+
            "explicitResetOnly=true "+
            "snapshotImmutable=true "+
            "protocolIdentityInState=false"
        );
    }

    private static void assertLifecycle(){
        UsageQuotaService service=
            new UsageQuotaService();

        UsageQuotaDefinition tokens=
            new UsageQuotaDefinition(
                "event:token_limit",
                10,
                "CUSTOM_LOCALLAB"
            );

        UsageQuotaDefinition claims=
            new UsageQuotaDefinition(
                "daily:claim_count",
                2,
                "CUSTOM_LOCALLAB"
            );

        service.define(tokens);
        service.define(claims);

        if(service.definitionCount()!=2)
            throw new AssertionError(
                "definitionCount="+
                service.definitionCount()
            );

        UsageQuotaService.Snapshot opened=
            service.openWindow(
                "EVENT:TOKEN_LIMIT",
                "season-42/day-7",
                3,
                123456789L
            );

        if(!"event:token_limit".equals(
                opened.key)||
           !"season-42/day-7".equals(
                opened.windowKey)||
           opened.used!=3||
           opened.limit!=10||
           opened.remaining!=7||
           !opened.hasExternalTimestamp()||
           opened.externalTimestamp!=123456789L)
            throw new AssertionError(
                "opened="+opened
            );

        UsageQuotaService.ConsumeResult accepted=
            service.tryConsume(
                "event:token_limit",
                4
            );

        if(!accepted.accepted||
           accepted.before.used!=3||
           accepted.after.used!=7||
           accepted.after.remaining!=3)
            throw new AssertionError(
                "accepted consume after="+
                accepted.after
            );

        UsageQuotaService.ConsumeResult rejected=
            service.tryConsume(
                "event:token_limit",
                4
            );

        if(rejected.accepted||
           rejected.before.used!=7||
           rejected.after.used!=7||
           rejected.after.remaining!=3)
            throw new AssertionError(
                "over-limit mutation="+
                rejected.after
            );

        UsageQuotaService.ConsumeResult exact=
            service.tryConsume(
                "event:token_limit",
                3
            );

        if(!exact.accepted||
           exact.after.used!=10||
           exact.after.remaining!=0||
           !exact.after.exhausted())
            throw new AssertionError(
                "exact exhaustion="+
                exact.after
            );

        UsageQuotaService.Snapshot nextWindow=
            service.openWindow(
                "event:token_limit",
                "season-42/day-8",
                0
            );

        if(nextWindow.used!=0||
           nextWindow.remaining!=10||
           nextWindow.hasExternalTimestamp())
            throw new AssertionError(
                "explicit reset="+
                nextWindow
            );

        service.openWindow(
            "daily:claim_count",
            "2026-09-20",
            1
        );

        if(service.snapshot().size()!=2)
            throw new AssertionError(
                "independent windows missing"
            );

        if(!service.closeWindow(
                "daily:claim_count"))
            throw new AssertionError(
                "closeWindow first=false"
            );

        if(service.closeWindow(
                "daily:claim_count"))
            throw new AssertionError(
                "closeWindow second=true"
            );

        if(service.get(
                "daily:claim_count")!=null)
            throw new AssertionError(
                "closed window still visible"
            );

        List<UsageQuotaService.Snapshot>
            snapshots=
                service.snapshot();

        boolean immutable=false;

        try{
            snapshots.clear();
        }catch(UnsupportedOperationException expected){
            immutable=true;
        }

        if(!immutable)
            throw new AssertionError(
                "quota snapshots mutable"
            );
    }

    private static void assertFailClosedBoundaries(){
        UsageQuotaService service=
            new UsageQuotaService();

        UsageQuotaDefinition quota=
            new UsageQuotaDefinition(
                "test:quota",
                5,
                "CUSTOM_LOCALLAB"
            );

        service.define(quota);

        service.define(
            new UsageQuotaDefinition(
                "TEST:QUOTA",
                5,
                "CUSTOM_LOCALLAB"
            )
        );

        expectIllegalState(
            ()->service.define(
                new UsageQuotaDefinition(
                    "test:quota",
                    6,
                    "CUSTOM_LOCALLAB"
                )
            )
        );

        expectIllegalState(
            ()->service.tryConsume(
                "test:quota",
                1
            )
        );

        expectIllegalArgument(
            ()->service.openWindow(
                "test:quota",
                "window",
                6
            )
        );

        service.openWindow(
            "test:quota",
            "window",
            0
        );

        expectIllegalArgument(
            ()->service.tryConsume(
                "test:quota",
                0
            )
        );

        expectIllegalArgument(
            ()->service.tryConsume(
                "test:quota",
                -1
            )
        );

        UsageQuotaService.ConsumeResult huge=
            service.tryConsume(
                "test:quota",
                Long.MAX_VALUE
            );

        if(huge.accepted||
           huge.after.used!=0)
            throw new AssertionError(
                "overflow-scale request mutated quota"
            );

        expectIllegalArgument(
            ()->new UsageQuotaDefinition(
                "bad key!",
                1,
                "CUSTOM_LOCALLAB"
            )
        );

        expectIllegalArgument(
            ()->new UsageQuotaDefinition(
                "valid:key",
                0,
                "CUSTOM_LOCALLAB"
            )
        );
    }

    private static void assertNoProtocolIdentity(){
        for(Class<?> type:
                new Class<?>[]{
                    UsageQuotaDefinition.class,
                    UsageQuotaService.Snapshot.class
                }){
            for(Field field:
                    type.getDeclaredFields()){
                String name=
                    field.getName()
                        .toLowerCase(
                            Locale.ROOT
                        );

                if(name.contains("widget")||
                   name.contains("packet")||
                   name.contains("opcode")||
                   name.contains("subtype"))
                    throw new AssertionError(
                        "protocol identity leaked into "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );
            }
        }
    }

    private static void expectIllegalArgument(
        Runnable action
    ){
        boolean failed=false;

        try{
            action.run();
        }catch(IllegalArgumentException expected){
            failed=true;
        }

        if(!failed)
            throw new AssertionError(
                "expected IllegalArgumentException"
            );
    }

    private static void expectIllegalState(
        Runnable action
    ){
        boolean failed=false;

        try{
            action.run();
        }catch(IllegalStateException expected){
            failed=true;
        }

        if(!failed)
            throw new AssertionError(
                "expected IllegalStateException"
            );
    }
}
