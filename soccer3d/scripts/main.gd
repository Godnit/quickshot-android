extends Node3D
## Soccer 3D: a lightweight arcade football prototype for Android API 27+

var ball: RigidBody3D
var controlled: CharacterBody3D
var players: Array[CharacterBody3D] = []
var opponents: Array[CharacterBody3D] = []
var score_home := 0
var score_away := 0
var elapsed := 0.0
var match_seconds := 180.0
var playing := true
var hud: Label
var menu: Panel
var status: Label
var joystick := Vector2.ZERO
var field_size := Vector2(28.0, 18.0)
var team_colors = [Color("#1e6cff"), Color("#f04444"), Color("#f1c40f"), Color("#2ecc71")]

func _ready() -> void:
    _build_world()
    _build_hud()
    _spawn_teams()
    _reset_ball()

func mat(color: Color, metallic := 0.0, rough := 0.75) -> StandardMaterial3D:
    var m := StandardMaterial3D.new(); m.albedo_color = color; m.metallic = metallic; m.roughness = rough; return m

func box(size: Vector3, color: Color, pos: Vector3) -> MeshInstance3D:
    var n := MeshInstance3D.new(); var b := BoxMesh.new(); b.size = size; n.mesh = b; n.material_override = mat(color); n.position = pos; add_child(n); return n

func _build_world() -> void:
    var env := WorldEnvironment.new(); var e := Environment.new(); e.background_mode = Environment.BG_COLOR; e.background_color = Color("#78b9e8"); e.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR; e.ambient_light_color = Color.WHITE; e.ambient_light_energy = 0.8; env.environment = e; add_child(env)
    var sun := DirectionalLight3D.new(); sun.rotation_degrees = Vector3(-55,-25,0); sun.light_energy = 1.2; sun.shadow_enabled = true; add_child(sun)
    box(Vector3(field_size.x,0.2,field_size.y), Color("#198754"), Vector3(0,-0.1,0))
    box(Vector3(field_size.x+0.2,0.12,0.08), Color.WHITE, Vector3(0,0.02,-field_size.y/2)); box(Vector3(field_size.x+0.2,0.12,0.08), Color.WHITE, Vector3(0,0.02,field_size.y/2)); box(Vector3(0.08,0.12,field_size.y), Color.WHITE, Vector3(-field_size.x/2,0.02,0)); box(Vector3(0.08,0.12,field_size.y), Color.WHITE, Vector3(field_size.x/2,0.02,0))
    box(Vector3(0.08,0.12,field_size.y), Color.WHITE, Vector3(0,0.02,0))
    for z in [-4.0,4.0]:
        box(Vector3(0.10,2.6,0.10), Color.WHITE, Vector3(-field_size.x/2-0.8,1.3,z)); box(Vector3(0.10,2.6,0.10), Color.WHITE, Vector3(field_size.x/2+0.8,1.3,z)); box(Vector3(1.7,0.10,0.10), Color.WHITE, Vector3(-field_size.x/2-0.8,2.6,0)); box(Vector3(1.7,0.10,0.10), Color.WHITE, Vector3(field_size.x/2+0.8,2.6,0))
    var cam := Camera3D.new(); cam.position = Vector3(0,18,20); cam.rotation_degrees = Vector3(-38,0,0); cam.current = true; add_child(cam)

func _make_player(color: Color, pos: Vector3, is_home: bool) -> CharacterBody3D:
    var p := CharacterBody3D.new(); p.position = pos; p.collision_layer = 1; p.collision_mask = 1
    var body := MeshInstance3D.new(); var c := CapsuleMesh.new(); c.height=1.55; c.radius=0.34; body.mesh=c; body.material_override=mat(color); body.position.y=0.8; p.add_child(body)
    var head := MeshInstance3D.new(); var s := SphereMesh.new(); s.radius=0.25; s.height=0.5; head.mesh=s; head.material_override=mat(Color("#f5cfa0")); head.position.y=1.75; p.add_child(head)
    var shape := CollisionShape3D.new(); var cap := CapsuleShape3D.new(); cap.height=1.55; cap.radius=0.34; shape.shape=cap; shape.position.y=0.8; p.add_child(shape); add_child(p); return p

func _spawn_teams() -> void:
    var home_positions=[Vector3(-7,0,-4),Vector3(-3,0,-6),Vector3(2,0,-5),Vector3(6,0,-3),Vector3(9,0,0)]
    for i in home_positions.size():
        var p=_make_player(team_colors[0],home_positions[i],true); players.append(p)
    controlled=players[2]
    var away_positions=[Vector3(7,0,4),Vector3(3,0,6),Vector3(-2,0,5),Vector3(-6,0,3),Vector3(-9,0,0)]
    for i in away_positions.size(): opponents.append(_make_player(team_colors[1],away_positions[i],false))

func _reset_ball() -> void:
    ball=RigidBody3D.new(); ball.position=Vector3(0,0.45,0); ball.mass=0.45; ball.gravity_scale=1.0; ball.collision_layer=2; ball.collision_mask=1
    var mesh:=MeshInstance3D.new(); var s:=SphereMesh.new(); s.radius=0.23; s.height=0.46; mesh.mesh=s; mesh.material_override=mat(Color.WHITE,0.1,0.3); ball.add_child(mesh)
    var shape:=CollisionShape3D.new(); var sh:=SphereShape3D.new(); sh.radius=0.23; shape.shape=sh; ball.add_child(shape); add_child(ball)

func _build_hud() -> void:
    var layer:=CanvasLayer.new(); add_child(layer)
    hud=Label.new(); hud.position=Vector2(32,22); hud.add_theme_font_size_override("font_size",30); hud.add_theme_color_override("font_color",Color.WHITE); layer.add_child(hud)
    status=Label.new(); status.position=Vector2(32,66); status.add_theme_font_size_override("font_size",18); layer.add_child(status)
    var pause:=Button.new(); pause.text="⚙ الإعدادات"; pause.position=Vector2(1040,24); pause.size=Vector2(200,52); pause.pressed.connect(_toggle_menu); layer.add_child(pause)
    for spec in [["تمريرة",Vector2(1040,585),"pass"],["تسديدة",Vector2(1150,505),"shoot"],["تزحلق",Vector2(1035,505),"tackle"]]:
        var b:=Button.new(); b.text=spec[0]; b.position=spec[1]; b.size=Vector2(115,70); b.add_theme_font_size_override("font_size",18); b.button_down.connect(func(): _action(spec[2])); layer.add_child(b)
    var hint:=Label.new(); hint.text="الحركة: WASD  •  J تمريرة  K تسديدة  L تزحلق"; hint.position=Vector2(32,665); hint.add_theme_color_override("font_color",Color.WHITE); layer.add_child(hint)

func _toggle_menu() -> void:
    if menu: menu.queue_free(); menu=null; playing=true; return
    playing=false; menu=Panel.new(); menu.position=Vector2(390,150); menu.size=Vector2(500,380); add_child(menu)
    var title:=Label.new(); title.text="⚽ Soccer 3D"; title.position=Vector2(150,28); title.add_theme_font_size_override("font_size",30); menu.add_child(title)
    var t:=Label.new(); t.text="اختيار الفريق\n\nالأزرق  •  الأحمر  •  الأصفر  •  الأخضر\n\nالجودة: تلقائية (متوافقة مع Android 8.1)"; t.position=Vector2(55,92); t.add_theme_font_size_override("font_size",20); menu.add_child(t)
    var b:=Button.new(); b.text="متابعة المباراة"; b.position=Vector2(145,285); b.size=Vector2(210,58); b.pressed.connect(_toggle_menu); menu.add_child(b)

func _action(kind: String) -> void:
    if not controlled or not ball: return
    var dir=(ball.global_position-controlled.global_position).normalized(); dir.y=0
    if kind=="shoot": ball.apply_central_impulse(dir*Vector3(0,0,1)*8.5 + Vector3(0,2.2,0))
    elif kind=="pass": ball.apply_central_impulse(dir*4.5)
    elif kind=="tackle": controlled.velocity += dir*5.0

func _physics_process(delta: float) -> void:
    if not playing: return
    elapsed += delta; match_seconds=max(0.0,match_seconds-delta)
    var input_vec=Input.get_vector("move_left","move_right","move_up","move_down")
    controlled.velocity=Vector3(input_vec.x*6.0,controlled.velocity.y,input_vec.y*6.0); controlled.move_and_slide(); controlled.position.x=clamp(controlled.position.x,-13.0,13.0); controlled.position.z=clamp(controlled.position.z,-8.0,8.0)
    for p in players:
        if p!=controlled: _ai_home(p,delta)
    for p in opponents: _ai_away(p,delta)
    ball.linear_velocity.x=clamp(ball.linear_velocity.x,-13,13); ball.linear_velocity.z=clamp(ball.linear_velocity.z,-13,13)
    if abs(ball.position.x)>14.2: _goal("home" if ball.position.x<0 else "away")
    hud.text="الأزرق  %d  -  %d  الأحمر" % [score_home,score_away]
    status.text="الوقت %02d:%02d     •     %s" % [int(match_seconds)/60,int(match_seconds)%60,"مباراة جارية" if match_seconds>0 else "انتهت المباراة"]

func _ai_home(p: CharacterBody3D, delta: float) -> void:
    var target=Vector3(-2,0,-3); p.velocity=(target-p.position).normalized()*1.4; p.move_and_slide()
func _ai_away(p: CharacterBody3D, delta: float) -> void:
    var target=ball.position; target.y=0; p.velocity=(target-p.position).normalized()*1.7; p.move_and_slide()
func _goal(team: String) -> void:
    if team=="home": score_home+=1
    else: score_away+=1
    ball.freeze=true; await get_tree().create_timer(1.2).timeout; ball.freeze=false; ball.position=Vector3.ZERO; ball.linear_velocity=Vector3.ZERO
