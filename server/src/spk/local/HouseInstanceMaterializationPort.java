package spk.local;

/**
 * Integration boundary to #158 WorldInstance ownership.
 *
 * The Construction aggregate does not allocate maps or own instance lifecycle.
 * A coordinator/adapter may materialize an immutable house snapshot in #158 and
 * return its opaque semantic instance reference.
 */
interface HouseInstanceMaterializationPort {
    String materialize(ConstructionService.Snapshot houseSnapshot);
}
