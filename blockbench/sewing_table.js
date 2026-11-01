(() => {
	const ROOT = 'C:/Users/patry/Desktop/gamblingmod';
	const TEX = ROOT + '/src/main/resources/assets/gamblecraft/textures/block/';
	const VANILLA = 'C:/Users/patry/AppData/Local/Temp/claude/C--Users-patry-Desktop-gamblingmod/f3559456-0561-4fa1-87f7-66f58f6c8684/scratchpad/bbtex/assets/minecraft/textures/block/';
	const SAVE = ROOT + '/blockbench/sewing_table.bbmodel';
	const EXPORT = ROOT + '/src/main/resources/assets/gamblecraft/models/block/sewing_table.json';
	Undo.initEdit({outliner: true, elements: [], textures: []});
	const tex = {};
	for (const [key, path] of Object.entries({
		planks: VANILLA + 'spruce_planks.png', log: VANILLA + 'stripped_spruce_log.png', iron: VANILLA + 'iron_block.png',
		machine: TEX + 'sewing_machine.png', spool: TEX + 'thread_spool.png', cloth: TEX + 'sewing_cloth.png'})) {
		tex[key] = new Texture({name: key}).fromPath(path).add(false);
	}
	// Minecraft's own UVs for a face of a box (what a JSON model gets without explicit uv).
	function autoUv(f, t, face) {
		switch (face) {
			case 'north': return [16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]];
			case 'south': return [f[0], 16 - t[1], t[0], 16 - f[1]];
			case 'east': return [16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]];
			case 'west': return [f[2], 16 - t[1], t[2], 16 - f[1]];
			case 'up': return [f[0], f[2], t[0], t[2]];
			default: return [f[0], 16 - t[2], t[0], 16 - f[2]];
		}
	}
	const spec = [
		{group: 'table', cubes: [
			{name: 'top', from: [0, 10, 0], to: [16, 12, 16], tex: 'planks'},
			{name: 'cloth', from: [1, 12, 1.5], to: [15, 12.25, 14.5], tex: 'cloth'},
			{name: 'leg_nw', from: [1, 0, 1], to: [3, 10, 3], tex: 'log'},
			{name: 'leg_ne', from: [13, 0, 1], to: [15, 10, 3], tex: 'log'},
			{name: 'leg_sw', from: [1, 0, 13], to: [3, 10, 15], tex: 'log'},
			{name: 'leg_se', from: [13, 0, 13], to: [15, 10, 15], tex: 'log'},
			{name: 'shelf', from: [2, 3, 2], to: [14, 4, 14], tex: 'planks'},
			{name: 'fabric_roll', from: [3, 4, 9], to: [13, 6.5, 11.5], tex: 'cloth'}
		]},
		{group: 'machine', cubes: [
			{name: 'base', from: [3, 12.25, 5], to: [13, 13.5, 11], tex: 'machine'},
			{name: 'pillar', from: [10, 13.5, 6], to: [12.5, 19, 10], tex: 'machine'},
			{name: 'arm', from: [3.5, 17, 6.5], to: [12.5, 19.5, 9.5], tex: 'machine'},
			{name: 'head', from: [3, 14.5, 6], to: [5.5, 19.5, 10], tex: 'machine'},
			{name: 'needle', from: [4, 13.5, 7.75], to: [4.5, 14.5, 8.25], tex: 'iron'},
			{name: 'hand_wheel', from: [12.5, 15.5, 6.5], to: [13.5, 18.5, 9.5], tex: 'iron'},
			{name: 'spool', from: [7.25, 19.5, 7.25], to: [8.75, 21.5, 8.75], tex: 'spool'},
			{name: 'spool_pin', from: [7.75, 21.5, 7.75], to: [8.25, 22, 8.25], tex: 'iron'}
		]}
	];
	for (const g of spec) {
		const group = new Group({name: g.group, origin: [8, 0, 8]}).init();
		for (const c of g.cubes) {
			const cube = new Cube({name: c.name, from: c.from, to: c.to, autouv: 0}).addTo(group).init();
			for (const face of ['north', 'south', 'east', 'west', 'up', 'down']) {
				cube.faces[face].texture = tex[c.tex].uuid;
				cube.faces[face].uv = autoUv(c.from, c.to, face);
			}
		}
	}
	Undo.finishEdit('Build sewing table');
	Canvas.updateAll();
	let model = Codecs.java_block.compile();
	if (typeof model !== 'string') {
		model = JSON.stringify(model, null, '\t');
	}
	Blockbench.writeFile(EXPORT, {content: model});
	Project.save_path = SAVE;
	Blockbench.writeFile(SAVE, {content: Codecs.project.compile({compressed: false})});
	return {cubes: Cube.all.length, textures: Texture.all.map(t => t.name), exported: EXPORT};
})()
