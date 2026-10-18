package spk.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Admission-only negative regression for #2559.
 * This verifies the existing opt-in same-JVM lease across TWO Worlds.
 * It does not prove cross-JVM epochs, crash durability, or save ACK.
 */
public final class G21377TwoWorldAccountAdmissionTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("g21377-two-world-admission-");
        final String key = LocalJvmAccountSessionLease.PROPERTY;
        String prior = System.getProperty(key);
        World first = null;
        World second = null;
        LocalJvmAccountSessionLease.Token owner = null;
        LocalJvmAccountSessionLease.Token successor = null;
        try {
            System.setProperty(key, "true");
            FilePlayerRepository repo = new FilePlayerRepository(
                username -> root.resolve(username + ".properties"));
            first = World.isolatedForTest(60000L, repo);
            second = World.isolatedForTest(60000L, repo);
            owner = LocalJvmAccountSessionLease.acquire(
                first.persistence(), "same-player");
            check(owner != null, "first World did not acquire lease");
            boolean refused = false;
            try {
                LocalJvmAccountSessionLease.Token invalid =
                    LocalJvmAccountSessionLease.acquire(
                        second.persistence(), "same-player");
                if (invalid != null)
                    invalid.releaseAfterInitializationFailure();
            } catch (IllegalStateException expected) {
                refused = String.valueOf(expected.getMessage()).contains(
                    "G21.181 JVM_ACCOUNT_SESSION_OWNED_NO_RECOVERY");
            }
            check(refused, "second World acquired the same named account");
            check(first.players().size() == 0 &&
                  second.players().size() == 0,
                  "rejected admission registered a World player");
            check(!Files.exists(root.resolve("same-player.properties")),
                  "admission-only probe wrote an account postimage");
            owner.releaseAfterInitializationFailure();
            owner = null;
            successor = LocalJvmAccountSessionLease.acquire(
                second.persistence(), "same-player");
            check(successor != null, "clean release prevented readmission");
            System.out.println(
                "G21377_TWO_WORLD_ACCOUNT_ADMISSION_PASS"+
                " firstOwner=true secondWorldDenied=true"+
                " noHydration=true noAccountWrite=true"+
                " cleanReadmission=true durableEpoch=false");
        } finally {
            if(successor != null) successor.releaseAfterInitializationFailure();
            if(owner != null) owner.releaseAfterInitializationFailure();
            if(second != null) second.close();
            if(first != null) first.close();
            if(prior == null) System.clearProperty(key);
            else System.setProperty(key, prior);
            try(Stream<Path> children = Files.walk(root)) {
                for(Path path : children.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new)) Files.deleteIfExists(path);
            }
        }
    }
    private static void check(boolean yes, String reason) {
        if(!yes) throw new AssertionError("G21377 " + reason);
    }
    private G21377TwoWorldAccountAdmissionTest() {}
}
