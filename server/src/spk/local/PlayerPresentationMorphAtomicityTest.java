package spk.local;

public final class PlayerPresentationMorphAtomicityTest {
    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(90_000L);
        DevAuthorityWorkbench dev=
            new DevAuthorityWorkbench();
        PlayerPresentationService presentation=
            new PlayerPresentationService(
                world,
                dev
            );
        EquipmentState equipment=
            new EquipmentState();
        PlayerState player=
            new PlayerState();

        try{
            ServerPacketWriter good=
                new ServerPacketWriter(
                    new OutboundPacketQueue(4096),
                    new IsaacCipher(
                        new int[]{1,2,3,4}
                    )
                );

            // NORMAL -> NPC failure must not create hidden transform authority.
            boolean initialFailed=false;
            try{
                presentation.morph(
                    1234,
                    "morph-atomic",
                    equipment,
                    player,
                    fullWriter(
                        new int[]{5,6,7,8}
                    )
                );
            }catch(java.io.IOException expected){
                initialFailed=true;
            }

            req(
                initialFailed,
                "initial morph publication did not fail"
            );
            req(
                dev.playerNpcTransformId()==null,
                "failed initial morph committed hidden transform"
            );

            String first=
                presentation.morph(
                    1234,
                    "morph-atomic",
                    equipment,
                    player,
                    good
                );

            req(
                first.contains(
                    "DEV_PLAYER_MORPH_OK npc=1234"
                ),
                "initial morph retry result="+first
            );
            req(
                Integer.valueOf(1234).equals(
                    dev.playerNpcTransformId()
                ),
                "initial morph retry did not commit"
            );

            // NPC A -> NPC B failure must preserve A exactly.
            boolean replacementFailed=false;
            try{
                presentation.morph(
                    2345,
                    "morph-atomic",
                    equipment,
                    player,
                    fullWriter(
                        new int[]{9,10,11,12}
                    )
                );
            }catch(java.io.IOException expected){
                replacementFailed=true;
            }

            req(
                replacementFailed,
                "replacement morph publication did not fail"
            );
            req(
                Integer.valueOf(1234).equals(
                    dev.playerNpcTransformId()
                ),
                "failed replacement morph lost prior transform"
            );

            String replacement=
                presentation.morph(
                    2345,
                    "morph-atomic",
                    equipment,
                    player,
                    good
                );

            req(
                replacement.contains(
                    "DEV_PLAYER_MORPH_OK npc=2345"
                ),
                "replacement morph retry result="+replacement
            );
            req(
                Integer.valueOf(2345).equals(
                    dev.playerNpcTransformId()
                ),
                "replacement morph retry did not commit"
            );

            // NPC -> NORMAL failure must preserve the prior NPC transform.
            boolean clearFailed=false;
            try{
                presentation.clear(
                    "morph-atomic",
                    equipment,
                    player,
                    fullWriter(
                        new int[]{13,14,15,16}
                    )
                );
            }catch(java.io.IOException expected){
                clearFailed=true;
            }

            req(
                clearFailed,
                "morph clear publication did not fail"
            );
            req(
                Integer.valueOf(2345).equals(
                    dev.playerNpcTransformId()
                ),
                "failed clear retired prior transform"
            );

            String cleared=
                presentation.clear(
                    "morph-atomic",
                    equipment,
                    player,
                    good
                );

            req(
                cleared.contains(
                    "DEV_PLAYER_MORPH_CLEAR old=2345"
                ),
                "clear retry result="+cleared
            );
            req(
                dev.playerNpcTransformId()==null,
                "clear retry did not commit normal appearance"
            );

            System.out.println(
                "PLAYER_PRESENTATION_MORPH_ATOMICITY_PASS "+
                "initialMorphFailureAtomic=true "+
                "replacementMorphFailureAtomic=true "+
                "clearMorphFailureAtomic=true "+
                "morphRetryExact=true"
            );
        }finally{
            world.close();
        }
    }

    private static ServerPacketWriter fullWriter(
        int[] seed
    )throws Exception{
        OutboundPacketQueue queue=
            new OutboundPacketQueue(1024);
        queue.offer(
            new byte[1024]
        );
        return new ServerPacketWriter(
            queue,
            new IsaacCipher(seed)
        );
    }

    private static void req(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(message);
    }

    private PlayerPresentationMorphAtomicityTest(){}
}
