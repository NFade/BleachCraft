package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.core.world.TempBlockJournal;
import dev.minebleach.reiatsutest.core.world.TempBlockJournal.Entry;
import java.util.UUID;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.PersistentState;

/**
 * Persists the temporary block journal in the overworld's data folder, so a crash cannot leave ice behind: on the next
 * server start every entry is rolled back (STATE_MACHINE section 4).
 */
public final class TempBlockState extends PersistentState {
	public static final String ID = "reiatsu_test_tempblocks";
	public static final PersistentState.Type<TempBlockState> TYPE =
			new PersistentState.Type<>(TempBlockState::new, TempBlockState::fromNbt, DataFixTypes.LEVEL);

	final TempBlockJournal journal = new TempBlockJournal();

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		NbtList list = new NbtList();
		for (Entry e : journal.snapshot()) {
			NbtCompound c = new NbtCompound();
			if (e.owner() != null) {
				c.putString("owner", e.owner().toString());
			}
			c.putString("dim", e.dimension());
			c.putInt("x", e.x());
			c.putInt("y", e.y());
			c.putInt("z", e.z());
			c.putString("prev", e.previous());
			c.putString("placed", e.placed());
			c.putLong("due", e.restoreAtTick());
			list.add(c);
		}
		nbt.put("entries", list);
		return nbt;
	}

	static TempBlockState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		TempBlockState s = new TempBlockState();
		NbtList list = nbt.getList("entries", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < list.size(); i++) {
			NbtCompound c = list.getCompound(i);
			UUID owner = null;
			if (c.contains("owner")) {
				try {
					owner = UUID.fromString(c.getString("owner"));
				} catch (IllegalArgumentException ignored) {
					// unowned entry: still rolled back on start
				}
			}
			s.journal.add(new Entry(owner, c.getString("dim"), c.getInt("x"), c.getInt("y"), c.getInt("z"),
					c.getString("prev"), c.getString("placed"), c.getLong("due")));
		}
		return s;
	}
}
