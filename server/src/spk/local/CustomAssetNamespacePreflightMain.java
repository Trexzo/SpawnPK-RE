package spk.local;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Command-line entry point for exact-v308 namespace collision preflight.
 *
 * Usage:
 *   java ... spk.local.CustomAssetNamespacePreflightMain <namespace-snapshot.tsv>
 */
public final class CustomAssetNamespacePreflightMain {
    public static void main(String[] args) throws Exception {
        if (args.length != 1)
            throw new IllegalArgumentException(
                "usage: CustomAssetNamespacePreflightMain <namespace-snapshot.tsv>"
            );

        Path snapshotPath = Paths.get(args[0]).toAbsolutePath().normalize();
        CustomAssetNamespaceSnapshot snapshot =
            CustomAssetNamespaceSnapshot.load(snapshotPath);
        CustomAssetNamespacePreflight.Result result =
            CustomAssetNamespacePreflight.run(snapshot);
        System.out.println(result.marker());
    }

    private CustomAssetNamespacePreflightMain() {}
}
