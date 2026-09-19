package spk.local;

public final class InteractionDistanceTest {
    public static void main(String[] args) {
        if (LocalSession.chebyshev(3087,3495,3095,3493) != 8) throw new AssertionError();
        if (LocalSession.chebyshev(3094,3493,3095,3493) != 1) throw new AssertionError();
        if (LocalSession.chebyshev(3095,3493,3095,3493) != 0) throw new AssertionError();
        System.out.println("V41_INTERACTION_DISTANCE_PASS far=8 adjacent=1 sameTile=0 bankOpenRadius=1");
    }
}
