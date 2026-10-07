package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Exact-v308 normal Duel selector adapter for the explicit LocalLab G10.1
 * proposal-only policy.
 */
final class LocalDuelUiHandler {
    enum Status {
        OPENED,
        MODE_SELECTED,
        MODE_REQUIRED,
        PROPOSED,
        CLOSED,
        CLOSED_UI_NOOP,
        UNSUPPORTED
    }

    static final class Result {
        final Status status;
        final NormalDuelPresentation.InputKind inputKind;
        final NormalDuelPresentation.DuelMode mode;
        final String targetRef;
        final DuelSessionService.Snapshot proposal;
        final String challengeSuffix;

        Result(
            Status status,
            NormalDuelPresentation.InputKind inputKind,
            NormalDuelPresentation.DuelMode mode,
            String targetRef,
            DuelSessionService.Snapshot proposal,
            String challengeSuffix
        ){
            this.status=Objects.requireNonNull(
                status,
                "status"
            );
            this.inputKind=inputKind;
            this.mode=mode;
            this.targetRef=targetRef;
            this.proposal=proposal;
            this.challengeSuffix=
                challengeSuffix;
        }
    }

    private final WorldPlayer owner;
    private final LocalLabDuelRuntime runtime;

    private boolean open;
    private String targetRef;
    private NormalDuelPresentation.DuelMode mode;

    LocalDuelUiHandler(
        WorldPlayer owner,
        LocalLabDuelRuntime runtime
    ){
        this.owner=Objects.requireNonNull(
            owner,
            "owner"
        );
        this.runtime=Objects.requireNonNull(
            runtime,
            "runtime"
        );
    }

    Result open(
        String challengedRef,
        ServerPacketWriter writer
    )throws IOException{
        String target=
            runtime.requireOnlineTarget(
                owner.username(),
                challengedRef
            );
        ServerPacketWriter packets=
            Objects.requireNonNull(
                writer,
                "writer"
            );

        packets.beginBatch();
        boolean ended=false;

        try{
            NormalDuelPresentation.openSelector(
                packets
            );
            packets.endBatch();
            ended=true;
        }catch(IOException failure){
            if(!ended)
                abortQuietly(packets);
            throw failure;
        }catch(RuntimeException failure){
            if(!ended)
                abortQuietly(packets);
            throw failure;
        }catch(Error failure){
            if(!ended)
                abortQuietly(packets);
            throw failure;
        }

        open=true;
        targetRef=target;
        mode=null;

        return new Result(
            Status.OPENED,
            null,
            null,
            targetRef,
            null,
            null
        );
    }

    Result handle(
        NormalDuelPresentation.Input input
    ){
        NormalDuelPresentation.Input checked=
            Objects.requireNonNull(
                input,
                "input"
            );

        if(!open)
            return new Result(
                Status.CLOSED_UI_NOOP,
                checked.kind,
                mode,
                targetRef,
                null,
                null
            );

        switch(checked.kind){
            case SELECT_MODE:
                mode=Objects.requireNonNull(
                    checked.mode,
                    "mode"
                );
                return new Result(
                    Status.MODE_SELECTED,
                    checked.kind,
                    mode,
                    targetRef,
                    null,
                    NormalDuelPresentation
                        .challengeSuffix(mode)
                );

            case INVITE:
                if(mode==null)
                    return new Result(
                        Status.MODE_REQUIRED,
                        checked.kind,
                        null,
                        targetRef,
                        null,
                        null
                    );

                LocalLabDuelRuntime.ProposalResult proposed=
                    runtime.propose(
                        owner.username(),
                        targetRef,
                        mode
                    );

                Result result=
                    new Result(
                        Status.PROPOSED,
                        checked.kind,
                        proposed.mode,
                        targetRef,
                        proposed.snapshot,
                        proposed.challengeSuffix
                    );

                clear();
                return result;

            case CLOSE:
                clear();
                return new Result(
                    Status.CLOSED,
                    checked.kind,
                    null,
                    null,
                    null,
                    null
                );

            case LOAD_LAST_RULES:
            default:
                return new Result(
                    Status.UNSUPPORTED,
                    checked.kind,
                    mode,
                    targetRef,
                    null,
                    null
                );
        }
    }

    boolean close(){
        boolean wasOpen=open;
        clear();
        return wasOpen;
    }

    boolean isOpen(){
        return open;
    }

    String targetRef(){
        return targetRef;
    }

    NormalDuelPresentation.DuelMode mode(){
        return mode;
    }

    private void clear(){
        open=false;
        targetRef=null;
        mode=null;
    }

    private static void abortQuietly(
        ServerPacketWriter writer
    ){
        try{
            writer.abortBatch();
        }catch(Throwable ignored){}
    }
}
