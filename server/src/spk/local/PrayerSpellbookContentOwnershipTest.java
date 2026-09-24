package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;

public final class PrayerSpellbookContentOwnershipTest {
    public static void main(String[] args)throws Exception{
        bindingOwnership();
        defaultCapabilitiesFailClosed();

        prayerBookParity("::prayerbook curses","curses");
        prayerBookParity("::prayerbook prayer","prayer");
        prayerBookParity("::prayerbook invalid","invalid");

        spellBookParity("::spellbook ancient","ancient");
        spellBookParity("::spellbook lunars","lunars");
        spellBookParity("::spellbook invalid","invalid");

        prayerOffParity();
        missingArgumentParity();
        migratedLegacyRoutesRemoved();
        prayerIconRemainsRuntimeOwned();

        System.out.println(
            "PRAYER_SPELLBOOK_CONTENT_OWNERSHIP_PASS "+
            "prayerBook=true "+
            "spellBook=true "+
            "prayerOff=true "+
            "typedBooks=true "+
            "wireParity=true "+
            "invalidParity=true "+
            "missingArgumentParity=true "+
            "legacyFallback=false "+
            "prayerIconRuntimeOwned=true"
        );
    }

    private static void bindingOwnership(){
        World world=
            World.isolatedForTest(20L);

        try{
            assertBinding(
                world.content()
                    .commandBinding(
                        "prayerbook"
                    ),
                "prayerbook"
            );
            assertBinding(
                world.content()
                    .commandBinding(
                        "spellbook"
                    ),
                "spellbook"
            );
            assertBinding(
                world.content()
                    .commandBinding(
                        "prayeroff"
                    ),
                "prayeroff"
            );
        }finally{
            world.close();
        }
    }

    private static void defaultCapabilitiesFailClosed(){
        ContentPlayer player=
            ContentRuntimeAdapters.player(
                new WorldPlayer()
            );

        expectUnsupported(
            ()->player.switchPrayerBook(
                ContentPrayerBook.NORMAL
            ),
            "prayer book mutation unavailable"
        );
        expectUnsupported(
            ()->player.switchSpellBook(
                ContentSpellBook.MODERN
            ),
            "spell book mutation unavailable"
        );
        expectUnsupported(
            player::deactivatePrayers,
            "prayer deactivation unavailable"
        );

        requireDefault(
            "switchPrayerBook",
            ContentPrayerBook.class
        );
        requireDefault(
            "switchSpellBook",
            ContentSpellBook.class
        );
        requireDefault(
            "deactivatePrayers"
        );
    }

    private static void prayerBookParity(
        String command,
        String token
    )throws Exception{
        World world=
            World.isolatedForTest(20L);
        WorldPlayer player=
            new WorldPlayer();
        PrayerState expectedState=
            new PrayerState();

        try{
            world.registerPlayer(
                player,
                "prayer-content-owner"
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

            ContentResult result=
                dispatch(
                    world,
                    player,
                    command,
                    actual
                );

            String expectedResult=
                expectedState.switchBook(
                    token,
                    expected
                );

            actual.flush();
            expected.flush();

            require(
                result!=null&&
                result.saveReason()==null,
                "prayerbook result="+result
            );
            require(
                (
                    "V510_PRAYER_BOOK command="+
                    clean(command)+
                    " result="+
                    expectedResult
                ).equals(
                    result.logText()),
                "prayerbook log parity command="+
                command+
                " actual="+
                result.logText()
            );
            require(
                Arrays.equals(
                    actualWire.toByteArray(),
                    expectedWire.toByteArray()
                ),
                "prayerbook wire parity command="+
                command
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(player);
            world.close();
        }
    }

    private static void spellBookParity(
        String command,
        String token
    )throws Exception{
        World world=
            World.isolatedForTest(20L);
        WorldPlayer player=
            new WorldPlayer();
        MagicState expectedState=
            new MagicState();

        try{
            world.registerPlayer(
                player,
                "spell-content-owner"
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

            ContentResult result=
                dispatch(
                    world,
                    player,
                    command,
                    actual
                );

            String expectedResult=
                expectedState.switchBook(
                    token,
                    expected
                );

            actual.flush();
            expected.flush();

            require(
                result!=null&&
                result.saveReason()==null,
                "spellbook result="+result
            );
            require(
                (
                    "V510_SPELL_BOOK command="+
                    clean(command)+
                    " result="+
                    expectedResult
                ).equals(
                    result.logText()),
                "spellbook log parity command="+
                command+
                " actual="+
                result.logText()
            );
            require(
                Arrays.equals(
                    actualWire.toByteArray(),
                    expectedWire.toByteArray()
                ),
                "spellbook wire parity command="+
                command
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(player);
            world.close();
        }
    }

    private static void prayerOffParity()
        throws Exception
    {
        World world=
            World.isolatedForTest(20L);
        WorldPlayer player=
            new WorldPlayer();
        PrayerState expectedState=
            new PrayerState();

        try{
            world.registerPlayer(
                player,
                "prayer-off-content-owner"
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

            ContentResult result=
                dispatch(
                    world,
                    player,
                    "::prayeroff",
                    actual
                );

            String expectedResult=
                expectedState.deactivateAll(
                    expected
                );

            actual.flush();
            expected.flush();

            require(
                result!=null&&
                result.saveReason()==null&&
                (
                    "V510_PRAYER_OFF result="+
                    expectedResult
                ).equals(
                    result.logText()),
                "prayeroff result="+result
            );
            require(
                Arrays.equals(
                    actualWire.toByteArray(),
                    expectedWire.toByteArray()
                ),
                "prayeroff wire parity"
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
        for(String command:
                new String[]{
                    "::prayerbook",
                    "::spellbook"
                }){
            World world=
                World.isolatedForTest(20L);
            WorldPlayer player=
                new WorldPlayer();

            try{
                world.registerPlayer(
                    player,
                    "book-missing-owner"
                );
                world.start();

                ByteArrayOutputStream wire=
                    new ByteArrayOutputStream();
                ServerPacketWriter packets=
                    writer(wire);

                ContentResult result=
                    dispatch(
                        world,
                        player,
                        command,
                        packets
                    );

                packets.flush();

                require(
                    result==null&&
                    wire.size()==0,
                    "missing argument parity command="+
                    command+
                    " result="+result+
                    " wire="+wire.size()
                );
            }finally{
                if(player.registered())
                    world.unregisterPlayer(player);
                world.close();
            }
        }
    }

    private static void migratedLegacyRoutesRemoved()
        throws Exception
    {
        LocalPrayerMagicCommandHandler legacy=
            new LocalPrayerMagicCommandHandler(
                new PrayerState(),
                new MagicState()
            );

        for(String[] tokens:
                new String[][]{
                    {"prayerbook","curses"},
                    {"spellbook","ancient"},
                    {"prayeroff"}
                }){
            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            ServerPacketWriter packets=
                writer(wire);

            boolean claimed=
                legacy.handle(
                    tokens,
                    String.join(
                        " ",
                        tokens
                    ),
                    packets,
                    "[prayer-content-test] "
                );

            packets.flush();

            require(
                !claimed&&
                wire.size()==0,
                "legacy route still claimed "+
                Arrays.toString(tokens)
            );
        }
    }

    private static void prayerIconRemainsRuntimeOwned()
        throws Exception
    {
        LocalPrayerMagicCommandHandler legacy=
            new LocalPrayerMagicCommandHandler(
                new PrayerState(),
                new MagicState()
            );
        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();
        ServerPacketWriter packets=
            writer(wire);

        boolean claimed=
            legacy.handle(
                new String[]{
                    "prayericon",
                    "3"
                },
                "prayericon 3",
                packets,
                "[prayer-content-test] "
            );

        packets.flush();

        require(
            claimed&&
            wire.size()>0,
            "prayericon runtime fixture ownership changed"
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
                "content command failed "+
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

    private static void requireDefault(
        String name,
        Class<?>... parameters
    ){
        try{
            Method method=
                ContentPlayer.class.getMethod(
                    name,
                    parameters
                );

            require(
                method.isDefault(),
                name+
                " must remain default-compatible"
            );
        }catch(ReflectiveOperationException error){
            throw new AssertionError(
                "missing ContentPlayer method "+
                name,
                error
            );
        }
    }

    private static void expectUnsupported(
        Runnable action,
        String message
    ){
        try{
            action.run();
        }catch(UnsupportedOperationException expected){
            require(
                expected.getMessage()!=null&&
                expected.getMessage().contains(
                    message
                ),
                "wrong unsupported message "+
                expected.getMessage()
            );
            return;
        }

        throw new AssertionError(
            "expected unsupported capability "+
            message
        );
    }

    private static String clean(
        String command
    ){
        String value=
            command==null
                ?""
                :command.trim();

        if(value.startsWith("::"))
            value=value.substring(2);

        return value;
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream wire
    ){
        return new ServerPacketWriter(
            wire,
            new IsaacCipher(
                new int[]{161,162,163,164}
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

    private PrayerSpellbookContentOwnershipTest(){}
}
