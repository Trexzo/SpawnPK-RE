package spk.local;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class MatchInstanceSessionCompositionAtomicityTest {
    public static void main(String[] args)throws Exception{
        String clan=read(
            "server/src/spk/local/ClanWarSessionService.java"
        );
        String duel=read(
            "server/src/spk/local/DuelSessionService.java"
        );
        String tournament=read(
            "server/src/spk/local/TournamentService.java"
        );

        assertService(
            clan,
            "ClanWarSessionService",
            "synchronized Snapshot startAccepted(",
            "sessions.put(",
            "synchronized Snapshot complete(",
            "entry.lifecycle=",
            "synchronized Snapshot cancel(",
            "entry.lifecycle="
        );

        assertService(
            duel,
            "DuelSessionService",
            "synchronized Snapshot startAccepted(",
            "entry.matchId=checkedMatchId;",
            "synchronized Snapshot complete(",
            "entry.state=State.COMPLETED;",
            "synchronized Snapshot cancelActive(",
            "entry.state=State.CANCELLED;"
        );

        assertTournamentService(
            tournament
        );

        System.out.println(
            "MATCH_INSTANCE_SESSION_COMPOSITION_ATOMICITY_PASS "+
            "clanStartup=true "+
            "clanTerminal=true "+
            "duelStartup=true "+
            "duelTerminal=true "+
            "tournamentStartup=true "+
            "tournamentTerminal=true "+
            "tournamentGlobalEventStartupOwnership=true "+
            "tournamentGlobalEventTerminalOwnership=true "+
            "tournamentStartupHoldFailureSafe=true "+
            "tournamentTerminalHoldOrder=true "+
            "tournamentOpaqueEventCapability=true "+
            "durableChildLeaseStartup=true "+
            "durableChildLeaseTerminal=true "+
            "terminalLeaseReleasedLast=true "+
            "parentPublishProtectedByLease=true "+
            "ownerPublishAfterOwnedAction=true "+
            "terminalPreflightInsideOwnership=true"
        );
    }

    private static void assertTournamentService(
        String source
    ){
        require(
            count(
                source,
                "withCompositionOwnership("
            )==1,
            "TournamentService legacy composition helper count"
        );

        require(
            count(
                source,
                "withEventAndCompositionOwnership("
            )==4,
            "TournamentService GlobalEvent+match composition call/helper count"
        );

        String start=method(
            source,
            "synchronized Snapshot startMatch("
        );
        String complete=method(
            source,
            "synchronized Snapshot completeMatch("
        );
        String cancel=method(
            source,
            "synchronized Snapshot cancelMatch("
        );

        int startOwned=
            start.indexOf(
                "withEventAndCompositionOwnership("
            );
        int startLifecycle=
            start.indexOf(
                "GlobalEventService.Snapshot event="
            );
        int startPublish=
            start.indexOf(
                "first.state="
            );

        require(
            startOwned>=0,
            "TournamentService startup lacks GlobalEvent+match composition ownership"
        );
        require(
            startLifecycle>startOwned,
            "TournamentService checks GlobalEvent lifecycle before composition ownership"
        );
        require(
            start.indexOf(
                "Lifecycle.ACTIVE",
                startLifecycle
            )>startLifecycle,
            "TournamentService startup lacks ACTIVE lifecycle check inside ownership"
        );
        require(
            startPublish>startOwned,
            "TournamentService publishes entrant state before owned startup action"
        );
        require(
            start.indexOf(
                "acquireWorldInstanceCompositionLease("
            )>startOwned&&
            start.indexOf(
                "acquireWorldInstanceCompositionLease("
            )<startPublish,
            "TournamentService startup does not acquire child lease before parent publication"
        );

        int startHoldAcquire=
            start.indexOf(
                "events.acquireTerminalHold("
            );
        int startTry=
            start.indexOf(
                "try{",
                startHoldAcquire
            );
        int startPublished=
            start.indexOf(
                "published=true;",
                startTry
            );
        int startFinally=
            start.indexOf(
                "}finally{",
                startPublished
            );
        int startHoldRelease=
            start.indexOf(
                "events.releaseTerminalHold(",
                startFinally
            );

        require(
            startHoldAcquire>startOwned&&
            startTry>startHoldAcquire&&
            startPublished>startTry&&
            startFinally>startPublished&&
            startHoldRelease>startFinally,
            "TournamentService startup terminal hold lacks failure-safe publication ordering"
        );

        require(
            source.contains(
                "GlobalEventService.TerminalHold"
            )&&
            source.contains(
                "requireEventHold("
            )&&
            !source.contains(
                "terminalHoldKey("
            ),
            "TournamentService GlobalEvent terminal ownership is not opaque capability based"
        );

        assertOwnedBeforePublishWith(
            complete,
            "withEventAndCompositionOwnership(",
            "tournamentMatch.state=",
            "TournamentService complete"
        );
        assertOwnedBeforePublishWith(
            cancel,
            "withEventAndCompositionOwnership(",
            "tournamentMatch.state=",
            "TournamentService cancel"
        );

        require(
            complete.indexOf(
                "withEventAndCompositionOwnership("
            )>=0&&
            complete.indexOf(
                "preflightOwnedInstance"
            )>
            complete.indexOf(
                "withEventAndCompositionOwnership("
            ),
            "TournamentService complete preflight outside GlobalEvent+match ownership"
        );

        require(
            cancel.indexOf(
                "withEventAndCompositionOwnership("
            )>=0&&
            cancel.indexOf(
                "preflightOwnedInstance"
            )>
            cancel.indexOf(
                "withEventAndCompositionOwnership("
            ),
            "TournamentService cancel preflight outside GlobalEvent+match ownership"
        );

        require(
            complete.indexOf(
                "events.releaseTerminalHold("
            )>
            complete.indexOf(
                "tournamentMatch.state="
            ),
            "TournamentService complete releases terminal hold before local terminal publication"
        );
        require(
            cancel.indexOf(
                "events.releaseTerminalHold("
            )>
            cancel.indexOf(
                "tournamentMatch.state="
            ),
            "TournamentService cancel releases terminal hold before local terminal publication"
        );

        int completeChildTerminal=
            complete.indexOf(
                "matches.completeOwned("
            );
        int completeParentPublish=
            complete.indexOf(
                "tournamentMatch.state="
            );
        int completeLeaseRelease=
            complete.indexOf(
                "releaseWorldInstanceCompositionLease("
            );

        require(
            completeChildTerminal>
                complete.indexOf(
                    "preflightOwnedInstance"
                )&&
            completeParentPublish>
                completeChildTerminal&&
            completeLeaseRelease>
                completeParentPublish,
            "TournamentService complete must retain child lease through terminal publication"
        );

        int cancelChildTerminal=
            cancel.indexOf(
                "matches.cancelOwned("
            );
        int cancelParentPublish=
            cancel.indexOf(
                "tournamentMatch.state="
            );
        int cancelLeaseRelease=
            cancel.indexOf(
                "releaseWorldInstanceCompositionLease("
            );

        require(
            cancelChildTerminal>
                cancel.indexOf(
                    "preflightOwnedInstance"
                )&&
            cancelParentPublish>
                cancelChildTerminal&&
            cancelLeaseRelease>
                cancelParentPublish,
            "TournamentService cancel must retain child lease through terminal publication"
        );
    }


    private static void assertService(
        String source,
        String label,
        String startSignature,
        String startPublish,
        String completeSignature,
        String completePublish,
        String cancelSignature,
        String cancelPublish
    ){
        require(
            count(
                source,
                "withCompositionOwnership("
            )==4,
            label+" composition ownership call/helper count"
        );

        String start=method(
            source,
            startSignature
        );
        String complete=method(
            source,
            completeSignature
        );
        String cancel=method(
            source,
            cancelSignature
        );

        assertOwnedBeforePublish(
            start,
            startPublish,
            label+" startup"
        );
        assertOwnedBeforePublish(
            complete,
            completePublish,
            label+" complete"
        );
        assertOwnedBeforePublish(
            cancel,
            cancelPublish,
            label+" cancel"
        );

        require(
            complete.indexOf(
                "withCompositionOwnership("
            )>=0&&
            complete.indexOf(
                "preflightOwnedInstance"
            )>
            complete.indexOf(
                "withCompositionOwnership("
            ),
            label+" complete preflight outside ownership"
        );

        require(
            cancel.indexOf(
                "withCompositionOwnership("
            )>=0&&
            cancel.indexOf(
                "preflightOwnedInstance"
            )>
            cancel.indexOf(
                "withCompositionOwnership("
            ),
            label+" cancel preflight outside ownership"
        );

        require(
            start.indexOf(
                "acquireWorldInstanceCompositionLease("
            )>
            start.indexOf(
                "withCompositionOwnership("
            )&&
            start.indexOf(
                "acquireWorldInstanceCompositionLease("
            )<
            start.indexOf(
                startPublish
            ),
            label+" startup parent publication is not protected by durable child lease"
        );
        int completeChildTerminal=
            complete.indexOf(
                "matches.completeOwned("
            );
        int completeParentPublish=
            complete.indexOf(
                completePublish
            );
        int completeLeaseRelease=
            complete.indexOf(
                "releaseWorldInstanceCompositionLease("
            );

        require(
            completeChildTerminal>
                complete.indexOf(
                    "preflightOwnedInstance"
                )&&
            completeParentPublish>
                completeChildTerminal&&
            completeLeaseRelease>
                completeParentPublish,
            label+" complete must retain child lease through parent terminal publication"
        );

        int cancelChildTerminal=
            cancel.indexOf(
                "matches.cancelOwned("
            );
        int cancelParentPublish=
            cancel.indexOf(
                cancelPublish
            );
        int cancelLeaseRelease=
            cancel.indexOf(
                "releaseWorldInstanceCompositionLease("
            );

        require(
            cancelChildTerminal>
                cancel.indexOf(
                    "preflightOwnedInstance"
                )&&
            cancelParentPublish>
                cancelChildTerminal&&
            cancelLeaseRelease>
                cancelParentPublish,
            label+" cancel must retain child lease through parent terminal publication"
        );
    }

    private static void assertOwnedBeforePublishWith(
        String method,
        String ownershipAnchor,
        String publishAnchor,
        String label
    ){
        int owned=
            method.indexOf(
                ownershipAnchor
            );
        int publish=
            method.indexOf(
                publishAnchor
            );

        require(
            owned>=0,
            label+" lacks requested composition ownership"
        );
        require(
            publish>owned,
            label+" publishes owner state before owned action"
        );
    }

    private static void assertOwnedBeforePublish(
        String method,
        String publishAnchor,
        String label
    ){
        int owned=
            method.indexOf(
                "withCompositionOwnership("
            );
        int publish=
            method.indexOf(
                publishAnchor
            );

        require(
            owned>=0,
            label+" lacks composition ownership"
        );
        require(
            publish>owned,
            label+" publishes owner state before owned action"
        );
    }

    private static String method(
        String source,
        String signature
    ){
        int start=
            source.indexOf(signature);

        require(
            start>=0,
            "missing method "+signature
        );

        int open=
            source.indexOf(
                '{',
                start
            );

        require(
            open>=0,
            "missing method body "+signature
        );

        int depth=0;

        for(int i=open;i<source.length();i++){
            char value=source.charAt(i);

            if(value=='{')
                depth++;
            else if(value=='}'){
                depth--;

                if(depth==0)
                    return source.substring(
                        start,
                        i+1
                    );
            }
        }

        throw new AssertionError(
            "unterminated method "+signature
        );
    }

    private static int count(
        String source,
        String needle
    ){
        int count=0;
        int from=0;

        while(true){
            int found=
                source.indexOf(
                    needle,
                    from
                );

            if(found<0)
                return count;

            count++;
            from=
                found+needle.length();
        }
    }

    private static String read(
        String path
    )throws Exception{
        return new String(
            Files.readAllBytes(
                Paths.get(path)
            ),
            StandardCharsets.UTF_8
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private MatchInstanceSessionCompositionAtomicityTest(){}
}
