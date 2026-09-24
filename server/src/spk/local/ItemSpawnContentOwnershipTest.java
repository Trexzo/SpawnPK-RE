package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.*;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;

public final class ItemSpawnContentOwnershipTest {
    public static void main(String[] args)throws Exception{
        bindingOwnership();
        defaultCapabilityFailsClosed();
        parity(
            "::item 4151",
            new String[]{"item","4151"},
            4151,
            1
        );
        parity(
            "::tabitem 995 2k",
            new String[]{"tabitem","995","2k"},
            995,
            2000
        );
        parity(
            "::item 999999",
            new String[]{"item","999999"},
            999999,
            0
        );
        missingArgumentParity();
        legacyDispatcherDependencyRemoved();

        System.out.println(
            "ITEM_SPAWN_CONTENT_OWNERSHIP_PASS "+
            "item=true "+
            "tabitem=true "+
            "wireParity=true "+
            "stateParity=true "+
            "rejectedSaveContract=true "+
            "missingArgumentParity=true "+
            "legacyFallback=false "+
            "rawContainerIdentity=false"
        );
    }

    private static void bindingOwnership(){
        World world=
            World.isolatedForTest(20L);

        try{
            assertBinding(
                world.content().commandBinding("item"),
                "item"
            );
            assertBinding(
                world.content().commandBinding("tabitem"),
                "tabitem"
            );
        }finally{
            world.close();
        }
    }

    private static void defaultCapabilityFailsClosed(){
        ContentPlayer player=
            ContentRuntimeAdapters.player(
                new WorldPlayer()
            );

        boolean rejected=false;

        try{
            player.grantItem(
                4151,
                1
            );
        }catch(UnsupportedOperationException expected){
            rejected=
                expected.getMessage()!=null&&
                expected.getMessage().contains(
                    "item grant unavailable"
                );
        }

        require(
            rejected,
            "writer-less content player granted item"
        );

        try{
            Method method=
                ContentPlayer.class.getMethod(
                    "grantItem",
                    int.class,
                    int.class
                );

            require(
                method.isDefault(),
                "grantItem must remain a default compatibility capability"
            );
        }catch(ReflectiveOperationException error){
            throw new AssertionError(
                "grantItem capability missing",
                error
            );
        }
    }

    private static void parity(
        String command,
        String[] legacyTokens,
        int itemId,
        int expectedCount
    )throws Exception{
        World world=
            World.isolatedForTest(20L);
        WorldPlayer player=
            new WorldPlayer();
        BankState expectedBank=
            new BankState();

        try{
            world.registerPlayer(
                player,
                "item-content-owner"
            );
            world.start();

            ByteArrayOutputStream actualWire=
                new ByteArrayOutputStream();
            ByteArrayOutputStream expectedWire=
                new ByteArrayOutputStream();

            ServerPacketWriter actual=
                writer(actualWire);
            ServerPacketWriter expected=
                writer(expectedWire);

            ContentResult content=
                dispatch(
                    world,
                    player,
                    command,
                    actual
                );

            LocalItemSpawnCommandHandler.Result legacy=
                new LocalItemSpawnCommandHandler(
                    expectedBank
                ).handle(
                    legacyTokens,
                    command,
                    expected
                );

            actual.flush();
            expected.flush();

            require(
                content!=null&&
                legacy!=null,
                "route not handled command="+
                command
            );

            require(
                legacy.logText.equals(
                    content.logText()),
                "log parity command="+
                command+
                " content="+
                content.logText()+
                " legacy="+
                legacy.logText
            );

            require(
                legacy.saveReason.equals(
                    content.saveReason()),
                "save parity command="+
                command+
                " content="+
                content.saveReason()+
                " legacy="+
                legacy.saveReason
            );

            require(
                Arrays.equals(
                    actualWire.toByteArray(),
                    expectedWire.toByteArray()
                ),
                "wire parity command="+
                command
            );

            require(
                player.bank().inventoryCount(
                    itemId
                )==expectedCount,
                "content inventory count command="+
                command+
                " actual="+
                player.bank().inventoryCount(
                    itemId
                )
            );

            require(
                expectedBank.inventoryCount(
                    itemId
                )==expectedCount,
                "legacy inventory count command="+
                command+
                " actual="+
                expectedBank.inventoryCount(
                    itemId
                )
            );

            if(itemId==999999)
                require(
                    "ITEM_SPAWN".equals(
                        content.saveReason()),
                    "rejected item lost save contract"
                );
        }finally{
            if(player.registered())
                world.unregisterPlayer(player);
            world.close();
        }
    }

    private static void missingArgumentParity()
        throws Exception
    {
        World world=
            World.isolatedForTest(20L);
        WorldPlayer player=
            new WorldPlayer();

        try{
            world.registerPlayer(
                player,
                "item-missing-argument-owner"
            );
            world.start();

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            ServerPacketWriter packets=
                writer(wire);

            ContentResult content=
                dispatch(
                    world,
                    player,
                    "::item",
                    packets
                );

            LocalItemSpawnCommandHandler.Result legacy=
                new LocalItemSpawnCommandHandler(
                    new BankState()
                ).handle(
                    new String[]{"item"},
                    "::item",
                    writer(
                        new ByteArrayOutputStream()
                    )
                );

            packets.flush();

            require(
                content==null&&
                legacy==null&&
                wire.size()==0,
                "missing item argument parity"
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(player);
            world.close();
        }
    }

    private static void legacyDispatcherDependencyRemoved(){
        for(Field field:
                LocalCommandDispatcher.class
                    .getDeclaredFields())
            require(
                field.getType()!=
                    LocalItemSpawnCommandHandler.class,
                "dispatcher retains item handler field "+
                field.getName()
            );

        for(Constructor<?> constructor:
                LocalCommandDispatcher.class
                    .getDeclaredConstructors())
            for(Class<?> parameter:
                    constructor.getParameterTypes())
                require(
                    parameter!=
                        LocalItemSpawnCommandHandler.class,
                    "dispatcher constructor retains item handler"
                );

        for(Field field:
                LocalSession.class
                    .getDeclaredFields())
            require(
                field.getType()!=
                    LocalItemSpawnCommandHandler.class,
                "session retains item handler field "+
                field.getName()
            );
    }

    private static ContentResult dispatch(
        World world,
        WorldPlayer player,
        String command,
        ServerPacketWriter packets
    )throws Exception{
        AtomicReference<ContentResult>
            result=new AtomicReference<>();
        AtomicReference<Throwable>
            failure=new AtomicReference<>();

        world.submitAndWait(
            player,
            ()->{
                try{
                    result.set(
                        world.content()
                            .dispatchCommand(
                                player,
                                command,
                                packets
                            )
                    );
                }catch(Throwable error){
                    failure.set(error);
                }
            },
            5_000L
        );

        if(failure.get()!=null)
            throw new AssertionError(
                "content item command failed "+
                command,
                failure.get()
            );

        return result.get();
    }

    private static void assertBinding(
        ContentRegistry.BindingInfo binding,
        String command
    ){
        require(
            binding!=null&&
            "locallab-core".equals(
                binding.moduleId)&&
            binding.priority==100&&
            binding.provenance==
                ContentProvenance.CUSTOM_LOCALLAB,
            command+" binding="+binding
        );
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream wire
    ){
        return new ServerPacketWriter(
            wire,
            new IsaacCipher(
                new int[]{151,152,153,154}
            )
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private ItemSpawnContentOwnershipTest(){}
}
