(() => {
	const ROOT = 'C:/Users/patry/Desktop/gamblingmod';
	const TEX = ROOT + '/src/main/resources/assets/gamblecraft/textures/item/';
	const SAVE = ROOT + '/blockbench/chip_3d.bbmodel';
	const EXPORT = ROOT + '/src/main/resources/assets/gamblecraft/models/item/chip_3d.json';
	Undo.initEdit({outliner: true, elements: [], textures: []});
	// The 100 chip stands in for every denomination; the exported model uses #face and #edge variables.
	const face = new Texture({name: 'face'}).fromPath(TEX + 'chip_100.png').add(false);
	const edge = new Texture({name: 'edge'}).fromPath(TEX + 'chip_100_edge.png').add(false);
	// An octagonal disc, 12 across and 1.5 thick, from three overlapping boxes; the round face texture's
	// transparent corners keep it looking round from above. Centred on y = 8, so display rotations turn it
	// about itself (Minecraft rotates items about the middle of the block).
	const boxes = [
		{name: 'disc_x', from: [2, 7.25, 4], to: [14, 8.75, 12]},
		{name: 'disc_z', from: [4, 7.25, 2], to: [12, 8.75, 14]},
		{name: 'disc_d', from: [3, 7.25, 3], to: [13, 8.75, 13]}
	];
	const group = new Group({name: 'chip', origin: [8, 0, 8]}).init();
	for (const b of boxes) {
		const cube = new Cube({name: b.name, from: b.from, to: b.to, autouv: 0}).addTo(group).init();
		for (const side of ['north', 'south', 'east', 'west']) {
			cube.faces[side].texture = edge.uuid;
			cube.faces[side].uv = [0, 0, 16, 1.5];
		}
		cube.faces.up.texture = face.uuid;
		cube.faces.up.uv = [b.from[0], b.from[2], b.to[0], b.to[2]];
		cube.faces.down.texture = face.uuid;
		cube.faces.down.uv = [b.from[0], 16 - b.to[2], b.to[0], 16 - b.from[2]];
	}
	// Held flat between the fingers, face up; tuned in Display mode.
	const display = {
		thirdperson_righthand: {rotation: [90, 0, 0], translation: [0, 1.5, -1], scale: [0.3, 0.3, 0.3]},
		thirdperson_lefthand: {rotation: [90, 0, 0], translation: [0, 1.5, -1], scale: [0.3, 0.3, 0.3]},
		firstperson_righthand: {rotation: [-70, 20, 0], translation: [0, 4, 0], scale: [0.35, 0.35, 0.35]},
		firstperson_lefthand: {rotation: [-70, -20, 0], translation: [0, 4, 0], scale: [0.35, 0.35, 0.35]},
		ground: {scale: [0.3, 0.3, 0.3]},
		head: {translation: [0, 7, 0], scale: [0.6, 0.6, 0.6]}
	};
	for (const [slot, t] of Object.entries(display)) {
		Project.display_settings[slot] = new DisplaySlot(slot, t);
	}
	Undo.finishEdit('Build chip');
	Canvas.updateAll();
	let model = Codecs.java_block.compile();
	if (typeof model !== 'string') {
		model = JSON.stringify(model, null, '\t');
	}
	Blockbench.writeFile(EXPORT, {content: model});
	Project.save_path = SAVE;
	Blockbench.writeFile(SAVE, {content: Codecs.project.compile({compressed: false})});
	return {cubes: Cube.all.length, exported: EXPORT};
})()
