package spk.local;

import java.util.ArrayList;
import java.util.List;
import spk.event.DomainEventBus;

/**
 * R9 direct producer and World event-bus bridge regression.
 * Test is executable as a normal main; the build runs it explicitly.
 */
public final class LocalLabPvmKillOutcomeProducerTest {
    private static void require(boolean condition,String reason){
        if(!condition)throw new AssertionError(reason);
    }

    private static NpcEntity dead(int scene){
        NpcEntity npc=new NpcEntity(scene,1,3088,3495);
        npc.enableLocalLabMortalFixture(10);
        require(npc.applyLocalLabMortalDamage(10)==0,"lethal hp");
        return npc;
    }

    public static void main(String[] args)throws Exception{
        String previous=System.getProperty("spk.local.r6MortalFixture");
        try{
            System.setProperty("spk.local.r6MortalFixture","true");
            WorldPlayer owner=new WorldPlayer();
            ArrayList<CombatOutcome> received=new ArrayList<>();

            DomainEventBus bus=new DomainEventBus(()->true);
            DomainEventBus.Subscription subscription=bus.subscribe(
                CombatOutcome.class,
                DomainEventBus.Priority.NORMAL,
                received::add
            );
            LocalLabPvmKillOutcomeProducer producer=
                new LocalLabPvmKillOutcomeProducer(
                    outcome->{
                        try{bus.publish(outcome);}
                        catch(Exception error){
                            throw new IllegalStateException(error);
                        }
                    }
                );
            NpcEntity first=dead(16382);

            require(
                producer.publishCompletedDeath(first,false,owner,5L)
                    .contains("NO_CONFIRMED_REMOVED_DEATH"),
                "unremoved NPC emitted kill"
            );
            require(received.isEmpty(),"premature observer delivery");

            String firstLog=producer.publishCompletedDeath(
                first,true,owner,5L
            );
            require(firstLog.contains("delivery=WORLD_DOMAIN_BUS"),"bus result");
            require(received.size()==1,"first kill emitted more or less than once");
            CombatOutcome result=received.get(0);
            require(result.type()==CombatOutcomeType.NPC_KILL,"NPC kill type");
            require(result.context()==CombatOutcomeContext.NPC_PVM,"PvM context");
            require(
                result.attacker().equals(owner.id().toString()),
                "canonical attacker identity"
            );
            require(
                "locallab:npc:def=1:scene=16382".equals(result.victim()),
                "victim must encode session actor scene identity"
            );
            require(result.worldTick()==5L,"event tick");
            require(
                LocalLabPvmKillOutcomeProducer.AUTHORITY.equals(result.sourceAuthority()),
                "custom provenance is required"
            );
            require(
                producer.publishCompletedDeath(first,true,owner,6L)
                    .contains("DUPLICATE_DENIED"),
                "duplicate kill must be refused"
            );
            require(received.size()==1,"duplicate published a second event");

            NpcEntity second=dead(16381);
            producer.publishCompletedDeath(second,true,owner,12L);
            require(received.size()==2,"new scene generation must be distinct");
            require(received.get(1).worldTick()==12L,"second generation timestamp");

            NpcEntity notDead=new NpcEntity(16380,1,3088,3495);
            notDead.enableLocalLabMortalFixture(10);
            require(
                producer.publishCompletedDeath(notDead,true,owner,13L)
                    .contains("NO_CONFIRMED_REMOVED_DEATH"),
                "non-lethal actor produced kill"
            );
            require(received.size()==2,"non-lethal event was published");

            NpcEntity unmarked=new NpcEntity(200,1,3088,3495);
            require(producer.publishCompletedDeath(
                unmarked,true,owner,14L)==null,
                "nonfixture actor emitted event"
            );

            LocalLabPvmKillOutcomeProducer failing=
                new LocalLabPvmKillOutcomeProducer(
                    outcome->{throw new IllegalStateException("test listener");}
                );
            NpcEntity third=dead(16379);
            require(
                failing.publishCompletedDeath(third,true,owner,20L)
                    .contains("OBSERVER_FAILED_ISOLATED"),
                "observer failure blocked terminal state"
            );
            require(
                failing.publishCompletedDeath(third,true,owner,21L)
                    .contains("DUPLICATE_DENIED"),
                "failed observer replayed committed fact"
            );

            System.clearProperty("spk.local.r6MortalFixture");
            require(new LocalLabPvmKillOutcomeProducer(received::add)
                .publishCompletedDeath(dead(16378),true,owner,21L)==null,
                "outcome emitted outside opt-in fixture");
            require(received.size()==2,"disabled flag still emitted");

            require(subscription.active(),"subscription disappeared");
            subscription.close();
            bus.close();

            System.out.println(
                "R9_CUSTOM_PVM_KILL_OUTCOME_DOMAIN_PASS"+
                " npcKillSemantic=true worldEventBus=true"+
                " authenticatedActorRef=true uniqueSceneGeneration=true"+
                " eventTickExact=true removedDeathGate=true"+
                " duplicateRejection=true unmarkedDenied=true"+
                " optInRequired=true observerFailureIsolated=true"+
                " noRewardsOrKillCountInvented=true"
            );
        }finally{
            if(previous==null)System.clearProperty("spk.local.r6MortalFixture");
            else System.setProperty("spk.local.r6MortalFixture",previous);
        }
    }
}
