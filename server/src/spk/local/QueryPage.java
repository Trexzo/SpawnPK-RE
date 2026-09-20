package spk.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable deterministic offset/limit page for semantic query services. */
final class QueryPage<T> {
    private final List<T> entries;
    private final int offset;
    private final int limit;
    private final int total;

    QueryPage(List<T> entries, int offset, int limit, int total) {
        if (offset < 0) throw new IllegalArgumentException("offset must be non-negative");
        if (limit <= 0) throw new IllegalArgumentException("limit must be positive");
        if (total < 0) throw new IllegalArgumentException("total must be non-negative");
        this.entries = Collections.unmodifiableList(new ArrayList<T>(entries));
        this.offset = offset;
        this.limit = limit;
        this.total = total;
    }

    List<T> entries() { return entries; }
    int offset() { return offset; }
    int limit() { return limit; }
    int total() { return total; }
    boolean hasMore() {
        return (long) offset + (long) entries.size() < (long) total;
    }

    static <T> QueryPage<T> slice(List<T> ordered, int offset, int limit) {
        if (offset < 0) throw new IllegalArgumentException("offset must be non-negative");
        if (limit <= 0) throw new IllegalArgumentException("limit must be positive");
        int total = ordered.size();
        int from = Math.min(offset, total);
        int remaining = total - from;
        int take = Math.min(limit, remaining);
        int to = from + take;
        return new QueryPage<T>(ordered.subList(from, to), offset, limit, total);
    }
}
