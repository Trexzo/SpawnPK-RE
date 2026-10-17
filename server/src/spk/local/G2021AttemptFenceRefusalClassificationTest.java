package spk.local;

/** G2021: real NPC source, followed by two different same-name replacements. */
public final class G2021AttemptFenceRefusalClassificationTest {
    public static void main(String[] args)throws Exception{
        G2017TerminalAttemptFenceRealDeathTest.main(args);
        try(G2017TerminalAttemptFenceRealDeathTest.Fixture f=
                new G2017TerminalAttemptFenceRealDeathTest.Fixture()){
            MonsterSpawnerPvmRuntime.FinalizeResult death=f.settle();
            f.world.unregisterPlayer(f.player,f.generation);
            WorldPlayer firstReplacement=new WorldPlayer();
            long firstGeneration=f.world.registerPlayer(firstReplacement,f.name);
            WorldPlayer secondReplacement=new WorldPlayer();
            long secondGeneration=-1L;
            try{
                LocalLabExperimentalTerminalAttemptFence fence=
                    new LocalLabExperimentalTerminalAttemptFence(f.world);
                LocalLabExperimentalTerminalAttemptFence.Attempt token=
                    fence.reserve(death,LocalLabTerminalDeliveryAssessment.Consumer.G1);
                G2017TerminalAttemptFenceRealDeathTest.check(
                    token.originStatus==
                        LocalLabSettledDeathEnvelope.OriginStatus.SAME_NAME_REBOUND&&
                    token.observedCurrentGeneration==firstGeneration,
                    "first post-death replacement is an explicitly observed rebound");
                f.world.unregisterPlayer(firstReplacement,firstGeneration);
                secondGeneration=f.world.registerPlayer(secondReplacement,f.name);
                LocalLabSettledDeathEnvelope second=
                    LocalLabSettledDeathEnvelope.observe(f.world,death);
                G2017TerminalAttemptFenceRealDeathTest.check(
                    second.originStatus==
                        LocalLabSettledDeathEnvelope.OriginStatus.SAME_NAME_REBOUND&&
                    second.observedCurrentGeneration==secondGeneration&&
                    secondGeneration!=firstGeneration,
                    "second replacement retains same enum but has new generation");
                String denied=null;
                try{
                    fence.reserve(death,LocalLabTerminalDeliveryAssessment.Consumer.G4);
                }catch(IllegalStateException expected){denied=expected.getMessage();}
                G2017TerminalAttemptFenceRealDeathTest.check(
                    "TERMINAL_ATTEMPT_CONFLICTING_DEATH_EVIDENCE".equals(denied)&&
                    fence.snapshot().reserved==1&&
                    fence.snapshot().refusalCount(
                        LocalLabExperimentalTerminalAttemptFence.Refusal
                            .CONFLICTING_DEATH_EVIDENCE)==1L,
                    "different observed rebound generations cannot split one death");
                fence.returned(token);
                LocalLabExperimentalTerminalAttemptFence.Snapshot after=fence.snapshot();
                G2017TerminalAttemptFenceRealDeathTest.check(
                    after.entries.get(0).observedCurrentGeneration==firstGeneration&&
                    after.entries.get(0).observation==
                        LocalLabExperimentalTerminalAttemptFence.Observation
                            .RETURNED_UNACKNOWLEDGED&&
                    f.world.pvmRecords().receiptCount()==1L&&
                    !after.safeToAutomaticallyReplay(),
                    "original reserved token preserved; no repeated NPC settlement or grant");
            }finally{
                if(f.world.players().owns(firstReplacement,firstGeneration))
                    f.world.unregisterPlayer(firstReplacement,firstGeneration);
                if(secondGeneration>=0L&&
                    f.world.players().owns(secondReplacement,secondGeneration))
                    f.world.unregisterPlayer(secondReplacement,secondGeneration);
            }
        }
        System.out.println("G2021_ATTEMPT_FENCE_REFUSAL_CLASSIFICATION_PASS"+
            " twoSameStatusReboundsDistinguished=true noCrossGenerationAttempt=true"+
            " oldAttemptRemainsObservable=true noGrantAck=true");
    }
    private G2021AttemptFenceRefusalClassificationTest(){}
}
