package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.List;

public final class AppearanceRankChannelTest {
    public static void main(String[] args)throws Exception{
        PlayerState player=
            new PlayerState();

        if(player.appearanceRank()!=0)
            throw new AssertionError(
                "default appearance rank"
            );

        byte[] plain=
            BootstrapPackets.appearanceBlock(
                "ranktest",
                null,
                player
            );

        if(readU16(plain,5)!=0)
            throw new AssertionError(
                "default aC wire="+
                readU16(plain,5)
            );

        player.setAppearanceRank(
            LocalLoginTransport.LOCAL_DEV_RANK
        );

        byte[] ranked=
            BootstrapPackets.appearanceBlock(
                "ranktest",
                null,
                player
            );

        if(readU16(ranked,5)!=
                LocalLoginTransport.LOCAL_DEV_RANK)
            throw new AssertionError(
                "ranked aC wire="+
                readU16(ranked,5)
            );

        EquipmentState equipment=
            new EquipmentState();
        PlayerPresentationService presentation=
            new PlayerPresentationService(
                new DevAuthorityWorkbench()
            );
        LocalDevPlayerCommandHandler commands=
            new LocalDevPlayerCommandHandler(
                presentation,
                equipment,
                player
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

        List<String> set=
            commands.handle(
                new String[]{
                    "devplayer",
                    "rank",
                    "38"
                },
                "ranktest",
                packets
            );

        if(player.appearanceRank()!=38||
           set==null||
           set.isEmpty()||
           !set.get(0).contains(
               "rank=38"
           )||
           wire.size()==0)
            throw new AssertionError(
                "dev rank set="+set+
                " value="+
                player.appearanceRank()+
                " wire="+wire.size()
            );

        List<String> clear=
            commands.handle(
                new String[]{
                    "devplayer",
                    "rank",
                    "clear"
                },
                "ranktest",
                packets
            );

        if(player.appearanceRank()!=0||
           clear==null||
           !clear.get(0).contains(
               "rank=0"
           ))
            throw new AssertionError(
                "dev rank clear="+clear+
                " value="+
                player.appearanceRank()
            );

        boolean rejected=false;
        try{
            player.setAppearanceRank(386);
        }catch(IllegalArgumentException expected){
            rejected=true;
        }

        if(!rejected)
            throw new AssertionError(
                "out-of-range rank accepted"
            );

        System.out.println(
            "APPEARANCE_RANK_CHANNEL_PASS "+
            "aC=true localDev205=true "+
            "devOverride38=true clear0=true persisted=false"
        );
    }

    private static int readU16(
        byte[] data,
        int offset
    ){
        return
            ((data[offset]&255)<<8)|
            (data[offset+1]&255);
    }
}
