import csv, os, zipfile, msgpack, re, json
OUT='/mnt/data/r3audit/generated'
os.makedirs(OUT,exist_ok=True)
# Settings exact map derived from rs.n.c.aC builder + Client.p config application + rs.f.a persistence.
rows=[
# raw, cfg, widget,label, field, default,persist, side
(174,10,12470,'Toggle/show roofs','ax',False,'show_roofs','Updates roof rendering/cache policy'),
(175,11,12480,'Fog distance','aw',False,'show_fog','Fog rendering toggle'),
(176,12,12482,'Left-click attack','ay',False,'left_click_attack','Client attack-menu behavior'),
(177,13,39975,'Particle/glow effects','az',True,'particle_system_1','Forced off in Lite mode; may redraw scene'),
(178,14,39973,'Show news broadcasts','aA',True,'show_broadcasts_1','Client broadcast visibility'),
(179,15,19099,'Show combat overlay','aB',True,'','No rs.f.a save/load key found for aB in exact client'),
(180,16,19097,'Shift dropping','aC',False,'shift_drop',''),
(181,17,19095,'Oldschool 07 ticks','aE',False,'oldschool_ticks','Queues ::newticks when 0; ::oldticks when nonzero'),
(182,18,19093,'Instant switching','aF',True,'instant_switching','Queues ::instantswitching when 0; ::queuedswitching when nonzero'),
(183,19,19091,'Prayer adjustments','aG',True,'prayer_adjustments',''),
(184,20,19089,'Swap rigour/augury','aI',False,'rigour_augury_swapped',''),
(185,21,19087,'Left click attack target only','aJ',False,'left_click_target_only',''),
(186,22,12472,'Resizable mode','ai','FIXED','screen_mode','Toggles screen-mode enum and applies display mode'),
(187,23,12474,'Lite mode','ah',False,'lite_version','Toggles Lite mode; reloads/lower graphics; particle effects forced disabled'),
(188,24,19085,'Extended zoom & distance','aO',True,'extended_zoom','Calls rs.V.a(boolean); control click also has ::extended route'),
(189,25,19083,'Desktop notifications','aP',False,'desktop_notifications',''),
(190,26,19081,'Player lighting','aQ',True,'player_lighting','May trigger client redraw'),
(191,27,19079,'Hide other pets (non-wild)','aT',False,'hide_non_wild_pets',''),
(192,28,19077,'Toggle pets for "bank all"','aZ',False,'bank_all_pet',''),
(193,29,19075,'Cosmetic "icons" visibility','bb',True,'show_icon_equip','May trigger client redraw'),
(194,30,19073,'Left click magic target only','aK',False,'left_click_magic_only','LOAD key exists but exact rs.f.a save writer does not emit it (persistence asymmetry)'),
(195,31,19071,'Lock spawnable item dropping','aL',False,'lock_spawnable_drop',''),
]
with open(OUT+'/settings_exact_map.csv','w',newline='',encoding='utf-8') as f:
 w=csv.writer(f);w.writerow(['raw_varp_id','internal_config_index','control_widget','client_label','state_field','static_default','settings_property_key','exact_client_side_effect_or_boundary']);w.writerows(rows)
# Condition VM
ops=[
(0,'RETURN','Return accumulator immediately',''),
(1,'CURRENT_SKILL_LEVEL','kN[index]','1 operand: skill index'),
(2,'BASE_SKILL_LEVEL','eo[index]','1 operand: skill index'),
(3,'SKILL_XP','ka[index]','1 operand: skill index'),
(4,'COUNT_ITEM','Count matching item quantity in target interface/container','2 operands: widget id, item id; contains SpawnPK item-equivalence special cases'),
(5,'VARP','dP[varpIndex]','1 operand'),
(6,'XP_FOR_BASE_LEVEL','lU[eo[skill]-1]','1 operand: skill index'),
(7,'VARP_PERCENT','dP[varp]*100/46875','1 operand'),
(8,'COMBAT_LEVEL','local player rs.a.k.bc','0 operands'),
(9,'TOTAL_LEVEL','Sum eo[] over client-selected skill mask','0 operands'),
(10,'HAS_ITEM','Return sentinel 999999999 if item is present in target container','2 operands: widget id, item id; SpawnPK equivalence special cases'),
(11,'CLIENT_FIELD_eY','Read Client.eY integer','Semantic label intentionally not guessed'),
(12,'CLIENT_FIELD_ko','Read Client.ko integer','Semantic label intentionally not guessed'),
(13,'VARP_BIT_TEST','(dP[varp] & (1<<bit)) != 0 ? 1 : 0','2 operands: varp, bit'),
(14,'VARBIT','Resolve rs.d.y varbit definition against dP[]','1 operand: varbit id'),
(15,'SET_SUBTRACT','Next value is subtracted','operator control'),
(16,'SET_DIVIDE','Next non-zero value divides accumulator','operator control'),
(17,'SET_MULTIPLY','Next value multiplies accumulator','operator control'),
(18,'WORLD_X','(localPlayer.ac >> 7) + sceneBaseX(eh)','0 operands'),
(19,'WORLD_Y','(localPlayer.ad >> 7) + sceneBaseY(ei)','0 operands'),
(20,'LITERAL','Immediate literal integer','1 operand'),
]
with open(OUT+'/condition_vm_opcodes.csv','w',newline='',encoding='utf-8') as f:
 w=csv.writer(f);w.writerow(['opcode','name','exact_behavior','operand_note']);w.writerows(ops)
# S2C126 global argument/control tokens
ctrl=[
('clear_exchange','equalsIgnoreCase','no args','Clear rs.n.c.ae.c exchange list'),
('add_exchange','startsWith','space then "int,int"','Parse two ints and append Integer[]{a,b} to rs.n.c.ae.c'),
('update_exchange','equalsIgnoreCase','no args','Call rs.n.c.ae.h() rebuild/update'),
('clearsellmarket','equalsIgnoreCase','no args','Call rs.n.c.au.h()'),
('clearbuymarket','equalsIgnoreCase','no args','Call rs.n.c.at.h(); set widget 65803 scroll V=0'),
('setsellitem','startsWith','setsellitem,<itemId>','Parse itemId; rs.n.e.c(25342,itemId,32)'),
('clearinvoverlay','equalsIgnoreCase','no args','If gp != -1 set gp=-1 and mark interface redraw'),
('cleardialog','equalsIgnoreCase','no args','gb=false; fN=0; mark interface redraw'),
('togglebh','equalsIgnoreCase','no args','Toggle rs.f.a.au; persist settings; print enabled/disabled client message'),
('clearcc','startsWith','clearcc <widgetStart>','Clamp start >=18144; clear text/actions across client clan-chat widget ranges'),
('LOGIN_REWARD_IDX','startsWith','LOGIN_REWARD_IDX <int>','Parse int into Client.P'),
('DISABLE_QUICK_PRAYERS','startsWith','prefix only','Set quick-prayer client state cp=false; refresh prayer UI'),
('ITEM_GUIDE_SELECTED_','startsWith','ITEM_GUIDE_SELECTED_<widgetId>','Strip prefix, parse decimal widgetId; remove prior <img=24> marker from 47505..47703 and prepend marker to selected row'),
('ITEM_GUIDE_BONUS_WIDGET','startsWith','ITEM_GUIDE_BONUS_WIDGET <ON|other>','split on space; ON calls Item Library bonus-widget visibility true, any other token false'),
('WIKI_SELECTED_','startsWith','WIKI_SELECTED_<widgetId>','Strip prefix, parse decimal widgetId; remove prior <img=39> marker from 46506..46605 and prepend marker to selected row'),
]
with open(OUT+'/s2c126_argument_control_routes.csv','w',newline='',encoding='utf-8') as f:
 w=csv.writer(f);w.writerow(['token','match','argument_shape','exact_client_effect']);w.writerows(ctrl)
# Collection log target-key bus
cl=[
(54315,'clear_collection_rows','Payload ignored','Clear text on odd widgets 54315..54413'),
(54421,'select_collection_row','decimal widget id','Set selected sprite for even widgets 54314..54412; reset scroll 54417'),
(54422,'select_collection_category','decimal widget id','If 54302..54306 select category sprite/reset 54313; reset all row sprites'),
]
with open(OUT+'/collection_log_s2c126_targets.csv','w',newline='',encoding='utf-8') as f:
 w=csv.writer(f);w.writerow(['target_key','semantic','payload','effect']);w.writerows(cl)
# Clan wars option widgets exact ids
clanw=[
('rule',24023,'All spellbooks'),('rule',24024,'Standard spells'),('rule',24025,'Binding only'),('rule',24030,'All allowed'),('rule',24031,'Standard prayers'),
('win_condition',24041,"Kill 'em all"),('win_condition',24050,'Last team standing'),
('kill_target',24051,'25 kills'),('kill_target',24052,'50 kills'),('kill_target',24053,'100 kills'),('kill_target',24054,'200 kills'),('kill_target',24055,'500 kills'),
('arena_or_rule',24068,'Wasteland'),('arena_or_rule',24069,'Plateau'),('arena_or_rule',24070,'Sylvan Glade'),('arena_or_rule',24071,'Forsaken Quarry'),('arena_or_rule',24072,'Turrets'),('arena_or_rule',24073,'Clan Cup Arena'),('arena_or_rule',24074,'Ghastly Swamp'),('arena_or_rule',24075,'Northleach Quell'),('arena_or_rule',24076,'Gridlock'),('arena_or_rule',24077,'Ethereal'),
('rule',24082,'Ignore freezing'),('rule',24083,'PJ timer'),('rule',24084,'Single spells'),('rule',24085,'EdgePvP mode'),
('action',24086,'Accept'),
]
with open(OUT+'/clan_wars_widget_routes.csv','w',newline='',encoding='utf-8') as f:
 w=csv.writer(f);w.writerow(['group','widget_id','label','client_outbound_contract']);
 for g,wid,label in clanw:w.writerow([g,wid,label,'Generic interface-button menu action 315 -> C2S opcode 185 carrying widget id (after local controller hooks)'])
# Gambling game selector
games=[(59853,'55x2 (P1 host)'),(59854,'55x2 (P2 host)'),(59855,'BJ (P1 host)'),(59856,'BJ (P2 host)'),(59857,'Dice duel'),(59858,'Flower poker')]
with open(OUT+'/gambling_widget_routes.csv','w',newline='',encoding='utf-8') as f:
 w=csv.writer(f);w.writerow(['widget_id','label','client_outbound_contract']);
 for wid,label in games:w.writerow([wid,label,'Generic interface-button menu action 315 -> C2S opcode 185 carrying widget id'])
# Achievement crosslink with current config overrides and world object baseline
ach='/mnt/data/SpawnPK-CLIENT-DISCOVERY-AUDIT-R2-2026-09-18/05_ACHIEVEMENT_CHAPTER_BOOTSTRAP.csv'
with zipfile.ZipFile('/mnt/data/spk_static_exact/configs.zip') as z:
 item=msgpack.unpackb(z.read('i.bin'),raw=False,strict_map_key=False)
 npc=msgpack.unpackb(z.read('e.bin'),raw=False,strict_map_key=False)
 obj=msgpack.unpackb(z.read('o.bin'),raw=False,strict_map_key=False)
base_obj={}
p0=next((p for p in os.listdir('/mnt/data/r831_base') if p.startswith('SpawnPK-LocalLab-')),None)
objtsv='/mnt/data/r831_base/'+p0+'/payload/server/research/r82/world_r1/object_defs_main_exact.tsv'
with open(objtsv,encoding='utf-8') as f:
 dr=csv.DictReader(f,delimiter='\t');
 for r in dr: base_obj[r['id']]=r
out=[]
with open(ach,encoding='utf-8') as f:
 for r in csv.DictReader(f):
  typ=r['render_type']; rid=r['render_id']; source=''; name=''; actions=''; detail=''
  if typ=='ITEM':
   d=item.get(rid)
   if d:
    source='configs v110 i.bin effective override'; name=d.get('name',''); actions=json.dumps(d.get('actions'),ensure_ascii=False) if 'actions' in d else ''
    detail='clone='+str(d.get('clone','')) if 'clone' in d else ''
   else:
    source='base obj.dat required (no i.bin override)'
  elif typ=='NPC_HEAD':
   d=npc.get(rid)
   if d:
    source='configs v110 e.bin override';name=d.get('name','');actions=json.dumps(d.get('actions'),ensure_ascii=False) if 'actions' in d else ''
   else: source='base NPC cache definition required (no e.bin override)'
  elif typ=='OBJ':
   d=obj.get(rid); b=base_obj.get(rid)
   if d:
    source='configs v110 o.bin override over base'; name=d.get('name') or (b or {}).get('name',''); actions=json.dumps(d.get('actions'),ensure_ascii=False) if 'actions' in d else (b or {}).get('actions','')
    detail='base_name='+((b or {}).get('name',''))
   elif b:
    source='cache v67 main object definition'; name=b.get('name','');actions=b.get('actions',''); detail='animationId='+b.get('animationId','')
   else: source='base object definition not present in carried WORLD table'
  out.append([r['index'],typ,rid,r['task_text'],source,name,actions,detail])
with open(OUT+'/achievement_definition_crosslink.csv','w',newline='',encoding='utf-8') as f:
 w=csv.writer(f);w.writerow(['index','render_type','render_id','task_text','definition_source','current_definition_name','current_actions','detail']);w.writerows(out)
print('wrote',OUT)
