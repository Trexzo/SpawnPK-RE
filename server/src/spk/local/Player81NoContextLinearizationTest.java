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
        WorldPlayer remote=
            new WorldPlayer();

        long generation=
            world.registerPlayer(
                player,
                "packet81-no-context"
            );
        long remoteGeneration=
            world.registerPlayer(
                remote,
                "packet81-no-context-remote"
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

            int localOnlyBytes=
                queue.queuedBytes();

            if(localOnlyBytes==0)
                throw new AssertionError(
                    "unregistered local-only packet81 did not publish"
                );

            drain(queue);

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

            int registeredBytes=
                queue.queuedBytes();

            if(registeredBytes<=localOnlyBytes)
                throw new AssertionError(
                    "registration did not affect next packet81 localOnlyBytes="+
                    localOnlyBytes+
                    " registeredBytes="+
                    registeredBytes
                );

            if(Player81WorldSync.clientIndexFor(
                    writer,
                    remote
                )<0)
                throw new AssertionError(
                    "registered next packet81 did not materialize remote player"
                );
        }finally{
            Player81WorldSync.unregister(
                writer
            );

            if(remote.registered())
                world.unregisterPlayer(
                    remote,
                    remoteGeneration
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
