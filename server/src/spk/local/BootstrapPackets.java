package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * M4 candidate frames reconstructed from the pinned client bytecode.
 *
 * The individual packet schemas are statically exact and the combined
 * 249 -> 73 -> 81 bootstrap is runtime-certified as of v0.3.4.
 * v2 retains M5 server-owned movement and initializes run energy using the same packet-81 local movement decoder.
 */
final class BootstrapPackets {
    static final int SKILLS_ROOT = 3917;
    static final int ACHIEVEMENTS_ROOT = 44100;
    static final int INVENTORY_ROOT = 3213;
    static final int EQUIPMENT_ROOT = 1644;
    static final int PRAYER_ROOT = 5608;
    static final int MAGIC_ROOT = 1151;
    // Exact production packet-71 roots recovered from the pinned current client session.
    static final int CLAN_CHAT_ROOT = 18128;
    static final int FRIENDS_ROOT = 5065;
    static final int IGNORE_ROOT = 5715;
    static final int LOGOUT_ROOT = 2449;
    static final int OPTIONS_ROOT = 904;
    static final int EMOTES_ROOT = 147;
    static final int SPAWN_TAB_SHORTCUT_ROOT = 0; // packet-71 handler resolves 0 -> 67027
    static final int SPAWN_TAB_ROOT = 67027;
    static final int SPAWN_TAB_INDEX = 13; // exact bottom-right native Spawn/Search tab
    static final int ACHIEVEMENTS_INDEX = 2;

    private BootstrapPackets() {}

    static void send(ServerPacketWriter w, String username) throws IOException {
        send(w, username, null);
    }

    static void send(ServerPacketWriter w, String username, int[] equippedItems) throws IOException {
        send(w, username, equippedItems, 100);
    }

    static void send(ServerPacketWriter w, String username, int[] equippedItems, int runEnergy) throws IOException {
        send(w, username, equippedItems, runEnergy, null);
    }

    /** v5.4 player-aware bootstrap; old overloads deliberately remain for regression parity. */
    static void send(ServerPacketWriter w, String username, int[] equippedItems, int runEnergy, PlayerState player) throws IOException {
        // Packet 249 is fixed 3 bytes.
        // Client branch: Client.mk = mU.N(); Client.di = mU.U();
        // N() is byte-A, U() is little-endian short-A.
        // Values below decode to mk=0 and local player index di=1.
        w.fixed(249, packet249(0, 1));

        // Packet 73 is fixed 4 bytes.
        // Client branch reads regionX=T() (BE short-A), then regionY=A() (BE short).
        // Region chunks for world coordinate ~3087,3495:
        // 3087>>3=385, 3495>>3=436.
        w.fixed(73, region73(385, 436));

        // Packet 81 is var-short.
        // Local type-3 placement + local appearance update mask + zero other players.
        // Base after packet 73: (385-6)*8=3032, (436-6)*8=3440;
        // local offset 55,55 -> world 3087,3495.
        w.varShort(81, player81TeleportWithAppearance(0, 55, 55, username, equippedItems, player));

        // Packet 110 is fixed-1 and updates the legacy run-energy field eY.
        // The custom SpawnPK orb renderer does NOT draw directly from eY.
        w.fixed(110, runEnergy110(runEnergy));

        // The custom run orb reads widget 149 text and parses all characters
        // except its trailing '%' marker. Production runtime evidence from this
        // exact client generation observed packet 126 key=149 data="97%".
        // Packet 126 is var-short: newline-terminated string, then T() short-A id.
        w.varShort(126, widgetText126(149, runEnergy + "%"));
    }

    static byte[] runEnergy110(int energy) {
        if (energy < 0 || energy > 100) throw new IllegalArgumentException("energy");
        return new byte[]{(byte)energy};
    }

    /** Packet 36: S() little-endian varp index + z() signed/raw byte value. */
    static byte[] config36(int index, int value) {
        if (index < 0 || index > 0xffff) throw new IllegalArgumentException("index");
        if (value < -128 || value > 127) throw new IllegalArgumentException("value");
        return new byte[]{(byte)index, (byte)(index >>> 8), (byte)value};
    }

    static byte[] widgetText126(int widgetId, String text) throws IOException {
        if (widgetId <= 0 || widgetId > 0xffff) throw new IllegalArgumentException("widgetId");
        if (text == null) text = "";
        byte[] raw = text.getBytes(StandardCharsets.ISO_8859_1);
        ByteArrayOutputStream b = new ByteArrayOutputStream(raw.length + 3);
        b.write(raw);
        b.write(10); // rs.x.e.F(): newline-terminated string
        // rs.x.e.T(): high byte raw, low byte decoded as (wire-128)&255.
        b.write((widgetId >>> 8) & 0xff);
        b.write((widgetId + 128) & 0xff);
        return b.toByteArray();
    }

    /**
     * Packet 71 fixed-3 sidebar mapping. The pinned client reads A() interface id
     * followed by N() tab index, where N() decodes (wire-128)&255.
     */
    static byte[] sidebar71(int interfaceId, int tab) {
        if (interfaceId < 0 || interfaceId > 0xffff) throw new IllegalArgumentException("interfaceId");
        if (tab < 0 || tab > 255) throw new IllegalArgumentException("tab");
        return new byte[]{(byte)(interfaceId >>> 8), (byte)interfaceId, (byte)((tab + 128) & 0xff)};
    }

    /** Packet 106 fixed-1 selected-tab update. Client O() decodes (-wire)&255. */
    static byte[] selectTab106(int tab) {
        if (tab < 0 || tab > 255) throw new IllegalArgumentException("tab");
        return new byte[]{(byte)((-tab) & 0xff)};
    }

    /** Packet 208 fixed-2: rs.x.e.V() signed little-endian walkable-interface id. */
    static byte[] walkableInterface208(int interfaceId) {
        if(interfaceId < -1 || interfaceId > 32767) throw new IllegalArgumentException("interfaceId");
        int v=interfaceId & 0xffff;
        return new byte[]{(byte)v,(byte)(v>>>8)};
    }

    /** Packet 97 fixed-2: rs.x.e.A() big-endian interface/root id. */
    static byte[] interface97(int interfaceId) {
        if (interfaceId < 0 || interfaceId > 0xffff) throw new IllegalArgumentException("interfaceId");
        return new byte[]{(byte)(interfaceId >>> 8), (byte)interfaceId};
    }

    /** Packet 164 fixed-2: rs.x.e.S() little-endian chatbox interface id. */
    static byte[] chatboxInterface164(int interfaceId) {
        if (interfaceId < 0 || interfaceId > 0xffff) throw new IllegalArgumentException("interfaceId");
        return new byte[]{(byte)interfaceId, (byte)(interfaceId >>> 8)};
    }


    /**
     * Packet 75 fixed-4 exact-current NPC model assignment.
     * Client reads U() twice: LE short with low byte encoded +128.
     */
    static byte[] interfaceNpcHead75(int npcId,int widgetId) {
        if(npcId<0||npcId>0xffff)throw new IllegalArgumentException("npcId");
        if(widgetId<0||widgetId>0xffff)throw new IllegalArgumentException("widgetId");
        return new byte[]{
            (byte)((npcId+128)&0xff),(byte)(npcId>>>8),
            (byte)((widgetId+128)&0xff),(byte)(widgetId>>>8)
        };
    }


    /**
     * Packet 248 fixed-4: open main interface + side/overlay interface.
     * Exact pinned-client branch reads T() for the main root then A() for the
     * side root, assigning them to cH and fu respectively.
     */
    static byte[] interfaceOverlay248(int mainRoot, int sideRoot) {
        if (mainRoot < 0 || mainRoot > 0xffff || sideRoot < 0 || sideRoot > 0xffff)
            throw new IllegalArgumentException("interface id");
        return new byte[]{
            (byte)(mainRoot >>> 8), (byte)((mainRoot + 128) & 0xff), // T()
            (byte)(sideRoot >>> 8), (byte)sideRoot                  // A()
        };
    }

    /**
     * Packet 53 var-short full widget item-container update.
     * Pinned client branch reads:
     *   A() widgetId, A() slotCount,
     *   y() quantity (255 => Y() 32-bit quantity), U() itemIdPlusOne.
     */
    /** Full 14-slot equipment widget update (classic widget 1688). */
    static byte[] equipmentContainer53(int[] equipmentItems) throws IOException {
        if (equipmentItems == null || equipmentItems.length != EquipmentState.EQUIPMENT_SLOTS)
            throw new IllegalArgumentException("equipmentItems must have 14 equipment slots");
        int[] qty = new int[equipmentItems.length];
        for (int i=0;i<equipmentItems.length;i++) if (equipmentItems[i] >= 0) qty[i] = 1;
        return equipmentContainer53(equipmentItems,qty);
    }

    static byte[] equipmentContainer53(int[] equipmentItems,int[] quantities) throws IOException {
        if (equipmentItems == null || quantities == null || equipmentItems.length != EquipmentState.EQUIPMENT_SLOTS || quantities.length != equipmentItems.length)
            throw new IllegalArgumentException("equipment arrays must have 14 slots");
        return itemContainer53(EquipmentState.EQUIPMENT_WIDGET,equipmentItems,quantities);
    }

    static byte[] itemContainer53(int widgetId, int[] itemIds, int[] quantities) throws IOException {
        if (itemIds == null || quantities == null || itemIds.length != quantities.length)
            throw new IllegalArgumentException("item arrays");
        if (widgetId < 0 || widgetId > 0xffff) throw new IllegalArgumentException("widgetId");
        if (itemIds.length > 0xffff) throw new IllegalArgumentException("too many slots");
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        putU16(b, widgetId); // A()
        putU16(b, itemIds.length); // A()
        for (int i=0;i<itemIds.length;i++) {
            int itemId=itemIds[i], qty=quantities[i];
            if (itemId < -1 || itemId >= 0xffff) throw new IllegalArgumentException("itemId="+itemId);
            if (qty < 0) throw new IllegalArgumentException("qty="+qty);
            if (qty < 255) {
                b.write(qty);
            } else {
                b.write(255);
                putYInt(b, qty);
            }
            int wireItem = itemId + 1; // interface arrays store 0 empty, item definition is value-1
            // U(): second byte is high, first byte decodes as (wire-128)&255.
            b.write((wireItem + 128) & 0xff);
            b.write((wireItem >>> 8) & 0xff);
        }
        return b.toByteArray();
    }

    /** Inverse of pinned rs.x.e.Y() byte order: p1<<24 | p0<<16 | p3<<8 | p2. */
    private static void putYInt(OutputStream out, int v) throws IOException {
        out.write((v >>> 16) & 0xff); // p0
        out.write((v >>> 24) & 0xff); // p1
        out.write(v & 0xff);          // p2
        out.write((v >>> 8) & 0xff);  // p3
    }

    static byte[] packet249(int flag, int localPlayerIndex) {
        if ((flag & ~0xff) != 0) throw new IllegalArgumentException("flag");
        if ((localPlayerIndex & ~0xffff) != 0) throw new IllegalArgumentException("localPlayerIndex");
        // N(): (wire-128)&255. U(): ((second&255)<<8)+((first-128)&255).
        return new byte[]{
            (byte)((flag + 128) & 0xff),
            (byte)((localPlayerIndex + 128) & 0xff),
            (byte)(localPlayerIndex >>> 8)
        };
    }

    static byte[] region73(int regionX, int regionY) {
        return new byte[]{
            (byte)(regionX >>> 8), (byte)((regionX + 128) & 0xff),
            (byte)(regionY >>> 8), (byte)regionY
        };
    }

    /**
     * Packet 65 var-short: one new NPC in the exact pinned-client bit layout.
     * This is the minimal no-mask spawn form used by the v4 NPC foundation.
     * dx/dy are signed 5-bit offsets from the local player (-16..15).
     */
    static byte[] npc65SingleSpawn(int npcIndex, int definitionId, int dx, int dy) {
        if (npcIndex < 0 || npcIndex >= 16383) throw new IllegalArgumentException("npcIndex");
        if (definitionId < 0 || definitionId >= 16384) throw new IllegalArgumentException("definitionId");
        if (dx < -16 || dx > 15 || dy < -16 || dy > 15) throw new IllegalArgumentException("npc offset");
        BitWriter b = new BitWriter();
        b.write(0, 8);                 // existing NPC count
        b.write(npcIndex, 14);         // new NPC index
        b.write(dy & 31, 5);           // signed Y offset (client reads first)
        b.write(dx & 31, 5);           // signed X offset
        b.write(0, 1);                 // no optional aw/ax byte
        b.write(0, 1);                 // no optional four-byte extension
        b.write(0, 1);                 // no optional orientation byte
        b.write(0, 1);                 // aH false
        b.write(definitionId, 14);      // NPC definition
        b.write(0, 1);                 // no synchronization mask
        // No 16383 sentinel is needed for this minimal one-entry form: after
        // this 43-bit entry the pinned client's (bitPos + 21 < packetBits)
        // guard naturally terminates at the 7-byte packet boundary.
        return b.finish();
    }

    static byte[] player81Idle() {
        BitWriter b = new BitWriter();
        b.write(0, 1);      // local player: no movement/update
        b.write(0, 8);      // existing other player count
        b.write(2047, 11);  // new-player sentinel
        return b.finish();  // exactly 00 7f f0
    }


    static byte[] player81WalkStep(int direction) {
        if (direction < 0 || direction > 7) throw new IllegalArgumentException("direction");
        BitWriter b = new BitWriter();
        b.write(1, 1);      // local movement/update follows
        b.write(1, 2);      // type 1 = one walking step
        b.write(direction, 3);
        b.write(0, 1);      // no local synchronization mask
        b.write(0, 8);      // no existing other players
        b.write(2047, 11);  // new-player sentinel
        return b.finish();
    }

    static byte[] player81RunSteps(int direction1, int direction2) {
        if (direction1 < 0 || direction1 > 7 || direction2 < 0 || direction2 > 7)
            throw new IllegalArgumentException("direction");
        BitWriter b = new BitWriter();
        b.write(1, 1);      // local movement/update follows
        b.write(2, 2);      // type 2 = two queued/running steps
        b.write(direction1, 3);
        b.write(direction2, 3);
        b.write(0, 1);      // no local synchronization mask
        b.write(0, 8);      // no existing other players
        b.write(2047, 11);  // new-player sentinel
        return b.finish();
    }

    static byte[] player81TeleportNoAppearance(int plane, int localX, int localY) {
        BitWriter b = new BitWriter();
        b.write(1, 1);       // local update follows
        b.write(3, 2);       // movement type 3 = teleport/placement
        b.write(plane & 3, 2);
        b.write(1, 1);       // branch field; current client consumes it
        b.write(0, 1);       // local player not queued for mask
        b.write(localX & 127, 7);
        b.write(localY & 127, 7);
        b.write(0, 8);       // no existing other players
        b.write(2047, 11);   // end new-player list
        return b.finish();   // exactly 5 bytes
    }

    static byte[] player81TeleportWithAppearance(int plane, int localX, int localY, String username) throws IOException {
        return player81TeleportWithAppearance(plane, localX, localY, username, null);
    }

    static byte[] player81TeleportWithAppearance(int plane, int localX, int localY, String username, int[] equippedItems) throws IOException {
        return player81TeleportWithAppearance(plane,localX,localY,username,equippedItems,null);
    }

    static byte[] player81TeleportWithAppearance(int plane, int localX, int localY, String username, int[] equippedItems, PlayerState player) throws IOException {
        byte[] appearance = appearanceBlock(username, equippedItems, player);

        BitWriter bits = new BitWriter();
        bits.write(1, 1);       // local update follows
        bits.write(3, 2);       // movement type 3 = teleport/placement
        bits.write(plane & 3, 2);
        bits.write(1, 1);       // branch field; current client consumes it
        bits.write(1, 1);       // queue self (index 2047) for synchronization mask
        bits.write(localX & 127, 7);
        bits.write(localY & 127, 7);
        bits.write(0, 8);       // no existing other players
        bits.write(2047, 11);   // end new-player list
        byte[] bitPayload = bits.finish(); // 5 bytes

        ByteArrayOutputStream out = new ByteArrayOutputStream(bitPayload.length + appearance.length + 2);
        out.write(bitPayload);
        out.write(16); // update mask bit 0x10 = appearance in this exact build
        // Client rs.x.e.O(): (-wireByte)&255
        out.write((-appearance.length) & 0xff);
        out.write(appearance);
        return out.toByteArray();
    }

    /** Packet 81 local-player mask-only synchronization, used after equipment changes. */
    static byte[] player81AppearanceOnly(String username, int[] equippedItems) throws IOException {
        return player81AppearanceOnly(username,equippedItems,null);
    }

    static byte[] player81AppearanceOnly(String username, int[] equippedItems, PlayerState player) throws IOException {
        return player81AppearanceOnly(username,equippedItems,player,null);
    }

    static byte[] player81AppearanceOnly(String username, int[] equippedItems, PlayerState player, Integer npcTransformId) throws IOException {
        byte[] appearance = appearanceBlock(username, equippedItems, player, npcTransformId);
        BitWriter bits = new BitWriter();
        bits.write(1, 1);      // local update follows
        bits.write(0, 2);      // movement type 0: no movement, mask follows
        bits.write(0, 8);      // no existing other players
        bits.write(2047, 11);  // end new-player list
        byte[] bitPayload = bits.finish();

        ByteArrayOutputStream out = new ByteArrayOutputStream(bitPayload.length + appearance.length + 2);
        out.write(bitPayload);
        out.write(16); // mask 0x10 = appearance
        out.write((-appearance.length) & 0xff); // O() encoded length
        out.write(appearance);
        return out.toByteArray();
    }

    /**
     * Minimal appearance block accepted by the pinned client's rs.a.k.a(rs.x.e).
     * It is intentionally plain: default identity-kit body, default colors/animations, no title.
     * This parser contract is offline-tested directly against client(6).jar.
     */
    static byte[] appearanceBlock(String username) throws IOException {
        return appearanceBlock(username, null);
    }

    static byte[] appearanceBlock(String username, int[] equippedItems) throws IOException {
        return appearanceBlock(username,equippedItems,null);
    }

    static byte[] appearanceBlock(String username, int[] equippedItems, PlayerState player) throws IOException {
        return appearanceBlock(username,equippedItems,player,null);
    }

    /**
     * Exact current-client player->NPC transform presentation. When slot 0 of
     * the normal 12-slot appearance array is 0xFFFF the client immediately
     * reads one additional unsigned-short NPC definition id and stops normal
     * body/equipment slot decoding. The actor remains in the PLAYER array.
     */
    static byte[] appearanceBlock(String username, int[] equippedItems, PlayerState player, Integer npcTransformId) throws IOException {
        if(npcTransformId!=null && (npcTransformId<0 || npcTransformId>16383))
            throw new IllegalArgumentException("npcTransformId 0..16383");
        if (username == null || username.isEmpty()) username = "localtest";
        byte[] name = username.getBytes(StandardCharsets.ISO_8859_1);
        if (name.length > 64) throw new IOException("local username too long for lab appearance: " + name.length);

        ByteArrayOutputStream b = new ByteArrayOutputStream();
        // Five leading appearance/state bytes: aY, bd, bf, bg, bh.
        // bd/bf use 255 as the client's no-icon sentinel.  v0.3 wrote zero,
        // which is why the localhost player acquired overhead status icons.
        b.write(
            player==null
                ?CharacterDesignProfile.MALE
                :player.characterGender()
        );            // aY: exact character-design gender selector, 0 male / 1 female
        b.write(255); // bd: no skull/status icon
        b.write(255); // bf: no prayer/status icon
        b.write(0);   // bg: presentation channel intentionally zero; v5.6 collection-icon guess was incorrect
        b.write(0);   // bh: default overhead offset state
        putU16(b, 0); // signed-short role aC; zero is safe in the parser

        // A player with all 12 slots zero parses correctly but has no renderable
        // body parts.  The 317 appearance editor maps its seven identity-kit
        // choices into slots {8,11,4,6,9,7,10}.  These are the canonical
        // classic default male kit indices used by that editor family.
        int[] equipment = new int[12];
        int[] kitSlots = {8, 11, 4, 6, 9, 7, 10};
        int[] kitIds =
            player==null
                ?CharacterDesignProfile.defaultKits(
                    CharacterDesignProfile.MALE
                )
                :player.characterKits();
        for (int i = 0; i < kitSlots.length; i++)
            if(kitIds[i]>=0)
                equipment[kitSlots[i]] = 256 + kitIds[i];
        if (equippedItems != null) {
            if (equippedItems.length != 12) throw new IllegalArgumentException("equippedItems must have 12 appearance slots");
            // Full-helm visual policy: the exact custom head item remains authoritative,
            // while identity-kit hair/beard are omitted to prevent double-render clipping.
            int headItem = equippedItems[EquipmentSlot.HEAD.appearanceIndex];
            if (headItem >= 0 && EquipmentMetadataRepository.hidesHairOrBeard(headItem)) {
                equipment[8] = 0;  // hair/head identity kit
                equipment[11] = 0; // beard/jaw identity kit
            }
            int chestItem = equippedItems[EquipmentSlot.CHEST.appearanceIndex];
            if (chestItem >= 0 && EquipmentMetadataRepository.hidesArms(chestItem)) {
                equipment[6] = 0;  // full-body chest model supplies its own arms/sleeves
            }
            for (int i = 0; i < equippedItems.length; i++) {
                int itemId = equippedItems[i];
                if (itemId >= 0) {
                    int appearanceValue = 512 + itemId;
                    if (appearanceValue > 0xffff) throw new IllegalArgumentException("equipped item too large: " + itemId);
                    equipment[i] = appearanceValue;
                }
            }
        }
        if(npcTransformId!=null){
            // Slot 0 special marker. Do NOT serialize slots 1..11: the exact
            // client breaks out of its equipment loop after reading this id.
            b.write(0xff); b.write(0xff);
            putU16(b,npcTransformId);
        } else {
            for (int value : equipment) {
                if (value == 0) {
                    b.write(0);
                } else {
                    b.write((value >>> 8) & 0xff);
                    b.write(value & 0xff);
                }
            }
        }
        // V9.08 exact-current optional extra appearance item (rs.a.k.bs).
        // This is the native SpawnPK icon/cosmetic channel after br[12].
        int extraItem = player==null ? -1 : player.nativeIconItemId();
        if (extraItem > 0) {
            b.write(1);
            putU16(b, extraItem); // exact parser: flag byte then unsigned BE item id
        } else {
            b.write(0);
        }

        // 5 exact character-design colour indices.
        int[] characterColours=
            player==null
                ?CharacterDesignProfile.defaultColours()
                :player.characterColours();
        for (int i = 0; i < characterColours.length; i++)
            b.write(characterColours[i]);

        // Seven appearance animation ids are resolved as one equipment-pose profile.
        // This keeps stand/walk/turn/run data together rather than hardcoding a
        // one-off stand mutation inside packet serialization.
        int[] anim = appearanceAnimations(equippedItems);
        for (int v : anim) putU16(b, v);

        b.write(0);       // no title/clan prefix string
        b.write(name);
        b.write(10);      // client strings are newline-terminated
        b.write(player == null ? 3 : player.combatLevel()); // combat level
        putU16(b, 0);     // skill/total role
        b.write(0);       // rank-extension selector; no 3 extra bytes

        // Exact current-client completionist-cape extension. rs.a.k.b(int)
        // special-cases 23063/21963/21964 and the model builder dereferences
        // aS/aI for those capes. Those fields are initialized only when this
        // appearance extension is present. Omitting it leaves aS null and can
        // poison rendering as soon as one of the special capes is equipped.
        //
        // The six selector bytes below are the exact inverse indexes for the
        // client's native default comp-colour vector bJ={924,924,62575,62575,6015,0}
        // in the order used by ::compcolors / rs.a.k.b(IIIIII):
        //   bJ[4], bJ[0], bJ[2], bJ[1], bJ[3], bJ[5]
        // -> {13,9,7,9,7,5}. The first five are HSB palette selectors and
        // the sixth is the raw RGB selector.
        if (hasSpecialCompletionistCape(equippedItems)) {
            b.write(1);
            int[] selectors = player == null ? new int[]{13,9,7,9,7,5} : player.compSelectors();
            for (int selector : selectors) b.write(selector);
        } else {
            b.write(0);
        }
        return b.toByteArray();
    }


    static boolean hasSpecialCompletionistCape(int[] equippedItems) {
        if (equippedItems == null || equippedItems.length <= EquipmentSlot.CAPE.appearanceIndex) return false;
        int id = equippedItems[EquipmentSlot.CAPE.appearanceIndex];
        // Exact pinned-client rs.a.k.b(int) set.
        return id == 23063 || id == 21963 || id == 21964;
    }

    static int[] appearanceAnimations(int[] equippedItems) {
        return EquipmentPoseRepository.forAppearance(equippedItems).toArray();
    }

    /**
     * Packet 134 fixed-6, exact pinned-client decoder:
     *   skill=y(); xp=X(); current=y().
     * X() reconstructs p2<<24 | p3<<16 | p0<<8 | p1.
     */
    static byte[] skill134(int skillIndex, int xp, int currentLevel) {
        if (skillIndex < 0 || skillIndex > 255) throw new IllegalArgumentException("skillIndex");
        if (xp < 0) throw new IllegalArgumentException("xp");
        if (currentLevel < 0 || currentLevel > 255) throw new IllegalArgumentException("currentLevel");
        return new byte[]{
            (byte)skillIndex,
            (byte)(xp >>> 8),
            (byte)xp,
            (byte)(xp >>> 24),
            (byte)(xp >>> 16),
            (byte)currentLevel
        };
    }

    private static void putU16(OutputStream out, int v) throws IOException {
        out.write((v >>> 8) & 0xff);
        out.write(v & 0xff);
    }
}
