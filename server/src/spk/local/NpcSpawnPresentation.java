package spk.local;

/** Exact optional new-NPC presentation fields currently recovered from packet 65. */
final class NpcSpawnPresentation {
    final Integer particleSelector; // null = presence bit 0 (AUTO/ABSENT)
    private NpcSpawnPresentation(Integer particleSelector){ this.particleSelector=particleSelector; }
    static NpcSpawnPresentation auto(){ return new NpcSpawnPresentation(null); }
    static NpcSpawnPresentation particle(int selector){
        if(selector<0||selector>255) throw new IllegalArgumentException("particle selector");
        return new NpcSpawnPresentation(selector);
    }
}
