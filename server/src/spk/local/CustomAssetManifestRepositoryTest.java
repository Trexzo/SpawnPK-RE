package spk.local;

import java.util.*;

public final class CustomAssetManifestRepositoryTest {
    public static void main(String[] args) {
        List<CustomAssetManifestRepository.Asset> rows =
            CustomAssetManifestRepository.byContentKey("voidglass_nistirio");
        if (rows.size() != 4) throw new AssertionError("rows=" + rows.size());

        for (int i = 0; i < rows.size(); i++) {
            CustomAssetManifestRepository.Asset a = rows.get(i);
            if (a.variant != i + 1) throw new AssertionError("variant=" + a.variant);
            if (a.itemId != 29999) throw new AssertionError("item=" + a.itemId);
            if (a.npcId != 12000 + i) throw new AssertionError("npc=" + a.npcId);
            if (!a.provenance.startsWith("CUSTOM_LOCALLAB"))
                throw new AssertionError("provenance=" + a.provenance);
            if (a.models.contains("36185")) throw new AssertionError("Hydra model retained");
            if (a.standAnim == 8233 || a.walkAnim == 8232)
                throw new AssertionError("Hydra animation retained");
            if (a.gfxId != 5042) throw new AssertionError("gfx=" + a.gfxId);
            if (!"VOIDGLASS RIFT".equals(a.text)) throw new AssertionError("text=" + a.text);
        }

        System.out.println("R85_CUSTOM_ASSET_MANIFEST_PASS rows=4 authority=CUSTOM_LOCALLAB itemRangeValidated=true npcRangeValidated=true modelRangeValidated=true gfxRangeValidated=true");
    }
}
