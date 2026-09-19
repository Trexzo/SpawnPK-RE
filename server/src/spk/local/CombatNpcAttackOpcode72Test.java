package spk.local;

import java.io.*;

/** Live regression for the exact current-client NPC Attack action. */
public final class CombatNpcAttackOpcode72Test {
    public static void main(String[] args) throws Exception {
        check(131, new byte[]{0x00,0x03}, 1488); // WORLD ordinal31 -> scene131 Player dummy
        check(129, new byte[]{0x00,0x01}, 1489); // WORLD ordinal29 -> scene129 PvM dummy
        check(134, new byte[]{0x00,0x06}, 1488); // WORLD ordinal34 -> scene134 Player dummy
        if (ClientPacketProbe.framingOnlyFixedLength(72) != -1)
            throw new AssertionError("opcode72 must not remain framing-only");
        System.out.println("V56_COMBAT_NPC_ATTACK_OPCODE72_PASS codec=BE_SHORT_A liveVectors=00_03->131/1488,00_01->129/1489,00_06->134/1488 semantic=NPC_ATTACK");
    }

    private static void check(int expectedScene, byte[] body, int expectedDef) throws Exception {
        int[] seed={11,22,33,44};
        IsaacCipher enc=new IsaacCipher(seed.clone());
        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        wire.write((72 + enc.nextInt()) & 0xff);
        wire.write(body);
        ClientPacketProbe p=new ClientPacketProbe(new ByteArrayInputStream(wire.toByteArray()),new IsaacCipher(seed.clone()),"[opcode72-test] ");
        if(!p.readNextKnownPacket()) throw new AssertionError("decode false");
        NpcAction a=p.takeNpcAction();
        if(a==null || a.opcode!=72 || a.sceneIndex!=expectedScene) throw new AssertionError(String.valueOf(a));
        int ord=expectedScene-100;
        HomeNpcSpawnRepository.Spawn spawn=null;
        for(HomeNpcSpawnRepository.Spawn s:HomeNpcSpawnRepository.all()) if(s.ordinal==ord){spawn=s;break;}
        if(spawn==null || spawn.npcDefinitionId!=expectedDef)
            throw new AssertionError("scene="+expectedScene+" ordinal="+ord+" spawn="+spawn+" expectedDef="+expectedDef);
    }
}
