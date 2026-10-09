package dev.minebleach.reiatsutest.entity;

import dev.minebleach.reiatsutest.registry.ModEntities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

/**
 * Effect anchor ({@code reiatsu_test:fx_anchor}, ADR section 2 and VFX_STORYBOARD 1.1 S1): holds only parameters, all
 * instances are simulated on the client. Vanilla entity tracking gives multiplayer visibility, late join and despawn.
 * Tracked data: kind, seed, startTime (game time), ownerId, targetId, p0..p3 and the phase of the effect (S1, S3).
 */
public class FxAnchorEntity extends Entity {
	public static final TrackedData<Byte> KIND = DataTracker.registerData(FxAnchorEntity.class, TrackedDataHandlerRegistry.BYTE);
	public static final TrackedData<Integer> SEED = DataTracker.registerData(FxAnchorEntity.class, TrackedDataHandlerRegistry.INTEGER);
	public static final TrackedData<Integer> START_TIME = DataTracker.registerData(FxAnchorEntity.class, TrackedDataHandlerRegistry.INTEGER);
	public static final TrackedData<Integer> OWNER_ID = DataTracker.registerData(FxAnchorEntity.class, TrackedDataHandlerRegistry.INTEGER);
	public static final TrackedData<Integer> TARGET_ID = DataTracker.registerData(FxAnchorEntity.class, TrackedDataHandlerRegistry.INTEGER);
	public static final TrackedData<Float> P0 = DataTracker.registerData(FxAnchorEntity.class, TrackedDataHandlerRegistry.FLOAT);
	public static final TrackedData<Float> P1 = DataTracker.registerData(FxAnchorEntity.class, TrackedDataHandlerRegistry.FLOAT);
	public static final TrackedData<Float> P2 = DataTracker.registerData(FxAnchorEntity.class, TrackedDataHandlerRegistry.FLOAT);
	public static final TrackedData<Float> P3 = DataTracker.registerData(FxAnchorEntity.class, TrackedDataHandlerRegistry.FLOAT);
	public static final TrackedData<Byte> PHASE_KIND = DataTracker.registerData(FxAnchorEntity.class, TrackedDataHandlerRegistry.BYTE);
	public static final TrackedData<Integer> PHASE_TIME = DataTracker.registerData(FxAnchorEntity.class, TrackedDataHandlerRegistry.INTEGER);

	/** Server only: ticks until the anchor discards itself (0 means until ended explicitly). */
	private int lifeTicks;

	public FxAnchorEntity(EntityType<? extends FxAnchorEntity> type, World world) {
		super(type, world);
		this.noClip = true;
		this.setNoGravity(true);
	}

	public static FxAnchorEntity create(World world) {
		return new FxAnchorEntity(ModEntities.FX_ANCHOR, world);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(KIND, FxAnchorKind.FIELD);
		builder.add(SEED, 0);
		builder.add(START_TIME, 0);
		builder.add(OWNER_ID, -1);
		builder.add(TARGET_ID, -1);
		builder.add(P0, 0f);
		builder.add(P1, 0f);
		builder.add(P2, 0f);
		builder.add(P3, 0f);
		builder.add(PHASE_KIND, FxAnchorKind.PHASE_STANDING);
		builder.add(PHASE_TIME, 0);
	}

	/** Server side set-up. */
	public FxAnchorEntity configure(byte kind, int seed, int ownerId, int targetId, float p0, float p1, float p2, float p3, int lifeTicks) {
		DataTracker t = this.dataTracker;
		t.set(KIND, kind);
		t.set(SEED, seed);
		t.set(START_TIME, (int) this.getWorld().getTime());
		t.set(OWNER_ID, ownerId);
		t.set(TARGET_ID, targetId);
		t.set(P0, p0);
		t.set(P1, p1);
		t.set(P2, p2);
		t.set(P3, p3);
		this.lifeTicks = lifeTicks;
		return this;
	}

	/** Server side: phase change (S3), e.g. rows scattered (32) or consumed (33) at the current game time. */
	public void setPhase(byte phaseKind) {
		this.dataTracker.set(PHASE_KIND, phaseKind);
		this.dataTracker.set(PHASE_TIME, (int) this.getWorld().getTime());
	}

	public byte kind() {
		return this.dataTracker.get(KIND);
	}

	public int seed() {
		return this.dataTracker.get(SEED);
	}

	public int startTime() {
		return this.dataTracker.get(START_TIME);
	}

	public int ownerId() {
		return this.dataTracker.get(OWNER_ID);
	}

	public int targetId() {
		return this.dataTracker.get(TARGET_ID);
	}

	public float p0() {
		return this.dataTracker.get(P0);
	}

	public float p1() {
		return this.dataTracker.get(P1);
	}

	public float p2() {
		return this.dataTracker.get(P2);
	}

	public float p3() {
		return this.dataTracker.get(P3);
	}

	public byte phaseKind() {
		return this.dataTracker.get(PHASE_KIND);
	}

	public int phaseTime() {
		return this.dataTracker.get(PHASE_TIME);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.getWorld().isClient) {
			return;
		}
		if (this.lifeTicks > 0 && this.age >= this.lifeTicks) {
			this.discard();
			return;
		}
		if (FxAnchorKind.followsOwner(kind())) {
			Entity owner = this.getWorld().getEntityById(ownerId());
			if (owner == null || !owner.isAlive()) {
				this.discard();
			} else {
				this.setPosition(owner.getX(), owner.getY(), owner.getZ());
			}
		}
	}

	@Override
	public boolean shouldRender(double distance) {
		return distance < 192.0 * 192.0;
	}

	@Override
	public boolean isCollidable() {
		return false;
	}

	@Override
	public boolean canHit() {
		return false;
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
	}
}
