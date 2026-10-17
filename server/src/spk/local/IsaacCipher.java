package spk.local;

/** Exact ISAAC variant used by SpawnPK client rs.q.a. */
public final class IsaacCipher {
    private int count;
    private final int[] results = new int[256];
    private final int[] memory = new int[256];
    private int accumulator;
    private int lastResult;
    private int counter;

    public IsaacCipher(int[] seed) {
        System.arraycopy(seed, 0, results, 0, Math.min(seed.length, results.length));
        init();
    }

    public int nextInt() {
        int old = count;
        count--;
        if (old == 0) {
            isaac();
            count = 255;
        }
        return results[count];
    }

    static final class Snapshot {
        final int count;
        final int[] results;
        final int[] memory;
        final int accumulator;
        final int lastResult;
        final int counter;

        Snapshot(
            int count,
            int[] results,
            int[] memory,
            int accumulator,
            int lastResult,
            int counter
        ){
            this.count=count;
            this.results=results;
            this.memory=memory;
            this.accumulator=accumulator;
            this.lastResult=lastResult;
            this.counter=counter;
        }
    }

    Snapshot snapshot() {
        return new Snapshot(
            count,
            results.clone(),
            memory.clone(),
            accumulator,
            lastResult,
            counter
        );
    }

    void restore(Snapshot snapshot) {
        if(snapshot==null)
            throw new NullPointerException("snapshot");

        count=snapshot.count;
        System.arraycopy(
            snapshot.results,
            0,
            results,
            0,
            results.length
        );
        System.arraycopy(
            snapshot.memory,
            0,
            memory,
            0,
            memory.length
        );
        accumulator=snapshot.accumulator;
        lastResult=snapshot.lastResult;
        counter=snapshot.counter;
    }


    private void isaac() {
        lastResult += ++counter;
        for (int i = 0; i < 256; i++) {
            int x = memory[i];
            switch (i & 3) {
                case 0: accumulator ^= accumulator << 13; break;
                case 1: accumulator ^= accumulator >>> 6; break;
                case 2: accumulator ^= accumulator << 2; break;
                case 3: accumulator ^= accumulator >>> 16; break;
                default: throw new AssertionError();
            }
            accumulator += memory[(i + 128) & 255];
            int y = memory[(x & 1020) >> 2] + accumulator + lastResult;
            memory[i] = y;
            lastResult = memory[((y >>> 8) & 1020) >> 2] + x;
            results[i] = lastResult;
        }
    }

    private void init() {
        int golden = 0x9e3779b9; // -1640531527
        int a = golden, b = golden, c = golden, d = golden;
        int e = golden, f = golden, g = golden, h = golden;

        for (int round = 0; round < 4; round++) {
            a ^= b << 11; d += a; b += c;
            b ^= c >>> 2; e += b; c += d;
            c ^= d << 8; f += c; d += e;
            d ^= e >>> 16; g += d; e += f;
            e ^= f << 10; h += e; f += g;
            f ^= g >>> 4; a += f; g += h;
            g ^= h << 8; b += g; h += a;
            h ^= a >>> 9; c += h; a += b;
        }

        for (int i = 0; i < 256; i += 8) {
            a += results[i]; b += results[i + 1]; c += results[i + 2]; d += results[i + 3];
            e += results[i + 4]; f += results[i + 5]; g += results[i + 6]; h += results[i + 7];

            a ^= b << 11; d += a; b += c;
            b ^= c >>> 2; e += b; c += d;
            c ^= d << 8; f += c; d += e;
            d ^= e >>> 16; g += d; e += f;
            e ^= f << 10; h += e; f += g;
            f ^= g >>> 4; a += f; g += h;
            g ^= h << 8; b += g; h += a;
            h ^= a >>> 9; c += h; a += b;

            memory[i] = a; memory[i + 1] = b; memory[i + 2] = c; memory[i + 3] = d;
            memory[i + 4] = e; memory[i + 5] = f; memory[i + 6] = g; memory[i + 7] = h;
        }

        for (int i = 0; i < 256; i += 8) {
            a += memory[i]; b += memory[i + 1]; c += memory[i + 2]; d += memory[i + 3];
            e += memory[i + 4]; f += memory[i + 5]; g += memory[i + 6]; h += memory[i + 7];

            a ^= b << 11; d += a; b += c;
            b ^= c >>> 2; e += b; c += d;
            c ^= d << 8; f += c; d += e;
            d ^= e >>> 16; g += d; e += f;
            e ^= f << 10; h += e; f += g;
            f ^= g >>> 4; a += f; g += h;
            g ^= h << 8; b += g; h += a;
            h ^= a >>> 9; c += h; a += b;

            memory[i] = a; memory[i + 1] = b; memory[i + 2] = c; memory[i + 3] = d;
            memory[i + 4] = e; memory[i + 5] = f; memory[i + 6] = g; memory[i + 7] = h;
        }

        isaac();
        count = 256;
    }
}
