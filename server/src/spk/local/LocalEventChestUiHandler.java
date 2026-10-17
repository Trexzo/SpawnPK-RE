package spk.local;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

/**
 * Live LocalLab composition for the exact-v308 Event Chest surface.
 *
 * The client proves the presentation and three action identities. This handler
 * intentionally supplies only a truthful empty LocalLab projection and rejects
 * every action until separate gameplay authority exists.
 */
final class LocalEventChestUiHandler {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_EVENT_CHEST_FAIL_CLOSED";

    static final class Result {
        final String status;
        final EventChestService.Action action;
        final boolean succeeded;
        final String detail;

        Result(
            String status,
            EventChestService.Action action,
            boolean succeeded,
            String detail
        ){
            this.status=Objects.requireNonNull(status,"status");
            this.action=Objects.requireNonNull(action,"action");
            this.succeeded=succeeded;
            this.detail=detail==null?"":detail;
        }
    }

    private final WorldPlayer player;
    private final EventChestService service;
    private boolean open;

    LocalEventChestUiHandler(
        WorldPlayer player
    ){
        this.player=
            Objects.requireNonNull(
                player,
                "player"
            );

        this.service=
            new EventChestService(
                (playerRef,action,projection,authority)->
                    EventChestService.ActionResult.failure(
                        action,
                        "LocalLab Event Chest mechanics are not configured"
                    ),
                AtomicTransactionService
                    .SourceAuthority
                    .CUSTOM_LOCALLAB
            );

        this.service.replaceProjection(
            new EventChestService.Projection(
                "LocalLab Event Chest",
                "No event configured",
                "Actions disabled",
                Collections.emptyList(),
                Arrays.asList(
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList()
                ),
                AtomicTransactionService
                    .SourceAuthority
                    .CUSTOM_LOCALLAB
            )
        );
    }

    synchronized EventChestService.Snapshot open(
        ServerPacketWriter packets
    )throws IOException{
        Objects.requireNonNull(
            packets,
            "packets"
        );

        EventChestService.Snapshot snapshot=
            service.snapshot();

        if(!snapshot.configured())
            throw new IllegalStateException(
                "Event Chest LocalLab projection missing"
            );

        EventChestPresentation.open(
            packets
        );
        EventChestPresentation.publishHeadingAndProgress(
            packets,
            snapshot.projection.headingText,
            snapshot.projection.progressText
        );
        EventChestPresentation.publishMainGrid(
            packets,
            emptyIds(
                EventChestPresentation
                    .MAIN_GRID_CAPACITY
            ),
            new int[
                EventChestPresentation
                    .MAIN_GRID_CAPACITY
            ]
        );

        for(int grid=0;grid<
                EventChestService.SMALL_GRID_COUNT;
                grid++)
            EventChestPresentation.publishSmallGrid(
                packets,
                grid,
                emptyIds(
                    EventChestPresentation
                        .SMALL_GRID_CAPACITY
                ),
                new int[
                    EventChestPresentation
                        .SMALL_GRID_CAPACITY
                ]
            );

        open=true;
        return snapshot;
    }

    synchronized Result handle(
        EventChestService.Action action
    ){
        EventChestService.Action checked=
            Objects.requireNonNull(
                action,
                "action"
            );

        if(!open)
            return new Result(
                "CLOSED_UI_NOOP",
                checked,
                false,
                "Event Chest root not owned"
            );

        String playerRef=
            player.username();

        if(playerRef==null||
           playerRef.trim().isEmpty())
            throw new IllegalStateException(
                "Event Chest requires registered WorldPlayer"
            );

        EventChestService.ActionResult result=
            service.requestAction(
                playerRef,
                checked
            );

        return new Result(
            result.succeeded
                ?"HANDLED"
                :"DISABLED_NO_GAMEPLAY_AUTHORITY",
            checked,
            result.succeeded,
            result.detail
        );
    }

    synchronized boolean close(){
        boolean wasOpen=open;
        open=false;
        return wasOpen;
    }

    synchronized boolean isOpen(){
        return open;
    }

    synchronized EventChestService.Snapshot snapshot(){
        return service.snapshot();
    }

    private static int[] emptyIds(
        int slots
    ){
        int[] ids=new int[slots];
        Arrays.fill(ids,-1);
        return ids;
    }
}
