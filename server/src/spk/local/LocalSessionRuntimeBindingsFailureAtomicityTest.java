package spk.local;

import java.io.*;
import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Map;

public final class LocalSessionRuntimeBindingsFailureAtomicityTest {
    private static final class Bridge
        implements LocalSessionRuntimeBindings.SessionBridge {
        @Override public void saveAccount(
            String tag,
            String reason
        ){}
    }

    private static boolean containsMessage(
        Throwable failure,
        String expected
    ){
        for(
            Throwable current=failure;
            current!=null;
            current=current.getCause()
        )
            if(current.getMessage()!=null&&
               current.getMessage().contains(expected))
                return true;

        return false;
    }

    private static final class FailingOutputStream
        extends OutputStream {

        @Override public void write(int value)
            throws IOException {
            throw new IOException(
                "forced runtime binding write failure"
            );
        }

        @Override public void write(
            byte[] data,
            int offset,
            int length
        )throws IOException{
            throw new IOException(
                "forced runtime binding write failure"
            );
        }
    }

    public static void main(String[] args)
        throws Exception {

        int player81WorldBaseline=
            mapSize(
                Player81WorldSync.class,
                "BY_WORLD"
            );
        int player81WriterBaseline=
            mapSize(
                Player81WorldSync.class,
                "BY_WRITER"
            );
        int npcWorldBaseline=
            mapSize(
                SharedNpcWorldRelay.class,
                "BY_WORLD"
            );
        int npcWriterBaseline=
            mapSize(
                SharedNpcWorldRelay.class,
                "BY_WRITER"
            );
        int tradeWorldBaseline=
            tradeWorldCount();

        World world=
            World.isolatedForTest(60_000L);
        WorldPlayer owner=
            new WorldPlayer();
        WorldPlayer peer=
            new WorldPlayer();

        long ownerGeneration=
            world.registerPlayer(
                owner,
                "binding-failure-owner"
            );
        long peerGeneration=
            world.registerPlayer(
                peer,
                "binding-failure-peer"
            );

        LocalSessionRuntimeBindings bindings=
            bindings(
                world,
                owner
            );

        ServerPacketWriter failingWriter=
            new ServerPacketWriter(
                new FailingOutputStream(),
                new IsaacCipher(
                    new int[]{1,2,3,4}
                )
            );

        try{
            boolean failed=false;

            try{
                bindings.register(
                    failingWriter,
                    "[binding-failure-atomic] "
                );
            }catch(IOException expected){
                failed=
                    expected instanceof
                        Player81WorldSync
                            .TerminalPlayerOptionsException&&
                    containsMessage(
                        expected,
                        "forced runtime binding write failure"
                    );
            }

            if(!failed)
                throw new AssertionError(
                    "forced registration failure was not propagated"
                );

            if(bindings.context()!=null)
                throw new AssertionError(
                    "failed registration retained local context"
                );

            if(writerTracked(
                    Player81WorldSync.class,
                    failingWriter
                ))
                throw new AssertionError(
                    "failed registration retained Player81 writer"
                );

            if(writerTracked(
                    SharedNpcWorldRelay.class,
                    failingWriter
                ))
                throw new AssertionError(
                    "failed registration retained SharedNpc writer"
                );

            if(tradeWriterRetained(
                    failingWriter
                ))
                throw new AssertionError(
                    "failed registration retained Trade writer"
                );

            assertBaselines(
                player81WorldBaseline,
                player81WriterBaseline,
                npcWorldBaseline,
                npcWriterBaseline,
                tradeWorldBaseline,
                "after failure"
            );

            ServerPacketWriter healthyWriter=
                new ServerPacketWriter(
                    new OutboundPacketQueue(),
                    new IsaacCipher(
                        new int[]{5,6,7,8}
                    )
                );

            bindings.register(
                healthyWriter,
                "[binding-failure-atomic] "
            );

            if(bindings.context()==null)
                throw new AssertionError(
                    "healthy retry did not install context"
                );

            if(!writerTracked(
                    Player81WorldSync.class,
                    healthyWriter
                )||
               !writerTracked(
                    SharedNpcWorldRelay.class,
                    healthyWriter
                )||
               !tradeWriterRetained(
                    healthyWriter
                ))
                throw new AssertionError(
                    "healthy retry did not install all runtime bindings"
                );

            bindings.unregister();

            if(bindings.context()!=null)
                throw new AssertionError(
                    "final unregister retained local context"
                );

            assertBaselines(
                player81WorldBaseline,
                player81WriterBaseline,
                npcWorldBaseline,
                npcWriterBaseline,
                tradeWorldBaseline,
                "after healthy unregister"
            );

            System.out.println(
                "LOCAL_SESSION_RUNTIME_BINDINGS_FAILURE_ATOMICITY_PASS "+
                "forcedFailurePropagated=true "+
                "allBindingsRolledBack=true "+
                "healthyRetry=true "+
                "finalBaselineRestored=true"
            );
        }finally{
            bindings.unregister();

            world.unregisterPlayer(
                owner,
                ownerGeneration
            );
            world.unregisterPlayer(
                peer,
                peerGeneration
            );
            world.close();
        }
    }

    private static LocalSessionRuntimeBindings bindings(
        World world,
        WorldPlayer player
    ){
        return new LocalSessionRuntimeBindings(
            world,
            player,
            new DevAuthorityWorkbench(),
            new NpcRegistry(
                new DevAuthorityWorkbench()
            ),
            player.movement(),
            player.bank(),
            new Bridge()
        );
    }

    private static void assertBaselines(
        int player81WorldBaseline,
        int player81WriterBaseline,
        int npcWorldBaseline,
        int npcWriterBaseline,
        int tradeWorldBaseline,
        String stage
    )throws Exception{
        if(mapSize(
                Player81WorldSync.class,
                "BY_WORLD"
            )!=player81WorldBaseline||
           mapSize(
                Player81WorldSync.class,
                "BY_WRITER"
            )!=player81WriterBaseline||
           mapSize(
                SharedNpcWorldRelay.class,
                "BY_WORLD"
            )!=npcWorldBaseline||
           mapSize(
                SharedNpcWorldRelay.class,
                "BY_WRITER"
            )!=npcWriterBaseline||
           tradeWorldCount()!=
                tradeWorldBaseline)
            throw new AssertionError(
                stage+
                " runtime baselines not restored"
            );
    }

    private static boolean writerTracked(
        Class<?> owner,
        ServerPacketWriter writer
    )throws Exception{
        synchronized(owner){
            return map(
                owner,
                "BY_WRITER"
            ).containsKey(writer);
        }
    }

    private static int mapSize(
        Class<?> owner,
        String fieldName
    )throws Exception{
        synchronized(owner){
            return map(
                owner,
                fieldName
            ).size();
        }
    }

    @SuppressWarnings("unchecked")
    private static IdentityHashMap<Object,Object> map(
        Class<?> owner,
        String fieldName
    )throws Exception{
        Field field=
            owner.getDeclaredField(
                fieldName
            );
        field.setAccessible(true);
        return (IdentityHashMap<Object,Object>)
            field.get(null);
    }

    private static int tradeWorldCount()
        throws Exception{
        synchronized(TradeService.class){
            return tradeStates().size();
        }
    }

    private static boolean tradeWriterRetained(
        ServerPacketWriter target
    )throws Exception{
        synchronized(TradeService.class){
            for(Object state:
                    tradeStates().values()){
                for(Object context:
                        tradeContexts(
                            state
                        ).values()){
                    Field writer=
                        context.getClass()
                            .getDeclaredField(
                                "writer"
                            );
                    writer.setAccessible(true);

                    if(writer.get(context)==target)
                        return true;
                }
            }

            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static IdentityHashMap<World,Object>
        tradeStates()throws Exception{
        Field field=
            TradeService.class
                .getDeclaredField(
                    "STATES"
                );
        field.setAccessible(true);
        return (IdentityHashMap<World,Object>)
            field.get(null);
    }

    @SuppressWarnings("unchecked")
    private static Map<Object,Object> tradeContexts(
        Object state
    )throws Exception{
        Field field=
            state.getClass()
                .getDeclaredField(
                    "contexts"
                );
        field.setAccessible(true);
        return (Map<Object,Object>)
            field.get(state);
    }

    private LocalSessionRuntimeBindingsFailureAtomicityTest(){}
}
