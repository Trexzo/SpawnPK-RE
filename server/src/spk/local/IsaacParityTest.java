package spk.local;

import java.lang.reflect.*;
import java.net.*;
import java.io.File;
import java.util.*;

public final class IsaacParityTest {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("usage: IsaacParityTest <client.jar>");
        int[][] seeds = {
            {0,0,0,0}, {1,2,3,4}, {0x12345678, -7, 0x7fffffff, 0x80000000}
        };
        try (URLClassLoader cl = new URLClassLoader(new URL[]{new File(args[0]).toURI().toURL()}, null)) {
            Class<?> original = Class.forName("rs.q.a", true, cl);
            Constructor<?> ctor = original.getConstructor(int[].class);
            Method next = original.getMethod("a");
            for (int[] seed : seeds) {
                Object ref = ctor.newInstance((Object) seed.clone());
                IsaacCipher ours = new IsaacCipher(seed.clone());
                for (int i=0;i<2048;i++) {
                    int a = (Integer) next.invoke(ref);
                    int b = ours.nextInt();
                    if (a != b) throw new AssertionError("ISAAC mismatch seed=" + Arrays.toString(seed) + " at " + i + ": " + a + " != " + b);
                }
            }
        }
        System.out.println("ISAAC_PARITY_WITH_CLIENT_RS_Q_A_PASS outputs=6144");
    }
}
