import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Standalone read-only capability probe; never changes application policy.
 * A successful probe execution does NOT mean strict nlink is supported.
 */
public final class FilesystemLinkCountProbe {
    private FilesystemLinkCountProbe() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 0) throw new IllegalArgumentException("no arguments");
        Path root = Files.createTempDirectory("spk-windows-linkcount-capability-");
        try {
            Path source = root.resolve("exact-selected-inode");
            Path secondName = root.resolve("hardlink-alias");
            Files.write(source, new byte[]{1, 2, 3, 4});
            final Object initial;
            try {
                initial = Files.getAttribute(source, "unix:nlink",
                    LinkOption.NOFOLLOW_LINKS);
            } catch (UnsupportedOperationException unsupported) {
                System.out.println(
                    "SPK_NLINK_PROBE_STATUS=UNSUPPORTED_UNIX_NLINK_PROVIDER" +
                    " strictWorldHardlinkGateUsable=false" +
                    " originalClientGui=false");
                return;
            }
            if (!(initial instanceof Number) ||
                ((Number) initial).longValue() != 1L) {
                System.out.println(
                    "SPK_NLINK_PROBE_STATUS=UNSAFE_INITIAL_LINK_COUNT" +
                    " strictWorldHardlinkGateUsable=false" +
                    " actual=" + String.valueOf(initial));
                return;
            }
            try {
                Files.createLink(secondName, source);
            } catch (UnsupportedOperationException | SecurityException unsupported) {
                System.out.println(
                    "SPK_NLINK_PROBE_STATUS=HARDLINK_CREATION_UNAVAILABLE" +
                    " strictWorldHardlinkGateUsable=false");
                return;
            } catch (java.io.IOException unavailable) {
                System.out.println(
                    "SPK_NLINK_PROBE_STATUS=HARDLINK_CREATION_FAILED" +
                    " strictWorldHardlinkGateUsable=false");
                return;
            }
            Object linked;
            try {
                linked = Files.getAttribute(source, "unix:nlink",
                    LinkOption.NOFOLLOW_LINKS);
            } catch (UnsupportedOperationException unsupported) {
                System.out.println(
                    "SPK_NLINK_PROBE_STATUS=UNSUPPORTED_AFTER_HARDLINK" +
                    " strictWorldHardlinkGateUsable=false");
                return;
            }
            boolean exact = Files.isSameFile(source, secondName) &&
                linked instanceof Number &&
                ((Number) linked).longValue() == 2L;
            System.out.println(
                "SPK_NLINK_PROBE_STATUS=" + (exact ?
                    "OBSERVED_SINGLE_AND_DOUBLE_LINK" :
                    "UNSAFE_OR_INCONSISTENT_LINK_COUNT") +
                " strictWorldHardlinkGateUsable=" + exact +
                " before=" + initial + " after=" + linked +
                " originalClientGui=false");
        } finally {
            try (Stream<Path> paths = Files.walk(root)) {
                for (Path p : paths.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new)) {
                    Files.deleteIfExists(p);
                }
            }
        }
    }
}
