package spk.local;

import java.lang.reflect.Field;
import java.util.*;

public final class ObjectiveProgressFoundationTest {
    public static void main(String[] args){
        assertRecoveredBootstrap();
        assertSemanticProgression();
        assertResetRollback();
        assertNoProtocolIdentity();

        System.out.println(
            "OBJECTIVE_PROGRESS_FOUNDATION_PASS "+
            "chapterBootstrap=9 "+
            "semanticKeys=true "+
            "goalClamp=true "+
            "completionTransition=true "+
            "claimBookkeeping=true "+
            "resetRollback=true "+
            "compareAndRestore=true "+
            "claimedRestore=true "+
            "duplicateConflictFailClosed=true "+
            "snapshotImmutable=true "+
            "protocolIdentityInState=false"
        );
    }

    private static void assertRecoveredBootstrap(){
        if(AchievementChapterBootstrapCatalog
                .size()!=9)
            throw new AssertionError(
                "chapter rows="+
                AchievementChapterBootstrapCatalog
                    .size()
            );

        AchievementChapterBootstrapCatalog.Row
            first=
                AchievementChapterBootstrapCatalog
                    .get(0);

        if(first.index!=0||
           !"ITEM".equals(
               first.renderType)||
           first.renderId!=1464||
           first.initialProgress!=0||
           first.goal!=1||
           first.claimed||
           !first.taskText.contains(
               "Vote for SPK"))
            throw new AssertionError(
                "first bootstrap row="+
                first
            );

        AchievementChapterBootstrapCatalog.Row
            vintage=
                AchievementChapterBootstrapCatalog
                    .get(4);

        if(vintage.renderId!=24263||
           vintage.goal!=50)
            throw new AssertionError(
                "vintage row="+
                vintage
            );

        AchievementChapterBootstrapCatalog.Row
            revenants=
                AchievementChapterBootstrapCatalog
                    .get(6);

        if(revenants.renderId!=23912||
           revenants.goal!=10||
           !revenants.taskText.contains(
               "{prog}"))
            throw new AssertionError(
                "revenant row="+
                revenants
            );

        boolean immutable=false;

        try{
            AchievementChapterBootstrapCatalog
                .all()
                .clear();
        }catch(UnsupportedOperationException expected){
            immutable=true;
        }

        if(!immutable)
            throw new AssertionError(
                "chapter bootstrap list mutable"
            );

        if(!AchievementChapterBootstrapCatalog
                .PRESENTATION_AUTHORITY
                .equals(
                    first.presentationAuthority))
            throw new AssertionError(
                "presentation authority="+
                first.presentationAuthority
            );
    }

    private static void assertSemanticProgression(){
        ObjectiveProgressService service=
            new ObjectiveProgressService();

        ObjectiveDefinition votes=
            new ObjectiveDefinition(
                "chapter:vote",
                1,
                "CUSTOM_LOCALLAB"
            );

        ObjectiveDefinition revenants=
            new ObjectiveDefinition(
                "chapter:blood_revenants",
                10,
                "CUSTOM_LOCALLAB"
            );

        service.define(votes);
        service.define(revenants);

        if(service.size()!=2)
            throw new AssertionError(
                "size="+service.size()
            );

        ObjectiveProgressService.ProgressResult
            progress=
                service.advance(
                    "chapter:blood_revenants",
                    4
                );

        if(progress.before.progress!=0||
           progress.after.progress!=4||
           progress.completedNow)
            throw new AssertionError(
                "first progress="+
                progress.after
            );

        boolean earlyClaimRejected=false;

        try{
            service.markClaimed(
                "chapter:blood_revenants"
            );
        }catch(IllegalStateException expected){
            earlyClaimRejected=true;
        }

        if(!earlyClaimRejected)
            throw new AssertionError(
                "incomplete objective claim accepted"
            );

        ObjectiveProgressService.ProgressResult
            completed=
                service.advance(
                    "chapter:blood_revenants",
                    Long.MAX_VALUE
                );

        if(completed.before.progress!=4||
           completed.after.progress!=10||
           !completed.after.complete||
           !completed.completedNow)
            throw new AssertionError(
                "completion="+
                completed.after
            );

        ObjectiveProgressService.ProgressResult
            afterComplete=
                service.advance(
                    "chapter:blood_revenants",
                    1
                );

        if(afterComplete.after.progress!=10||
           afterComplete.completedNow)
            throw new AssertionError(
                "completed objective changed="+
                afterComplete.after
            );

        if(!service.markClaimed(
                "chapter:blood_revenants"))
            throw new AssertionError(
                "first claim mark failed"
            );

        if(service.markClaimed(
                "chapter:blood_revenants"))
            throw new AssertionError(
                "claim mark not idempotent"
            );

        ObjectiveProgressService.Snapshot
            claimed=
                service.get(
                    "CHAPTER:BLOOD_REVENANTS"
                );

        if(!claimed.claimed||
           !claimed.complete||
           claimed.progress!=10||
           !"CUSTOM_LOCALLAB".equals(
               claimed.sourceAuthority))
            throw new AssertionError(
                "claimed snapshot="+
                claimed
            );

        service.advance(
            "chapter:vote",
            1
        );

        if(!service.get(
                "chapter:vote")
                .complete)
            throw new AssertionError(
                "independent objective did not complete"
            );

        service.define(
            new ObjectiveDefinition(
                "chapter:vote",
                1,
                "CUSTOM_LOCALLAB"
            ),
            1,
            false
        );

        boolean conflictRejected=false;

        try{
            service.define(
                new ObjectiveDefinition(
                    "chapter:vote",
                    2,
                    "CUSTOM_LOCALLAB"
                )
            );
        }catch(IllegalStateException expected){
            conflictRejected=true;
        }

        if(!conflictRejected)
            throw new AssertionError(
                "conflicting duplicate definition accepted"
            );

        boolean invalidAdvance=false;

        try{
            service.advance(
                "chapter:vote",
                0
            );
        }catch(IllegalArgumentException expected){
            invalidAdvance=true;
        }

        if(!invalidAdvance)
            throw new AssertionError(
                "zero progress accepted"
            );

        boolean unknownRejected=false;

        try{
            service.advance(
                "missing:objective",
                1
            );
        }catch(IllegalArgumentException expected){
            unknownRejected=true;
        }

        if(!unknownRejected)
            throw new AssertionError(
                "unknown objective accepted"
            );

        List<ObjectiveProgressService.Snapshot>
            snapshot=
                service.snapshot();

        boolean immutable=false;

        try{
            snapshot.clear();
        }catch(UnsupportedOperationException expected){
            immutable=true;
        }

        if(!immutable)
            throw new AssertionError(
                "objective snapshot mutable"
            );
    }

    private static void assertResetRollback(){
        ObjectiveProgressService service=
            new ObjectiveProgressService();
        ObjectiveDefinition definition=
            new ObjectiveDefinition(
                "repeatable:test",
                2L,
                "CUSTOM_LOCALLAB"
            );

        service.define(
            definition,
            2L,
            true
        );

        ObjectiveProgressService.Snapshot before=
            service.get(
                "repeatable:test"
            );
        ObjectiveProgressService.Snapshot reset=
            service.resetCompleted(
                "repeatable:test"
            );

        if(reset.complete||
           reset.progress!=0L||
           reset.claimed)
            throw new AssertionError(
                "reset postimage="+reset
            );

        ObjectiveProgressService.Snapshot restored=
            service.restoreIfUnchanged(
                reset,
                before
            );

        if(!restored.complete||
           restored.progress!=2L||
           !restored.claimed)
            throw new AssertionError(
                "completed objective restore="+
                restored
            );

        ObjectiveProgressService.Snapshot secondReset=
            service.resetCompleted(
                "repeatable:test"
            );
        service.advance(
            "repeatable:test",
            1L
        );

        boolean changedRejected=false;

        try{
            service.restoreIfUnchanged(
                secondReset,
                before
            );
        }catch(IllegalStateException expected){
            changedRejected=true;
        }

        ObjectiveProgressService.Snapshot changed=
            service.get(
                "repeatable:test"
            );

        if(!changedRejected||
           changed.progress!=1L||
           changed.complete||
           changed.claimed)
            throw new AssertionError(
                "compare-and-restore overwrote changed objective "+
                changed
            );
    }

    private static void assertNoProtocolIdentity(){
        for(Class<?> type:
                new Class<?>[]{
                    ObjectiveDefinition.class,
                    ObjectiveProgressService
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
                   name.contains("packet")||
                   name.contains("opcode")||
                   name.contains("subtype")||
                   name.contains("renderid"))
                    throw new AssertionError(
                        "protocol/presentation identity leaked into "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );
            }
        }
    }
}
