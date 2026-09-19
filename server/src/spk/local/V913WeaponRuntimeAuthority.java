package spk.local;

import java.util.*;

/** Direct production-runtime presentation authority from V9.13 nearby weapon captures. */
final class V913WeaponRuntimeAuthority {
    static final class Profile {
        final int itemId; final String name;
        final int preAnimation,attackAnimation,actorGfx,projectileId,targetGfx,speedTicks;
        final boolean directBasicAttack; final String evidence;
        /** Exact packet-117 geometry recovered from constructor args; cycle delay remains unresolved. */
        final int projectileStartHeight,projectileEndHeight,projectileSlope,projectileStartDistance;
        /** Exact packet-174 basic-attack sound parameters where directly observed. */
        final int soundId,soundParam2,soundParam3;
        Profile(int itemId,String name,int pre,int anim,int gfx,int proj,int targetGfx,int speed,boolean basic,String evidence){
            this(itemId,name,pre,anim,gfx,proj,targetGfx,speed,basic,evidence,-1,-1,-1,-1,-1,-1,-1);
        }
        Profile(int itemId,String name,int pre,int anim,int gfx,int proj,int targetGfx,int speed,boolean basic,String evidence,
                int startHeight,int endHeight,int slope,int startDistance,int soundId,int soundParam2,int soundParam3){
            this.itemId=itemId;this.name=name;this.preAnimation=pre;this.attackAnimation=anim;this.actorGfx=gfx;this.projectileId=proj;this.targetGfx=targetGfx;this.speedTicks=speed;this.directBasicAttack=basic;this.evidence=evidence;
            this.projectileStartHeight=startHeight;this.projectileEndHeight=endHeight;this.projectileSlope=slope;this.projectileStartDistance=startDistance;
            this.soundId=soundId;this.soundParam2=soundParam2;this.soundParam3=soundParam3;
        }
        boolean hasProjectileGeometry(){return projectileId>=0&&projectileStartHeight>=0&&projectileEndHeight>=0&&projectileSlope>=0&&projectileStartDistance>=0;}
        boolean hasBasicSound(){return directBasicAttack&&soundId>=0;}
        public String toString(){return itemId+":"+name+" pre="+preAnimation+" anim="+attackAnimation+" gfx="+actorGfx+" proj="+projectileId+" targetGfx="+targetGfx+" speed="+speedTicks+" basic="+directBasicAttack+
            " projectileGeometry="+(hasProjectileGeometry()?(projectileStartHeight+"/"+projectileEndHeight+" slope="+projectileSlope+" dist="+projectileStartDistance):"UNRESOLVED")+
            " sound="+(soundId<0?"NONE":soundId+"/"+soundParam2+"/"+soundParam3);}
    }
    private static final LinkedHashMap<Integer,Profile> ROWS=new LinkedHashMap<>();
    static {
        // Exact packet-117 constructor geometry recovered in R5.1 from V9.13:
        // ranged family startHeight=37, endHeight=31, slope=16, startDistance=64.
        put(new Profile(28539,"Tumeken's shadow (i)",-1,16199,5090,5092,5091,4,true,"V9.13_191738_DIRECT_RUNTIME",37,31,16,64,-1,-1,-1));
        put(new Profile(25557,"Webweaver bow",426,15409,4056,4057,-1,3,true,"V9.13_191738_DIRECT_RUNTIME impact2264=STRONG_CORRELATION_NOT_PROMOTED",37,31,16,64,-1,-1,-1));
        put(new Profile(22218,"Swift bow",426,15409,1116,1120,-1,2,true,"V9.13_191738_DIRECT_RUNTIME",37,31,16,64,-1,-1,-1));
        put(new Profile(27928,"Noxious halberd",-1,440,-1,-1,-1,-1,true,"V9.13_191738_DIRECT_RUNTIME speed=UNRESOLVED"));
        put(new Profile(25001,"Blood rune c'bow",-1,4230,-1,27,-1,5,true,"V9.13_191738_DIRECT_RUNTIME sound2695=OBSERVED baseActorGfx=NONE",37,31,16,64,2695,0,10));
        put(new Profile(28860,"Scorching bow (i)",426,15409,4080,4079,-1,3,true,"V9.13_192540_DIRECT_RUNTIME supersedes_unbound_15624/4135/4136",37,31,16,64,-1,-1,-1));
        // Mystic Armadyl projectile constructor is a distinct zero-height/zero-slope family.
        put(new Profile(28843,"Mystic armadyl battlestaff",-1,10546,457,1019,-1,5,true,"V9.13_192540_DIRECT_RUNTIME basic_sequence_candidate; range/server_legality_unresolved",0,0,0,64,-1,-1,-1));
        put(new Profile(21099,"Dragon sword",-1,8145,-1,-1,-1,4,true,"V9.13_192540_DIRECT_RUNTIME"));
        put(new Profile(24093,"Blood slayer tentacle",-1,1658,-1,-1,-1,-1,true,"V9.13_192540_DIRECT_RUNTIME speed=UNRESOLVED"));
        put(new Profile(25567,"Cursed fang",-1,15416,-1,-1,-1,-1,true,"V9.13_192540_TARGET_CONNECTED_SINGLE_SEQUENCE speed=UNRESOLVED"));
        // Spell action only; sound is exact but must never become the staff's generic basic attack.
        put(new Profile(11791,"Staff of the dead / Flames of Zamorak",-1,811,-1,-1,-1,5,false,"V9.13_191738_SPELL_ACTION sound1655=FLAMES_OF_ZAMORAK",-1,-1,-1,-1,1655,0,10));
    }
    private static void put(Profile p){ROWS.put(p.itemId,p);} static Profile resolve(int itemId){return ROWS.get(itemId);}
    static Collection<Profile> all(){return Collections.unmodifiableCollection(ROWS.values());} static int count(){return ROWS.size();}
    static int directlyBasicAttackCount(){int n=0;for(Profile p:ROWS.values())if(p.directBasicAttack)n++;return n;}
    static int projectileGeometryCount(){int n=0;for(Profile p:ROWS.values())if(p.hasProjectileGeometry())n++;return n;}
    static int exactBasicSoundCount(){int n=0;for(Profile p:ROWS.values())if(p.hasBasicSound())n++;return n;}
    private V913WeaponRuntimeAuthority(){}
}
