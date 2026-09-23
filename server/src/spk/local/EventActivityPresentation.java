package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Exact-v308 Event Activity Viewer projection over semantic EventActivityService.
 *
 * Owns only S2C250 subtype-6 framing and the exact client-visible row shape.
 * Quota definitions, reset policy, token economics, persistence and anti-abuse
 * remain server/domain authority.
 */
final class EventActivityPresentation {
    static final int APPLICATION_SUBTYPE=6;
    static final int ROOT=30072;
    static final int MAX_ROWS=7;
    static final int MAX_VISIBLE_DETAILS=3;
    static final String PRESENTATION_AUTHORITY="EXACT_CURRENT_CLIENT";

    static void open(
        ServerPacketWriter packets
    )throws IOException{
        Objects.requireNonNull(packets,"packets")
            .fixed(
                97,
                BootstrapPackets.interface97(
                    ROOT
                )
            );
    }

    static void rebuild(
        ServerPacketWriter packets,
        EventActivityService.Snapshot snapshot
    )throws IOException{
        Objects.requireNonNull(packets,"packets");
        Objects.requireNonNull(snapshot,"snapshot");

        if(snapshot.rows.size()>MAX_ROWS)
            throw new IllegalArgumentException(
                "Event Activity rows="+
                snapshot.rows.size()+
                " max="+MAX_ROWS
            );

        clear(packets);

        for(EventActivityService.RowSnapshot row:
                snapshot.rows)
            append(
                packets,
                row
            );

        render(packets);
    }

    static void clear(
        ServerPacketWriter packets
    )throws IOException{
        ApplicationPacket250Writer.send(
            Objects.requireNonNull(packets,"packets"),
            APPLICATION_SUBTYPE,
            clearBody()
        );
    }

    static void append(
        ServerPacketWriter packets,
        EventActivityService.RowSnapshot row
    )throws IOException{
        ApplicationPacket250Writer.send(
            Objects.requireNonNull(packets,"packets"),
            APPLICATION_SUBTYPE,
            appendBody(row)
        );
    }

    static void render(
        ServerPacketWriter packets
    )throws IOException{
        ApplicationPacket250Writer.send(
            Objects.requireNonNull(packets,"packets"),
            APPLICATION_SUBTYPE,
            renderBody()
        );
    }

    static byte[] clearBody(){
        return checkedBody(
            ApplicationPacket250Writer
                .payload()
                .u8(0)
                .bytes()
        );
    }

    static byte[] appendBody(
        EventActivityService.RowSnapshot row
    ){
        EventActivityService.RowSnapshot checked=
            Objects.requireNonNull(
                row,
                "row"
            );

        if(checked.ordinal<0||
           checked.ordinal>=MAX_ROWS)
            throw new IllegalArgumentException(
                "ordinal="+checked.ordinal
            );

        if(checked.details.size()>
                MAX_VISIBLE_DETAILS)
            throw new IllegalArgumentException(
                "Event Activity visible details="+
                checked.details.size()+
                " max="+
                MAX_VISIBLE_DETAILS+
                " activity="+
                checked.activityKey
            );

        validateModeProjection(checked);

        ApplicationPacket250Writer.Payload payload=
            ApplicationPacket250Writer
                .payload()
                .u8(1)
                .stringNl(
                    wireText(
                        checked.title,
                        "title"
                    )
                )
                .u8(checked.details.size());

        for(String detail:checked.details)
            payload.stringNl(
                wireText(
                    detail,
                    "detail"
                )
            );

        payload
            .i32(
                checkedI32(
                    checked.currentUsage,
                    "currentUsage"
                )
            )
            .i32(
                checkedI32(
                    checked.presentationLimit,
                    "presentationLimit"
                )
            )
            .i64(
                checkedTimer(
                    checked.timerDurationMillis
                )
            );

        return checkedBody(
            payload.bytes()
        );
    }

    static byte[] renderBody(){
        return checkedBody(
            ApplicationPacket250Writer
                .payload()
                .u8(2)
                .bytes()
        );
    }

    private static void validateModeProjection(
        EventActivityService.RowSnapshot row
    ){
        if(row.currentUsage<0)
            throw new IllegalArgumentException(
                "currentUsage="+
                row.currentUsage
            );

        switch(row.mode){
            case LOCKED:
                if(row.presentationLimit!=-1L)
                    throw new IllegalStateException(
                        "LOCKED Event Activity limit="+
                        row.presentationLimit
                    );
                break;
            case UNLIMITED:
                if(row.presentationLimit!=0L)
                    throw new IllegalStateException(
                        "UNLIMITED Event Activity limit="+
                        row.presentationLimit
                    );
                break;
            case FINITE:
                if(row.presentationLimit<=0L)
                    throw new IllegalStateException(
                        "FINITE Event Activity limit="+
                        row.presentationLimit
                    );
                break;
            default:
                throw new AssertionError(row.mode);
        }
    }

    private static byte[] checkedBody(
        byte[] body
    ){
        /*
         * Canonical writer owns the S2C250 VAR_BYTE ceiling. Calling encode
         * here validates subtype-local bodies without duplicating framing.
         */
        ApplicationPacket250Writer.encode(
            APPLICATION_SUBTYPE,
            body
        );
        return body;
    }

    private static int checkedI32(
        long value,
        String field
    ){
        if(value<Integer.MIN_VALUE||
           value>Integer.MAX_VALUE)
            throw new IllegalArgumentException(
                field+
                " outside i32 "+
                value
            );

        return (int)value;
    }

    private static long checkedTimer(
        long value
    ){
        if(value<0L)
            throw new IllegalArgumentException(
                "timerDurationMillis="+
                value
            );

        return value;
    }

    private static String wireText(
        String value,
        String field
    ){
        if(value==null)
            throw new NullPointerException(field);

        for(int i=0;i<value.length();i++){
            char ch=value.charAt(i);

            if(ch=='\n'||ch=='\r')
                throw new IllegalArgumentException(
                    field+
                    " contains line terminator"
                );

            if(ch>0xff)
                throw new IllegalArgumentException(
                    field+
                    " not ISO-8859-1 at index="+
                    i
                );
        }

        return value;
    }

    private EventActivityPresentation(){}
}
