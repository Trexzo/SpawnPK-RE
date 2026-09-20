package spk.content.api;

/** Semantic NPC-option intent with raw opcode/framing removed. */
public interface ContentNpcOptionContext {
    int npcDefinitionId();
    int option();
    int sceneIndex();
    int worldX();
    int worldY();
}
