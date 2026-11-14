package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/** R46 isolated in-memory original-v308 skill134 and arithmetic guards. */
public final class R46LocalLabMortalMagicExperienceTest {
    public static void main(String[] args)throws Exception {
        String r6=System.getProperty("spk.local.r6MortalFixture");
        String r45=System.getProperty("spk.local.r45MortalMagic");
        String r46=System.getProperty("spk.local.r46MortalMagicXp");
        try{
            System.clearProperty("spk.local.r6MortalFixture");
            System.clearProperty("spk.local.r45MortalMagic");
            System.clearProperty("spk.local.r46MortalMagicXp");
            WorldPlayer player=new WorldPlayer();
            NpcEntity mortal=new NpcEntity(16382,1,3088,3495);
            mortal.enableLocalLabMortalFixture(10);
            ByteArrayOutputStream output=new ByteArrayOutputStream();
            ServerPacketWriter writer=new ServerPacketWriter(
                output,new IsaacCipher(new int[]{1,2,3,4}));
            if(LocalLabMortalMagicExperience.enabled())
                throw new AssertionError("default magic XP must be OFF");
            if(LocalLabMortalMagicExperience.awardPublishedDamage(
                10,mortal,player,writer)!=null)
                throw new AssertionError("default magical XP was awarded");
            if(player.playerState().xp(PlayerState.MAGIC)!=PlayerState.XP_99)
                throw new AssertionError("default Magic XP changed");
            if(output.size()!=0)
                throw new AssertionError("default Magic S2C134 emitted");

            System.setProperty("spk.local.r46MortalMagicXp","true");
            if(LocalLabMortalMagicExperience.enabled())
                throw new AssertionError("R46 unpaired setting enabled");
            System.setProperty("spk.local.r6MortalFixture","true");
            if(LocalLabMortalMagicExperience.enabled())
                throw new AssertionError("R46 without R45 enabled");
            System.setProperty("spk.local.r45MortalMagic","true");
            if(!LocalLabMortalMagicExperience.enabled())
                throw new AssertionError("R46 explicit pairing not enabled");

            NpcEntity nonfixture=new NpcEntity(103,7605,3087,3495);
            String skipped=LocalLabMortalMagicExperience.awardPublishedDamage(
                10,nonfixture,player,writer);
            if(skipped==null||!skipped.contains("NONMORTAL"))
                throw new AssertionError("ordinary NPC incorrectly awarded XP");
            if(output.size()!=0)
                throw new AssertionError("nonfixture caused network write");

            LocalLabMortalMagicExperience.preflight(10,player);
            String granted=LocalLabMortalMagicExperience.awardPublishedDamage(
                10,mortal,player,writer);
            if(granted==null||
               !granted.startsWith("R46_MAGIC_XP_GRANTED")||
               !granted.contains("skill=MAGIC damage=10")||
               !granted.contains("originalSpawnpkXpRate=false"))
                throw new AssertionError("custom Magic award failed: "+granted);
            if(player.playerState().xp(PlayerState.MAGIC)!=PlayerState.XP_99+10||
               player.playerState().currentLevel(PlayerState.MAGIC)!=99)
                throw new AssertionError("Magic XP or current level mismatch");
            byte[] actual=output.toByteArray();
            if(actual.length!=7)
                throw new AssertionError("S2C134 expected exactly seven bytes");
            IsaacCipher decode=new IsaacCipher(new int[]{1,2,3,4});
            if(((actual[0]&255)-decode.nextInt()&255)!=134)
                throw new AssertionError("native opcode134 ISAAC mismatch");
            if(!Arrays.equals(
                Arrays.copyOfRange(actual,1,7),
                BootstrapPackets.skill134(
                    PlayerState.MAGIC,PlayerState.XP_99+10,99)))
                throw new AssertionError("v308 native skill134 payload mismatch");

            player.playerState().setXp(PlayerState.MAGIC,Integer.MAX_VALUE);
            boolean overflowRejected=false;
            try{
                LocalLabMortalMagicExperience.preflight(10,player);
            }catch(ArithmeticException expected){
                overflowRejected=true;
            }
            if(!overflowRejected||
               player.playerState().xp(PlayerState.MAGIC)!=Integer.MAX_VALUE||
               output.size()!=7)
                throw new AssertionError("overflow preflight not side-effect-free");

            System.out.println(
                "R46_CUSTOM_MORTAL_MAGIC_XP_TEST_PASS defaultOff=true "+
                "flagPairing=true ordinaryNpcDenied=true xpPerDamage10=true "+
                "nativeIsaac134=true currentLevel99=true overflowFailClosed=true");
        }finally{
            restore("spk.local.r6MortalFixture",r6);
            restore("spk.local.r45MortalMagic",r45);
            restore("spk.local.r46MortalMagicXp",r46);
        }
    }

    private static void restore(String key,String value){
        if(value==null)System.clearProperty(key);
        else System.setProperty(key,value);
    }
}
