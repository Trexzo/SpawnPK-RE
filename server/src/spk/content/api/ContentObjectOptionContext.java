package spk.content.api;

/** Semantic object-option intent with transport/opcode details removed. */
public interface ContentObjectOptionContext {
    int objectId();
    int option();
    int worldX();
    int worldY();
}
