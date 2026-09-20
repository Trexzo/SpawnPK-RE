package spk.content.api;

import java.io.IOException;

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
    )throws IOException;

    void runEnergy(int energy)throws IOException;

    void specialEnergy(
        int percent
    )throws IOException;

    void animationAndGfx(
        int animationId,
        int gfxId,
        int gfxHeight,
        int gfxDelay
    )throws IOException;
}
