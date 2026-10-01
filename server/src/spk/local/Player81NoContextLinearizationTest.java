package spk.local;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Player81NoContextLinearizationTest {
    public static void main(String[] args)throws Exception{
        assertNoContextSourceContract();
        assertRegistrationAffectsNextPacketOnly();

        System.out.println(
            "PLAYER81_NO_CONTEXT_LINEARIZATION_PASS "+
            "singleClassification=true "+
            "noSecondLiveTransform=true "+
            "noRetroactiveRelayFlush=true "+
            "registrationAffectsNextPacket=true"
        );
    }

    private static void assertNoContextSourceContract()
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

        String marker=
            "PreparedBatchStartStatus\n"+
            "                        .NO_CONTEXT){";

        int start=source.indexOf(marker);
        int end=source.indexOf(
            "final Player81WorldSync.PreparedBatch unbatchedPrepared",
            start
        );

        if(start<0||end<=start)
            throw new AssertionError(
                "NO_CONTEXT packet81 branch not found"
            );

        String branch=
            source.substring(
                start,
                end
            );

        if(branch.contains(
                "Player81WorldSync.transform("
            ))
            throw new AssertionError(
                "NO_CONTEXT branch re-reads live Player81 context"
            );

        if(branch.contains(
                "SharedNpcWorldRelay"
            ))
            throw new AssertionError(
                "NO_CONTEXT branch retroactively flushes relay state"
            );

        if(!branch.contains(
                "pending.write(checkedBody)"
            ))
            throw new AssertionError(
                "NO_CONTEXT branch does not frame original local-only body"
            );
    }

    private static void assertRegistrationAffectsNextPacketOnly()
        throws Exception
    {
        World world=
            World.isolatedForTest(
                600L
            );
        WorldPlayer player=
            new WorldPlayer();
        long generation=
            world.registerPlayer(
                player,
                "packet81-no-context"
            );

        OutboundPacketQueue queue=
            new OutboundPacketQueue();
        ServerPacketWriter writer=
            new ServerPacketWriter(
                queue,
                new IsaacCipher(
                    new int[]{7,8,9,10}
                )
            );

        try{
            writer.varShort(
                81,
                BootstrapPackets
                    .player81Idle()
            );

            if(queue.queuedBytes()==0)
                throw new AssertionError(
                    "unregistered local-only packet81 did not publish"
                );

            drain(queue);

            long before=
                Player81WorldSync
                    .latestPublishedEventSequence(
                        writer
                    );

            Player81WorldSync.register(
                writer,
                world,
                player,
                new DevAuthorityWorkbench()
            );

            writer.varShort(
                81,
                BootstrapPackets
                    .player81Idle()
            );

            if(queue.queuedBytes()==0)
                throw new AssertionError(
                    "registered packet81 did not publish"
                );

            long after=
                Player81WorldSync
                    .latestPublishedEventSequence(
                        writer
                    );

            if(after<=before)
                throw new AssertionError(
                    "registration did not affect next packet81 before="+
                    before+
                    " after="+
                    after
                );
        }finally{
            Player81WorldSync.unregister(
                writer
            );

            if(player.registered())
                world.unregisterPlayer(
                    player,
                    generation
                );

            world.close();
        }
    }

    private static void drain(
        OutboundPacketQueue queue
    )throws Exception{
        ByteArrayOutputStream sink=
            new ByteArrayOutputStream();

        while(queue.queuedBytes()>0)
            queue.drainTo(
                sink,
                1<<20
            );
    }

    private Player81NoContextLinearizationTest(){}
}
