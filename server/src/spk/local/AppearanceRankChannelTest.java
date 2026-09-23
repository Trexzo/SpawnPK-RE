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

        if(LocalLoginTransport.LOCAL_DEV_RANK==0)
            throw new AssertionError(
                "login privilege fixture unexpectedly zero"
            );
        if(player.appearanceRank()==LocalLoginTransport.LOCAL_DEV_RANK)
            throw new AssertionError(
                "login privilege leaked into appearance rank"
            );

        player.setAppearanceRank(45);

        byte[] ranked=
            BootstrapPackets.appearanceBlock(
                "ranktest",
                null,
                player
            );

        if(readU16(ranked,5)!=45)
            throw new AssertionError(
                "ranked aC wire="+
                readU16(ranked,5)
            );

        int[] wornHead=new int[12];
        java.util.Arrays.fill(wornHead,-1);
        wornHead[EquipmentSlot.HEAD.appearanceIndex]=22131;
        AppearanceProjection wornProjection=
            readAppearanceProjection(
                BootstrapPackets.appearanceBlock(
                    "ranktest",
                    wornHead,
                    player
                )
            );
        if(wornProjection.head!=512+22131)
            throw new AssertionError(
                "worn staff partyhat did not occupy br[0]: "+
                wornProjection.head
            );
        if(wornProjection.extraItem!=-1)
            throw new AssertionError(
                "worn staff partyhat leaked into bs: "+
                wornProjection.extraItem
            );

        EquipmentState emptyEquipment=new EquipmentState();
        player.cosmetic().set(22131);
        player.syncEquipmentPresentation(emptyEquipment);
        AppearanceProjection overrideProjection=
            readAppearanceProjection(
                BootstrapPackets.appearanceBlock(
                    "ranktest",
                    emptyEquipment.appearanceItems(),
                    player
                )
            );
        if(overrideProjection.head!=0)
            throw new AssertionError(
                "cosmetic Override falsely occupied br[0]: "+
                overrideProjection.head
            );
        if(overrideProjection.extraItem!=22131)
            throw new AssertionError(
                "cosmetic Override did not use bs: "+
                overrideProjection.extraItem
            );
        player.cosmetic().clear();
        player.syncEquipmentPresentation(emptyEquipment);

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
            "aC=true loginPrivilegeSeparate=true explicit45=true "+
            "wornHeadVsOverrideBs=true "+
            "devOverride38=true clear0=true persisted=false"
        );
    }

    private static final class AppearanceProjection{
        final int head;
        final int extraItem;

        AppearanceProjection(int head,int extraItem){
            this.head=head;
            this.extraItem=extraItem;
        }
    }

    private static AppearanceProjection readAppearanceProjection(
        byte[] data
    ){
        int offset=7; // five state bytes + signed-short aC
        int head=0;
        for(int slot=0;slot<12;slot++){
            int high=data[offset++]&255;
            int value=0;
            if(high!=0){
                value=(high<<8)|(data[offset++]&255);
            }
            if(slot==EquipmentSlot.HEAD.appearanceIndex)
                head=value;
        }

        int extraFlag=data[offset++]&255;
        int extraItem=-1;
        if(extraFlag!=0){
            extraItem=readU16(data,offset);
        }
        return new AppearanceProjection(head,extraItem);
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
