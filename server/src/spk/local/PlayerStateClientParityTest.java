package spk.local;

import java.lang.reflect.*;
import java.util.*;

/** Exact pinned-client parser proof for packet134 values and player appearance combat/comp state. */
public final class PlayerStateClientParityTest {
    public static void main(String[] args)throws Exception{
        PlayerState state=new PlayerState();
        Class<?> bufferClass=Class.forName("rs.x.e");
        Method y=bufferClass.getMethod("y");
        Method X=bufferClass.getMethod("X");
        for(int skill=0;skill<PlayerState.COMBAT_SKILL_COUNT;skill++){
            byte[] payload=BootstrapPackets.skill134(skill,state.xp(skill),state.currentLevel(skill));
            Object buf=bufferClass.getConstructor(byte[].class).newInstance((Object)payload);
            int gotSkill=(Integer)y.invoke(buf);
            int gotXp=(Integer)X.invoke(buf);
            int gotCurrent=(Integer)y.invoke(buf);
            if(gotSkill!=skill || gotXp!=PlayerState.XP_99 || gotCurrent!=99)
                throw new AssertionError("skill134 decode skill="+gotSkill+" xp="+gotXp+" current="+gotCurrent);
        }

        Class<?> playerClass=Class.forName("rs.a.k");
        Class<?> colorClass=Class.forName("rs.n.c.w");
        Field g=colorClass.getDeclaredField("g");g.setAccessible(true);
        @SuppressWarnings("unchecked") Map<Integer,String> palette=(Map<Integer,String>)g.get(null);
        palette.put(63015,"FF9620"); palette.put(63011,"940000"); palette.put(63009,"DFD6D7"); palette.put(63007,"0");
        int[] ap=new int[12];Arrays.fill(ap,-1);ap[EquipmentSlot.CAPE.appearanceIndex]=23063;
        byte[] appearance=BootstrapPackets.appearanceBlock("opensrc",ap,state);
        Object player=playerClass.getConstructor().newInstance();
        Object buffer=bufferClass.getConstructor(byte[].class).newInstance((Object)appearance);
        playerClass.getMethod("a",bufferClass).invoke(player,buffer);
        Field combat=playerClass.getField("bc");
        if(combat.getInt(player)!=126) throw new AssertionError("combat="+combat.getInt(player));
        System.out.println("V54_PLAYER_STATE_CLIENT_PARITY_PASS skills0to6=99 xp="+PlayerState.XP_99+" hitpoints=99 prayer=99 combat=126 packet134Exact=true");
    }
}
