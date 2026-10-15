package dev.minebleach.reiatsutest.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * R11: the crystal field of the ice wall ({@link IceCrystalField}). The Python module {@code blender/scripts/ice_wall_field.py} (which the Blender pieces and
 * the check sheet are built with) writes a golden file; the Java port must give the same cells and the same points.
 */
class IceCrystalFieldTest {
	private static final Path GOLDEN = Paths.get("src", "test", "resources", "ice_wall_field_golden.json");
	private static final double EPS = 1e-9;

	private static JsonObject golden() throws IOException {
		assertTrue(Files.isRegularFile(GOLDEN), GOLDEN.toAbsolutePath().toString());
		return JsonParser.parseString(Files.readString(GOLDEN, StandardCharsets.UTF_8)).getAsJsonObject();
	}

	private static double[] vec(JsonElement e) {
		JsonArray a = e.getAsJsonArray();
		double[] out = new double[a.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = a.get(i).getAsDouble();
		}
		return out;
	}

	private static void assertVec(double[] expected, double[] actual, String what) {
		assertEquals(expected.length, actual.length, what);
		for (int i = 0; i < expected.length; i++) {
			assertEquals(expected[i], actual[i], EPS, what + "[" + i + "]");
		}
	}

	@Test
	void theJavaPortGivesTheSameCellsAsThePythonModule() throws IOException {
		JsonObject g = golden();
		JsonArray pts = g.getAsJsonArray("points");
		JsonArray cells = g.getAsJsonArray("cells");
		assertTrue(cells.size() >= 10 && pts.size() >= 4);
		for (JsonElement ce : cells) {
			JsonObject c = ce.getAsJsonObject();
			int x = c.get("x").getAsInt();
			int y = c.get("y").getAsInt();
			int z = c.get("z").getAsInt();
			String at = "cell (" + x + "," + y + "," + z + ")";
			IceCrystalField.Cell cell = IceCrystalField.cell(x, y, z, c.get("below").getAsBoolean(), c.get("above").getAsBoolean());
			assertEquals(c.get("piece").getAsString(), cell.piece, at + " piece");
			assertEquals(c.get("top").getAsBoolean(), cell.top, at + " top");
			assertEquals(c.get("yaw").getAsDouble(), cell.yaw, EPS, at + " yaw");
			assertEquals(c.get("colR").getAsDouble(), cell.colR, EPS, at + " colR");
			assertEquals(c.get("fBottom").getAsDouble(), cell.fBottom, EPS, at + " fBottom");
			assertEquals(c.get("fTop").getAsDouble(), cell.fTop, EPS, at + " fTop");
			assertEquals(c.get("tipScale").getAsDouble(), cell.tipScale, EPS, at + " tipScale");
			assertEquals(c.get("feetYaw").getAsDouble(), cell.feetYaw, EPS, at + " feetYaw");
			if (c.get("feet").isJsonNull()) {
				assertNull(cell.feet, at + " feet");
			} else {
				assertEquals(c.get("feet").getAsString(), cell.feet, at + " feet");
				assertVec(vec(c.get("feetOrigin")), cell.feetOrigin(), at + " feetOrigin");
			}
			JsonArray sats = c.getAsJsonArray("sats");
			assertEquals(sats.size(), cell.sats.size(), at + " satellites");
			for (int s = 0; s < sats.size(); s++) {
				JsonObject sat = sats.get(s).getAsJsonObject();
				assertEquals(sat.get("kind").getAsString(), cell.sats.get(s).kind(), at + " sat kind " + s);
				assertEquals(sat.get("az").getAsDouble(), cell.sats.get(s).az(), EPS, at + " sat az " + s);
				assertEquals(sat.get("z").getAsDouble(), cell.sats.get(s).z(), EPS, at + " sat z " + s);
				assertVec(vec(c.getAsJsonArray("satOrigins").get(s)), cell.satOrigin(s), at + " satOrigin " + s);
			}
			for (int i = 0; i < pts.size(); i++) {
				double[] p = vec(pts.get(i));
				assertVec(vec(c.getAsJsonArray("points").get(i)), cell.column(p[0], p[1], p[2]), at + " point " + i);
			}
		}
	}

	@Test
	void theCellOfABlockIsAPureFunctionOfItsPositionAndItsTwoNeighbours() {
		for (int i = 0; i < 200; i++) {
			int x = i * 37 - 3000;
			int y = (i * 11) % 300 - 20;
			int z = i * -53 + 1000;
			boolean b = i % 2 == 0;
			boolean a = i % 3 == 0;
			IceCrystalField.Cell c1 = IceCrystalField.cell(x, y, z, b, a);
			IceCrystalField.Cell c2 = IceCrystalField.cell(x, y, z, b, a);
			assertEquals(c1.piece, c2.piece);
			assertEquals(c1.yaw, c2.yaw, 0.0);
			assertEquals(c1.sats, c2.sats);
			assertEquals(c1.top, !a);
			assertTrue(c1.top ? !IceCrystalField.SHAFTS.contains(c1.piece) : IceCrystalField.SHAFTS.contains(c1.piece), "tops end the column, the rest are shafts");
			assertEquals(!b, c1.feet != null, "feet only on the lowest cell");
			assertTrue(IceCrystalField.PIECES.contains(c1.piece));
			assertTrue(c1.sats.size() <= IceCrystalField.MAX_SATS);
			for (IceCrystalField.Sat s : c1.sats) {
				assertTrue(IceCrystalField.SATS.contains(s.kind()));
			}
		}
	}

	@Test
	void theCellsOfAColumnFuseIntoOneSpire() {
		// the top ring of the cell at Y and the bottom ring of the cell at Y + 1 are the same hexagon: no seam, whatever the pieces are
		for (int i = 0; i < 300; i++) {
			int x = i * 7 - 500;
			int y = (i * 13) % 200 - 30;
			int z = i * 5 - 400;
			IceCrystalField.Cell lower = IceCrystalField.cell(x, y, z, i % 2 == 0, true);
			IceCrystalField.Cell upper = IceCrystalField.cell(x, y + 1, z, true, i % 3 == 0);
			for (int k = 0; k < 6; k++) {
				double a = Math.toRadians(60.0 * k + 17.0);
				double[] top = lower.column(0.5 * Math.cos(a), 0.5 * Math.sin(a), 1.0);
				double[] bottom = upper.column(0.5 * Math.cos(a), 0.5 * Math.sin(a), 0.0);
				assertEquals(top[0], bottom[0], 1e-12, "x at the seam " + i);
				assertEquals(top[1], bottom[1], 1e-12, "y at the seam " + i);
			}
		}
	}

	@Test
	void theColumnsStayInsideTheirNeighbourhood() {
		// radius of a column at most 0.56, lean + offset at most about 0.37: nothing of a shaft ring may be further than 1 block from the cell centre
		double worst = 0.0;
		Set<String> pieces = new HashSet<>();
		for (int i = 0; i < 2000; i++) {
			IceCrystalField.Cell c = IceCrystalField.cell(i * 3 - 3000, (i * 7) % 120, i * 11 - 7000, i % 2 == 0, i % 5 != 0);
			pieces.add(c.piece);
			for (int k = 0; k < 6; k++) {
				double a = Math.toRadians(60.0 * k);
				for (double z : new double[] {0.0, 0.5, 1.0}) {
					double[] p = c.column(0.5 * Math.cos(a), 0.5 * Math.sin(a), z);
					worst = Math.max(worst, Math.max(Math.abs(p[0]), Math.abs(p[1])));
					assertTrue(p[2] >= -1e-9 && (c.top || p[2] <= 1.0 + 1e-9), "a ring point keeps its height (only a tip is stretched upwards)");
				}
			}
			assertTrue(c.fBottom > 0.5 && c.fBottom <= 1.2 && c.fTop > 0.5 && c.fTop <= 1.2);
		}
		assertTrue(worst < 0.95, "worst reach " + worst);
		assertTrue(pieces.size() >= 7, "the variation really uses the pieces, got " + pieces);
	}

	@Test
	void everyPieceNamedByTheFieldIsAnObjectOfTheModel() {
		assertEquals(IceCrystalField.SHAFTS.size() + IceCrystalField.TIPS.size() + IceCrystalField.CROWNS.size() + IceCrystalField.SATS.size()
				+ IceCrystalField.FEET.size(), IceCrystalField.PIECES.size());
		assertEquals("rukia_ice_wall_tip_b_core", IceCrystalField.objectName("tip_b", true));
		assertEquals("rukia_ice_wall_sat_a", IceCrystalField.objectName("sat_a", false));
		assertNotNull(IceCrystalField.CORE_PIECES);
		assertEquals(9, IceCrystalField.CORE_PIECES.size());
	}
}
