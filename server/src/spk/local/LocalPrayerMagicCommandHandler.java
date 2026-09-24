package spk.local;

import java.io.IOException;

/**
 * Command adapter for prayer/spellbook state changes.
 *
 * Invocation remains on the authoritative WorldPulse thread. Mechanics stay in
 * PrayerState / MagicState; this class only removes command parsing/routing
 * responsibility from LocalSession.
 */
final class LocalPrayerMagicCommandHandler {
    private final PrayerState prayers;
    private final MagicState magic;

    LocalPrayerMagicCommandHandler(PrayerState prayers,MagicState magic){
        this.prayers=java.util.Objects.requireNonNull(prayers,"prayers");
        this.magic=java.util.Objects.requireNonNull(magic,"magic");
    }

    boolean handle(String[] p,String clean,ServerPacketWriter serverPackets,String tag)throws IOException{
        if(p==null||p.length==0)return false;
        String command=p[0];

        if(p.length>=2 && command.equalsIgnoreCase("prayericon")){
            int icon=parseInt(p[1],-999);
            String r=prayers.publishManualHeadIcon(icon,serverPackets);
            System.out.println(tag+"V510_PRAYER_ICON result="+r);
            return true;
        }

        return false;
    }

    private static int parseInt(String s,int fallback){
        try{return Integer.parseInt(s);}catch(Exception e){return fallback;}
    }
}
