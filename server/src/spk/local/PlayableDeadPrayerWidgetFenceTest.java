package spk.local;

import java.io.ByteArrayOutputStream;

/**
 * P5.8: dead/0HP prayer widget clicks must neither modify the normal
 * or curses activation set nor produce S2C36 varps.
 *
 * Explicit CUSTOM_LOCALLAB policy: no assumption about auto-clearing
 * prayer state on death, passive drain, or protection effects.
 */
public final class PlayableDeadPrayerWidgetFenceTest {
    private static final int THICK_SKIN=5609;
    private static final int PIETY=18014;
    private static final int CURSE_PROTECT_ITEM=22503;
    private static final int HP=PlayerState.HITPOINTS;

    private static void require(boolean test,String message){
        if(!test)throw new AssertionError("P58_PRAYER_DEAD "+message);
    }

    public static void main(String[] args)throws Exception{
        require(args.length==0,"unexpected args");
        WorldPlayer player=new WorldPlayer();
        LocalGameplayWidgetHandler router=
            new LocalGameplayWidgetHandler(player);
        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ServerPacketWriter packets=new ServerPacketWriter(
            wire,new IsaacCipher(new int[]{81,82,83,84}));
        PlayerLifecycleService lifecycle=
            new PlayerLifecycleService(player);

        String normalOn=router.handle(THICK_SKIN,packets);
        require(normalOn.contains("enabled=true")&&
            player.prayers().activeWidget(THICK_SKIN)&&
            player.prayers().activeCount()==1&&
            wire.size()>0,"normal prayer baseline");
        int normalWire=wire.size();

        PlayerLifecycleService.DamageResult firstDeath=
            lifecycle.applyDamage(500,20L,"P58_NORMAL_DEATH");
        require(firstDeath.died&&
            player.lifecycle().dead()&&
            player.playerState().currentLevel(HP)==0,
            "normal fixture death");

        String repeat=router.handle(THICK_SKIN,packets);
        String piety=router.handle(PIETY,packets);
        require(repeat.contains("REJECTED_PLAYER_DEAD")&&
            piety.contains("REJECTED_PLAYER_DEAD")&&
            player.prayers().activeWidget(THICK_SKIN)&&
            !player.prayers().activeWidget(PIETY)&&
            player.prayers().activeCount()==1&&
            wire.size()==normalWire,
            "dead normal prayer changed varps or activation");

        require(lifecycle.tick(24L)==
            PlayerLifecycleService.TickResult.NONE,
            "early respawn");
        require(router.handle(THICK_SKIN,packets)
            .contains("REJECTED_PLAYER_DEAD")&&
            wire.size()==normalWire,"pre-respawn prayer");

        require(lifecycle.tick(25L)==
            PlayerLifecycleService.TickResult.RESPAWNED,
            "normal lifecycle respawn");
        String normalOff=router.handle(THICK_SKIN,packets);
        require(normalOff.contains("enabled=false")&&
            !player.prayers().activeWidget(THICK_SKIN)&&
            player.prayers().activeCount()==0&&
            wire.size()>normalWire,
            "normal prayer after respawn");

        // Book context is switched only while alive, then new death
        // tests the same fence on the Curses prayer widgets.
        player.prayers().switchBook("curses",packets);
        String curseOn=router.handle(CURSE_PROTECT_ITEM,packets);
        require(curseOn.contains("enabled=true")&&
            player.prayers().activeWidget(CURSE_PROTECT_ITEM)&&
            player.prayers().activeCount()==1,
            "curse prayer baseline");
        int curseWire=wire.size();

        PlayerLifecycleService.DamageResult secondDeath=
            lifecycle.applyDamage(500,30L,"P58_CURSE_DEATH");
        require(secondDeath.died&&player.lifecycle().dead(),
            "curse fixture death");
        String curseDead=router.handle(CURSE_PROTECT_ITEM,packets);
        require(curseDead.contains("REJECTED_PLAYER_DEAD")&&
            player.prayers().activeWidget(CURSE_PROTECT_ITEM)&&
            player.prayers().activeCount()==1&&
            wire.size()==curseWire,
            "dead curse click changed activation or packet bytes");

        require(lifecycle.tick(35L)==
            PlayerLifecycleService.TickResult.RESPAWNED,
            "curse lifecycle respawn");
        String curseOff=router.handle(CURSE_PROTECT_ITEM,packets);
        require(curseOff.contains("enabled=false")&&
            !player.prayers().activeWidget(CURSE_PROTECT_ITEM)&&
            player.prayers().activeCount()==0,
            "curse prayer after respawn");

        // Contradictory ALIVE lifecycle + 0 HP must fail closed.
        player.playerState().setCurrentLevel(HP,0);
        int beforeZeroHp=wire.size();
        String zeroHp=router.handle(CURSE_PROTECT_ITEM,packets);
        require(zeroHp.contains("REJECTED_PLAYER_DEAD")&&
            player.prayers().activeCount()==0&&
            wire.size()==beforeZeroHp,
            "zero-HP ALIVE prayer accepted");

        require(router.handle(999999,packets)==null,
            "unrelated widget routing changed");

        System.out.println(
            "P58_DEAD_PRAYER_WIDGET_FENCE_PASS"+
            " liveNormalAndCursesToggle=true"+
            " deadNormalToggleAndPietyDenied=true"+
            " deadCurseToggleDenied=true"+
            " deadNoConfig36Emitted=true"+
            " activeStatePreservedDuringDeath=true"+
            " bothRespawnRoutesUsable=true"+
            " zeroHpAliveDenied=true"+
            " unrelatedWidgetFallthrough=true"+
            " originalAutoClearDrainProtectionUnknown=true"+
            " graphicalClientAndConcurrencyUnverified=true");
    }

    private PlayableDeadPrayerWidgetFenceTest(){}
}
