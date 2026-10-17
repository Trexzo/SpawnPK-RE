package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Exact-v308 Tournament root adapter for the explicit LocalLab G9.1
 * registration-only policy.
 */
final class LocalTournamentUiHandler {
    enum Status {
        OPENED,
        REGISTERED,
        ALREADY_REGISTERED,
        UNSUPPORTED,
        CLOSED_UI_NOOP
    }

    static final class Result {
        final Status status;
        final TournamentPresentation.InputKind inputKind;
        final int entrantCount;
        final String detail;

        Result(
            Status status,
            TournamentPresentation.InputKind inputKind,
            int entrantCount,
            String detail
        ){
            this.status=Objects.requireNonNull(
                status,
                "status"
            );
            this.inputKind=inputKind;
            this.entrantCount=entrantCount;
            this.detail=detail;
        }
    }

    private final WorldPlayer player;
    private final LocalLabTournamentRuntime runtime;
    private boolean open;

    LocalTournamentUiHandler(
        WorldPlayer player,
        LocalLabTournamentRuntime runtime
    ){
        this.player=Objects.requireNonNull(
            player,
            "player"
        );
        this.runtime=Objects.requireNonNull(
            runtime,
            "runtime"
        );
    }

    Result open(
        ServerPacketWriter writer
    )throws IOException{
        ServerPacketWriter packets=
            Objects.requireNonNull(
                writer,
                "writer"
            );

        packets.beginBatch();
        boolean ended=false;

        try{
            TournamentPresentation.openTournament(
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

        return new Result(
            Status.OPENED,
            null,
            runtime.entrantCount(),
            "root="+
                TournamentPresentation.TOURNAMENT_ROOT
        );
    }

    boolean close(){
        boolean wasOpen=open;
        open=false;
        return wasOpen;
    }

    boolean isOpen(){
        return open;
    }

    boolean owns(
        TournamentPresentation.Input input
    ){
        if(input==null)
            return false;

        return input.kind==
                TournamentPresentation.InputKind.ENTER||
            input.kind==
                TournamentPresentation.InputKind.SPECTATE||
            input.kind==
                TournamentPresentation.InputKind.OPEN_SHOP;
    }

    Result handle(
        TournamentPresentation.Input input
    ){
        TournamentPresentation.Input checked=
            Objects.requireNonNull(
                input,
                "input"
            );

        if(!owns(checked))
            return null;

        if(!open)
            return new Result(
                Status.CLOSED_UI_NOOP,
                checked.kind,
                runtime.entrantCount(),
                "rootOpen=false"
            );

        if(checked.kind==
                TournamentPresentation.InputKind.ENTER){
            LocalLabTournamentRuntime.RegistrationResult
                registered=
                    runtime.register(
                        player.username()
                    );

            return new Result(
                registered.created
                    ?Status.REGISTERED
                    :Status.ALREADY_REGISTERED,
                checked.kind,
                registered.snapshot
                    .entrants
                    .size(),
                "event="+
                    LocalLabTournamentRuntime.EVENT_ID+
                " lifecycle="+
                    registered.snapshot.eventLifecycle
            );
        }

        return new Result(
            Status.UNSUPPORTED,
            checked.kind,
            runtime.entrantCount(),
            "G9.1 registration-only"
        );
    }

    private static void abortQuietly(
        ServerPacketWriter writer
    ){
        try{
            writer.abortBatch();
        }catch(Throwable ignored){}
    }
}
