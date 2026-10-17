package spk.local;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Read-only LocalLab composition over the recovered exact-v308 PK Ratings
 * presentation. G11.1 publishes an online-player roster only; it does not own
 * rating, ranking, row-target or reward semantics.
 */
final class LocalPkRatingsUiHandler {
    static final String AUTHORITY=
        "LOCAL_LAB_POLICY_G111_PK_RATINGS_READ_ONLY_V1";
    static final int FONT_INDEX=0;

    private final World world;
    private final PkRatingsService service;

    LocalPkRatingsUiHandler(
        World world
    ){
        this.world=Objects.requireNonNull(
            world,
            "world"
        );
        this.service=
            new PkRatingsService(
                (player,row,authority)->
                    PkRatingsService
                        .ActionResult.failure(
                            "G11.1 PK Ratings is read-only"
                        ),
                (player,navigation,authority)->
                    PkRatingsService
                        .ActionResult.failure(
                            "G11.1 PK Ratings navigation unsupported"
                        ),
                AtomicTransactionService
                    .SourceAuthority
                    .CUSTOM_LOCALLAB
            );
    }

    PkRatingsService.Snapshot open(
        ServerPacketWriter writer
    )throws IOException{
        ServerPacketWriter packets=
            Objects.requireNonNull(
                writer,
                "writer"
            );

        PkRatingsService.Snapshot snapshot=
            service.replaceRows(
                buildRows(
                    "LocalLab PK Ratings (not ranked)",
                    "online:"
                )
            );

        packets.beginBatch();
        boolean ended=false;

        try{
            packets.fixed(
                97,
                BootstrapPackets.interface97(
                    PkRatingsPresentation
                        .RATINGS_ROOT
                )
            );

            PkRatingsPresentation.rebuild(
                packets,
                snapshot,
                FONT_INDEX
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

        return snapshot;
    }

    PkRatingsService.Snapshot navigate(
        String playerRef,
        PkRatingsService.Navigation navigation,
        ServerPacketWriter writer
    )throws IOException{
        PartyService.requireRef(
            playerRef
        );
        PkRatingsService.Navigation checked=
            Objects.requireNonNull(
                navigation,
                "navigation"
            );

        final String header;
        final String keyPrefix;

        switch(checked){
            case DAILY_PK:
                header="LocalLab Daily PK (not scored)";
                keyPrefix="daily-online:";
                break;
            case TOURNAMENT_PK:
                header="LocalLab Tournament PK (not scored)";
                keyPrefix="tournament-online:";
                break;
            default:
                throw new AssertionError(
                    checked
                );
        }

        PkRatingsService.Snapshot snapshot=
            service.replaceRows(
                buildRows(
                    header,
                    keyPrefix
                )
            );

        PkRatingsPresentation.rebuild(
            Objects.requireNonNull(
                writer,
                "writer"
            ),
            snapshot,
            FONT_INDEX
        );

        return snapshot;
    }

    PkRatingsService.Snapshot snapshot(){
        return service.snapshot();
    }

    private List<PkRatingsService.Row> buildRows(
        String headerText,
        String keyPrefix
    ){
        ArrayList<PkRatingsService.Row> rows=
            new ArrayList<>();

        rows.add(
            row(
                "locallab:header",
                Objects.requireNonNull(
                    headerText,
                    "headerText"
                )
            )
        );

        List<WorldPlayer> players=
            world.players().snapshot();

        int remaining=
            PkRatingsService.MAX_ROWS-1;

        for(WorldPlayer player:players){
            if(remaining<=0)
                break;

            String username=player.username();

            if(username==null||
               username.trim().isEmpty())
                continue;

            rows.add(
                row(
                    Objects.requireNonNull(
                        keyPrefix,
                        "keyPrefix"
                    )+
                        username,
                    "Online: "+
                        username
                )
            );
            remaining--;
        }

        return rows;
    }

    private static PkRatingsService.Row row(
        String key,
        String text
    ){
        return new PkRatingsService.Row(
            key,
            text,
            false,
            AtomicTransactionService
                .SourceAuthority
                .CUSTOM_LOCALLAB
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
