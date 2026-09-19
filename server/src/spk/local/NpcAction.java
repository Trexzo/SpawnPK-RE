package spk.local;

/** Exact current-client NPC interaction action. opcode155=normal first option; opcode72=Attack. */
final class NpcAction {
    final int opcode;
    final int sceneIndex;
    NpcAction(int opcode, int sceneIndex) { this.opcode=opcode; this.sceneIndex=sceneIndex; }
    @Override public String toString(){ return "NpcAction{opcode="+opcode+",sceneIndex="+sceneIndex+"}"; }
}
