package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalPrayerMagicCommandHandlerTest {
    public static void main(String[] args)throws Exception{
        WorldPlayer player=new WorldPlayer();
        LocalPrayerMagicCommandHandler h=
            new LocalPrayerMagicCommandHandler(player.prayers());

        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ServerPacketWriter w=new ServerPacketWriter(wire,new IsaacCipher(new int[]{1,2,3,4}));

        int before=wire.size();
        if(!h.handle(new String[]{"prayerbook","curses"},"prayerbook curses",w,"[pm-test] "))
            throw new AssertionError("prayerbook not handled");
        if(player.prayers().book()!=PrayerDefinitionRepository.Book.CURSES)
            throw new AssertionError("prayerbook state not delegated");
        if(wire.size()<=before)throw new AssertionError("prayerbook emitted no packets");

        before=wire.size();
        if(!h.handle(new String[]{"spellbook","ancient"},"spellbook ancient",w,"[pm-test] "))
            throw new AssertionError("spellbook not handled");
        if(player.magic().book()!=SpellDefinitionRepository.Book.ANCIENT)
            throw new AssertionError("spellbook state not delegated");
        if(wire.size()<=before)throw new AssertionError("spellbook emitted no packets");

        before=wire.size();
        if(!h.handle(new String[]{"prayericon","3"},"prayericon 3",w,"[pm-test] "))
            throw new AssertionError("prayericon not handled");
        if(player.prayers().manualHeadIcon()!=3)
            throw new AssertionError("prayericon state not delegated");
        if(wire.size()<=before)throw new AssertionError("prayericon emitted no packet");

        if(!h.handle(new String[]{"prayeroff"},"prayeroff",w,"[pm-test] "))
            throw new AssertionError("prayeroff not handled");

        if(h.handle(new String[]{"regionload","12850"},"regionload 12850",w,"[pm-test] "))
            throw new AssertionError("unrelated command must remain outside prayer/magic handler");

        System.out.println("LOCAL_PRAYER_MAGIC_COMMAND_HANDLER_PASS prayerBook=true spellBook=true prayerIcon=true unrelatedRejected=true");
    }
}
