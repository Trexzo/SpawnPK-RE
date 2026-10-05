package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Live session bridge from semantic teleport navigation into existing LocalLab
 * region relocation. One runtime instance retains the navigation request ledger
 * for the session while each invocation supplies its exact writer/scene context.
 */
final class LocalTeleportNavigationRuntime {
    static final class Result {
        final TeleportNavigationService.RequestResult navigation;
        final LocalRegionDevCommandHandler.Result relocation;

        Result(
            TeleportNavigationService.RequestResult navigation,
            LocalRegionDevCommandHandler.Result relocation
        ){
            this.navigation=Objects.requireNonNull(
                navigation,
                "navigation"
            );
            this.relocation=relocation;
        }

        boolean succeeded(){
            return navigation.executedSuccessfully;
        }
    }

    private static final class Invocation {
        final SceneUpdatePublisher scenePublisher;
        final ServerPacketWriter writer;
        LocalRegionDevCommandHandler.Result relocation;

        Invocation(
            SceneUpdatePublisher scenePublisher,
            ServerPacketWriter writer
        ){
            this.scenePublisher=Objects.requireNonNull(
                scenePublisher,
                "scenePublisher"
            );
            this.writer=Objects.requireNonNull(
                writer,
                "writer"
            );
        }
    }

    private final LocalRegionDevCommandHandler regionRelocation;
    private final TeleportNavigationService navigation;
    private Invocation invocation;

    LocalTeleportNavigationRuntime(
        LocalRegionDevCommandHandler regionRelocation
    ){
        this.regionRelocation=
            Objects.requireNonNull(
                regionRelocation,
                "regionRelocation"
            );

        this.navigation=
            new TeleportNavigationService(
                this::execute,
                LocalTeleportDestinationCatalog.POLICY_AUTHORITY
            );
    }

    synchronized Result request(
        String playerRef,
        TeleportNavigationService.EntryKind kind,
        SceneUpdatePublisher scenePublisher,
        ServerPacketWriter writer
    )throws IOException{
        Objects.requireNonNull(kind,"kind");

        if(kind==TeleportNavigationService.EntryKind.HOME)
            throw new IllegalArgumentException(
                "HOME remains owned by validated magic-home runtime"
            );

        if(invocation!=null)
            throw new IllegalStateException(
                "teleport navigation invocation already active"
            );

        Invocation active=
            new Invocation(
                scenePublisher,
                writer
            );
        invocation=active;

        try{
            TeleportNavigationService.RequestResult result=
                navigation.request(
                    playerRef,
                    kind
                );

            return new Result(
                result,
                active.relocation
            );
        }finally{
            invocation=null;
        }
    }

    synchronized TeleportNavigationService.PlayerSnapshot snapshot(
        String playerRef
    ){
        return navigation.snapshot(
            playerRef
        );
    }

    private TeleportNavigationService.ExecutionResult execute(
        String playerRef,
        TeleportNavigationService.EntryKind kind,
        String policyAuthority
    ){
        if(!LocalTeleportDestinationCatalog.POLICY_AUTHORITY.equals(
                policyAuthority))
            return TeleportNavigationService.ExecutionResult.failure(
                "policy authority mismatch"
            );

        if(kind==TeleportNavigationService.EntryKind.HOUSE)
            return TeleportNavigationService.ExecutionResult.failure(
                "HOUSE_UNCONFIGURED house instance entrypoint not live"
            );

        LocalTeleportDestinationCatalog.Destination destination=
            LocalTeleportDestinationCatalog.get(
                kind
            );

        if(destination==null)
            return TeleportNavigationService.ExecutionResult.failure(
                "UNCONFIGURED_LOCAL_DESTINATION kind="+kind
            );

        Invocation active=invocation;
        if(active==null)
            return TeleportNavigationService.ExecutionResult.failure(
                "NO_LIVE_SESSION_CONTEXT"
            );

        try{
            LocalRegionDevCommandHandler.Result relocation=
                regionRelocation.enterForPanel(
                    destination.regionId,
                    destination.plane,
                    active.scenePublisher,
                    active.writer
                );

            active.relocation=relocation;

            if(relocation==null||
               relocation.saveReason==null||
               relocation.logText==null||
               !relocation.logText.startsWith(
                    "V5160_REGION_LOAD OK "))
                return TeleportNavigationService.ExecutionResult.failure(
                    relocation==null
                        ?"REGION_RELOCATION_NO_RESULT"
                        :relocation.detailText
                );

            return TeleportNavigationService.ExecutionResult.success(
                "kind="+kind+
                " region="+destination.regionId+
                " name=["+destination.expectedName+"]"+
                " group=["+destination.expectedGroup+"]"+
                " arrival=COLLISION_SAFE_LOCAL_LAB"
            );
        }catch(IOException failure){
            throw new TeleportIoFailure(
                failure
            );
        }
    }

    static IOException unwrapIo(
        RuntimeException failure
    ){
        if(failure instanceof TeleportIoFailure)
            return ((TeleportIoFailure)failure).cause;
        return null;
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
