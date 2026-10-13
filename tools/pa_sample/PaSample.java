import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.data.gson.AnimationSerializing;
import dev.kosmx.playerAnim.core.impl.AnimationProcessor;
import dev.kosmx.playerAnim.core.util.Vec3f;

import java.io.FileReader;
import java.io.Reader;
import java.util.List;
import java.util.Locale;

/**
 * Headless evaluation of an emote JSON with playerAnimator's OWN core classes (no Minecraft needed): prints what the game
 * would feed to the model for every part (the torso is called "body" inside playerAnimator) at every tick (plus the half tick), CSV: tick,part,channel,v0,v1,v2.
 *
 *   java -cp playeranimator.jar;gson.jar tools/pa_sample/PaSample.java animation.json [parts,comma] [halfTicks 1|0]
 */
public class PaSample {
	public static void main(String[] args) throws Exception {
		List<KeyframeAnimation> anims;
		try (Reader r = new FileReader(args[0])) {
			anims = AnimationSerializing.deserializeAnimation(r);
		}
		KeyframeAnimation a = anims.get(0);
		String[] parts = args.length > 1 ? args[1].split(",") : new String[] {"head", "body", "rightArm", "leftArm", "rightLeg", "leftLeg", "rightItem"};
		boolean half = args.length > 2 && args[2].equals("1");
		KeyframeAnimationPlayer p = new KeyframeAnimationPlayer(a);
		AnimationProcessor proc = new AnimationProcessor(p);   // the same entry point the model mixin uses
		int end = a.stopTick;
		for (int tick = 0; tick <= end; tick++) {
			for (int h = 0; h < (half ? 2 : 1); h++) {
				float delta = h * 0.5f;
				p.setupAnim(delta);
				proc.setTickDelta(delta);
				for (String part : parts) {
					for (TransformType t : new TransformType[] {TransformType.ROTATION, TransformType.POSITION}) {
						Vec3f v = proc.get3DTransform(part, t, new Vec3f(0, 0, 0));
						System.out.println(String.format(Locale.ROOT, "%.1f,%s,%s,%.6f,%.6f,%.6f", tick + delta, part, t.name(),
								v.getX().floatValue(), v.getY().floatValue(), v.getZ().floatValue()));
					}
				}
			}
			proc.tick();
		}
	}
}
