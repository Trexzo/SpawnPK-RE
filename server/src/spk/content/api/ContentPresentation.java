package spk.content.api;

/**
 * Safe presentation publisher for content.
 *
 * Implementations may use the exact-current packet layer internally, but content
 * modules never receive ServerPacketWriter, ISAAC state or packet buffers.
 */
public interface ContentPresentation {
    /**
     * Semantic standard-dialogue presentation capability.
     *
     * The production LocalLab runtime overrides this capability. The default
     * keeps alternate/test implementations source-compatible while failing
     * closed if they do not provide dialogue presentation.
     */
    default ContentDialoguePresentation dialogue(){
        throw new UnsupportedOperationException(
            "dialogue presentation unavailable"
        );
    }

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
