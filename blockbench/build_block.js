(() => {
	// Builds a java_block model from a spec (blockbench/<model>.json, see tools/bb_build.py):
	// cubes with a texture key for every face (overridable per face), Minecraft's own face UVs.
	const SPEC = __SPEC__;
	Undo.initEdit({outliner: true, elements: [], textures: []});
	const tex = {};
	for (const [key, path] of Object.entries(SPEC.files)) {
		tex[key] = new Texture({name: key}).fromPath(path).add(false);
	}
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
	function clampUv(uv) {
		// faces above the block (y > 16) would sample outside the texture; wrap them back into 0..16
		const h = Math.abs(uv[3] - uv[1]);
		if (uv[1] < 0 || uv[3] < 0 || uv[1] > 16 || uv[3] > 16) {
			const top = Math.max(0, 16 - Math.min(16, h));
			return [uv[0], top, uv[2], top + Math.min(16, h)];
		}
		return uv;
	}
	const group = new Group({name: SPEC.name, origin: [8, 0, 8]}).init();
	for (const c of SPEC.cubes) {
		const cube = new Cube({name: c.name, from: c.from, to: c.to, autouv: 0}).addTo(group).init();
		for (const face of ['north', 'south', 'east', 'west', 'up', 'down']) {
			cube.faces[face].texture = tex[(c.faces && c.faces[face]) || c.tex].uuid;
			cube.faces[face].uv = (c.uv && c.uv[face]) || clampUv(autoUv(c.from, c.to, face));
		}
	}
	Undo.finishEdit('Build ' + SPEC.name);
	Canvas.updateAll();
	let model = Codecs.java_block.compile();
	if (typeof model !== 'string') {
		model = JSON.stringify(model, null, '\t');
	}
	Blockbench.writeFile(SPEC.exportPath, {content: model});
	Project.save_path = SPEC.savePath;
	Blockbench.writeFile(SPEC.savePath, {content: Codecs.project.compile({compressed: false})});
	return {cubes: Cube.all.length, exported: SPEC.exportPath};
})()
