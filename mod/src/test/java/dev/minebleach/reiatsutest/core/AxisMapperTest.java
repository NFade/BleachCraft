package dev.minebleach.reiatsutest.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.minebleach.reiatsutest.core.obj.AxisMapper;
import org.junit.jupiter.api.Test;

class AxisMapperTest {
	@Test
	void tipMapsToObjPlusY() {
		assertArrayEquals(new float[] {0, 1, 0}, AxisMapper.blenderToObj(0, 0, 1), 1e-6f);
	}

	@Test
	void edgeMinusYMapsToObjPlusZ() {
		assertArrayEquals(new float[] {0, 0, 1}, AxisMapper.blenderToObj(0, -1, 0), 1e-6f);
	}

	@Test
	void gripMapsToModelCentre() {
		float[] grip = {0, 0, 0.19f};
		assertArrayEquals(new float[] {0.5f, 0.5f, 0.5f}, AxisMapper.blenderToModel(grip, grip), 1e-6f);
	}

	@Test
	void tipRelativeToGrip() {
		float[] p = AxisMapper.blenderToModel(new float[] {0, 0, 0.80f}, new float[] {0, 0, 0.19f});
		assertArrayEquals(new float[] {0.5f, 0.5f + 0.61f, 0.5f}, p, 1e-5f);
	}

	@Test
	void mappingIsProperRotation() {
		// det of (x,y,z)->(x,z,-y) must be +1 (winding preserved): e_x x e_y = e_z must map to image(e_x) x image(e_y) = image(e_z)
		float[] ex = AxisMapper.blenderToObj(1, 0, 0);
		float[] ey = AxisMapper.blenderToObj(0, 1, 0);
		float[] ez = AxisMapper.blenderToObj(0, 0, 1);
		float[] cross = {
			ex[1] * ey[2] - ex[2] * ey[1],
			ex[2] * ey[0] - ex[0] * ey[2],
			ex[0] * ey[1] - ex[1] * ey[0]
		};
		assertArrayEquals(ez, cross, 1e-6f);
	}

	@Test
	void inverseRoundTrips() {
		float[] b = {0.3f, -0.7f, 1.2f};
		float[] o = AxisMapper.blenderToObj(b);
		assertArrayEquals(b, AxisMapper.objToBlender(o[0], o[1], o[2]), 1e-6f);
	}

	@Test
	void vFlip() {
		assertEquals(0.25f, AxisMapper.flipV(0.75f), 1e-6f);
	}
}
