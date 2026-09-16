package com.srqingchen.eden.network;

import com.srqingchen.eden.EdenConfig;
import com.srqingchen.eden.EdenProtocol;
import com.srqingchen.eden.attachment.RaidState;
import com.srqingchen.eden.data.CampaignData;
import com.srqingchen.eden.data.DifficultyConfigData;
import com.srqingchen.eden.dimension.EdenDimensions;
import com.srqingchen.eden.item.CardItem;
import com.srqingchen.eden.item.CurioCards;
import com.srqingchen.eden.registry.EdenAttachments;
import com.srqingchen.eden.registry.EdenItems;
import com.srqingchen.eden.system.CampaignSystem;
import com.srqingchen.eden.system.CurseCardSystem;
import com.srqingchen.eden.system.RaidService;
import com.srqingchen.eden.system.RaidWorldFeatures;
import com.srqingchen.eden.system.ShopCatalog;
import com.srqingchen.eden.talent.ClassId;
import com.srqingchen.eden.talent.ClassSkillSystem;
import com.srqingchen.eden.talent.TalentSystem;
import com.srqingchen.eden.util.EdenMessages;
import com.srqingchen.eden.util.EdenMessages.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Network plumbing. Payloads are registered on the MOD bus (RegisterPayloadHandlersEvent is an
 * IModBusEvent); sends go through {@link PacketDistributor}.
 * <p><b>Dedicated-server safety</b>: this class is loaded on BOTH dists during mod construction, and the
 * FML transformer eagerly resolves every class referenced in its bytecode - so it must NEVER mention a
 * client-only type (screens, Minecraft). Client-bound GUI handlers go through {@link ClientHooks},
 * which the client mod entrypoint installs before any payload event fires.
 */
public class EdenNetwork {

    /** Client-side GUI entry points, installed by EdenProtocolClient (null on a dedicated server). */
    public interface ClientHooks {
        void openShop(OpenShopPayload payload);

        void openEditor(EditorDataPayload payload);

        void openLaunchPad(OpenLaunchPadPayload payload);

        void openChronicle(ChroniclePayload payload);
    }

    @Nullable
    public static ClientHooks clientHooks;

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(SyncRaidStatePayload.TYPE, SyncRaidStatePayload.STREAM_CODEC, EdenNetwork::handleSync);
        // Shop: open + balance refresh are client-bound; the buy request is server-bound.
        registrar.playToClient(OpenShopPayload.TYPE, OpenShopPayload.STREAM_CODEC, EdenNetwork::handleOpenShop);
        registrar.playToClient(ShopBalancePayload.TYPE, ShopBalancePayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientShopData.balance = payload.balance()));
        registrar.playToServer(BuyPayload.TYPE, BuyPayload.STREAM_CODEC, EdenNetwork::handleBuy);
        // Class active-skill key (client -> server); P1 acknowledges, P3 fires the actual per-class skill.
        registrar.playToServer(UseClassSkillPayload.TYPE, UseClassSkillPayload.STREAM_CODEC, EdenNetwork::handleUseClassSkill);
        // Card active key (client -> server): short tap vs. hold routes to CurseCardSystem.
        registrar.playToServer(UseCardSkillPayload.TYPE, UseCardSkillPayload.STREAM_CODEC, EdenNetwork::handleUseCardSkill);
        // Talent system (P2/P3): server pushes the full snapshot; client sends unlock / choose-class / convert requests.
        registrar.playToClient(TalentSyncPayload.TYPE, TalentSyncPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientTalentData.update(payload)));
        registrar.playToServer(TalentClickPayload.TYPE, TalentClickPayload.STREAM_CODEC, EdenNetwork::handleTalentClick);
        registrar.playToServer(ChooseClassPayload.TYPE, ChooseClassPayload.STREAM_CODEC, EdenNetwork::handleChooseClass);
        registrar.playToServer(ConvertTPPayload.TYPE, ConvertTPPayload.STREAM_CODEC, EdenNetwork::handleConvert);

        // Difficulty editor: server -> client pushes the config + profile list; the client sends the
        // edited table back to save. The screen itself opens through ClientHooks (client dist only).
        registrar.playToClient(EditorDataPayload.TYPE, EditorDataPayload.STREAM_CODEC, EdenNetwork::handleEditorData);
        registrar.playToServer(SaveConfigPayload.TYPE, SaveConfigPayload.STREAM_CODEC, EdenNetwork::handleSaveConfig);
        // Launch pad (§3/§9/§15): server pushes the campaign snapshot + unlock rows, the client sends the
        // chosen expedition (difficulty + destination); the server re-validates before launching.
        registrar.playToClient(OpenLaunchPadPayload.TYPE, OpenLaunchPadPayload.STREAM_CODEC, EdenNetwork::handleOpenLaunchPad);
        registrar.playToServer(LaunchRaidPayload.TYPE, LaunchRaidPayload.STREAM_CODEC, EdenNetwork::handleLaunchRaid);
        // Chronicle wall (§13/§14): server pushes the campaign snapshot; the screen is read-only.
        registrar.playToClient(ChroniclePayload.TYPE, ChroniclePayload.STREAM_CODEC, EdenNetwork::handleChronicle);
    }

    private static void handleSync(SyncRaidStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientRaidData.update(payload));
    }

    private static void handleOpenShop(OpenShopPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (clientHooks != null) {
                clientHooks.openShop(payload);
            }
        });
    }

    private static void handleEditorData(EditorDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (clientHooks != null) {
                clientHooks.openEditor(payload);
            }
        });
    }

    private static void handleOpenLaunchPad(OpenLaunchPadPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (clientHooks != null) {
                clientHooks.openLaunchPad(payload);
            }
        });
    }

    private static void handleChronicle(ChroniclePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (clientHooks != null) {
                clientHooks.openChronicle(payload);
            }
        });
    }

    /**
     * A launch request from the pad screen. Server-authoritative: re-check every campaign unlock, then
     * hand to {@link RaidService#startRaid} with the chosen dimension key.
     */
    private static void handleLaunchRaid(LaunchRaidPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) {
                return;
            }
            if (!sp.level().dimension().equals(EdenDimensions.ARK)) {
                return;   // only the ark pad launches expeditions
            }
            MinecraftServer server = sp.level().getServer();
            if (server == null) {
                return;
            }
            CampaignData campaign = CampaignData.get(server);
            int diffIdx = OpenLaunchPadPayload.DIFFICULTIES.indexOf(payload.difficulty());
            if (diffIdx < 0 || !CampaignSystem.isDifficultyUnlocked(campaign, payload.difficulty())) {
                EdenMessages.send(sp, Type.WARNING, "eden.msg.launch_locked_difficulty");
                return;
            }
            int dimIdx = OpenLaunchPadPayload.DIMENSIONS.indexOf(payload.dimension());
            if (dimIdx < 0) {
                return;
            }
            int stage = campaign.stage();
            boolean dimOk = switch (payload.dimension()) {
                case "nether" -> stage >= 3 || stage == 0;
                case "end" -> stage >= 4 || stage == 0;
                default -> true;
            };
            if (!dimOk) {
                EdenMessages.send(sp, Type.WARNING, "eden.msg.launch_locked_dimension");
                return;
            }
            var dimKey = switch (payload.dimension()) {
                case "nether" -> EdenDimensions.RAID_NETHER;
                case "end" -> EdenDimensions.RAID_END;
                default -> EdenDimensions.RAID_OVERWORLD;
            };
            if (RaidService.startRaid(sp, payload.difficulty(), dimKey)) {
                EdenMessages.send(sp, Type.SUCCESS, "eden.msg.entered_raid", payload.difficulty());
            } else {
                EdenMessages.send(sp, Type.DANGER, "eden.msg.raid_dim_unavailable");
            }
        });
    }

    private static void handleBuy(BuyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
                ShopCatalog.buy(sp, payload.key(), payload.count());
                ShopCatalog.sendBalance(sp);
            }
        });
    }

    /** The player pressed the active-skill key (default R): dispatch to the active class's skill (see {@link ClassSkillSystem}). */
    private static void handleUseClassSkill(UseClassSkillPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
                ClassSkillSystem.useSkill(sp);
            }
        });
    }

    /** The player pressed the card-active key (default Tab): short tap or hold (see {@code CurseCardSystem}). */
    private static void handleUseCardSkill(UseCardSkillPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
                CurseCardSystem.onUse(sp, payload.longPress());
            }
        });
    }

    private static void handleTalentClick(TalentClickPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) TalentSystem.unlockNode(sp, payload.nodeId());
        });
    }

    private static void handleChooseClass(ChooseClassPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) TalentSystem.chooseClass(sp, ClassId.byId(payload.classId()));
        });
    }

    private static void handleConvert(ConvertTPPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) TalentSystem.convert(sp, payload.amount());
        });
    }

    /**
     * Server-side save of the edited difficulty config: authoritative - clamps every value and writes it to
     * the {@link DifficultyConfigData} SavedData (profile, charge speeds, particle tuning, card quality/star
     * distributions). The particle colour is not edited by the client, so the existing colour is preserved.
     */
    private static void handleSaveConfig(SaveConfigPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp && sp.level().getServer() != null) {
                DifficultyConfigData config = DifficultyConfigData.get(sp.level().getServer());
                List<String> diffs = DifficultyConfigData.DIFFICULTIES;
                int qc = DifficultyConfigData.QUALITY_COUNT;
                int sc = DifficultyConfigData.STAR_COUNT;
                for (int i = 0; i < diffs.size(); i++) {
                    DifficultyConfigData.Entry old = config.entryFor(diffs.get(i));
                    String profile = getStr(payload.selected(), i, diffs.get(i));
                    int c = i * 3;
                    float b = clampSpeed(getF(payload.charge(), c, DifficultyConfigData.DEFAULT_BASE));
                    float ce = clampSpeed(getF(payload.charge(), c + 1, DifficultyConfigData.DEFAULT_CELL));
                    float cr = clampSpeed(getF(payload.charge(), c + 2, DifficultyConfigData.DEFAULT_CRYSTAL));
                    float scale = Math.max(0.1f, getF(payload.particle(), c, DifficultyConfigData.DEFAULT_PARTICLE_SCALE));
                    float chance = clamp01(getF(payload.particle(), c + 1, DifficultyConfigData.DEFAULT_PARTICLE_CHANCE));
                    float speed = Math.max(0.0f, getF(payload.particle(), c + 2, DifficultyConfigData.DEFAULT_PARTICLE_SPEED));
                    int dens = Math.max(0, Math.min(512, getI(payload.density(), i, DifficultyConfigData.DEFAULT_PARTICLE_DENSITY)));
                    List<Integer> q = subInts(payload.quality(), i * qc, qc);
                    List<Integer> s = subInts(payload.star(), i * sc, sc);
                    config.setEntry(diffs.get(i), new DifficultyConfigData.Entry(profile, b, ce, cr,
                            old.particleColor(), scale, chance, dens, speed, q, s));
                }
                EdenProtocol.LOGGER.info("[Eden] difficulty config updated by {}", sp.getName().getString());
            }
        });
    }

    /** Keep a client-sent charge speed in a sane, non-negative range. */
    private static float clampSpeed(float v) {
        return Math.max(0.0f, Math.min(100.0f, v));
    }

    private static float clamp01(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
    }

    private static String getStr(List<String> l, int i, String def) {
        return (l != null && i >= 0 && i < l.size() && l.get(i) != null) ? l.get(i) : def;
    }

    private static float getF(List<Float> l, int i, float def) {
        return (l != null && i >= 0 && i < l.size() && l.get(i) != null) ? l.get(i) : def;
    }

    private static int getI(List<Integer> l, int i, int def) {
        return (l != null && i >= 0 && i < l.size() && l.get(i) != null) ? l.get(i) : def;
    }

    private static List<Integer> subInts(List<Integer> l, int from, int count) {
        List<Integer> out = new ArrayList<>(count);
        for (int k = 0; k < count; k++) {
            out.add(Math.max(0, getI(l, from + k, 1)));
        }
        return out;
    }

    /** Push the given player's current raid state + ambient-particle tuning to their own client. */
    public static void syncTo(ServerPlayer player) {
        RaidState s = player.getData(EdenAttachments.RAID_STATE);
        int timeLeft = 0;
        if (s.inRaid) {
            long elapsed = player.level().getGameTime() - s.raidStartTick;
            long limitTicks = EdenConfig.RAID_DURATION_MINUTES.get() * 60L * 20L;
            timeLeft = (int) Math.max(0L, (limitTicks - elapsed) / 20L);
        }
        MinecraftServer server = player.level().getServer();
        DifficultyConfigData.Entry cfg = server != null
                ? DifficultyConfigData.get(server).entryFor(s.difficulty)
                : DifficultyConfigData.defaultEntry(s.difficulty);
        // Pathfinder card (§8 撤离卡): sync the nearest standing extraction point as the HUD bearing target.
        boolean hasExtract = false;
        BlockPos extract = null;
        if (s.inRaid && server != null
                && CurioCards.isEquipped(player, (CardItem) EdenItems.CARD_PATHFINDER.get())) {
            ServerLevel raid = server.getLevel(EdenDimensions.RAID_OVERWORLD);
            if (raid != null && player.level().dimension().equals(EdenDimensions.RAID_OVERWORLD)) {
                extract = RaidWorldFeatures.nearestExtractionPoint(raid, player.position());
                hasExtract = extract != null;
            }
        }
        PacketDistributor.sendToPlayer(player, new SyncRaidStatePayload(
                s.inRaid, s.difficulty, s.erosion, s.erosionLevel, EdenConfig.EROSION_MAX.get(), timeLeft,
                cfg.particleColor(), cfg.particleScale(), cfg.particleChance(), cfg.particleDensity(), cfg.particleSpeed(),
                new ArrayList<>(s.revealedAffixes), s.affixes.size() - s.revealedAffixes.size(), hasExtract,
                extract != null ? extract.getX() : 0, extract != null ? extract.getY() : 0,
                extract != null ? extract.getZ() : 0));
    }
}
