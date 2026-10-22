package spk.local;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;

public final class Player81PreparedTransformFailureTest {
    public static void main(String[] args)throws Exception{
        assertPreparedFailureIsTerminal();
        assertStagedWriterFramesNothingOnTransformFailure();
        assertTransformPrecedesFramingInBothPreparedPaths();

        System.out.println(
            "PLAYER81_PREPARED_TRANSFORM_FAILURE_PASS "+
            "preparedFailureTerminal=true "+
            "stagedFailureFramesNothing=true "+
            "unbatchedTransformBeforeFraming=true "+
            "noLocalOnlyFallback=true"
        );
    }

    private static void assertPreparedFailureIsTerminal()
        throws Exception
    {
        Player81WorldSync.PreparedBatch prepared=
            malformedPrepared();

        boolean failed=false;
        try{
            Player81WorldSync.transformPrepared(
                prepared,
                BootstrapPackets.player81Idle()
            );
        }catch(IOException expected){
            failed=
                expected.getMessage()!=null&&
                expected.getMessage().contains(
                    "prepared player81 transform failed"
                );
        }

        if(!failed)
            throw new AssertionError(
                "prepared transform failure was not propagated"
            );

        if(!prepared.completed)
            throw new AssertionError(
                "failed prepared transform remained committable"
            );
    }

    private static void assertStagedWriterFramesNothingOnTransformFailure()
        throws Exception
    {
        OutboundPacketQueue queue=
            new OutboundPacketQueue();
        ServerPacketWriter writer=
            new ServerPacketWriter(
                queue,
                new IsaacCipher(
                    new int[]{301,302,303,304}
                )
            );

        writer.beginBatch();

        Player81WorldSync.PreparedBatch malformed=
            malformedPrepared();

        setField(
            writer,
            "batchPlayer81Initialized",
            true
        );
        setField(
            writer,
            "batchPlayer81",
            malformed
        );

        boolean failed=false;
        try{
            writer.varShort(
                81,
                BootstrapPackets.player81Idle()
            );
        }catch(IOException expected){
            failed=true;
        }

        if(!failed)
            throw new AssertionError(
                "staged writer swallowed prepared transform failure"
            );

        if(queue.queuedBytes()!=0)
            throw new AssertionError(
                "failed staged transform published packet81 bytes="+
                queue.queuedBytes()
            );

        Field pendingField=
            ServerPacketWriter.class
                .getDeclaredField("pending");
        pendingField.setAccessible(true);
        java.io.ByteArrayOutputStream pending=
            (java.io.ByteArrayOutputStream)
                pendingField.get(writer);

        if(pending.size()!=0)
            throw new AssertionError(
                "failed staged transform appended framed bytes="+
                pending.size()
            );

        writer.abortBatch();

        if(queue.queuedBytes()!=0)
            throw new AssertionError(
                "aborting failed staged transform emitted bytes"
            );
    }

    private static void assertTransformPrecedesFramingInBothPreparedPaths()
        throws Exception
    {
        String source=
            Files.readString(
                Path.of(
                    "server",
                    "src",
                    "spk",
                    "local",
                    "ServerPacketWriter.java"
                ),
                StandardCharsets.UTF_8
            );

        int stagedStart=
            source.indexOf(
                "if(staged){"
            );
        int stagedTransform=
            source.indexOf(
                "Player81WorldSync.transformPrepared(",
                stagedStart
            );
        int stagedFrame=
            source.indexOf(
                "writeOpcode(opcode);",
                stagedTransform
            );

        if(stagedStart<0||
           stagedTransform<stagedStart||
           stagedFrame<stagedTransform)
            throw new AssertionError(
                "staged packet81 no longer transforms before framing"
            );

        int unbatchedStart=
            source.indexOf(
                "final Player81WorldSync.PreparedBatch unbatchedPrepared"
            );
        int unbatchedTransform=
            source.indexOf(
                "Player81WorldSync.transformPrepared(",
                unbatchedStart
            );
        int unbatchedFrame=
            source.indexOf(
                "writeOpcode(opcode);",
                unbatchedTransform
            );

        if(unbatchedStart<0||
           unbatchedTransform<unbatchedStart||
           unbatchedFrame<unbatchedTransform)
            throw new AssertionError(
                "unbatched packet81 no longer transforms before framing"
            );

        String syncSource=
            Files.readString(
                Path.of(
                    "server",
                    "src",
                    "spk",
                    "local",
                    "Player81WorldSync.java"
                ),
                StandardCharsets.UTF_8
            );

        int method=
            syncSource.indexOf(
                "static byte[] transformPrepared("
            );
        int nextMethod=
            syncSource.indexOf(
                "@FunctionalInterface",
                method
            );

        if(method<0||nextMethod<=method)
            throw new AssertionError(
                "transformPrepared source block not found"
            );

        String block=
            syncSource.substring(
                method,
                nextMethod
            );

        if(!block.contains(
                "throws IOException"
            )||
           !block.contains(
                "abortPreparedBatch("
            )||
           block.contains(
                "return body;\n        }catch"
            )||
           block.contains(
                "using certified local-only body"
            ))
            throw new AssertionError(
                "prepared transform failure can still silently fall back"
            );
    }

    private static Player81WorldSync.PreparedBatch malformedPrepared(){
        return new Player81WorldSync.PreparedBatch(
            null,
            new LinkedHashMap<>(),
            new HashMap<>()
        );
    }

    private static void setField(
        Object target,
        String name,
        Object value
    )throws Exception{
        Field field=
            target.getClass()
                .getDeclaredField(name);
        field.setAccessible(true);
        field.set(
            target,
            value
        );
    }

    private Player81PreparedTransformFailureTest(){}
}
