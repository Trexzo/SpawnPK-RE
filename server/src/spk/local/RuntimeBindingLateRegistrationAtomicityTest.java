package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.IdentityHashMap;

public final class RuntimeBindingLateRegistrationAtomicityTest {
    private static final int QUEUE_CAPACITY=4096;

    private static final class Bridge
        implements LocalSessionRuntimeBindings.SessionBridge
    {
        @Override public void saveAccount(
            String tag,
            String reason
        ){}
    }

    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(
                611L
            );
        WorldPlayer a=
            new WorldPlayer();
        WorldPlayer b=
            new WorldPlayer();

        long generationA=
            world.registerPlayer(
                a,
                "late-bind-a"
            );
        long generationB=
            world.registerPlayer(
                b,
                "late-bind-b"
            );

        OutboundPacketQueue queueA=
            new OutboundPacketQueue(
                QUEUE_CAPACITY
            );
        OutboundPacketQueue queueB=
            new OutboundPacketQueue(
                QUEUE_CAPACITY
            );

        ServerPacketWriter writerA=
            new ServerPacketWriter(
                queueA,
                new IsaacCipher(
                    new int[]{2001,2002,2003,2004}
                )
            );
        ServerPacketWriter writerB=
            new ServerPacketWriter(
                queueB,
                new IsaacCipher(
                    new int[]{2005,2006,2007,2008}
                )
            );

        NpcRegistry npcsA=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );
        NpcRegistry npcsB=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );

        Player81WorldSync.Context oldPlayer81A=
            Player81WorldSync.register(
                writerA,
                world,
                a,
                new DevAuthorityWorkbench()
            );
        Player81WorldSync.register(
            writerB,
            world,
            b,
            new DevAuthorityWorkbench()
        );

        SharedNpcWorldRelay.register(
            writerA,
            world,
            a,
            npcsA,
            a.movement()
        );
        SharedNpcWorldRelay.register(
            writerB,
            world,
            b,
            npcsB,
            b.movement()
        );

        Object oldRelayA=
            relayContextFor(
                writerA
            );

        TradeService.register(
            world,
            a,
            generationA,
            a.bank(),
            writerA,
            ()->{}
        );
        TradeService.register(
            world,
            b,
            generationB,
            b.bank(),
            writerB,
            ()->{}
        );

        LocalSessionRuntimeBindings bindings=
            new LocalSessionRuntimeBindings(
                world,
                a,
                new DevAuthorityWorkbench(),
                npcsA,
                a.movement(),
                a.bank(),
                new Bridge()
            );

        OutboundPacketQueue.BatchReservation pressure=
            null;

        try{
            String opened=
                TradeService.start(
                    world,
                    a,
                    b
                );

            if(opened==null||
               !opened.contains(
                    "TRADE_UI_OPEN"
               ))
                throw new AssertionError(
                    "old Trade fixture failed: "+
                    opened
                );

            drain(queueA);
            drain(queueB);

            /*
             * A writer-local batch is transient busy state, not terminal
             * transport failure. Registration must reject without waiting while
             * old runtime authority remains intact.
             */
            writerA.beginBatch();
            boolean busyRetryable=false;

            try{
                bindings.register(
                    writerA,
                    "[late-bind-busy] ",
                    generationA
                );
            }catch(Player81WorldSync
                    .RetryablePlayerOptionsException expected){
                busyRetryable=true;
            }finally{
                writerA.abortBatch();
            }

            if(!busyRetryable||
               player81ContextFor(writerA)!=oldPlayer81A||
               relayContextFor(writerA)!=oldRelayA||
               !TradeService.active(a)||
               !TradeService.active(b))
                throw new AssertionError(
                    "busy writer admission did not preserve exact old runtime bundle"
                );

            pressure=
                OutboundPacketQueue.reserveBatch(
                    queueA,
                    QUEUE_CAPACITY
                );

            boolean retryable=false;

            try{
                bindings.register(
                    writerA,
                    "[late-bind] ",
                    generationA
                );
            }catch(Player81WorldSync
                    .RetryablePlayerOptionsException expected){
                retryable=true;
            }

            if(!retryable)
                throw new AssertionError(
                    "player-option admission rejection did not abort runtime replacement"
                );

            if(bindings.context()!=null)
                throw new AssertionError(
                    "failed runtime replacement retained tentative Player81 context"
                );

            if(player81ContextFor(
                    writerA
                )!=oldPlayer81A)
                throw new AssertionError(
                    "failed option prepublication retired exact old Player81 context"
                );

            if(relayContextFor(
                    writerA
                )!=oldRelayA)
                throw new AssertionError(
                    "failed option prepublication retired exact old SharedNpc context"
                );

            if(!TradeService.active(a)||
               !TradeService.active(b))
                throw new AssertionError(
                    "failed option prepublication destroyed old live Trade"
                );

            if(Player81WorldSync.clientIndexFor(
                    writerA,
                    b
                )<0)
            {
                /*
                 * The old context has not yet observed B. Prove it remains
                 * usable by transforming one packet81, then require visibility.
                 */
                Player81WorldSync.transformForTest(
                    oldPlayer81A,
                    BootstrapPackets.player81Idle()
                );
            }

            if(player81ContextFor(
                    writerA
                )!=oldPlayer81A)
                throw new AssertionError(
                    "old Player81 authority was not usable after rejected late bind"
                );

            pressure.release();
            pressure=null;

            drain(queueA);
            drain(queueB);

            bindings.register(
                writerA,
                "[late-bind] ",
                generationA
            );

            Player81WorldSync.Context newPlayer81=
                bindings.context();

            if(newPlayer81==null||
               newPlayer81==oldPlayer81A||
               player81ContextFor(
                    writerA
               )!=newPlayer81)
                throw new AssertionError(
                    "successful retry did not install one fresh Player81 context"
                );

            Object newRelay=
                relayContextFor(
                    writerA
                );

            if(newRelay==null||
               newRelay==oldRelayA)
                throw new AssertionError(
                    "successful retry did not install one fresh SharedNpc context"
                );

            if(TradeService.active(a)||
               TradeService.active(b))
                throw new AssertionError(
                    "successful runtime replacement did not retire old live Trade"
                );

            assertTerminalDirectOptionFailureRetiresBundle();

            System.out.println(
                "RUNTIME_BINDING_LATE_REGISTRATION_ATOMICITY_PASS "+
                "recoverableOptionsPrepublishedBeforeReplacement=true "+
                "busyWriterAdmissionRetryable=true "+
                "failedPreparationPreservesOldPlayer81=true "+
                "failedPreparationPreservesOldSharedNpc=true "+
                "failedPreparationPreservesOldTrade=true "+
                "rollbackOnlyOwnsNewLayers=true "+
                "successfulRetryInstallsFreshBundle=true "+
                "terminalDirectOptionFailureRetiresBundle=true"
            );
        }finally{
            if(pressure!=null)
                pressure.release();

            bindings.unregister();

            TradeService.unregister(a);
            TradeService.unregister(b);
            SharedNpcWorldRelay.unregister(writerA);
            SharedNpcWorldRelay.unregister(writerB);
            Player81WorldSync.unregister(writerA);
            Player81WorldSync.unregister(writerB);

            if(a.registered())
                world.unregisterPlayer(
                    a,
                    generationA
                );
            if(b.registered())
                world.unregisterPlayer(
                    b,
                    generationB
                );

            world.close();
        }
    }

    private static void assertTerminalDirectOptionFailureRetiresBundle()
        throws Exception
    {
        World world=
            World.isolatedForTest(
                612L
            );
        WorldPlayer a=
            new WorldPlayer();
        WorldPlayer b=
            new WorldPlayer();

        long generationA=
            world.registerPlayer(
                a,
                "terminal-options-a"
            );
        long generationB=
            world.registerPlayer(
                b,
                "terminal-options-b"
            );

        SwitchFailOutputStream directA=
            new SwitchFailOutputStream();
        ServerPacketWriter writerA=
            new ServerPacketWriter(
                directA,
                new IsaacCipher(
                    new int[]{2101,2102,2103,2104}
                )
            );

        OutboundPacketQueue queueB=
            new OutboundPacketQueue(
                QUEUE_CAPACITY
            );
        ServerPacketWriter writerB=
            new ServerPacketWriter(
                queueB,
                new IsaacCipher(
                    new int[]{2105,2106,2107,2108}
                )
            );

        NpcRegistry npcsA=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );
        NpcRegistry npcsB=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );

        Player81WorldSync.Context contextA=
            Player81WorldSync.register(
                writerA,
                world,
                a,
                new DevAuthorityWorkbench()
            );
        Player81WorldSync.Context contextB=
            Player81WorldSync.register(
                writerB,
                world,
                b,
                new DevAuthorityWorkbench()
            );

        /*
         * Keep the peer out of the option-prepublication path so the direct
         * target writer is the deterministic failing publication.
         */
        contextB.playerOptionsSent=true;

        SharedNpcWorldRelay.register(
            writerA,
            world,
            a,
            npcsA,
            a.movement()
        );
        SharedNpcWorldRelay.register(
            writerB,
            world,
            b,
            npcsB,
            b.movement()
        );

        TradeService.register(
            world,
            a,
            generationA,
            a.bank(),
            writerA,
            ()->{}
        );
        TradeService.register(
            world,
            b,
            generationB,
            b.bank(),
            writerB,
            ()->{}
        );

        LocalSessionRuntimeBindings bindings=
            new LocalSessionRuntimeBindings(
                world,
                a,
                new DevAuthorityWorkbench(),
                npcsA,
                a.movement(),
                a.bank(),
                new Bridge()
            );

        try{
            String opened=
                TradeService.start(
                    world,
                    a,
                    b
                );

            if(opened==null||
               !opened.contains(
                    "TRADE_UI_OPEN"
               ))
                throw new AssertionError(
                    "terminal direct Trade fixture failed: "+
                    opened
                );

            drain(queueB);

            if(player81ContextFor(
                    writerA
                )!=contextA)
                throw new AssertionError(
                    "terminal direct fixture lost old Player81 context before failure"
                );

            directA.fail=true;

            boolean terminal=false;

            try{
                bindings.register(
                    writerA,
                    "[terminal-options] ",
                    generationA
                );
            }catch(Player81WorldSync
                    .TerminalPlayerOptionsException expected){
                terminal=
                    expected.owner==a&&
                    expected.writer==writerA;
            }

            directA.fail=false;

            if(!terminal)
                throw new AssertionError(
                    "direct option publication failure was not classified terminal"
                );

            if(bindings.context()!=null)
                throw new AssertionError(
                    "terminal direct registration retained tentative runtime context"
                );

            if(player81ContextFor(
                    writerA
                )!=null)
                throw new AssertionError(
                    "terminal direct failure retained old Player81 authority"
                );

            if(relayContextFor(
                    writerA
                )!=null)
                throw new AssertionError(
                    "terminal direct failure retained old SharedNpc authority"
                );

            if(TradeService.active(a)||
               TradeService.active(b))
                throw new AssertionError(
                    "terminal direct failure retained old live Trade"
                );
        }finally{
            directA.fail=false;

            bindings.unregister();

            TradeService.unregister(a);
            TradeService.unregister(b);
            SharedNpcWorldRelay.unregister(writerA);
            SharedNpcWorldRelay.unregister(writerB);
            Player81WorldSync.unregister(writerA);
            Player81WorldSync.unregister(writerB);

            if(a.registered())
                world.unregisterPlayer(
                    a,
                    generationA
                );
            if(b.registered())
                world.unregisterPlayer(
                    b,
                    generationB
                );

            world.close();
        }
    }

    private static final class SwitchFailOutputStream
        extends java.io.OutputStream
    {
        final ByteArrayOutputStream bytes=
            new ByteArrayOutputStream();
        boolean fail;

        @Override public void write(
            int value
        )throws java.io.IOException{
            if(fail)
                throw new java.io.IOException(
                    "TERMINAL_DIRECT_OPTION_FAIL"
                );

            bytes.write(value);
        }

        @Override public void write(
            byte[] data,
            int offset,
            int length
        )throws java.io.IOException{
            if(fail)
                throw new java.io.IOException(
                    "TERMINAL_DIRECT_OPTION_FAIL"
                );

            bytes.write(
                data,
                offset,
                length
            );
        }
    }

    private static Object player81ContextFor(
        ServerPacketWriter writer
    )throws Exception{
        Field field=
            Player81WorldSync.class
                .getDeclaredField(
                    "BY_WRITER"
                );
        field.setAccessible(true);

        synchronized(Player81WorldSync.class){
            @SuppressWarnings("unchecked")
            IdentityHashMap<ServerPacketWriter,Object>
                contexts=
                    (IdentityHashMap<ServerPacketWriter,Object>)
                    field.get(null);

            return contexts.get(writer);
        }
    }

    private static Object relayContextFor(
        ServerPacketWriter writer
    )throws Exception{
        Field field=
            SharedNpcWorldRelay.class
                .getDeclaredField(
                    "BY_WRITER"
                );
        field.setAccessible(true);

        synchronized(SharedNpcWorldRelay.class){
            @SuppressWarnings("unchecked")
            IdentityHashMap<ServerPacketWriter,Object>
                contexts=
                    (IdentityHashMap<ServerPacketWriter,Object>)
                    field.get(null);

            return contexts.get(writer);
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
                Integer.MAX_VALUE
            );
    }

    private RuntimeBindingLateRegistrationAtomicityTest(){}
}
