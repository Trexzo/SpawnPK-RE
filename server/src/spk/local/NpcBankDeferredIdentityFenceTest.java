package spk.local;

import java.io.ByteArrayOutputStream;

public final class NpcBankDeferredIdentityFenceTest {
    public static void main(String[] args)throws Exception{
        WorldPlayer player=
            new WorldPlayer();

        MovementState movement=
            player.movement();
        BankState bank=
            player.bank();
        NpcRegistry npcs=
            new NpcRegistry();

        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();

        ServerPacketWriter writer=
            new ServerPacketWriter(
                wire,
                new IsaacCipher(
                    new int[]{1,2,3,4}
                )
            );

        LocalRoutedNpcInteractionHandler handler=
            new LocalRoutedNpcInteractionHandler(
                npcs,
                bank,
                movement
            );

        String spawnedOriginal=
            npcs.devSpawnNpc(
                7605,
                3,
                0,
                movement,
                writer
            );

        requireContains(
            spawnedOriginal,
            "DEV_NPC_SPAWN_OK",
            "original banker spawn"
        );

        NpcEntity original=
            banker(npcs);

        String deferred=
            handler.handle(
                new NpcAction(
                    155,
                    original.sceneIndex
                ),
                original,
                writer
            );

        requireContains(
            deferred,
            "DEFERRED_UNTIL_ADJACENT",
            "deferred banker acceptance"
        );

        if(!handler.hasPendingBank()||
           handler.pendingBankNpc()!=original)
            throw new AssertionError(
                "pending bank did not retain original NPC identity"
            );

        if(movement.queued()==0)
            throw new AssertionError(
                "deferred banker did not queue approach"
            );

        requireContains(
            npcs.devRemoveNpc(
                original.sceneIndex,
                writer
            ),
            "DEV_NPC_REMOVE_OK",
            "original banker removal"
        );

        String spawnedReplacement=
            npcs.devSpawnNpc(
                7605,
                3,
                0,
                movement,
                writer
            );

        requireContains(
            spawnedReplacement,
            "DEV_NPC_SPAWN_OK",
            "replacement banker spawn"
        );

        NpcEntity replacement=
            banker(npcs);

        if(replacement==original)
            throw new AssertionError(
                "replacement reused original object identity"
            );

        if(replacement.sceneIndex!=
                original.sceneIndex)
            throw new AssertionError(
                "fixture did not reuse scene index "+
                original.sceneIndex+
                " -> "+
                replacement.sceneIndex
            );

        String stale=
            handler.tick(
                System.currentTimeMillis(),
                writer
            );

        requireContains(
            stale,
            "CANCELLED_MISSING_OR_TIMEOUT",
            "stale banker cancellation"
        );

        if(bank.isOpen())
            throw new AssertionError(
                "stale banker interaction opened bank"
            );

        if(handler.hasPendingBank()||
           handler.pendingBankNpc()!=null)
            throw new AssertionError(
                "stale pending bank identity survived"
            );

        if(movement.queued()!=0)
            throw new AssertionError(
                "stale banker approach path was not cleared"
            );

        requireContains(
            npcs.devRemoveNpc(
                replacement.sceneIndex,
                writer
            ),
            "DEV_NPC_REMOVE_OK",
            "replacement banker cleanup"
        );

        String spawnedFresh=
            npcs.devSpawnNpc(
                7605,
                1,
                0,
                movement,
                writer
            );

        requireContains(
            spawnedFresh,
            "DEV_NPC_SPAWN_OK",
            "fresh banker spawn"
        );

        NpcEntity fresh=
            banker(npcs);

        if(fresh.sceneIndex!=
                original.sceneIndex)
            throw new AssertionError(
                "fresh banker did not reuse expected dynamic scene"
            );

        String opened=
            handler.handle(
                new NpcAction(
                    155,
                    fresh.sceneIndex
                ),
                fresh,
                writer
            );

        requireContains(
            opened,
            "V511_BANK_OPEN_NPC npc=7605",
            "fresh banker open"
        );
        requireContains(
            opened,
            "OPENED_ADJACENT_IMMEDIATE",
            "fresh banker adjacency"
        );

        if(!bank.isOpen())
            throw new AssertionError(
                "fresh banker did not open bank"
            );

        System.out.println(
            "NPC_BANK_DEFERRED_IDENTITY_FENCE_PASS "+
            "originalIdentityCaptured=true "+
            "sceneIndexReused=true "+
            "replacementIdentityRejected=true "+
            "staleBankOpenRejected=true "+
            "staleApproachCleared=true "+
            "freshReplacementWorks=true"
        );
    }

    private static NpcEntity banker(
        NpcRegistry npcs
    ){
        NpcEntity found=null;

        for(NpcEntity npc:
                npcs.snapshot()){
            if(npc.definitionId!=7605)
                continue;

            if(found!=null)
                throw new AssertionError(
                    "multiple banker fixture NPCs visible"
                );

            found=npc;
        }

        if(found==null)
            throw new AssertionError(
                "banker fixture missing"
            );

        return found;
    }

    private static void requireContains(
        String actual,
        String expected,
        String phase
    ){
        if(actual==null||
           !actual.contains(expected))
            throw new AssertionError(
                phase+
                " expected="+expected+
                " actual="+actual
            );
    }

    private NpcBankDeferredIdentityFenceTest(){}
}
