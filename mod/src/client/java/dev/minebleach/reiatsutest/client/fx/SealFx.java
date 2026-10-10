package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import java.util.Random;
import net.minecraft.util.math.Vec3d;

/**
 * Seal (id 10) and bankai end (id 11), VFX_STORYBOARD 2.3: the released power is drawn back into the sword. A ring of motes
 * (Rukia) or petals (Byakuya) at radius 1.2 collapses into the chest while a ground ring shrinks; the HUD plays its own
 * transition (7.5). Anchors that end with the seal (swarm, rows, sheen, ribbons) hook in here in the later steps; at this
 * step only the common part exists.
 */
public final class SealFx {
	private SealFx() {
	}

	public static void play(EffectEventS2C e) {
		int id = e.effectId();
		boolean end = id == 11;
		CharacterId ch = AuraFx.lastCharacter(e.casterId());
		boolean rukia = ch != CharacterId.BYAKUYA;
		Vec3d pos = new Vec3d(e.x(), e.y(), e.z());
		EffectTimeline t = FxTimelines.create(id, e.seed(), pos, new Vec3d(e.dx(), e.dy(), e.dz()), e.params(), e.casterId(), FxClient.forceRemote());
		String tint = rukia ? "#CFEFFF" : "#F9C8F6";
		String ringTint = FxTune.s("seal.ringTint", rukia ? "#8CCBF2" : "#E8A0F0");
		t.at(0.0, x -> {
			AuraFx.dissolve(e.casterId());
			collapse(x, rukia, tint, ringTint, end);
			if (end) {
				x.sound("block.beacon.deactivate", 0.6, 0.7);
				x.sound("block.bell.resonate", 0.8, 0.5);
				x.sound("block.respawn_anchor.deplete", 0.9, 0.5);
				x.shake(0.35, 0.3);
				x.flash(0.18, "#FF8A7A", true);
			} else {
				x.sound("block.beacon.deactivate", 1.0, 0.7);
				if (!rukia) {
					x.sound("item.trident.return", 0.8, 0.6);
				}
			}
		});
		t.lifetime(1.0);
		FxTimelines.start(t);
	}

	private static void collapse(EffectTimeline t, boolean rukia, String tint, String ringTint, boolean end) {
		Random r = t.rng(3);
		Vec3d c = t.pos.add(0, 1.1, 0);
		int n = end ? 36 : 24;
		for (int i = 0; i < n; i++) {
			double a = (i + r.nextDouble()) / n * Math.PI * 2;
			double rad = 1.2 + r.nextDouble() * 0.4;
			double hy = (r.nextDouble() - 0.5) * 1.2;
			double px = c.x + Math.cos(a) * rad;
			double pz = c.z + Math.sin(a) * rad;
			double py = c.y + hy;
			// inward: arrives at the chest after about 0.5 s (life 10 ticks, drag 1)
			double vx = (c.x - px) / 0.5;
			double vy = (c.y - py) / 0.5;
			double vz = (c.z - pz) / 0.5;
			FxParticles.Spec p = FxParticles.spec(rukia ? FxParticles.Kind.FROST_MOTE : FxParticles.Kind.PETAL).at(px, py, pz).vel(vx, vy, vz).drag(1.0)
					.life(10).size(rukia ? 0.3 : 0.3).seed(r.nextInt(64)).owner(t).fade(0.5);
			if (rukia) {
				p.color("#EAF8FF").colorTo("#9ED3F0");
			} else {
				p.color("#F9C8F6").colorTo("#E5A4DC").spin((r.nextDouble() - 0.5) * 0.5);
			}
			p.spawn();
		}
		for (int i = 0; i < 10; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			double rad = 1.0 + r.nextDouble() * 0.5;
			double px = c.x + Math.cos(a) * rad;
			double pz = c.z + Math.sin(a) * rad;
			double py = c.y + (r.nextDouble() - 0.5) * 0.8;
			FxGlowBatch.sprite(GlowSprite.STAR4).at(px, py, pz).vel((c.x - px) / 0.45, (c.y - py) / 0.45, (c.z - pz) / 0.45).lifeTicks(9)
					.size(0.45 + r.nextDouble() * 0.4).color(tint).peak(0.9).twinkle().owner(t).spawn();
		}
		// ground ring contracting into the feet, then a soft flash of the blade being closed
		FxGlowBatch.sprite(GlowSprite.RING_SOFT).at(t.pos.x, t.pos.y + 0.05, t.pos.z).ground().size(end ? 12 : 8, 1.0).sizeEase(FxMath.IQ).life(0.5)
				.curve(0.03, 0.4).color(ringTint).peak(0.8).owner(t).spawn();
		FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(c.x, c.y, c.z).size(2.4, 0.4).sizeEase(FxMath.IQ).life(0.5).curve(0.1, 0.45).color(tint).peak(0.6)
				.owner(t).spawn();
	}
}
