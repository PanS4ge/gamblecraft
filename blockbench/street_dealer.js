(() => {
	const TEXTURE = 'C:/Users/patry/Desktop/gamblingmod/src/main/resources/assets/gamblecraft/textures/entity/street_dealer.png';
	const SAVE = 'C:/Users/patry/Desktop/gamblingmod/blockbench/street_dealer.bbmodel';
	Undo.initEdit({outliner: true, elements: [], textures: [], animations: []});
	Project.texture_width = 64;
	Project.texture_height = 64;
	const spec = [
		{name: 'head', origin: [0, 24, 0], cubes: [
			{name: 'head', from: [-4, 24, -4], to: [4, 34, 4], uv: [0, 0]},
			{name: 'nose', from: [-1, 23, -6], to: [1, 27, -4], uv: [24, 0]},
			{name: 'visor_band', from: [-4, 24, -4], to: [4, 34, 4], uv: [32, 0], inflate: 0.3},
			{name: 'visor_brim', from: [-4, 31, -8], to: [4, 32, -4], uv: [28, 38]}
		], children: [
			{name: 'cigar', origin: [1.5, 25.5, -4], cubes: [
				{name: 'cigar', from: [1, 25, -7], to: [2, 26, -4], uv: [54, 52]}
			]}
		]},
		{name: 'body', origin: [0, 24, 0], cubes: [
			{name: 'torso', from: [-4, 12, -3], to: [4, 24, 3], uv: [16, 20]},
			{name: 'vest', from: [-4, 12, -3], to: [4, 24, 3], uv: [0, 38], inflate: 0.25},
			{name: 'bow_tie', from: [-2, 21, -4], to: [2, 23, -3], uv: [28, 44]}
		]},
		{name: 'right_arm', origin: [5, 22, 0], cubes: [
			{name: 'right_arm', from: [4, 12, -2], to: [8, 24, 2], uv: [44, 22]}
		], children: [
			{name: 'deck', origin: [6, 12, -1], cubes: [
				{name: 'deck', from: [4.5, 11, -5], to: [7.5, 12, -1], uv: [40, 44]}
			]}
		]},
		{name: 'left_arm', origin: [-5, 22, 0], cubes: [
			{name: 'left_arm', from: [-8, 12, -2], to: [-4, 24, 2], uv: [44, 22], mirror: true}
		], children: [
			{name: 'tray', origin: [-6, 12, -2], cubes: [
				{name: 'tray', from: [-9, 11, -5], to: [-3, 12, 1], uv: [28, 56]},
				{name: 'glass', from: [-7, 12, -3], to: [-6, 14, -2], uv: [60, 40]}
			]}
		]},
		{name: 'right_leg', origin: [2, 12, 0], cubes: [
			{name: 'right_leg', from: [0, 0, -2], to: [4, 12, 2], uv: [0, 22]}
		]},
		{name: 'left_leg', origin: [-2, 12, 0], cubes: [
			{name: 'left_leg', from: [-4, 0, -2], to: [0, 12, 2], uv: [0, 22], mirror: true}
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
				new Cube({name: c.name, from: c.from, to: c.to, uv_offset: c.uv, box_uv: true, inflate: c.inflate || 0, mirror_uv: !!c.mirror}).addTo(g).init();
			}
			if (p.children) build(p.children, g);
		}
	}
	build(spec, null);
	const tex = new Texture({name: 'street_dealer'}).fromPath(TEXTURE).add(false);
	Cube.all.forEach(c => c.applyTexture(tex, true));

	const animations = {
		idle: {length: 2, loop: 'loop', bones: {
			right_arm: {rotation: [[0, [35, 0, 0]], [1, [38, 0, 0]], [2, [35, 0, 0]]]},
			left_arm: {rotation: [[0, [30, -25, 0]], [1, [32, -25, 0]], [2, [30, -25, 0]]]},
			head: {rotation: [[0, [-8, 0, 0]], [1, [-6, 4, 0]], [2, [-8, 0, 0]]]}
		}},
		deal: {length: 0.4, loop: 'once', bones: {
			right_arm: {rotation: [[0, [35, 0, 0]], [0.12, [75, -25, -10]], [0.22, [70, 20, -5]], [0.4, [35, 0, 0]]]},
			left_arm: {rotation: [[0, [30, -25, 0]], [0.2, [38, -30, 0]], [0.4, [30, -25, 0]]]},
			head: {rotation: [[0, [-10, 0, 0]], [0.2, [-16, 8, 0]], [0.4, [-10, 0, 0]]]}
		}},
		shuffle: {length: 1.2, loop: 'once', bones: {
			right_arm: {rotation: [[0, [35, 0, 0]], [0.15, [55, 25, 0]], [0.35, [55, 5, -12]], [0.55, [55, 28, 0]], [0.75, [55, 5, -12]], [0.95, [55, 28, 0]], [1.2, [35, 0, 0]]]},
			left_arm: {rotation: [[0, [30, -25, 0]], [0.15, [55, -25, 0]], [0.35, [55, -5, 12]], [0.55, [55, -28, 0]], [0.75, [55, -5, 12]], [0.95, [55, -28, 0]], [1.2, [30, -25, 0]]]},
			deck: {rotation: [[0, [0, 0, 0]], [0.35, [0, 25, 0]], [0.55, [0, -15, 0]], [0.75, [0, 25, 0]], [0.95, [0, -15, 0]], [1.2, [0, 0, 0]]]},
			head: {rotation: [[0, [-8, 0, 0]], [0.3, [-22, 0, 0]], [0.9, [-22, 0, 0]], [1.2, [-8, 0, 0]]]}
		}},
		cheer: {length: 1, loop: 'once', bones: {
			right_arm: {rotation: [[0, [35, 0, 0]], [0.2, [170, 0, 20]], [0.5, [160, 0, 10]], [0.8, [170, 0, 20]], [1, [35, 0, 0]]]},
			left_arm: {rotation: [[0, [30, -25, 0]], [0.2, [170, 0, -20]], [0.5, [160, 0, -10]], [0.8, [170, 0, -20]], [1, [30, -25, 0]]]},
			head: {rotation: [[0, [-8, 0, 0]], [0.25, [20, 0, 0]], [0.75, [20, 0, 0]], [1, [-8, 0, 0]]]},
			body: {position: [[0, [0, 0, 0]], [0.25, [0, 2, 0]], [0.45, [0, 0, 0]], [0.65, [0, 2, 0]], [0.85, [0, 0, 0]]]}
		}},
		sulk: {length: 1.5, loop: 'once', bones: {
			head: {rotation: [[0, [-8, 0, 0]], [0.3, [-32, 0, 0]], [0.6, [-32, -15, 0]], [0.9, [-32, 15, 0]], [1.2, [-32, 0, 0]], [1.5, [-8, 0, 0]]]},
			right_arm: {rotation: [[0, [35, 0, 0]], [0.3, [-5, 0, 6]], [1.2, [-5, 0, 6]], [1.5, [35, 0, 0]]]},
			left_arm: {rotation: [[0, [30, -25, 0]], [0.3, [-5, 0, -6]], [1.2, [-5, 0, -6]], [1.5, [30, -25, 0]]]},
			body: {rotation: [[0, [0, 0, 0]], [0.3, [8, 0, 0]], [1.2, [8, 0, 0]], [1.5, [0, 0, 0]]]}
		}}
	};
	for (const [name, a] of Object.entries(animations)) {
		const anim = new Animation({name: 'animation.street_dealer.' + name, length: a.length, loop: a.loop}).add();
		for (const [bone, channels] of Object.entries(a.bones)) {
			const animator = anim.getBoneAnimator(groups[bone]);
			for (const [channel, keys] of Object.entries(channels)) {
				for (const [time, v] of keys) {
					animator.addKeyframe({channel, time, interpolation: 'catmullrom', data_points: [{x: v[0], y: v[1], z: v[2]}]});
				}
			}
		}
	}
	Undo.finishEdit('Build street dealer');
	Canvas.updateAll();
	Project.save_path = SAVE;
	Blockbench.writeFile(SAVE, {content: Codecs.project.compile({compressed: false})});
	return {groups: Group.all.length, cubes: Cube.all.length, animations: Animation.all.map(a => a.name), saved: SAVE};
})()
