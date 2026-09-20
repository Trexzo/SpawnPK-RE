package spk.local;

public final class NpcViewIndexMapTest {
    public static void main(String[] args){
        NpcViewIndexMap map=
            new NpcViewIndexMap();

        EntityId first=EntityId.next();
        EntityId second=EntityId.next();

        int sceneA=map.bind(first,101);

        if(sceneA!=101)
            throw new AssertionError(
                "fixed scene binding changed"
            );

        if(map.bind(first,101)!=101)
            throw new AssertionError(
                "idempotent binding changed"
            );

        if(!Integer.valueOf(101).equals(
            map.sceneIndex(first)))
            throw new AssertionError(
                "entity -> scene lookup missing"
            );

        if(!first.equals(map.entityId(101)))
            throw new AssertionError(
                "scene -> entity lookup missing"
            );

        boolean sceneCollision=false;
        try{
            map.bind(second,101);
        }catch(IllegalStateException expected){
            sceneCollision=true;
        }

        if(!sceneCollision)
            throw new AssertionError(
                "scene collision did not fail closed"
            );

        map.bind(second,102);

        boolean entityRemap=false;
        try{
            map.bind(second,103);
        }catch(IllegalStateException expected){
            entityRemap=true;
        }

        if(!entityRemap)
            throw new AssertionError(
                "entity remap did not fail closed"
            );

        if(!map.unbind(first))
            throw new AssertionError(
                "unbind failed"
            );

        if(map.sceneIndex(first)!=null||
           map.entityId(101)!=null)
            throw new AssertionError(
                "unbind left stale reverse mapping"
            );

        if(map.size()!=1)
            throw new AssertionError(
                "unexpected map size "+
                map.size()
            );

        System.out.println(
            "NPC_VIEW_INDEX_MAP_PASS "+
            "viewerLocal=true canonicalIds=true sceneIdsGlobal=false"
        );
    }
}
