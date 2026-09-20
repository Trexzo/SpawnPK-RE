package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalSessionRuntimeBindingsTest {
    private static final class Bridge
        implements LocalSessionRuntimeBindings.SessionBridge
    {
        String saveReason;

        @Override public void saveAccount(
            String tag,
            String reason
        ){
            saveReason=reason;
        }
    }

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(50L);
        try{
            WorldPlayer player=new WorldPlayer();
            world.registerPlayer(player,"runtime-test");

            DevAuthorityWorkbench dev=new DevAuthorityWorkbench();
            NpcRegistry npcs=new NpcRegistry(dev);
            Bridge bridge=new Bridge();

            LocalSessionRuntimeBindings bindings=
                new LocalSessionRuntimeBindings(
                    world,
                    player,
                    dev,
                    npcs,
                    player.movement(),
                    player.bank(),
                    bridge
                );

            if(bindings.context()!=null)
                throw new AssertionError(
                    "runtime bindings must start unregistered"
                );

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            ServerPacketWriter writer=
                new ServerPacketWriter(
                    wire,
                    new IsaacCipher(new int[]{1,2,3,4})
                );

            bindings.register(
                writer,
                "[runtime-bindings-test] "
            );

            if(bindings.context()==null)
                throw new AssertionError(
                    "Player81 context was not registered"
                );

            Player81WorldSync.Context first=
                bindings.context();

            bindings.register(
                writer,
                "[runtime-bindings-test] "
            );

            if(bindings.context()!=first)
                throw new AssertionError(
                    "repeat registration replaced active context"
                );

            bindings.unregister();

            if(bindings.context()!=null)
                throw new AssertionError(
                    "runtime bindings did not clear context"
                );

            // Cleanup must remain safe when session-finally executes after a
            // partially initialized or already-closed runtime.
            bindings.unregister();

            System.out.println(
                "LOCAL_SESSION_RUNTIME_BINDINGS_PASS "+
                "register=true repeatStable=true unregister=true"
            );
        }finally{
            world.close();
        }
    }
}
