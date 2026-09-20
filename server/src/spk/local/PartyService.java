package spk.local;

import java.util.*;

/**
 * Protocol-independent Party membership and invitation state.
 *
 * Caller policy owns authorization, party-size limits, eligibility, difficulty,
 * rewards and presentation. This service only enforces internal state integrity.
 */
final class PartyService {
    enum Visibility {
        PUBLIC,
        PRIVATE
    }

    static final class Snapshot {
        final PartyId id;
        final String leaderRef;
        final Visibility visibility;
        final List<String> members;
        final List<String> pendingInvites;
        final String sourceAuthority;

        Snapshot(Party party){
            this.id=party.id;
            this.leaderRef=party.leaderRef;
            this.visibility=party.visibility;
            this.members=immutable(party.members);
            this.pendingInvites=immutable(party.pendingInvites);
            this.sourceAuthority=party.sourceAuthority;
        }

        boolean member(String participantRef){
            return members.contains(
                requireRef(participantRef)
            );
        }

        boolean invited(String participantRef){
            return pendingInvites.contains(
                requireRef(participantRef)
            );
        }
    }

    private static final class Party {
        final PartyId id;
        final String leaderRef;
        final String sourceAuthority;
        Visibility visibility;
        final LinkedHashSet<String> members=
            new LinkedHashSet<>();
        final LinkedHashSet<String> pendingInvites=
            new LinkedHashSet<>();

        Party(
            PartyId id,
            String leaderRef,
            Visibility visibility,
            String sourceAuthority
        ){
            this.id=id;
            this.leaderRef=leaderRef;
            this.visibility=visibility;
            this.sourceAuthority=sourceAuthority;
            members.add(leaderRef);
        }

        Snapshot snapshot(){
            return new Snapshot(this);
        }
    }

    private final LinkedHashMap<PartyId,Party> parties=
        new LinkedHashMap<>();

    synchronized Snapshot create(
        PartyId id,
        String leaderRef,
        Visibility visibility,
        String sourceAuthority
    ){
        PartyId key=Objects.requireNonNull(id,"id");

        if(parties.containsKey(key))
            throw new IllegalStateException(
                "duplicate party id="+key
            );

        Party party=
            new Party(
                key,
                requireRef(leaderRef),
                Objects.requireNonNull(
                    visibility,
                    "visibility"
                ),
                requireAuthority(sourceAuthority)
            );

        parties.put(key,party);
        return party.snapshot();
    }

    synchronized Snapshot setVisibility(
        PartyId id,
        Visibility visibility
    ){
        Party party=require(id);
        party.visibility=
            Objects.requireNonNull(
                visibility,
                "visibility"
            );
        return party.snapshot();
    }

    synchronized Snapshot invite(
        PartyId id,
        String participantRef
    ){
        Party party=require(id);
        String participant=requireRef(participantRef);

        if(party.members.contains(participant))
            throw new IllegalStateException(
                "participant already member "+
                participant
            );

        if(!party.pendingInvites.add(participant))
            throw new IllegalStateException(
                "participant already invited "+
                participant
            );

        return party.snapshot();
    }

    synchronized Snapshot cancelInvite(
        PartyId id,
        String participantRef
    ){
        Party party=require(id);
        String participant=requireRef(participantRef);

        if(!party.pendingInvites.remove(participant))
            throw new IllegalStateException(
                "participant not invited "+
                participant
            );

        return party.snapshot();
    }

    synchronized Snapshot acceptInvite(
        PartyId id,
        String participantRef
    ){
        Party party=require(id);
        String participant=requireRef(participantRef);

        if(party.members.contains(participant))
            throw new IllegalStateException(
                "participant already member "+
                participant
            );

        if(!party.pendingInvites.remove(participant))
            throw new IllegalStateException(
                "participant has no pending invite "+
                participant
            );

        party.members.add(participant);
        return party.snapshot();
    }

    synchronized Snapshot leave(
        PartyId id,
        String participantRef
    ){
        Party party=require(id);
        String participant=requireRef(participantRef);

        if(party.leaderRef.equals(participant))
            throw new IllegalStateException(
                "leader departure policy unresolved"
            );

        if(!party.members.remove(participant))
            throw new IllegalStateException(
                "participant not member "+
                participant
            );

        return party.snapshot();
    }

    synchronized Snapshot kick(
        PartyId id,
        String participantRef
    ){
        Party party=require(id);
        String participant=requireRef(participantRef);

        if(party.leaderRef.equals(participant))
            throw new IllegalStateException(
                "cannot kick semantic party leader"
            );

        if(!party.members.remove(participant))
            throw new IllegalStateException(
                "participant not member "+
                participant
            );

        return party.snapshot();
    }

    synchronized Snapshot get(PartyId id){
        Party party=parties.get(
            Objects.requireNonNull(id,"id")
        );
        return party==null?null:party.snapshot();
    }

    synchronized int size(){
        return parties.size();
    }

    synchronized List<Snapshot> snapshot(){
        ArrayList<Party> ordered=
            new ArrayList<>(parties.values());

        ordered.sort(
            Comparator.comparing(value->value.id)
        );

        ArrayList<Snapshot> out=new ArrayList<>();

        for(Party party:ordered)
            out.add(party.snapshot());

        return Collections.unmodifiableList(out);
    }

    private Party require(PartyId id){
        PartyId key=Objects.requireNonNull(id,"id");
        Party party=parties.get(key);

        if(party==null)
            throw new IllegalArgumentException(
                "unknown party id="+key
            );

        return party;
    }

    private static List<String> immutable(
        Collection<String> values
    ){
        return Collections.unmodifiableList(
            new ArrayList<>(values)
        );
    }

    static String requireRef(String value){
        if(value==null)
            throw new NullPointerException(
                "participantRef"
            );

        String clean=value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                "participantRef blank"
            );

        return clean;
    }

    private static String requireAuthority(String value){
        if(value==null)
            throw new NullPointerException(
                "sourceAuthority"
            );

        String clean=value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                "sourceAuthority blank"
            );

        return clean;
    }
}
