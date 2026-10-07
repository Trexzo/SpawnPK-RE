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
                buildRows()
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

    PkRatingsService.Snapshot snapshot(){
        return service.snapshot();
    }

    private List<PkRatingsService.Row> buildRows(){
        ArrayList<PkRatingsService.Row> rows=
            new ArrayList<>();

        rows.add(
            row(
                "locallab:header",
                "LocalLab PK Ratings (not ranked)"
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
                    "online:"+
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
