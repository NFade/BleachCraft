package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.core.world.FrostJournal;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.PersistentState;

/**
 * The rollback journal of the territory freeze in the overworld's data folder ({@code data/reiatsu_test_frost.dat}). It is written
 * a few times a second while a field changes the world, so a crash or a killed server can never leave frost behind: the next start
 * reads it and puts every original block back ({@link FrostField#onServerStarted}). Compact on purpose: two palettes and flat arrays,
 * about 12 bytes per frozen block.
 */
public final class FrostState extends PersistentState {
	public static final String ID = "reiatsu_test_frost";
	public static final PersistentState.Type<FrostState> TYPE = new PersistentState.Type<>(FrostState::new, FrostState::fromNbt, DataFixTypes.LEVEL);

	/** Everything that is frozen right now (after a load: everything the previous run left, rolled back before anything else happens). */
	final FrostJournal journal;

	FrostState() {
		this(new FrostJournal());
	}

	private FrostState(FrostJournal journal) {
		this.journal = journal;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		FrostJournal.Snapshot s = journal.snapshot();
		nbt.putInt("version", 1);
		nbt.put("dims", toList(s.dimensions()));
		nbt.put("palette", toList(s.palette()));
		nbt.putLongArray("pos", s.positions());
		nbt.putIntArray("dim", s.dimIndex());
		nbt.putIntArray("pal", s.paletteIndex());
		return nbt;
	}

	private static NbtList toList(String[] a) {
		NbtList l = new NbtList();
		for (String x : a) {
			l.add(NbtString.of(x));
		}
		return l;
	}

	private static String[] fromList(NbtCompound nbt, String key) {
		NbtList l = nbt.getList(key, NbtElement.STRING_TYPE);
		String[] out = new String[l.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = l.getString(i);
		}
		return out;
	}

	static FrostState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		return new FrostState(FrostJournal.fromSnapshot(new FrostJournal.Snapshot(fromList(nbt, "dims"), fromList(nbt, "palette"),
				nbt.getLongArray("pos"), nbt.getIntArray("dim"), nbt.getIntArray("pal"))));
	}
}
