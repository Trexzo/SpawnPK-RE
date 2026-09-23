package spk.local;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Compatibility shell for the retired Voidglass R3 native-compositor config patcher.
 *
 * R3's direct i.bin/e.bin mutator is intentionally no longer available. Issue #9 now
 * requires an exact-v308 BASE_PLUS_EXACT_OVERRIDES namespace census before any binary
 * build, and the eventual binary packer may write only to an isolated cache copy.
 */
public final class VoidglassR3ConfigPatchTool {
    static final int OLD_ITEM_ID = 32760;
    static final int ITEM_ID = 29999;
    static final String RETIRED_MARKER =
        "VOIDGLASS_R3_NATIVE_COMPOSITOR_PATCHER_RETIRED";

    public static void main(String[] args) throws Exception {
        if (args.length != 2)
            throw new IllegalArgumentException(
                "usage: VoidglassR3ConfigPatchTool preflight <namespace-snapshot.tsv>"
            );

        String mode = args[0].toLowerCase(Locale.ROOT);
        if (!"preflight".equals(mode)) {
            throw new IllegalStateException(
                RETIRED_MARKER +
                " mode=" + mode +
                " reason=Issue_9_requires_exact_v308_namespace_preflight_and_isolated_cache_output"
            );
        }

        Path snapshot = Paths.get(args[1]).toAbsolutePath().normalize();
        CustomAssetNamespacePreflight.Result result = preflight(snapshot);
        System.out.println(
            "VOIDGLASS_R3_CONFIG_PREFLIGHT_PASS " +
            "nativeCompositorPatcherRetired=true " +
            result.marker()
        );
    }

    static CustomAssetNamespacePreflight.Result preflight(Path snapshot) throws Exception {
        return CustomAssetNamespacePreflight.run(
            CustomAssetNamespaceSnapshot.load(snapshot)
        );
    }

    static String retirementReason() {
        return RETIRED_MARKER +
            " direct_i.bin_e.bin_mutation=false isolated_cache_pipeline_required=true";
    }

    private VoidglassR3ConfigPatchTool() {}
}
