package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Collections;

/**
 * P5.16: the genuine Quick Prayer semantic selection/presentation
 * cannot toggle or confirm under a detached/old World generation.
 */
public final class PlayableQuickPrayerSelectionOwnershipTest {
    private PlayableQuickPrayerSelectionOwnershipTest(){}

    private static void require(boolean ok,String message){
        if(!ok)throw new AssertionError(
            "P516_SELECTION_OWNER "+message);
    }

    private static ServerPacketWriter writer(){
        return new ServerPacketWriter(
            new ByteArrayOutputStream(),
            new IsaacCipher(new int[]{241,242,243,244})
        );
    }

    public static void main(String[] args)throws Exception{
        require(args.length==0,"unexpected CLI arguments");
        World world=World.isolatedForTest(60_000L);
        WorldPlayer owner=new WorldPlayer();
        long first=world.registerPlayer(owner,"p516-selector");
        long current=first;
        try{
            QuickPrayerSelectionService selection=
                new QuickPrayerSelectionService(
                    owner,"LOCAL_LAB_POLICY_P516_EDITOR_WORLD");
            QuickPrayerSelectionPresentation presentation=
                new QuickPrayerSelectionPresentation(selection);
            LocalQuickPrayerSelectionWorldGate old=
                new LocalQuickPrayerSelectionWorldGate(
                    world,owner,()->first,presentation);
            presentation.open(
                QuickPrayerSelectionService.Book.NORMAL,writer());

            String toggle=old.handle(
                QuickPrayerSelectionPresentation.FIRST_SELECTOR);
            QuickPrayerSelectionService.Snapshot editing=
                selection.snapshot();
            require(toggle.contains("QUICK_SELECTION_TOGGLE")&&
                editing.editing&&
                editing.revision(
                    QuickPrayerSelectionService.Book.NORMAL)==0L&&
                editing.draftSelection.equals(
                    Collections.singletonList("normal:thick_skin")),
                "first generation selected wrong draft");

            require(world.unregisterPlayer(owner,first),
                "failed first generation unregister");
            String rejectedToggle=old.handle(
                QuickPrayerSelectionPresentation.FIRST_SELECTOR);
            String rejectedConfirm=old.handle(
                QuickPrayerSelectionPresentation.CONFIRM_WIDGET);
            QuickPrayerSelectionService.Snapshot unchanged=
                selection.snapshot();
            require("REJECTED_STALE_WORLD_OWNER".equals(
                    rejectedToggle)&&
                "REJECTED_STALE_WORLD_OWNER".equals(
                    rejectedConfirm)&&
                unchanged.editing&&
                unchanged.revision(
                    QuickPrayerSelectionService.Book.NORMAL)==0L&&
                unchanged.draftSelection.equals(
                    Collections.singletonList("normal:thick_skin"))&&
                unchanged.confirmed(
                    QuickPrayerSelectionService.Book.NORMAL).isEmpty(),
                "detached World changed draft or confirmed selection");

            long second=world.registerPlayer(owner,"p516-selector");
            current=second;
            require(second!=first&&
                world.players().owns(owner,second),
                "same-player ABA generation was not rotated");
            require("REJECTED_STALE_WORLD_OWNER".equals(
                    old.handle(
                        QuickPrayerSelectionPresentation.CONFIRM_WIDGET))&&
                selection.snapshot().editing,
                "stale generation confirmed after ABA");

            LocalQuickPrayerSelectionWorldGate fresh=
                new LocalQuickPrayerSelectionWorldGate(
                    world,owner,()->second,presentation);
            String confirmed=fresh.handle(
                QuickPrayerSelectionPresentation.CONFIRM_WIDGET);
            QuickPrayerSelectionService.Snapshot committed=
                selection.snapshot();
            require(confirmed.contains("QUICK_SELECTION_CONFIRM")&&
                !committed.editing&&
                committed.revision(
                    QuickPrayerSelectionService.Book.NORMAL)==1L&&
                committed.confirmed(
                    QuickPrayerSelectionService.Book.NORMAL)
                    .equals(Collections.singletonList(
                        "normal:thick_skin")),
                "fresh generation failed to commit draft once");

            // Open a second edit while the current owner still exists,
            // then close the World. Neither toggle nor Confirm may commit.
            presentation.open(
                QuickPrayerSelectionService.Book.NORMAL,writer());
            long beforeClose=selection.snapshot().revision(
                QuickPrayerSelectionService.Book.NORMAL);
            require(world.unregisterPlayer(owner,second),
                "failed to unregister new generation");
            world.close();
            String closedToggle=fresh.handle(
                QuickPrayerSelectionPresentation.FIRST_SELECTOR);
            String closedConfirm=fresh.handle(
                QuickPrayerSelectionPresentation.CONFIRM_WIDGET);
            QuickPrayerSelectionService.Snapshot blocked=
                selection.snapshot();
            require("REJECTED_STALE_WORLD_OWNER".equals(
                    closedToggle)&&
                "REJECTED_STALE_WORLD_OWNER".equals(
                    closedConfirm)&&
                blocked.editing&&
                blocked.revision(
                    QuickPrayerSelectionService.Book.NORMAL)==beforeClose&&
                blocked.confirmed(
                    QuickPrayerSelectionService.Book.NORMAL)
                    .equals(Collections.singletonList(
                        "normal:thick_skin")),
                "closed World revised saved selection");
            require(presentation.close(),
                "test cleanup: editor should still be open");

            System.out.println(
                "P516_QUICK_SELECTION_WORLD_OWNER_PASS"+
                " liveToggleDraft=true"+
                " detachedToggleAndConfirmDenied=true"+
                " samePlayerABAOldGenerationDenied=true"+
                " newGenerationConfirmOnce=true"+
                " closedWorldCannotConfirm=true"+
                " editorRetainedOnFailedConfirm=true"+
                " productionBridgeUsesWorldGate=true"+
                " nativeV308AndCrossJvmLeaseUnverified=true");
        }finally{
            if(!world.closed()){
                if(world.players().owns(owner,current))
                    world.unregisterPlayer(owner,current);
                world.close();
            }
        }
    }
}
