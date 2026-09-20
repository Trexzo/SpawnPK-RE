package spk.local;

import java.io.IOException;

/**
 * LOCAL_DEV item spawn command adapter.
 *
 * Transport/decoder state is intentionally not exposed here. The caller keeps
 * decoder-alignment diagnostics while this adapter owns only command parsing and
 * BankState item spawning.
 */
final class LocalItemSpawnCommandHandler {
    private final BankState bank;

    LocalItemSpawnCommandHandler(BankState bank){
        this.bank=java.util.Objects.requireNonNull(bank,"bank");
    }

    Result handle(String[] p,String rawCommand,ServerPacketWriter serverPackets)throws IOException{
        if(p==null||p.length<2)return null;
        if(!p[0].equalsIgnoreCase("item")&&!p[0].equalsIgnoreCase("tabitem"))return null;

        int id=parseInt(p[1],-1);
        int amount=p.length>=3?parseAmount(p[2],1):1;
        String spawn=bank.spawnItem(id,amount,serverPackets);

        return new Result(
            "V522_ITEM_COMMAND source="+p[0].toLowerCase(java.util.Locale.ROOT)+
            " command="+rawCommand+" result="+spawn,
            "ITEM_SPAWN"
        );
    }

    static final class Result {
        final String logText;
        final String saveReason;

        Result(String logText,String saveReason){
            this.logText=logText;
            this.saveReason=saveReason;
        }
    }

    private static int parseInt(String s,int fallback){
        try{return Integer.parseInt(s);}catch(Exception e){return fallback;}
    }

    private static int parseAmount(String s,int fallback){
        if(s==null)return fallback;
        String t=s.trim().toLowerCase(java.util.Locale.ROOT).replace(",","");
        long mul=1L;
        if(t.endsWith("k")){mul=1_000L;t=t.substring(0,t.length()-1);}
        else if(t.endsWith("m")){mul=1_000_000L;t=t.substring(0,t.length()-1);}
        else if(t.endsWith("b")){mul=1_000_000_000L;t=t.substring(0,t.length()-1);}
        try{
            long base=Long.parseLong(t);
            long v=Math.max(1L,Math.min(1_000_000_000L,base*mul));
            return (int)v;
        }catch(Exception e){
            return fallback;
        }
    }
}
