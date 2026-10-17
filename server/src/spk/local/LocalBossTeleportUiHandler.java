package spk.local;

import java.io.IOException;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Live LocalLab composition for the exact-v308 Boss Teleport interface.
 *
 * Exact client widget/root authority remains in BossTeleportPresentation.
 * Row->boss policy and destination ownership are explicitly LocalLab-owned.
 */
final class LocalBossTeleportUiHandler {
    static final String POLICY_AUTHORITY=
        "LOCAL_LAB_POLICY_G3_BOSS_TELEPORT_V1";
    static final String BOSS_KEY=
        "boss:locallab-vetions-rest";
    static final String TARGET_KEY=
        "teleport:locallab-boss";
    static final int CONFIGURED_ROW=0;
    static final int REGION_ID=16168;

    @FunctionalInterface
    interface LiveTeleport {
        boolean execute(
            ServerPacketWriter writer,
            String tag
        )throws IOException;
    }

    enum Status {
        OPENED,
        SELECTED,
        TELEPORTED,
        TELEPORT_FAILED,
        MISSING_SELECTION,
        UNCONFIGURED_ROW,
        DROP_TABLE_UNAVAILABLE,
        CLOSED_UI_NOOP
    }

    static final class Result {
        final Status status;
        final int rowIndex;
        final boolean teleportSucceeded;
        final String detail;

        Result(
            Status status,
            int rowIndex,
            boolean teleportSucceeded,
            String detail
        ){
            this.status=Objects.requireNonNull(
                status,
                "status"
            );
            this.rowIndex=rowIndex;
            this.teleportSucceeded=teleportSucceeded;
            this.detail=
                detail==null
                    ?""
                    :detail;
        }
    }

    private static final class Invocation {
        final ServerPacketWriter writer;
        final String tag;

        Invocation(
            ServerPacketWriter writer,
            String tag
        ){
            this.writer=Objects.requireNonNull(
                writer,
                "writer"
            );
            this.tag=
                tag==null
                    ?""
                    :tag;
        }
    }

    private final Supplier<String> playerRef;
    private final LiveTeleport liveTeleport;
    private final BossTeleportService service;
    private boolean open;
    private Invocation invocation;

    LocalBossTeleportUiHandler(
        Supplier<String> playerRef,
        LiveTeleport liveTeleport
    ){
        this.playerRef=Objects.requireNonNull(
            playerRef,
            "playerRef"
        );
        this.liveTeleport=Objects.requireNonNull(
            liveTeleport,
            "liveTeleport"
        );

        this.service=
            new BossTeleportService(
                (player,entry)->
                    BossTeleportService
                        .EligibilityDecision
                        .allow(),
                this::execute
            );

        this.service.replaceCatalog(
            Collections.singletonList(
                new BossTeleportService.Entry(
                    BOSS_KEY,
                    "Vetion's Rest",
                    "LocalLab boss destination - recovered region 16168",
                    TARGET_KEY,
                    null,
                    Collections.emptyList(),
                    POLICY_AUTHORITY
                )
            )
        );
    }

    Result open(
        ServerPacketWriter writer
    )throws IOException{
        BossTeleportPresentation.open(
            Objects.requireNonNull(
                writer,
                "writer"
            )
        );

        BossTeleportService.PlayerSnapshot current=
            service.getPlayer(
                currentPlayer()
            );

        if(current!=null&&current.hasSelection())
            service.clearSelection(
                current.playerRef
            );

        open=true;

        return new Result(
            Status.OPENED,
            -1,
            false,
            "root="+
                BossTeleportPresentation.ROOT+
                " rows="+
                BossTeleportPresentation.MAX_ROWS+
                " configuredRow="+
                CONFIGURED_ROW+
                " policy="+
                POLICY_AUTHORITY
        );
    }

    boolean ownsWidget(
        int widget
    ){
        return BossTeleportPresentation
            .resolveWidget(widget)!=null;
    }

    Result handleWidget(
        int widget,
        ServerPacketWriter writer,
        String tag
    )throws IOException{
        BossTeleportPresentation.Input input=
            BossTeleportPresentation
                .resolveWidget(widget);

        if(input==null)
            return null;

        if(!open)
            return new Result(
                Status.CLOSED_UI_NOOP,
                input.rowIndex,
                false,
                "boss teleport root not open"
            );

        switch(input.kind){
            case SELECT_ROW:
                if(input.rowIndex!=CONFIGURED_ROW)
                    return new Result(
                        Status.UNCONFIGURED_ROW,
                        input.rowIndex,
                        false,
                        "row mapping unconfigured authority="+
                            POLICY_AUTHORITY
                    );

                BossTeleportService.Entry entry=
                    service.getBoss(
                        BOSS_KEY
                    );

                /*
                 * Publish before mutating selection so transport failure cannot
                 * leave hidden semantic selection authority.
                 */
                BossTeleportPresentation
                    .publishSelectionText(
                        Objects.requireNonNull(
                            writer,
                            "writer"
                        ),
                        entry
                    );

                service.selectBoss(
                    currentPlayer(),
                    BOSS_KEY
                );

                return new Result(
                    Status.SELECTED,
                    input.rowIndex,
                    false,
                    "bossKey="+
                        BOSS_KEY+
                        " region="+
                        REGION_ID+
                        " originalRowMappingClaim=false"
                );

            case VIEW_FULL_DROP_TABLE:
                return new Result(
                    Status.DROP_TABLE_UNAVAILABLE,
                    -1,
                    false,
                    "drop authority unavailable"
                );

            case TELEPORT:
                BossTeleportService.PlayerSnapshot selected=
                    service.getPlayer(
                        currentPlayer()
                    );

                if(selected==null||
                   !selected.hasSelection())
                    return new Result(
                        Status.MISSING_SELECTION,
                        -1,
                        false,
                        "select configured row first"
                    );

                if(invocation!=null)
                    throw new IllegalStateException(
                        "boss teleport invocation already active"
                    );

                Invocation active=
                    new Invocation(
                        writer,
                        tag
                    );
                invocation=active;

                final BossTeleportService.TeleportRequestResult result;

                try{
                    try{
                        result=
                            service.requestTeleport(
                                currentPlayer()
                            );
                    }catch(TeleportIoFailure failure){
                        throw failure.cause;
                    }
                }finally{
                    invocation=null;
                }

                if(!result.executedSuccessfully)
                    return new Result(
                        Status.TELEPORT_FAILED,
                        -1,
                        false,
                        result.detail
                    );

                /*
                 * Successful region relocation retires this root's ownership.
                 * Any stale row/teleport packets therefore fail closed.
                 */
                open=false;

                return new Result(
                    Status.TELEPORTED,
                    -1,
                    true,
                    "region="+
                        REGION_ID+
                        " successfulRequests="+
                        result.player.successfulTeleports
                );

            default:
                throw new IllegalStateException(
                    "Unhandled Boss Teleport input "+input.kind
                );
        }
    }

    boolean close(){
        boolean wasOpen=open;
        open=false;
        invocation=null;

        BossTeleportService.PlayerSnapshot current=
            service.getPlayer(
                currentPlayer()
            );

        if(current!=null&&current.hasSelection())
            service.clearSelection(
                current.playerRef
            );

        return wasOpen;
    }

    boolean isOpen(){
        return open;
    }

    BossTeleportService.PlayerSnapshot snapshot(){
        return service.getPlayer(
            currentPlayer()
        );
    }

    BossTeleportService.Snapshot serviceSnapshot(){
        return service.snapshot();
    }

    private BossTeleportService.ExecutionResult execute(
        String player,
        String target,
        String authority
    ){
        if(!TARGET_KEY.equals(target))
            return BossTeleportService
                .ExecutionResult
                .failure(
                    "target authority mismatch "+target
                );

        if(!POLICY_AUTHORITY.equals(authority))
            return BossTeleportService
                .ExecutionResult
                .failure(
                    "policy authority mismatch "+authority
                );

        Invocation active=invocation;

        if(active==null)
            return BossTeleportService
                .ExecutionResult
                .failure(
                    "NO_LIVE_SESSION_CONTEXT"
                );

        try{
            return liveTeleport.execute(
                    active.writer,
                    active.tag
                )
                ?BossTeleportService
                    .ExecutionResult
                    .success()
                :BossTeleportService
                    .ExecutionResult
                    .failure(
                        "live BOSS navigation failed"
                    );
        }catch(IOException failure){
            throw new TeleportIoFailure(
                failure
            );
        }
    }

    private String currentPlayer(){
        String value=
            Objects.requireNonNull(
                playerRef.get(),
                "playerRef value"
            ).trim();

        if(value.isEmpty())
            throw new IllegalStateException(
                "playerRef blank"
            );

        return value;
    }

    private static final class TeleportIoFailure
        extends RuntimeException
    {
        final IOException cause;

        TeleportIoFailure(
            IOException cause
        ){
            super(
                Objects.requireNonNull(
                    cause,
                    "cause"
                )
            );
            this.cause=cause;
        }
    }
}
