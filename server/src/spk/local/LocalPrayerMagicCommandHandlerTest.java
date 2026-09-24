package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public final class LocalPrayerMagicCommandHandlerTest {
    public static void main(String[] args)throws Exception{
        WorldPlayer player=
            new WorldPlayer();

        LocalPrayerMagicCommandHandler handler=
            new LocalPrayerMagicCommandHandler(
                player.prayers()
            );

        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();

        ServerPacketWriter packets=
            new ServerPacketWriter(
                wire,
                new IsaacCipher(
                    new int[]{1,2,3,4}
                )
            );

        int before=
            wire.size();

        String applied=
            handler.prayerIcon(
                3,
                packets
            );

        packets.flush();

        if(!applied.equals(
                "V510_PRAYER_ICON result=LOCAL_PRAYER_HEADICON_FIXTURE value=3 semanticMapping=UNASSIGNED_R25"))
            throw new AssertionError(
                "prayericon effect="+
                applied
            );

        if(player.prayers()
                .manualHeadIcon()!=3)
            throw new AssertionError(
                "prayericon state not delegated"
            );

        if(wire.size()<=before)
            throw new AssertionError(
                "prayericon emitted no packet"
            );

        int beforeRejected=
            wire.size();

        String rejected=
            handler.prayerIcon(
                21,
                packets
            );

        packets.flush();

        if(!rejected.equals(
                "V510_PRAYER_ICON result=REJECTED_HEADICON_RANGE expected=-1..20"))
            throw new AssertionError(
                "prayericon reject="+
                rejected
            );

        if(wire.size()!=beforeRejected)
            throw new AssertionError(
                "rejected prayericon emitted packet"
            );

        for(Method method:
                LocalPrayerMagicCommandHandler.class
                    .getDeclaredMethods())
            if("handle".equals(
                    method.getName()))
                throw new AssertionError(
                    "raw prayer/magic command parser remains"
                );

        Constructor<?>[] constructors=
            LocalPrayerMagicCommandHandler.class
                .getDeclaredConstructors();

        if(constructors.length!=1||
           constructors[0]
               .getParameterCount()!=1||
           constructors[0]
               .getParameterTypes()[0]!=
                PrayerState.class)
            throw new AssertionError(
                "prayer handler constructor boundary changed"
            );

        System.out.println(
            "LOCAL_PRAYER_MAGIC_COMMAND_HANDLER_PASS "+
            "prayerIconEffect=true "+
            "rangeFailClosed=true "+
            "rawParserAbsent=true "+
            "magicDependencyTrim=true"
        );
    }

    private LocalPrayerMagicCommandHandlerTest(){}
}
