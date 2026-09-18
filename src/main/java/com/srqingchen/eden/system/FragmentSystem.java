package com.srqingchen.eden.system;

import com.mojang.serialization.MapCodec;
import com.srqingchen.eden.EdenProtocol;
import com.srqingchen.eden.data.CampaignData;
import com.srqingchen.eden.registry.EdenDataComponents;
import com.srqingchen.eden.registry.EdenItems;
import com.srqingchen.eden.util.EdenMessages;
import com.srqingchen.eden.util.EdenMessages.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetComponentsFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 《三位起草人》残片层 (S1 批C). Three drafters signed the Eden Protocol - 肃 (purge), 融 (symbiosis),
 * 铭 (archive) - and their pages are still down in the caves. Each kind has 6 pages; WHERE a page is
 * found is the storytelling: 肃 rides the abandoned-mineshaft minecart chests, 融 sleeps in trial
 * chamber reward chests, 铭 clings to lush-cave glow berries and deep down in the deepslate. Page 18
 * (铭·6) is physically present but charred - only the same unreadable triangle rune that marks every
 * page's corner still glimmers.
 * <p>Placement is a small loot-time system of its own: two fixed tables gain a fragment pool (gated
 * per-roll by "raid dimension + S1 live"), and two block-break seams drop 铭 fragments directly.
 * Reading (right-click) banks the page server-wide - same page, different players, shared discovery -
 * and quietly feeds the UNEXPLAINED archive meter.
 */
public final class FragmentSystem {
    private FragmentSystem() {}

    public static final int KIND_PURITY = 0, KIND_SYMBIOSIS = 1, KIND_ARCHIVE = 2;
    public static final String[] KINDS = {"purity", "symbiosis", "archive"};
    public static final int PAGES = 6;

    /** Fragment pool weight vs the empty entry: 6/(6+18) = 25% per chest. */
    private static final int FRAGMENT_WEIGHT = 6;
    private static final int EMPTY_WEIGHT = 18;

    /** Per-run "already dropped here" memory for block-seam drops: position -> tick (anti-farm). */
    private static final Map<String, Long> SEAM_COOLDOWN = new ConcurrentHashMap<>();
    private static final long SEAM_COOLDOWN_TICKS = 20 * 60;   // one fragment per minute per block column

    // ---------- placement ----------

    /** Fragment pools for the two structure-chest seams; runs on datapack-load threads (idempotent). */
    public static void onLootTableLoad(LootTableLoadEvent event) {
        Identifier name = event.getName();
        int kind;
        if (name.equals(Identifier.fromNamespaceAndPath("minecraft", "chests/abandoned_mineshaft"))) {
            kind = KIND_PURITY;
        } else if (name.getNamespace().equals("minecraft") && name.getPath().startsWith("chests/trial_chambers")) {
            kind = KIND_SYMBIOSIS;
        } else {
            return;
        }
        try {
            event.getTable().addPool(buildFragmentPool(kind));
        } catch (RuntimeException e) {
            EdenProtocol.LOGGER.debug("[Eden] fragment pool skipped for {}: {}", name, e.getMessage());
        }
    }

    private static LootPool buildFragmentPool(int kind) {
        LootPool.Builder builder = LootPool.lootPool()
                .name("eden_fragment_" + KINDS[kind])
                .setRolls(ConstantValue.exactly(1.0f))
                .when(LootInjectionSystem.RaidDimensionCondition.builder())
                .when(SeasonOneCondition.builder());
        for (int page = 1; page <= PAGES; page++) {
            final int p = page;
            builder.add(LootItem.lootTableItem(EdenItems.FRAGMENT.get())
                    .setWeight(FRAGMENT_WEIGHT)
                    .apply(SetComponentsFunction.setComponent(EdenDataComponents.FRAGMENT.get(),
                            new EdenDataComponents.Fragment(kind, p))));
        }
        builder.add(net.minecraft.world.level.storage.loot.entries.EmptyLootItem.emptyItem()
                .setWeight(EMPTY_WEIGHT));
        return builder.build();
    }

    /** 铭 seam: glow-berry bushes and deepslate in the raid worlds during the season occasionally hide a page.
     * Facade for the S0 definition's block-break hook (dispatched only while S0 is active). */
    public static void blockBroken(BreakBlockEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer sp) || !(sp.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        if (sp.getBlockY() >= 0) {
            return;
        }
        BlockState state = event.getState();
        boolean berries = (state.is(Blocks.CAVE_VINES) || state.is(Blocks.CAVE_VINES_PLANT))
                && state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.BERRIES)
                && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.BERRIES);
        boolean deepslate = state.is(Blocks.DEEPSLATE);
        if (!berries && !deepslate) {
            return;
        }
        String key = pos.asLong() + "|" + level.dimension().identifier();
        long now = level.getGameTime();
        Long last = SEAM_COOLDOWN.get(key);
        if (last != null && now - last < SEAM_COOLDOWN_TICKS) {
            return;
        }
        float chance = berries ? 0.12f : 0.02f;
        if (level.getRandom().nextFloat() >= chance) {
            return;
        }
        SEAM_COOLDOWN.put(key, now);
        if (SEAM_COOLDOWN.size() > 4096) {
            SEAM_COOLDOWN.clear();   // bounded memory; the cooldown is an anti-farm courtesy
        }
        dropFragment(level, pos, KIND_ARCHIVE, 1 + level.getRandom().nextInt(PAGES));
    }

    public static void dropFragment(ServerLevel level, BlockPos pos, int kind, int page) {
        ItemStack stack = new ItemStack(EdenItems.FRAGMENT.get());
        stack.set(EdenDataComponents.FRAGMENT.get(), new EdenDataComponents.Fragment(kind, page));
        ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        entity.setDefaultPickUpDelay();
        level.addFreshEntity(entity);
    }

    // ---------- reading ----------

    /** Decode a fragment: read the page out, bank it server-wide (first copy pays), burn the scrap. */
    public static void read(ServerPlayer sp, int kind, int page) {
        MinecraftServer server = sp.level().getServer();
        if (server == null || kind < 0 || kind >= KINDS.length || page < 1 || page > PAGES) {
            return;
        }
        CampaignData data = CampaignData.get(server);
        boolean first = data.discoverPage(KINDS[kind], page);
        String bodyKey = "eden.fragment." + KINDS[kind] + "." + page;
        EdenMessages.send(sp, Type.SPECIAL, "eden.fragment.decoded",
                kindName(kind), page);
        // The page itself, verbatim - the charred final page "reads" as burn marks and one rune.
        sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable(bodyKey)
                .withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC));
        if (first) {
            data.addProtocolArchive(3.0f);
            EdenMessages.send(sp, Type.SUCCESS, "eden.fragment.first");
            for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                if (other != sp) {
                    EdenMessages.send(other, Type.INFO, "eden.fragment.shared",
                            sp.getName().getString(), kindName(kind), page);
                }
            }
        }
    }

    private static String kindName(int kind) {
        return net.minecraft.network.chat.Component.translatable("eden.fragment.kind." + KINDS[kind]).getString();
    }

    // ---------- roll-time condition: the active season must scatter fragments ----------

    /** True when the roll happens while a fragment season is live (see {@code Seasons.current}).
     * Registered as {@code eden:season_one} from {@link LootInjectionSystem.Conditions}; the id
     * stays for datapack compatibility, the check now routes through the season registry. */
    public record SeasonOneCondition() implements LootItemCondition {
        public static final MapCodec<SeasonOneCondition> MAP_CODEC = MapCodec.unit(new SeasonOneCondition());

        @Override
        public MapCodec<? extends LootItemCondition> codec() {
            return MAP_CODEC;
        }

        @Override
        public boolean test(LootContext context) {
            MinecraftServer server = context.getLevel().getServer();
            if (server == null) {
                return false;
            }
            var season = com.srqingchen.eden.season.Seasons.current(server);
            return season != null && season.fragmentsActive();
        }

        public static LootItemCondition.Builder builder() {
            return () -> new SeasonOneCondition();
        }
    }
}
