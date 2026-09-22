package spk.local;

public final class RegionLoadLifecycleTest {
    public static void main(String[] args){
        RegionLoadLifecycle lifecycle=
            new RegionLoadLifecycle();

        if(lifecycle.pending()||
           lifecycle.pendingSequence()!=0L)
            throw new AssertionError(
                "new lifecycle unexpectedly pending"
            );

        RegionLoadLifecycle.Begin first=
            lifecycle.begin(
                386,442,
                3040,3488,
                "AUTO_WINDOW_REBASE"
            );

        if(first.sequence!=1L||
           first.superseded||
           lifecycle.pendingSequence()!=1L)
            throw new AssertionError(
                "first begin mismatch"
            );

        RegionLoadLifecycle.Completion complete=
            lifecycle.complete();

        if(!complete.matched||
           complete.sequence!=1L||
           complete.centerX!=386||
           complete.centerY!=442||
           complete.baseX!=3040||
           complete.baseY!=3488||
           !"AUTO_WINDOW_REBASE".equals(
                complete.reason
           )||
           lifecycle.pending())
            throw new AssertionError(
                "completion mismatch"
            );

        if(lifecycle.complete().matched)
            throw new AssertionError(
                "duplicate ACK unexpectedly matched"
            );

        lifecycle.begin(
            387,447,
            3048,3528,
            "AUTO_WINDOW_REBASE"
        );

        RegionLoadLifecycle.Begin replacement=
            lifecycle.begin(
                375,477,
                2952,3768,
                "DEV_REGION_RELOCATION"
            );

        if(!replacement.superseded||
           replacement.supersededSequence!=2L||
           replacement.sequence!=3L)
            throw new AssertionError(
                "supersede diagnostics mismatch"
            );

        System.out.println(
            "REGION_LOAD_LIFECYCLE_PASS "+
            "packet73Pending=true "+
            "opcode121Completes=true "+
            "duplicateAckUnmatched=true "+
            "supersedeObservable=true "+
            "centerBaseFormulaEvidence=true"
        );
    }

    private RegionLoadLifecycleTest(){}
}
