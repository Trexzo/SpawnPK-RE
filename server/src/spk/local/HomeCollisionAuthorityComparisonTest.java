package spk.local;

import java.util.*;

public final class HomeCollisionAuthorityComparisonTest {
    public static void main(String[] args){
        int sameOpen=0;
        int sameBlocked=0;
        int homeOpenWorldBlocked=0;
        int homeBlockedWorldOpen=0;
        ArrayList<String> samples=new ArrayList<>();

        final int[][] dirs={
            {-1,0},{1,0},{0,-1},{0,1},
            {-1,-1},{1,-1},{-1,1},{1,1}
        };

        int minX=MovementState.REGION_BASE_X;
        int minY=MovementState.REGION_BASE_Y;
        int maxX=minX+MovementState.REGION_SIZE-1;
        int maxY=minY+MovementState.REGION_SIZE-1;

        HashSet<Integer> regions=new HashSet<>();

        for(int x=minX;x<=maxX;x++){
            for(int y=minY;y<=maxY;y++){
                regions.add(((x>>6)<<8)|(y>>6));

                for(int[] d:dirs){
                    int nx=x+d[0];
                    int ny=y+d[1];

                    if(nx<minX||nx>maxX||
                       ny<minY||ny>maxY)
                        continue;

                    boolean home=
                        HomeCombatPathfinder.canStep(
                            x,y,nx,ny
                        );

                    boolean world=
                        WorldCollisionAuthority.canStep(
                            x,y,0,nx,ny
                        );

                    if(home==world){
                        if(home)sameOpen++;
                        else sameBlocked++;
                    }else if(home){
                        homeOpenWorldBlocked++;
                        if(samples.size()<24)
                            samples.add(
                                "HOME_OPEN_WORLD_BLOCKED "+
                                x+","+y+"->"+nx+","+ny
                            );
                    }else{
                        homeBlockedWorldOpen++;
                        if(samples.size()<24)
                            samples.add(
                                "HOME_BLOCKED_WORLD_OPEN "+
                                x+","+y+"->"+nx+","+ny
                            );
                    }
                }
            }
        }

        int missingRegions=0;
        for(Integer rid:regions)
            if(!WorldCollisionAuthority.hasRegion(rid))
                missingRegions++;

        boolean knownHome=
            HomeCombatPathfinder.canStep(
                3083,3495,3084,3495
            );
        boolean knownWorld=
            WorldCollisionAuthority.canStep(
                3083,3495,0,3084,3495
            );

        System.out.println(
            "HOME_COLLISION_AUTHORITY_COMPARE "+
            "sameOpen="+sameOpen+
            " sameBlocked="+sameBlocked+
            " homeOpenWorldBlocked="+homeOpenWorldBlocked+
            " homeBlockedWorldOpen="+homeBlockedWorldOpen+
            " regions="+regions.size()+
            " missingRegions="+missingRegions+
            " known3084_home="+knownHome+
            " known3084_world="+knownWorld
        );

        for(String sample:samples)
            System.out.println(sample);
    }
}
