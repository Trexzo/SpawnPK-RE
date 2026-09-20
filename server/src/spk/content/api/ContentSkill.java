package spk.content.api;

/** Stable semantic combat-skill identifiers exposed to content modules. */
public enum ContentSkill {
    ATTACK(0),
    DEFENCE(1),
    STRENGTH(2),
    HITPOINTS(3),
    RANGED(4),
    PRAYER(5),
    MAGIC(6);

    private final int protocolIndex;

    ContentSkill(int protocolIndex){
        this.protocolIndex=protocolIndex;
    }

    public int protocolIndex(){
        return protocolIndex;
    }
}
