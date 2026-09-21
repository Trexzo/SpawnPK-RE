package spk.local;

public final class WorldPlayerTimedEffectIntegrationTest {
    public static void main(String[] args){
        World world=World.isolatedForTest(20L);
        WorldPlayer registered=new WorldPlayer();
        WorldPlayer offline=new WorldPlayer();

        try{
            if(registered.timedEffects()==null||
               offline.timedEffects()==null||
               registered.timedEffects()==offline.timedEffects())
                throw new AssertionError(
                    "timed effects are not canonical per-player state"
                );

            registered.timedEffects().applyFixed(
                "TELEBLOCK",
                2,
                0L,
                "CUSTOM_LOCALLAB"
            );

            offline.timedEffects().applyFixed(
                "FREEZE",
                1,
                0L,
                "CUSTOM_LOCALLAB"
            );

            world.registerPlayer(
                registered,
                "timed-effect-registered"
            );

            if(!world.tickTargetsSnapshot().isEmpty())
                throw new AssertionError(
                    "test unexpectedly has session tick targets"
                );

            long tick1=world.observePulse(20L);

            if(tick1!=1L||
               !registered.timedEffects().active(
                   "TELEBLOCK"))
                throw new AssertionError(
                    "registered effect expired before deadline tick="+
                    tick1
                );

            if(!offline.timedEffects().active(
                    "FREEZE"))
                throw new AssertionError(
                    "unregistered player was ticked by World membership"
                );

            long tick2=world.observePulse(40L);

            if(tick2!=2L||
               registered.timedEffects().active(
                   "TELEBLOCK"))
                throw new AssertionError(
                    "registered effect did not expire on deadline tick="+
                    tick2
                );

            world.registerPlayer(
                offline,
                "timed-effect-offline"
            );

            long tick3=world.observePulse(60L);

            if(tick3!=3L||
               offline.timedEffects().active(
                   "FREEZE"))
                throw new AssertionError(
                    "overdue offline effect did not expire after registration tick="+
                    tick3
                );

            if(!world.tickTargetsSnapshot().isEmpty())
                throw new AssertionError(
                    "semantic effect integration created a session tick target"
                );

            System.out.println(
                "WORLD_PLAYER_TIMED_EFFECT_INTEGRATION_PASS "+
                "canonicalOwner=WorldPlayer "+
                "registeredTick=true "+
                "sessionTickTargetRequired=false "+
                "deadlineExpiry=true "+
                "offlineCatchup=true "+
                "protocolIndependent=true"
            );
        }finally{
            if(registered.registered())
                world.unregisterPlayer(
                    registered
                );
            if(offline.registered())
                world.unregisterPlayer(
                    offline
                );
            world.close();
        }
    }

    private WorldPlayerTimedEffectIntegrationTest(){}
}
