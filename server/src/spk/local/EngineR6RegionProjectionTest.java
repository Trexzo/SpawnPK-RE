package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.net.Socket;

/**
 * R6 region projection regression updated for the current ownership model.
 *
 * NpcRegistry is the viewer-local packet-65 projection.  HOME server authority
 * lives in HomeWorldRuntimePlan / WorldHomeNpcService and must survive region
 * view detach/reattach.
 *
 * Exact-v308 lifecycle authority requires HOME projection replay only after the
 * client acknowledges the packet-73 load with opcode 121.
 */
public final class EngineR6RegionProjectionTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(600L);

        LocalSession session=
            new LocalSession(
                new Socket(),
                true,
                true,
                world
            );

        WorldPlayer player=
            (WorldPlayer)get(
                session,
                "worldPlayer"
            );

        MovementState movement=
            (MovementState)get(
                session,
                "movement"
            );

        NpcRegistry npcs=
            (NpcRegistry)get(
                session,
                "npcs"
            );

        PetState pet=
            (PetState)get(
                session,
                "petState"
            );

        HomeWorldRuntimePlan home=
            (HomeWorldRuntimePlan)get(
                session,
                "homeWorld"
            );

        RegionLoadLifecycle regionLoads=
            (RegionLoadLifecycle)get(
                session,
                "regionLoads"
            );

        LocalRegionDevCommandHandler regions=
            (LocalRegionDevCommandHandler)get(
                session,
                "regionDevCommands"
            );

        world.registerPlayer(
            player,
            "r6projection"
        );

        ByteArrayOutputStream out=
            new ByteArrayOutputStream();

        ServerPacketWriter writer=
            new ServerPacketWriter(
                out,
                new IsaacCipher(
                    new int[]{81,82,83,84}
                )
            );

        npcs.bootstrapHome(
            writer,
            movement,
            pet,
            home
        );
        writer.flush();

        int homeViewBefore=
            npcs.visibleCount();

        int worldRegistryBefore=
            home.trackedWorldNpcCount();

        req(
            homeViewBefore>20,
            "home view npcs="+homeViewBefore
        );

        req(
            worldRegistryBefore>=homeViewBefore,
            "HOME world authority smaller than client projection world="+
            worldRegistryBefore+
            " view="+
            homeViewBefore
        );

        out.reset();

        LocalRegionDevCommandHandler.Result external=
            regions.handle(
                new String[]{
                    "regionload",
                    "16193",
                    "0"
                },
                "r6projection",
                null,
                writer
            );

        writer.flush();

        req(
            external!=null&&
            external.detailText.startsWith(
                "OK region=16193"
            ),
            external==null
                ?"null"
                :external.logText
        );

        req(
            external.detailText.contains(
                "removedHomeNpcView="+
                homeViewBefore
            ),
            external.detailText
        );

        req(
            movement.transientRegion(),
            "not transient"
        );

        req(
            movement.x()==4064&&
            movement.y()==4192,
            "unexpected deterministic landing="+
            movement.x()+","+
            movement.y()
        );

        req(
            out.size()>0,
            "no transition packets"
        );

        /*
         * Current contract:
         *   - NpcRegistry is the packet-65 client view.
         *   - HOME actors leave that view outside HOME.
         *   - World-owned HOME authority must remain intact.
         */
        req(
            npcs.visibleCount()==0,
            "HOME NPC client view retained in external region count="+
            npcs.visibleCount()
        );

        req(
            home.trackedWorldNpcCount()==
                worldRegistryBefore,
            "HOME world registry mutated entering external region before="+
            worldRegistryBefore+
            " after="+
            home.trackedWorldNpcCount()
        );

        req(
            regionLoads.pending(),
            "external packet73 did not open region-load lifecycle"
        );

        out.reset();

        LocalRegionDevCommandHandler.Result returned=
            regions.handle(
                new String[]{"regionhome"},
                "r6projection",
                external.scenePublisher,
                writer
            );

        writer.flush();

        req(
            returned!=null&&
            returned.detailText.startsWith(
                "OK world="+
                MovementState.INITIAL_X+","+
                MovementState.INITIAL_Y
            ),
            returned==null
                ?"null"
                :returned.logText
        );

        req(
            returned.detailText.contains(
                "homeSceneReplay=DEFERRED_UNTIL_OPCODE121"
            ),
            returned.detailText
        );

        req(
            movement.inHomeWindow(),
            "did not restore HOME window"
        );

        req(
            regionLoads.pending(),
            "HOME packet73 did not await opcode121"
        );

        req(
            npcs.visibleCount()==0,
            "HOME NPC view republished before opcode121 count="+
            npcs.visibleCount()
        );

        req(
            home.trackedWorldNpcCount()==
                worldRegistryBefore,
            "HOME world registry changed before ACK"
        );

        /*
         * Simulate the exact opcode-121 completion boundary.  The dedicated
         * LocalRegionStreamHandler regression owns packet timing; this R6 test
         * verifies projection ownership and non-duplication after that boundary.
         */
        RegionLoadLifecycle.Completion completion=
            regionLoads.complete();

        req(
            completion.matched,
            "HOME opcode121 completion did not match"
        );

        req(
            "DEV_RETURN_HOME_RELOCATION".equals(
                completion.reason
            ),
            "unexpected HOME lifecycle reason="+
            completion.reason
        );

        int republished=
            npcs.reattachHomeView(
                writer,
                movement,
                home
            );

        writer.flush();

        req(
            republished>20,
            "HOME NPC view was not republished after ACK added="+
            republished
        );

        req(
            npcs.visibleCount()==republished,
            "HOME NPC projection duplicated after ACK added="+
            republished+
            " visible="+
            npcs.visibleCount()
        );

        req(
            home.trackedWorldNpcCount()==
                worldRegistryBefore,
            "HOME world registry mutated after return before="+
            worldRegistryBefore+
            " after="+
            home.trackedWorldNpcCount()
        );

        world.unregisterPlayer(player);
        world.close();

        System.out.println(
            "V5160_ENGINE_R6_REGION_PROJECTION_PASS "+
            "region=16193 "+
            "landing=4064,4192 "+
            "homeNpcViewRemoved="+homeViewBefore+" "+
            "worldRegistryPreserved="+worldRegistryBefore+" "+
            "externalViewPruned=true "+
            "homeReplayAfter121=true "+
            "homeNpcRepublished="+republished+" "+
            "multiplayerGate=true "+
            "nonpersistent=true"
        );
    }

    private static Object get(
        Object owner,
        String name
    )throws Exception{
        Field field=
            owner.getClass()
                .getDeclaredField(name);

        field.setAccessible(true);

        return field.get(owner);
    }

    private static void req(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(message);
    }

    private EngineR6RegionProjectionTest(){}
}