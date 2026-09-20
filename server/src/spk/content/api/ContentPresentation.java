package spk.content.api;

/**
 * Safe presentation publisher for content.
 *
 * Implementations may use the exact-current packet layer internally, but content
 * modules never receive ServerPacketWriter, ISAAC state or packet buffers.
 */
public interface ContentPresentation {
    void skill(
        ContentSkill skill,
        int experience,
        int currentLevel
    );

    void runEnergy(int energy);

    void specialEnergy(
        int percent
    );

    void animationAndGfx(
        int animationId,
        int gfxId,
        int gfxHeight,
        int gfxDelay
    );
}
