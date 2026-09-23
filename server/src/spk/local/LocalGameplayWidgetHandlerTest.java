package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalGameplayWidgetHandlerTest {
    public static void main(String[] args)throws Exception{
        WorldPlayer player=new WorldPlayer();
        LocalGameplayWidgetHandler h=new LocalGameplayWidgetHandler(
            player.prayers(),
            player.playerState(),
            player.equipment(),
            player.combatStyles(),
            player.magic(),
            player.bank()
        );

        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ServerPacketWriter w=new ServerPacketWriter(
            wire,new IsaacCipher(new int[]{1,2,3,4}));

        int before=wire.size();
        String prayer=h.handle(5609,w); // Thick Skin, normal prayer book.
        if(prayer==null||
           !prayer.contains("V510_PRAYER_WIDGET widget=5609")||
           !prayer.contains("PRAYER_TOGGLE name=Thick Skin enabled=true"))
            throw new AssertionError("prayer route="+prayer);
        if(!player.prayers().activeWidget(5609))
            throw new AssertionError("prayer state not delegated");
        if(wire.size()<=before)
            throw new AssertionError("prayer click emitted no config packet");

        // WorldPlayer intentionally starts with Bloodrend equipped, so derive
        // the exact current combat root instead of assuming an unarmed fixture.
        int combatRoot=CombatInterfaceRepository.forWeapon(
            player.equipment().weapon());
        CombatStyleRepository.Style expectedStyle=
            CombatStyleRepository.defaultForRoot(combatRoot);
        if(expectedStyle==null)
            throw new AssertionError("default combat style unresolved root="+combatRoot);

        before=wire.size();
        String style=h.handle(expectedStyle.widget,w);
        if(style==null||
           !style.contains("V510_COMBAT_STYLE widget="+expectedStyle.widget)||
           !style.contains("COMBAT_STYLE_SELECTED"))
            throw new AssertionError("combat-style route="+style);
        if(player.combatStyles().value()!=expectedStyle.value)
            throw new AssertionError("combat-style state not delegated");
        if(wire.size()<=before)
            throw new AssertionError("combat style emitted no config packet");

        // Bones to bananas: direct spell with exact visible resource requirements.
        player.bank().spawnItem(557,2,w);
        player.bank().spawnItem(555,2,w);
        player.bank().spawnItem(561,1,w);
        String direct=h.handle(1159,w);
        if(direct==null||
           !direct.contains("V510_MAGIC_DIRECT widget=1159")||
           !direct.contains("ACCEPTED_CLIENT_VISIBLE_REQUIREMENTS spell=Bones to bananas"))
            throw new AssertionError("direct spell route="+direct);
        if(!player.magic().last().contains("DIRECT_ACCEPT"))
            throw new AssertionError("direct spell state not delegated");

        String home=h.handle(1195,w);
        if(home==null||
           !home.contains("V510_MAGIC_DIRECT widget=1195")||
           !home.contains("ACCEPTED_CLIENT_VISIBLE_REQUIREMENTS spell=Home Teleport"))
            throw new AssertionError("home teleport route="+home);
        if(!h.consumeAcceptedHomeTeleport())
            throw new AssertionError("accepted home teleport effect not exposed");
        if(h.consumeAcceptedHomeTeleport())
            throw new AssertionError("home teleport effect was not one-shot");

        if(h.handle(999999,w)!=null)
            throw new AssertionError("unrelated widget must fall through");

        System.out.println(
            "LOCAL_GAMEPLAY_WIDGET_HANDLER_PASS prayer=true combatStyle=true directSpell=true homeTeleportEffect=true unrelatedFallthrough=true");
    }
}
