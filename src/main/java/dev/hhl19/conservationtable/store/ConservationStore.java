package dev.hhl19.conservationtable.store;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 玩家随身仓库：物品 -> 数量。
 * 条目按「物品 + 组件」区分，所以附魔/自定义名不同的同类物品各占一条，不会被合并。
 * 只做 1:1 存取，数量归零即移除条目，不含任何兑换逻辑。
 */
public final class ConservationStore {
	/** template 恒为单个数量，实际数量单独记在 count 中。 */
	public record Entry(ItemStack template, long count) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				ItemStack.CODEC.fieldOf("item").forGetter(Entry::template),
				Codec.LONG.fieldOf("count").forGetter(Entry::count)
		).apply(instance, Entry::new));

		public Entry {
			ItemStack single = template.copy();
			single.setCount(1);
			template = single;
		}
	}

	public static final Codec<ConservationStore> CODEC =
			Entry.CODEC.listOf().xmap(ConservationStore::new, ConservationStore::snapshot);

	// ItemStack 没有重写 equals/hashCode，不能直接当 Map 的键，所以用列表 + 显式比对。
	private final List<Entry> entries = new ArrayList<>();

	public ConservationStore() {
	}

	private ConservationStore(List<Entry> entries) {
		for (Entry entry : entries) {
			if (entry.count() > 0) {
				merge(entry.template(), entry.count());
			}
		}
	}

	private List<Entry> snapshot() {
		return Collections.unmodifiableList(new ArrayList<>(entries));
	}

	/** 与 probe 完全同种（物品 + 组件）的数量，没有则返回 0。 */
	public long get(ItemStack probe) {
		for (Entry entry : entries) {
			if (ItemStack.isSameItemSameComponents(entry.template(), probe)) {
				return entry.count();
			}
		}
		return 0L;
	}

	public void deposit(ItemStack stack, long amount) {
		if (!stack.isEmpty() && amount > 0) {
			merge(stack, amount);
		}
	}

	/** 取出至多 amount 个，返回实际取出的数量。 */
	public long withdraw(ItemStack probe, long amount) {
		if (amount <= 0) {
			return 0L;
		}
		for (int i = 0; i < entries.size(); i++) {
			Entry entry = entries.get(i);
			if (!ItemStack.isSameItemSameComponents(entry.template(), probe)) {
				continue;
			}
			long taken = Math.min(entry.count(), amount);
			long remaining = entry.count() - taken;
			if (remaining == 0) {
				entries.remove(i);
			} else {
				entries.set(i, new Entry(entry.template(), remaining));
			}
			return taken;
		}
		return 0L;
	}

	public List<Entry> view() {
		return Collections.unmodifiableList(entries);
	}

	public boolean isEmpty() {
		return entries.isEmpty();
	}

	private void merge(ItemStack stack, long amount) {
		for (int i = 0; i < entries.size(); i++) {
			Entry entry = entries.get(i);
			if (ItemStack.isSameItemSameComponents(entry.template(), stack)) {
				entries.set(i, new Entry(entry.template(), entry.count() + amount));
				return;
			}
		}
		entries.add(new Entry(stack, amount));
	}
}
