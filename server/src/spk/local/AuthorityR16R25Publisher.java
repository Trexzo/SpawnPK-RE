package spk.local;

import java.io.*;

/**
 * Exact-current client state/publication helpers recovered by SpawnPK-Authority R16-R25.
 * These helpers encode only proven client contracts. They deliberately do not invent
 * server-owned gameplay rules such as enchantment results, repair costs, timed-status
 * duration units, or coin-bag exchange mutation.
 */
final class AuthorityR16R25Publisher {
    private AuthorityR16R25Publisher(){}

    static byte[] config87(int configId,int value){
        return new PacketPayloadWriter().putU16LE(configId).putI32X(value).toByteArray();
    }
    static byte[] control126(int key,String payload) throws IOException {
        return BootstrapPackets.widgetText126(key,payload==null?"":payload);
    }
    static void sendControl126(ServerPacketWriter w,int key,String payload) throws IOException {
        w.varShort(126,control126(key,payload));
    }
    static void sendConfig36(ServerPacketWriter w,int configId,int signedByteValue) throws IOException {
        if(signedByteValue<-128||signedByteValue>127)throw new IllegalArgumentException("signedByteValue="+signedByteValue);
        w.fixed(36,BootstrapPackets.config36(configId,signedByteValue));
    }
    static void sendConfig87(ServerPacketWriter w,int configId,int value) throws IOException {
        w.fixed(87,config87(configId,value));
    }

    // R19 bank state feeds.
    static void bankTabIcon(ServerPacketWriter w,int tabOneBased,int itemId,int quantity)throws IOException{
        if(tabOneBased<1||tabOneBased>8)throw new IllegalArgumentException("tab="+tabOneBased);
        sendControl126(w,15,tabOneBased+","+itemId+","+quantity);
    }
    static void bankSelectedTab(ServerPacketWriter w,int selectedOneBased)throws IOException{
        if(selectedOneBased<0||selectedOneBased>8)throw new IllegalArgumentException("selected="+selectedOneBased);
        sendControl126(w,16,Integer.toString(selectedOneBased));
    }

    // R20 Enchantment Chest state only; result computation remains server authority.
    static void enchantmentStatus(ServerPacketWriter w,int status)throws IOException{ sendControl126(w,38,Integer.toString(status)); }
    static void enchantmentSelectedResult(ServerPacketWriter w,int zeroBased)throws IOException{
        if(zeroBased<0||zeroBased>59)throw new IllegalArgumentException("selected="+zeroBased);
        sendControl126(w,71,Integer.toString(zeroBased));
    }
    static void widgetScrollControl(ServerPacketWriter w,int value)throws IOException{ sendControl126(w,63,Integer.toString(value)); }

    // R21 Repair Coffer state feeds. Balance text is a direct widget-text key.
    static void cofferGoldText(ServerPacketWriter w,String text)throws IOException{ sendControl126(w,45903,text); }
    static void cofferBloodShardText(ServerPacketWriter w,String text)throws IOException{ sendControl126(w,45914,text); }
    static void cofferRepairScrollText(ServerPacketWriter w,String text)throws IOException{ sendControl126(w,45925,text); }
    static void cofferAutoSelection(ServerPacketWriter w,int selectedWidget)throws IOException{
        if(selectedWidget!=45912&&selectedWidget!=45923)throw new IllegalArgumentException("selection="+selectedWidget);
        sendControl126(w,18,Integer.toString(selectedWidget));
    }

    // R23 native special-attack orb and timed-status lifecycle.
    static void specialAttackOrb(ServerPacketWriter w,double zeroToTen)throws IOException{
        if(Double.isNaN(zeroToTen)||Double.isInfinite(zeroToTen))throw new IllegalArgumentException("spec="+zeroToTen);
        sendControl126(w,31,Double.toString(zeroToTen));
    }
    static void timedStatus(ServerPacketWriter w,int typeId,long durationValue)throws IOException{
        if(typeId<1||typeId>51)throw new IllegalArgumentException("type="+typeId);
        sendControl126(w,14,typeId+","+durationValue);
    }

    // R24 tail controls.
    static void petLoadout(ServerPacketWriter w,int p0,int p1)throws IOException{ sendControl126(w,43,p0+" "+p1); }
    static void textColorSelection(ServerPacketWriter w,int fontColor,Integer shadowColor)throws IOException{
        sendControl126(w,44,shadowColor==null?Integer.toString(fontColor):fontColor+","+shadowColor);
    }
    static void textColorCommandToken(ServerPacketWriter w,String commandToken)throws IOException{
        if(commandToken==null||commandToken.trim().isEmpty())throw new IllegalArgumentException("commandToken");
        sendControl126(w,45,commandToken.trim());
    }
    static void writeOnly70(ServerPacketWriter w,int a,int b,long offset,String text)throws IOException{
        sendControl126(w,70,a+" "+b+" "+offset+" "+(text==null?"":text));
    }

    static String status(){
        return "AUTHORITY_R16_R25 exactClient=6232bae2 request103=true bankConfiguredWithdraw141=true coin140FailClosed=true bankStateR19=true enchantStateR20=true cofferStateR21=true scriptPacket250R22=true s2c126R23R24=true prayerMagicStyleR25=true";
    }
}
