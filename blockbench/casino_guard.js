(() => {
	// The casino guard: an iron-golem frame in a black suit, dark glasses and an earpiece.
	// UV offsets match tools/gen_f9_assets.py guard_texture(); animations are read by CasinoGuardModel.
	const TEXTURE = 'C:/Users/patry/Desktop/gamblingmod/src/main/resources/assets/gamblecraft/textures/entity/casino_guard.png';
	const SAVE = 'C:/Users/patry/Desktop/gamblingmod/blockbench/casino_guard.bbmodel';
	Undo.initEdit({outliner: true, elements: [], textures: [], animations: []});
	Project.texture_width = 128;
	Project.texture_height = 128;
	const spec = [
		{name: 'body', origin: [0, 21, 0], cubes: [
			{name: 'torso', from: [-9, 21, -5.5], to: [9, 33, 5.5], uv: [0, 40]},
			{name: 'tie', from: [-1, 23, -6.5], to: [1, 32, -5.5], uv: [60, 40]},
			{name: 'waist', from: [-4.5, 16, -3], to: [4.5, 21, 3], uv: [0, 70]}
		], children: [
			{name: 'head', origin: [0, 33, -1], cubes: [
				{name: 'head', from: [-4, 33, -5], to: [4, 43, 3], uv: [0, 0]},
				{name: 'nose', from: [-1, 33, -7], to: [1, 37, -5], uv: [32, 0]},
				{name: 'glasses', from: [-4.5, 38, -6], to: [4.5, 40, -5], uv: [40, 0]},
				{name: 'earpiece', from: [4, 36, -2], to: [5, 38, 0], uv: [60, 0]},
				{name: 'wire', from: [4, 31, -1], to: [5, 36, 0], uv: [66, 0]}
			]},
			{name: 'right_arm', origin: [11, 31, 0], cubes: [
				{name: 'right_arm', from: [9, 3, -3], to: [13, 33, 3], uv: [60, 58]}
			]},
			{name: 'left_arm', origin: [-11, 31, 0], cubes: [
				{name: 'left_arm', from: [-13, 3, -3], to: [-9, 33, 3], uv: [60, 58], mirror: true}
			]}
		]},
		{name: 'right_leg', origin: [4, 16, 0], cubes: [
			{name: 'right_leg', from: [1.5, 0, -2.5], to: [7.5, 16, 2.5], uv: [90, 0]}
		]},
		{name: 'left_leg', origin: [-4, 16, 0], cubes: [
			{name: 'left_leg', from: [-7.5, 0, -2.5], to: [-1.5, 16, 2.5], uv: [90, 0], mirror: true}
		]}
	];
	const groups = {};
	function build(parts, parent) {
		for (const p of parts) {
			const g = new Group({name: p.name, origin: p.origin});
			if (parent) g.addTo(parent);
			g.init();
			groups[p.name] = g;
			for (const c of p.cubes) {
				new Cube({name: c.name, from: c.from, to: c.to, uv_offset: c.uv, box_uv: true, mirror_uv: !!c.mirror}).addTo(g).init();
			}
			if (p.children) build(p.children, g);
		}
	}
	build(spec, null);
	const tex = new Texture({name: 'casino_guard'}).fromPath(TEXTURE).add(false);
	Cube.all.forEach(c => c.applyTexture(tex, true));

	const animations = {
		idle: {length: 3, loop: 'loop', bones: {
			body: {position: [[0, [0, 0, 0]], [1.5, [0, 0.4, 0]], [3, [0, 0, 0]]]},
			right_arm: {rotation: [[0, [0, 0, 3]], [1.5, [0, 0, 5]], [3, [0, 0, 3]]]},
			left_arm: {rotation: [[0, [0, 0, -3]], [1.5, [0, 0, -5]], [3, [0, 0, -3]]]}
		}},
		punch: {length: 0.5, loop: 'once', bones: {
			right_arm: {rotation: [[0, [0, 0, 0]], [0.15, [150, 0, -10]], [0.3, [25, 0, 0]], [0.5, [0, 0, 0]]]},
			left_arm: {rotation: [[0, [0, 0, 0]], [0.15, [150, 0, 10]], [0.3, [25, 0, 0]], [0.5, [0, 0, 0]]]},
			body: {rotation: [[0, [0, 0, 0]], [0.15, [-6, 0, 0]], [0.3, [10, 0, 0]], [0.5, [0, 0, 0]]]}
		}},
		charge: {length: 1.5, loop: 'hold', bones: {
			right_arm: {rotation: [[0, [0, 0, 0]], [0.35, [90, 0, 0]], [0.9, [92, 0, 0]], [1.5, [90, 0, 0]]]},
			left_arm: {rotation: [[0, [0, 0, 0]], [0.35, [60, -20, 20]], [1.5, [62, -20, 20]]]},
			body: {rotation: [[0, [0, 0, 0]], [0.35, [-6, 0, 0]], [1.5, [-6, 0, 0]]]},
			right_leg: {rotation: [[0, [0, 0, 0]], [0.35, [-12, 0, 0]], [1.5, [-12, 0, 0]]]},
			left_leg: {rotation: [[0, [0, 0, 0]], [0.35, [12, 0, 0]], [1.5, [12, 0, 0]]]}
		}},
		fire: {length: 0.6, loop: 'once', bones: {
			right_arm: {rotation: [[0, [90, 0, 0]], [0.08, [125, 0, 0]], [0.6, [0, 0, 0]]]},
			left_arm: {rotation: [[0, [60, -20, 20]], [0.1, [70, -20, 20]], [0.6, [0, 0, 0]]]},
			body: {rotation: [[0, [-6, 0, 0]], [0.08, [-14, 0, 0]], [0.6, [0, 0, 0]]]}
		}}
	};
	for (const [name, a] of Object.entries(animations)) {
		const anim = new Animation({name: 'animation.casino_guard.' + name, length: a.length, loop: a.loop}).add();
		for (const [bone, channels] of Object.entries(a.bones)) {
			const animator = anim.getBoneAnimator(groups[bone]);
			for (const [channel, keys] of Object.entries(channels)) {
				for (const [time, v] of keys) {
					animator.addKeyframe({channel, time, interpolation: 'catmullrom', data_points: [{x: v[0], y: v[1], z: v[2]}]});
				}
			}
		}
	}
	Undo.finishEdit('Build casino guard');
	Canvas.updateAll();
	Project.save_path = SAVE;
	Blockbench.writeFile(SAVE, {content: Codecs.project.compile({compressed: false})});
	return {groups: Group.all.length, cubes: Cube.all.length, animations: Animation.all.map(a => a.name), saved: SAVE};
})()
