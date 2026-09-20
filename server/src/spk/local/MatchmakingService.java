package spk.local;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Protocol-independent queue/lobby to assignment lifecycle.
 *
 * Selection policy is caller-supplied. The service owns only queue membership,
 * minimum-capacity metadata and WAITING -> STARTING -> ASSIGNED integrity.
 */
final class MatchmakingService {
    enum MemberState {
        WAITING,
        STARTING,
        ASSIGNED
    }

    static final class QueueDefinition {
        final QueueId id;
        final int minimumCapacity;
        final String sourceAuthority;

        QueueDefinition(
            QueueId id,
            int minimumCapacity,
            String sourceAuthority
        ){
            this.id=Objects.requireNonNull(id,"id");

            if(minimumCapacity<=0)
                throw new IllegalArgumentException(
                    "minimumCapacity="+minimumCapacity
                );

            this.minimumCapacity=minimumCapacity;
            this.sourceAuthority=
                requireAuthority(sourceAuthority);
        }
    }

    static final class MatchAssignmentId {
        final long value;

        MatchAssignmentId(long value){
            if(value<=0)
                throw new IllegalArgumentException(
                    "assignment id="+value
                );
            this.value=value;
        }

        @Override public boolean equals(Object other){
            return other instanceof MatchAssignmentId&&
                ((MatchAssignmentId)other).value==value;
        }

        @Override public int hashCode(){
            return Long.hashCode(value);
        }

        @Override public String toString(){
            return "match-assignment-"+
                Long.toUnsignedString(value);
        }
    }

    static final class MemberSnapshot {
        final String participantRef;
        final MemberState state;
        final MatchAssignmentId assignmentId;
        final WorldInstanceId instanceId;

        MemberSnapshot(Member member){
            this.participantRef=member.participantRef;
            this.state=member.state;
            this.assignmentId=member.assignmentId;
            this.instanceId=member.instanceId;
        }

        boolean hasAssignment(){
            return assignmentId!=null;
        }

        boolean hasInstance(){
            return instanceId!=null;
        }
    }

    static final class QueueSnapshot {
        final QueueDefinition definition;
        final List<MemberSnapshot> members;

        QueueSnapshot(Queue queue){
            this.definition=queue.definition;

            ArrayList<MemberSnapshot> out=
                new ArrayList<>();

            for(Member member:queue.members.values())
                out.add(new MemberSnapshot(member));

            this.members=Collections.unmodifiableList(out);
        }

        int waitingCount(){
            int count=0;

            for(MemberSnapshot member:members){
                if(member.state==MemberState.WAITING)
                    count++;
            }

            return count;
        }
    }

    static final class AssignmentSnapshot {
        final MatchAssignmentId assignmentId;
        final QueueId queueId;
        final List<String> participants;
        final MemberState state;
        final WorldInstanceId instanceId;

        AssignmentSnapshot(Assignment assignment){
            this.assignmentId=assignment.id;
            this.queueId=assignment.queueId;
            this.participants=
                Collections.unmodifiableList(
                    new ArrayList<>(
                        assignment.participants
                    )
                );
            this.state=assignment.state;
            this.instanceId=assignment.instanceId;
        }
    }

    interface AssignmentSelector {
        List<String> select(
            QueueDefinition definition,
            List<String> waitingParticipants
        );
    }

    private static final class Member {
        final String participantRef;
        MemberState state=MemberState.WAITING;
        MatchAssignmentId assignmentId;
        WorldInstanceId instanceId;

        Member(String participantRef){
            this.participantRef=participantRef;
        }
    }

    private static final class Queue {
        final QueueDefinition definition;
        final LinkedHashMap<String,Member> members=
            new LinkedHashMap<>();

        Queue(QueueDefinition definition){
            this.definition=definition;
        }

        QueueSnapshot snapshot(){
            return new QueueSnapshot(this);
        }
    }

    private static final class Assignment {
        final MatchAssignmentId id;
        final QueueId queueId;
        final List<String> participants;
        MemberState state=MemberState.STARTING;
        WorldInstanceId instanceId;

        Assignment(
            MatchAssignmentId id,
            QueueId queueId,
            List<String> participants
        ){
            this.id=id;
            this.queueId=queueId;
            this.participants=
                Collections.unmodifiableList(
                    new ArrayList<>(participants)
                );
        }

        AssignmentSnapshot snapshot(){
            return new AssignmentSnapshot(this);
        }
    }

    private final LinkedHashMap<QueueId,Queue> queues=
        new LinkedHashMap<>();

    private final LinkedHashMap<MatchAssignmentId,Assignment>
        assignments=
            new LinkedHashMap<>();

    private final AtomicLong assignmentSequence=
        new AtomicLong();

    synchronized QueueSnapshot defineQueue(
        QueueDefinition definition
    ){
        QueueDefinition checked=
            Objects.requireNonNull(
                definition,
                "definition"
            );

        if(queues.containsKey(checked.id))
            throw new IllegalStateException(
                "duplicate queue id="+checked.id
            );

        Queue queue=new Queue(checked);
        queues.put(checked.id,queue);
        return queue.snapshot();
    }

    synchronized MemberSnapshot enqueue(
        QueueId queueId,
        String participantRef
    ){
        Queue queue=requireQueue(queueId);
        String participant=
            PartyService.requireRef(
                participantRef
            );

        if(queue.members.containsKey(participant))
            throw new IllegalStateException(
                "participant already in queue "+
                queueId+" ref="+participant
            );

        Member member=new Member(participant);
        queue.members.put(participant,member);
        return new MemberSnapshot(member);
    }

    synchronized MemberSnapshot dequeue(
        QueueId queueId,
        String participantRef
    ){
        Queue queue=requireQueue(queueId);
        String participant=
            PartyService.requireRef(
                participantRef
            );

        Member member=queue.members.get(participant);

        if(member==null)
            throw new IllegalStateException(
                "participant not in queue "+
                queueId+" ref="+participant
            );

        if(member.state!=MemberState.WAITING)
            throw new IllegalStateException(
                "cannot dequeue from "+
                member.state+
                " ref="+participant
            );

        queue.members.remove(participant);
        return new MemberSnapshot(member);
    }

    synchronized AssignmentSnapshot beginAssignment(
        QueueId queueId,
        AssignmentSelector selector
    ){
        Queue queue=requireQueue(queueId);
        AssignmentSelector checkedSelector=
            Objects.requireNonNull(
                selector,
                "selector"
            );

        ArrayList<String> waiting=new ArrayList<>();

        for(Member member:queue.members.values()){
            if(member.state==MemberState.WAITING)
                waiting.add(member.participantRef);
        }

        if(waiting.size()<
                queue.definition.minimumCapacity)
            throw new IllegalStateException(
                "queue below minimum capacity "+
                queue.definition.minimumCapacity+
                " waiting="+waiting.size()
            );

        List<String> raw=
            Objects.requireNonNull(
                checkedSelector.select(
                    queue.definition,
                    Collections.unmodifiableList(
                        new ArrayList<>(waiting)
                    )
                ),
                "selector result"
            );

        ArrayList<String> selected=new ArrayList<>();
        HashSet<String> unique=new HashSet<>();
        HashSet<String> waitingSet=
            new HashSet<>(waiting);

        for(String value:raw){
            String participant=
                PartyService.requireRef(value);

            if(!unique.add(participant))
                throw new IllegalStateException(
                    "selector returned duplicate "+
                    participant
                );

            if(!waitingSet.contains(participant))
                throw new IllegalStateException(
                    "selector returned non-waiting participant "+
                    participant
                );

            selected.add(participant);
        }

        if(selected.size()<
                queue.definition.minimumCapacity)
            throw new IllegalStateException(
                "selector below minimum capacity "+
                queue.definition.minimumCapacity+
                " selected="+selected.size()
            );

        MatchAssignmentId assignmentId=
            new MatchAssignmentId(
                assignmentSequence.incrementAndGet()
            );

        Assignment assignment=
            new Assignment(
                assignmentId,
                queue.definition.id,
                selected
            );

        for(String participant:selected){
            Member member=queue.members.get(participant);
            member.state=MemberState.STARTING;
            member.assignmentId=assignmentId;
        }

        assignments.put(assignmentId,assignment);
        return assignment.snapshot();
    }

    synchronized boolean assign(
        MatchAssignmentId assignmentId,
        WorldInstanceId instanceId
    ){
        Assignment assignment=
            requireAssignment(assignmentId);
        WorldInstanceId instance=
            Objects.requireNonNull(
                instanceId,
                "instanceId"
            );

        if(assignment.state==MemberState.ASSIGNED){
            if(!assignment.instanceId.equals(instance))
                throw new IllegalStateException(
                    "assignment already bound to "+
                    assignment.instanceId
                );
            return false;
        }

        if(assignment.state!=MemberState.STARTING)
            throw new IllegalStateException(
                "assignment state="+assignment.state
            );

        Queue queue=requireQueue(assignment.queueId);

        for(String participant:
                assignment.participants){
            Member member=queue.members.get(participant);

            if(member==null||
               member.state!=MemberState.STARTING||
               !assignment.id.equals(
                    member.assignmentId))
                throw new IllegalStateException(
                    "assignment member state drift "+
                    participant
                );
        }

        assignment.state=MemberState.ASSIGNED;
        assignment.instanceId=instance;

        for(String participant:
                assignment.participants){
            Member member=queue.members.get(participant);
            member.state=MemberState.ASSIGNED;
            member.instanceId=instance;
        }

        return true;
    }

    synchronized QueueSnapshot getQueue(QueueId id){
        Queue queue=queues.get(
            Objects.requireNonNull(id,"id")
        );
        return queue==null?null:queue.snapshot();
    }

    synchronized AssignmentSnapshot getAssignment(
        MatchAssignmentId id
    ){
        Assignment assignment=assignments.get(
            Objects.requireNonNull(id,"id")
        );
        return assignment==null
            ?null
            :assignment.snapshot();
    }

    synchronized int queueCount(){
        return queues.size();
    }

    synchronized int assignmentCount(){
        return assignments.size();
    }

    private Queue requireQueue(QueueId id){
        QueueId key=Objects.requireNonNull(id,"id");
        Queue queue=queues.get(key);

        if(queue==null)
            throw new IllegalArgumentException(
                "unknown queue id="+key
            );

        return queue;
    }

    private Assignment requireAssignment(
        MatchAssignmentId id
    ){
        MatchAssignmentId key=
            Objects.requireNonNull(id,"id");
        Assignment assignment=assignments.get(key);

        if(assignment==null)
            throw new IllegalArgumentException(
                "unknown assignment id="+key
            );

        return assignment;
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
