package spk.local;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class VoidglassR3ConfigPatchToolTest {
    public static void main(String[] args) throws Exception {
        if (VoidglassR3CustomContent.ITEM_ID >= 30000)
            throw new AssertionError("client bound");
        if (VoidglassR3CustomContent.CANDIDATES.length != 1)
            throw new AssertionError("old compositor candidate family still exposed");

        boolean mutationRejected = false;
        try {
            VoidglassR3ConfigPatchTool.main(new String[]{"patch", "unused"});
        } catch (IllegalStateException expected) {
            mutationRejected = expected.getMessage().contains(
                VoidglassR3ConfigPatchTool.RETIRED_MARKER
            );
        }
        if (!mutationRejected)
            throw new AssertionError("legacy direct config mutation mode was not retired");

        Path snapshot = Files.createTempFile(
            "spk-v308-custom-namespace-", ".tsv"
        );
        try {
            Files.write(
                snapshot,
                validSnapshot().getBytes(StandardCharsets.UTF_8)
            );
            CustomAssetNamespacePreflight.Result result =
                VoidglassR3ConfigPatchTool.preflight(snapshot);
            if (result.claims != 4 || result.references != 1)
                throw new AssertionError(
                    "preflight counts=" + result.claims + "/" + result.references
                );
        } finally {
            Files.deleteIfExists(snapshot);
        }

        System.out.println(
            "V5185_VOIDGLASS_R3_CONFIG_TOOL_PASS " +
            "nativeCompositorPatcherRetired=true " +
            "directConfigMutation=false exactV308NamespacePreflight=true " +
            "isolatedCachePipelineRequired=true"
        );
    }

    private static String validSnapshot() {
        String sha = CustomAssetAuthoringRepository.EXACT_V308_CLIENT_SHA256;
        return "clientSha256\t" + sha + "\n" +
            "scope\tBASE_PLUS_EXACT_OVERRIDES\n" +
            "recordType\tnamespace\tcontext\tvalue\n" +
            "CAPACITY\tITEM\tGLOBAL\t30000\n" +
            "CAPACITY\tNPC\tGLOBAL\t16384\n" +
            "CAPACITY\tMODEL\tPRIMARY\t100000\n" +
            "CAPACITY\tTEXTURE\tGLOBAL\t340\n" +
            "CAPACITY\tANIMATION\tGLOBAL\t35260\n" +
            "CAPACITY\tGFX\tGLOBAL\t7964\n" +
            "CAPACITY\tFRAME_GROUP\tGLOBAL\t65536\n" +
            "PRESENT\tGFX\tGLOBAL\t5042\n" +
            "GFX_CONTEXT\tGFX\tPRIMARY\t5042\n";
    }

    private VoidglassR3ConfigPatchToolTest() {}
}
