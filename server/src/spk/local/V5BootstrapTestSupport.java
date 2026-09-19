package spk.local;

import java.io.*;
import java.util.*;

/** Shared assertions for the v5.5 native-sidebar bootstrap, including combat tab 0. */
final class V5BootstrapTestSupport {
    private V5BootstrapTestSupport() {}

    static void expectNativeSidebarInventoryAndFountain(InputStream in, IsaacCipher s2c) throws IOException {
        int[][] mappings = {
            {CombatInterfaceRepository.forWeapon(new EquipmentState().weapon()), CombatInterfaceRepository.TAB_INDEX},
            {BootstrapPackets.SKILLS_ROOT, 1},
            {BootstrapPackets.ACHIEVEMENTS_ROOT, BootstrapPackets.ACHIEVEMENTS_INDEX},
            {BootstrapPackets.INVENTORY_ROOT, 3},
            {BootstrapPackets.EQUIPMENT_ROOT, 4},
            {BootstrapPackets.PRAYER_ROOT, 5},
            {BootstrapPackets.MAGIC_ROOT, 6},
            {BootstrapPackets.CLAN_CHAT_ROOT, 7},
            {BootstrapPackets.FRIENDS_ROOT, 8},
            {BootstrapPackets.IGNORE_ROOT, 9},
            {BootstrapPackets.LOGOUT_ROOT, 10},
            {BootstrapPackets.OPTIONS_ROOT, 11},
            {BootstrapPackets.EMOTES_ROOT, 12},
            {BootstrapPackets.SPAWN_TAB_SHORTCUT_ROOT, BootstrapPackets.SPAWN_TAB_INDEX}
        };
        for (int[] m : mappings) {
            byte[] got = expectFixedBytes(in, s2c, 71, 3);
            byte[] want = BootstrapPackets.sidebar71(m[0], m[1]);
            if (!Arrays.equals(got, want))
                throw new AssertionError("sidebar71 got="+ClientPacketProbe.hex(got,16)+" expected="+ClientPacketProbe.hex(want,16));
        }
        // v5.4 server-owned combat-skill bootstrap. All seven are 99 / 13,034,431.
        for(int skill=0;skill<PlayerState.COMBAT_SKILL_COUNT;skill++){
            byte[] got=expectFixedBytes(in,s2c,134,6);
            byte[] want=BootstrapPackets.skill134(skill,PlayerState.XP_99,99);
            if(!Arrays.equals(got,want))
                throw new AssertionError("skill134 skill="+skill+" got="+ClientPacketProbe.hex(got,16)+" expected="+ClientPacketProbe.hex(want,16));
        }
        byte[] inv53 = expectVarShort(in, s2c, 53);
        if (Binary.u16(inv53,0) != 3214 || Binary.u16(inv53,2) != 28)
            throw new AssertionError("normal inventory bootstrap widget="+Binary.u16(inv53,0)+" slots="+Binary.u16(inv53,2));
        byte[] eq53 = expectVarShort(in, s2c, 53);
        if (Binary.u16(eq53,0) != EquipmentState.EQUIPMENT_WIDGET || Binary.u16(eq53,2) != EquipmentState.EQUIPMENT_SLOTS)
            throw new AssertionError("equipment bootstrap widget="+Binary.u16(eq53,0)+" slots="+Binary.u16(eq53,2));
        // WORLD-R6 now replays the exact HOME scene overlay after region/player
        // bootstrap and before the one unified initial NPC packet. Consume and
        // semantically validate all 85/101/151 packets against runtime base.
        expectHomeSceneOverlay(in,s2c);

        byte[] npc65 = expectVarShort(in, s2c, 65);
        HomeWorldRuntimePlan home=new HomeWorldRuntimePlan();
        java.util.List<NpcEntity> expectedNpcs=home.bootstrapNpcs(MovementState.INITIAL_X,MovementState.INITIAL_Y);
        byte[] wantNpc = NpcSyncEncoder.initial(expectedNpcs,MovementState.INITIAL_X,MovementState.INITIAL_Y);
        if (!Arrays.equals(npc65, wantNpc))
            throw new AssertionError("WORLD-R6 HOME npc bootstrap mismatch got="+ClientPacketProbe.hex(npc65,96)+" expected="+ClientPacketProbe.hex(wantNpc,96));
    }

    static byte[] expectFixedBytes(InputStream in, IsaacCipher c, int op, int len) throws IOException {
        int got=((in.read()&255)-c.nextInt())&255;
        if(got!=op)throw new AssertionError("op="+got+" expected="+op);
        return Binary.readExactly(in,len);
    }

    static byte[] expectVarShort(InputStream in, IsaacCipher c, int op) throws IOException {
        int got=((in.read()&255)-c.nextInt())&255;
        if(got!=op)throw new AssertionError("op="+got+" expected="+op);
        int n=((in.read()&255)<<8)|(in.read()&255);
        return Binary.readExactly(in,n);
    }
    private static void expectHomeSceneOverlay(InputStream in,IsaacCipher c) throws IOException {
        int bx=-1,by=-1,mut=0;
        java.util.List<HomeObjectOverlayRepository.Mutation> expected=HomeObjectOverlayRepository.all();
        while(mut<expected.size()){
            int op=((in.read()&255)-c.nextInt())&255;
            if(op==85){
                byte[] p=Binary.readExactly(in,2);
                by=SceneObjectPacketCodec.decO(p[0]); bx=SceneObjectPacketCodec.decO(p[1]);
                continue;
            }
            HomeObjectOverlayRepository.Mutation e=expected.get(mut++);
            int coord,shapeRot,id=-1;
            if(op==101){
                if(!e.isRemove()) throw new AssertionError("HOME scene expected add seq="+e.seq+" got101");
                byte[] p=Binary.readExactly(in,2); shapeRot=SceneObjectPacketCodec.decO(p[0]); coord=SceneObjectPacketCodec.decY(p[1]);
            } else if(op==151){
                if(!e.isAdd()) throw new AssertionError("HOME scene expected remove seq="+e.seq+" got151");
                byte[] p=Binary.readExactly(in,4); coord=SceneObjectPacketCodec.decN(p[0]); id=SceneObjectPacketCodec.decS(p[1],p[2]); shapeRot=SceneObjectPacketCodec.decP(p[3]);
                if(id!=e.wireObjectId) throw new AssertionError("HOME scene id seq="+e.seq+" got="+id+" expected="+e.wireObjectId);
            } else throw new AssertionError("unexpected HOME scene opcode="+op+" at mutation="+mut);
            int lx=bx+((coord>>>4)&7),ly=by+(coord&7);
            int wx=MovementState.REGION_BASE_X+lx,wy=MovementState.REGION_BASE_Y+ly;
            if(wx!=e.worldX||wy!=e.worldY||(shapeRot>>>2)!=e.shape||(shapeRot&3)!=e.rotation)
                throw new AssertionError("HOME scene semantic mismatch seq="+e.seq+" got="+wx+","+wy+" shapeRot="+shapeRot+" expected="+e);
        }
    }

}
