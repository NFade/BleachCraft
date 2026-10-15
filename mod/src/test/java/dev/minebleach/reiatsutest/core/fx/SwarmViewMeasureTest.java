package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * R12 measurement of what the shikai swarm puts on the screen, with the numbers of the swarm renderer (1000 petals, the classic orbit in the first person of the owner,
 * the ribbons in the third person), before (the old gate: {@code idle=false}, the old ribbon numbers) and after. Not a picture: the camera is simulated, a petal counts as
 * "in view" inside a 70 degree vertical / 16:9 frustum, the screen share of a petal is its kite area over the area of the view plane at its distance. The table is printed and
 * written to {@code build/r12_swarm_measure.txt}. UNVERIFIED against the game: the real renderer adds the mesh petals, the lightmap and the glow batch blending.
 */
class SwarmViewMeasureTest {
	private static final int N = 1000;
	private static final double FOVY = Math.toRadians(70);
	private static final double FOVX = 2 * Math.atan(Math.tan(FOVY / 2) * 16.0 / 9.0);
	private static final double PETAL_SCALE = 1.8;
	private static final double DT = 1.0 / 60.0;

	/** Result of one scenario. */
	private record Stat(double inView, double near, double coverage, double peakCoverage, double flashes, double glowCoverage) {
	}

	private static double kiteShare(double size, double dist) {
		// kite: length L = 0.12 * size, width 0.8 L, area 0.4 L^2
		double l = 0.12 * size;
		double area = 0.4 * l * l;
		double plane = 4 * dist * dist * Math.tan(FOVX / 2) * Math.tan(FOVY / 2);
		return area / plane;
	}

	private static double spriteShare(double size, double dist) {
		double plane = 4 * dist * dist * Math.tan(FOVX / 2) * Math.tan(FOVY / 2);
		return size * size / plane;
	}

	/** Normal of the tumbling petal (column 2 of the rotation matrix, as the renderer). */
	private static void normal(SwarmMath.Slots s, int i, double now, double[] n) {
		double ax = s.tx[i];
		double ay = s.ty[i];
		double az = s.tz[i];
		double ang = s.tumblePhase[i] + s.tumbleRate[i] * now;
		double c = Math.cos(ang);
		double sn = Math.sin(ang);
		double k = 1 - c;
		n[0] = ax * az * k + ay * sn;
		n[1] = ay * az * k - ax * sn;
		n[2] = c + az * az * k;
	}

	/** First person of the owner in the classic orbit. {@code gated}: the new gate with activity, else the old gate. */
	private static Stat firstPerson(boolean gated, double speed, double turnDegPerSec, double seconds) {
		SwarmMath.Slots s = new SwarmMath.Slots(1234L, N);
		SwarmView.Fp fp = new SwarmView.Fp();
		double[] out = new double[3];
		double[] nrm = new double[3];
		double yaw = 0;
		double act = 0;
		double sumIn = 0;
		double sumNear = 0;
		double sumCov = 0;
		double peak = 0;
		double sumGlow = 0;
		long flashes = 0;
		boolean[] lit = new boolean[N];
		int frames = (int) (seconds / DT);
		double fx = 0;
		double fz = 0;
		for (int f = 0; f < frames; f++) {
			double t = 20 + f * DT; // an old swarm: settled in the orbit
			yaw += Math.toRadians(turnDegPerSec) * DT;
			double dist = speed * DT;
			fx += Math.sin(yaw) * -dist;
			fz += Math.cos(yaw) * dist;
			double target = SwarmView.activity(speed, Math.abs(turnDegPerSec), 4.3, 90.0);
			act = SwarmView.smoothActivity(act, gated ? target : 0, DT, 12, 3);
			double cy = 1.62;
			double dx = -Math.sin(yaw);
			double dz = Math.cos(yaw);
			int in = 0;
			int near = 0;
			double cov = 0;
			double glow = 0;
			for (int i = 0; i < N; i++) {
				SwarmMath.idle(s, i, t, false, 0, 0, 0, out);
				double x = out[0] - 0; // feet at the origin (the swarm follows the owner, so the walk adds nothing relative)
				double y = out[1] - cy;
				double z = out[2];
				double dl = Math.sqrt(x * x + y * y + z * z);
				double fwd = x * dx + z * dz;
				double sc = SwarmView.fpScale(i, dl, fp, gated, act);
				boolean seen = false;
				if (sc > 0.001 && fwd > 0.05) {
					// frustum: angle from the view axis in the horizontal and vertical plane
					double hx = Math.atan2(x * dz - z * dx, fwd);
					double vy = Math.atan2(y, Math.sqrt(Math.max(1e-9, dl * dl - y * y)) * Math.signum(fwd));
					seen = Math.abs(hx) < FOVX / 2 && Math.abs(vy) < FOVY / 2;
				}
				if (!seen) {
					lit[i] = false;
					continue;
				}
				in++;
				if (dl < 3.0) {
					near++;
				}
				double size = PETAL_SCALE * sc * (0.8 + 0.4 * s.tumblePhase[i] / 6.2832);
				cov += kiteShare(size, dl);
				normal(s, i, t, nrm);
				double facing = Math.abs((nrm[0] * -x + nrm[1] * -y + nrm[2] * -z) / dl);
				double glint = Math.pow(facing, 16);
				boolean star = glint > 0.55 && dl < 40;
				double gmul = SwarmView.fpGlow(dl, fp, gated, act);
				double gw = gated ? fp.glint : 1.0; // weight of the star glints of the resting swarm in the first person (swarm.fpGlint)
				if (star && gmul * 0.85 * glint * gw > 0.08) { // a visible flash: intensity of the star above 8 percent
					if (!lit[i]) {
						flashes++;
					}
					lit[i] = true;
					double ssize = (0.38 + 0.4 * glint) * (0.5 + 0.5 * gmul);
					glow += spriteShare(ssize, dl) * gmul * gw;
				} else {
					lit[i] = false;
				}
				// halo: 1 in 8 (idle FP uses haloMask 7)
				if ((i & 7) == 0 && dl < 30) {
					glow += spriteShare(0.7 * (0.5 + 0.5 * gmul), dl) * 0.36 * gmul;
				}
			}
			sumIn += in;
			sumNear += near;
			sumCov += cov;
			sumGlow += glow;
			peak = Math.max(peak, cov + glow);
		}
		return new Stat(sumIn / frames, sumNear / frames, sumCov / frames, peak, flashes / seconds, sumGlow / frames);
	}

	/** Third person: camera 3.5 blocks behind and above the owner. Returns the number drawn, the number in the sword zone and the glints per frame. */
	private static double[] thirdPerson(boolean tp, double strength) {
		SwarmMath.Slots s = new SwarmMath.Slots(777L, N);
		SwarmView.Tp p = new SwarmView.Tp();
		double[] out = new double[3];
		double[] nrm = new double[3];
		double drawn = 0;
		double inZone = 0;
		double glints = 0;
		double halos = 0;
		double spread = 0;
		int frames = 600;
		// owner looks along +z; the camera stands behind (-z) and above, looking at the chest
		double camX = 0;
		double camY = 2.4;
		double camZ = -3.5;
		for (int f = 0; f < frames; f++) {
			double t = 20 + f * DT;
			for (int i = 0; i < N; i++) {
				if (tp && !SwarmView.tpKeep(i, p)) {
					continue;
				}
				SwarmMath.ribbonIdle(s, i, t, out);
				double sc = SwarmMath.ribbonScale(s, i, t, new SwarmMath.Mode(SwarmMath.Kind.IDLE, 0));
				double zone = tp ? SwarmView.swordClear(out[0], out[1] - 1.3, out[2], 0, 0, 1, strength, p) : 1.0;
				if (sc * zone <= 0.02) {
					continue;
				}
				drawn++;
				spread += Math.hypot(out[0], out[2]);
				// the zone of the sword: in front of the owner, 1.85 forward, radius 0.7 around the forward line at shoulder height
				double dy = out[1] - 1.3;
				if (out[2] > 0 && out[2] < 1.85 && Math.hypot(out[0], dy) < 0.7) {
					inZone += zone <= 0.5 ? 0 : 1;
				}
				double x = out[0] - camX;
				double y = out[1] - camY;
				double z = out[2] - camZ;
				double dl = Math.sqrt(x * x + y * y + z * z);
				normal(s, i, t, nrm);
				double facing = Math.abs((nrm[0] * -x + nrm[1] * -y + nrm[2] * -z) / dl);
				if (Math.pow(facing, 16) > 0.55) {
					glints++;
				}
				if ((i & 15) == 0) {
					halos++;
				}
			}
		}
		return new double[] {drawn / frames, inZone / frames, glints / frames, halos / frames, drawn > 0 ? spread / drawn : 0};
	}

	private static String row(String name, Stat s) {
		return String.format(Locale.ROOT, "%-28s inView %6.1f  within3m %5.1f  coverage %6.3f%%  peak %6.3f%%  glintFlashes/s %5.1f  glowCoverage %6.3f%%%n", name, s.inView, s.near,
				s.coverage * 100, s.peakCoverage * 100, s.flashes, s.glowCoverage * 100);
	}

	@Test
	void measure() throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("R12 swarm measurement (1000 petals, simulated camera, 10 s per scenario, 70 deg vertical fov 16:9)\n");
		sb.append("--- first person of the owner, classic orbit (old gate = before R12) ---\n");
		double[][] scen = {{0, 0}, {4.3, 0}, {0, 180}, {4.3, 120}};
		String[] names = {"standing", "walking 4.3 m/s", "turning 180 deg/s", "walking + turning 120"};
		Stat[] oldS = new Stat[scen.length];
		Stat[] newS = new Stat[scen.length];
		for (int k = 0; k < scen.length; k++) {
			oldS[k] = firstPerson(false, scen[k][0], scen[k][1], 10);
			newS[k] = firstPerson(true, scen[k][0], scen[k][1], 10);
			sb.append(row("OLD " + names[k], oldS[k]));
			sb.append(row("NEW " + names[k], newS[k]));
		}
		sb.append("--- third person, camera 3.5 behind, resting ribbons ---\n");
		double[] o = thirdPerson(false, 0);
		double[] n = thirdPerson(true, 0);
		double[] os = thirdPerson(false, 1);
		double[] ns = thirdPerson(true, 1);
		String fmt = "%-30s petals %6.1f  inSwordZone %6.1f  glints %5.1f  halos %5.1f  meanRadius %.2f%n";
		sb.append(String.format(Locale.ROOT, fmt, "ALL idle (no gate)", o[0], o[1], o[2], o[3], o[4]));
		sb.append(String.format(Locale.ROOT, fmt, "GATED idle", n[0], n[1], n[2], n[3], n[4]));
		sb.append(String.format(Locale.ROOT, fmt, "ALL striking (no gate)", os[0], os[1], os[2], os[3], os[4]));
		sb.append(String.format(Locale.ROOT, fmt, "GATED striking", ns[0], ns[1], ns[2], ns[3], ns[4]));
		System.out.print(sb);
		Path out = Path.of("build", "r12_swarm_measure.txt");
		Files.createDirectories(out.getParent());
		Files.writeString(out, sb.toString());
		// what has to hold after the change (numbers are the measured ones with a margin)
		for (int k = 1; k < scen.length; k++) {
			// the petals are back as they were (R12 round 3), the white star glints are off, the halos are weaker
			assertTrue(newS[k].coverage >= oldS[k].coverage * 0.9 && newS[k].coverage <= oldS[k].coverage * 1.1, names[k] + " petals as before " + newS[k].coverage + " vs " + oldS[k].coverage);
			assertTrue(newS[k].flashes < 0.5, names[k] + " flashes " + newS[k].flashes + " vs " + oldS[k].flashes);
			assertTrue(newS[k].glowCoverage <= oldS[k].glowCoverage * 0.35, names[k] + " glow " + newS[k].glowCoverage + " vs " + oldS[k].glowCoverage);
		}
		assertTrue(newS[0].coverage >= oldS[0].coverage * 0.9, "standing: the petals are back");
		assertTrue(n[0] < o[0] * 0.6, "third person: fewer petals");
		assertTrue(n[4] < 1.3, "third person: the loops stay close around the body: " + n[4]);
		assertTrue(ns[1] < os[1] * 0.4, "the sword zone is cleared while striking");
		assertTrue(n[2] < o[2] * 0.6 && n[3] < o[3] * 0.6, "third person: fewer sparkles and halos");
	}
}
