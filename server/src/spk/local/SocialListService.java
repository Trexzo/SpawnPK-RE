package spk.local;

import java.util.*;

/**
 * Protocol-independent server-owned Friends/Ignore aggregate.
 *
 * Exact-current client research proves the four native mutation intents, but
 * original server persistence, capacity, privacy and messaging rules remain
 * separate authority. This service therefore owns semantic list state while
 * caller-supplied policy owns capacity and cross-list exclusivity.
 */
final class SocialListService {
    static final int SNAPSHOT_VERSION=1;

    enum MutationStatus {
        ADDED,
        REMOVED,
        ALREADY_PRESENT,
        NOT_PRESENT,
        CONFLICTING_LIST,
        CAPACITY_REACHED
    }

    static final class AccountRef
        implements Comparable<AccountRef> {

        private final String value;

        AccountRef(String value){
            this.value=normalizeRef(value);
        }

        String value(){
            return value;
        }

        @Override public int compareTo(
            AccountRef other
        ){
            return value.compareTo(
                Objects.requireNonNull(
                    other,
                    "other"
                ).value
            );
        }

        @Override public boolean equals(
            Object other
        ){
            return other instanceof AccountRef&&
                value.equals(
                    ((AccountRef)other).value
                );
        }

        @Override public int hashCode(){
            return value.hashCode();
        }

        @Override public String toString(){
            return value;
        }
    }

    static final class Policy {
        static final int UNBOUNDED=-1;

        final int maxFriends;
        final int maxIgnores;
        final boolean crossListExclusive;
        final String sourceAuthority;

        Policy(
            int maxFriends,
            int maxIgnores,
            boolean crossListExclusive,
            String sourceAuthority
        ){
            this.maxFriends=
                requireLimit(
                    maxFriends,
                    "maxFriends"
                );
            this.maxIgnores=
                requireLimit(
                    maxIgnores,
                    "maxIgnores"
                );
            this.crossListExclusive=
                crossListExclusive;
            this.sourceAuthority=
                requireAuthority(
                    sourceAuthority
                );
        }

        private static int requireLimit(
            int value,
            String label
        ){
            if(value<UNBOUNDED)
                throw new IllegalArgumentException(
                    label+"="+value
                );

            return value;
        }
    }

    static final class Snapshot {
        private final int version;
        private final AccountRef owner;
        private final List<AccountRef> friends;
        private final List<AccountRef> ignores;

        Snapshot(
            int version,
            AccountRef owner,
            Collection<AccountRef> friends,
            Collection<AccountRef> ignores
        ){
            if(version<=0)
                throw new IllegalArgumentException(
                    "version="+version
                );

            this.version=version;
            this.owner=
                Objects.requireNonNull(
                    owner,
                    "owner"
                );
            this.friends=
                immutableUniqueSorted(
                    friends,
                    "friends"
                );
            this.ignores=
                immutableUniqueSorted(
                    ignores,
                    "ignores"
                );
        }

        int version(){
            return version;
        }

        AccountRef owner(){
            return owner;
        }

        List<AccountRef> friends(){
            return friends;
        }

        List<AccountRef> ignores(){
            return ignores;
        }

        boolean friend(
            AccountRef target
        ){
            return Collections.binarySearch(
                friends,
                Objects.requireNonNull(
                    target,
                    "target"
                )
            )>=0;
        }

        boolean ignored(
            AccountRef target
        ){
            return Collections.binarySearch(
                ignores,
                Objects.requireNonNull(
                    target,
                    "target"
                )
            )>=0;
        }

        @Override public String toString(){
            return "SocialListSnapshot{"+
                "version="+version+
                ",owner="+owner+
                ",friends="+friends.size()+
                ",ignores="+ignores.size()+
                "}";
        }
    }

    static final class MutationResult {
        final MutationStatus status;
        final Snapshot snapshot;

        MutationResult(
            MutationStatus status,
            Snapshot snapshot
        ){
            this.status=
                Objects.requireNonNull(
                    status,
                    "status"
                );
            this.snapshot=
                Objects.requireNonNull(
                    snapshot,
                    "snapshot"
                );
        }

        boolean changed(){
            return status==
                    MutationStatus.ADDED||
                status==
                    MutationStatus.REMOVED;
        }
    }

    private static final class State {
        final AccountRef owner;
        final LinkedHashSet<AccountRef> friends=
            new LinkedHashSet<>();
        final LinkedHashSet<AccountRef> ignores=
            new LinkedHashSet<>();

        State(
            AccountRef owner
        ){
            this.owner=owner;
        }

        Snapshot snapshot(){
            return new Snapshot(
                SNAPSHOT_VERSION,
                owner,
                friends,
                ignores
            );
        }
    }

    private final Policy policy;
    private final LinkedHashMap<AccountRef,State>
        states=
            new LinkedHashMap<>();

    SocialListService(
        Policy policy
    ){
        this.policy=
            Objects.requireNonNull(
                policy,
                "policy"
            );
    }

    Policy policy(){
        return policy;
    }

    synchronized Snapshot ensure(
        AccountRef owner
    ){
        return state(owner).snapshot();
    }

    synchronized Snapshot get(
        AccountRef owner
    ){
        State state=
            states.get(
                Objects.requireNonNull(
                    owner,
                    "owner"
                )
            );

        return state==null
            ?null
            :state.snapshot();
    }

    synchronized MutationResult addFriend(
        AccountRef owner,
        AccountRef target
    ){
        State state=state(owner);
        AccountRef checked=
            Objects.requireNonNull(
                target,
                "target"
            );

        if(state.friends.contains(checked))
            return result(
                MutationStatus.ALREADY_PRESENT,
                state
            );

        if(policy.crossListExclusive&&
           state.ignores.contains(checked))
            return result(
                MutationStatus.CONFLICTING_LIST,
                state
            );

        if(capacityReached(
                policy.maxFriends,
                state.friends.size()))
            return result(
                MutationStatus.CAPACITY_REACHED,
                state
            );

        state.friends.add(checked);

        return result(
            MutationStatus.ADDED,
            state
        );
    }

    synchronized MutationResult removeFriend(
        AccountRef owner,
        AccountRef target
    ){
        State state=state(owner);
        AccountRef checked=
            Objects.requireNonNull(
                target,
                "target"
            );

        if(!state.friends.remove(checked))
            return result(
                MutationStatus.NOT_PRESENT,
                state
            );

        return result(
            MutationStatus.REMOVED,
            state
        );
    }

    synchronized MutationResult addIgnore(
        AccountRef owner,
        AccountRef target
    ){
        State state=state(owner);
        AccountRef checked=
            Objects.requireNonNull(
                target,
                "target"
            );

        if(state.ignores.contains(checked))
            return result(
                MutationStatus.ALREADY_PRESENT,
                state
            );

        if(policy.crossListExclusive&&
           state.friends.contains(checked))
            return result(
                MutationStatus.CONFLICTING_LIST,
                state
            );

        if(capacityReached(
                policy.maxIgnores,
                state.ignores.size()))
            return result(
                MutationStatus.CAPACITY_REACHED,
                state
            );

        state.ignores.add(checked);

        return result(
            MutationStatus.ADDED,
            state
        );
    }

    synchronized MutationResult removeIgnore(
        AccountRef owner,
        AccountRef target
    ){
        State state=state(owner);
        AccountRef checked=
            Objects.requireNonNull(
                target,
                "target"
            );

        if(!state.ignores.remove(checked))
            return result(
                MutationStatus.NOT_PRESENT,
                state
            );

        return result(
            MutationStatus.REMOVED,
            state
        );
    }

    synchronized Snapshot restore(
        Snapshot snapshot
    ){
        Objects.requireNonNull(
            snapshot,
            "snapshot"
        );

        if(snapshot.version()!=
                SNAPSHOT_VERSION)
            throw new IllegalArgumentException(
                "unsupported social snapshot version="+
                snapshot.version()
            );

        if(capacityExceeded(
                policy.maxFriends,
                snapshot.friends().size()))
            throw new IllegalArgumentException(
                "friend snapshot exceeds policy capacity owner="+
                snapshot.owner()
            );

        if(capacityExceeded(
                policy.maxIgnores,
                snapshot.ignores().size()))
            throw new IllegalArgumentException(
                "ignore snapshot exceeds policy capacity owner="+
                snapshot.owner()
            );

        if(policy.crossListExclusive){
            HashSet<AccountRef> overlap=
                new HashSet<>(
                    snapshot.friends()
                );
            overlap.retainAll(
                snapshot.ignores()
            );

            if(!overlap.isEmpty())
                throw new IllegalArgumentException(
                    "social snapshot contains cross-list overlap owner="+
                    snapshot.owner()+
                    " overlap="+overlap
                );
        }

        State replacement=
            new State(
                snapshot.owner()
            );

        replacement.friends.addAll(
            snapshot.friends()
        );
        replacement.ignores.addAll(
            snapshot.ignores()
        );

        states.put(
            replacement.owner,
            replacement
        );

        return replacement.snapshot();
    }

    synchronized List<Snapshot> snapshot(){
        ArrayList<Snapshot> out=
            new ArrayList<>();

        for(State state:
                states.values())
            out.add(
                state.snapshot()
            );

        out.sort(
            Comparator.comparing(
                value->
                    value.owner()
            )
        );

        return Collections.unmodifiableList(
            out
        );
    }

    synchronized int ownerCount(){
        return states.size();
    }

    private State state(
        AccountRef owner
    ){
        AccountRef checked=
            Objects.requireNonNull(
                owner,
                "owner"
            );

        State state=
            states.get(checked);

        if(state!=null)
            return state;

        State created=
            new State(checked);

        states.put(
            checked,
            created
        );

        return created;
    }

    private static MutationResult result(
        MutationStatus status,
        State state
    ){
        return new MutationResult(
            status,
            state.snapshot()
        );
    }

    private static boolean capacityReached(
        int limit,
        int current
    ){
        return limit!=Policy.UNBOUNDED&&
            current>=limit;
    }

    private static boolean capacityExceeded(
        int limit,
        int current
    ){
        return limit!=Policy.UNBOUNDED&&
            current>limit;
    }

    private static List<AccountRef>
        immutableUniqueSorted(
            Collection<AccountRef> values,
            String label
        ){
        Objects.requireNonNull(
            values,
            label
        );

        TreeSet<AccountRef> sorted=
            new TreeSet<>();

        for(AccountRef value:
                values){
            AccountRef checked=
                Objects.requireNonNull(
                    value,
                    label+" entry"
                );

            if(!sorted.add(checked))
                throw new IllegalArgumentException(
                    "duplicate "+label+
                    " entry="+checked
                );
        }

        return Collections.unmodifiableList(
            new ArrayList<>(sorted)
        );
    }

    private static String normalizeRef(
        String value
    ){
        if(value==null)
            throw new NullPointerException(
                "accountRef"
            );

        String normalized=
            value.trim()
                .toLowerCase(
                    Locale.ROOT
                );

        if(normalized.isEmpty())
            throw new IllegalArgumentException(
                "accountRef blank"
            );

        return normalized;
    }

    private static String requireAuthority(
        String value
    ){
        if(value==null)
            throw new NullPointerException(
                "sourceAuthority"
            );

        String normalized=
            value.trim();

        if(normalized.isEmpty())
            throw new IllegalArgumentException(
                "sourceAuthority blank"
            );

        return normalized;
    }
}
