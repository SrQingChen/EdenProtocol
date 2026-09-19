package com.srqingchen.eden;

import com.mojang.logging.LogUtils;
import com.srqingchen.eden.command.EdenCommands;
import com.srqingchen.eden.dimension.DimensionManager;
import com.srqingchen.eden.item.EdenTooltips;
import com.srqingchen.eden.network.EdenNetwork;
import com.srqingchen.eden.registry.EdenAttachments;
import com.srqingchen.eden.registry.EdenBlockEntities;
import com.srqingchen.eden.registry.EdenBlocks;
import com.srqingchen.eden.registry.EdenCreativeTabs;
import com.srqingchen.eden.registry.EdenDataComponents;
import com.srqingchen.eden.registry.EdenEffects;
import com.srqingchen.eden.registry.EdenItems;
import com.srqingchen.eden.system.AffixSystem;
import com.srqingchen.eden.system.CrewScaler;
import com.srqingchen.eden.system.CurseCardSystem;
import com.srqingchen.eden.system.DragonHunt;
import com.srqingchen.eden.system.EcologySystem;
import com.srqingchen.eden.system.ErosionSystem;
import com.srqingchen.eden.system.ExtractionClimaxSystem;
import com.srqingchen.eden.system.FailureRetention;
import com.srqingchen.eden.system.MarketSystem;
import com.srqingchen.eden.system.OasisSystem;
import com.srqingchen.eden.system.PollutionCoreSystem;
import com.srqingchen.eden.system.RaidGambitSystem;
import com.srqingchen.eden.system.ReviveSystem;
import com.srqingchen.eden.system.TipsSystem;
import com.srqingchen.eden.system.TriggerCards;
import com.srqingchen.eden.talent.TalentMechanics;
import com.srqingchen.eden.talent.TalentSystem;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * 《净土协议 / EDEN PROTOCOL》 main mod class.
 * <p>Cooperative extraction-raider: teams delve into a taint-polluted world, survive the erosion
 * system, extract via a return pod, and convert salvage into supply points to advance the shared
 * "Duststar" (尘星) purification campaign.
 * <p>Each content class owns its own DeferredRegister; referencing it here forces static content to
 * load, then we subscribe every register to the mod event bus.
 */
@Mod(EdenProtocol.MODID)
public class EdenProtocol {
    public static final String MODID = "eden";
    public static final Logger LOGGER = LogUtils.getLogger();

    public EdenProtocol(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        // MOD-bus events
        modEventBus.addListener(EdenNetwork::registerPayloads);

        // Subscribe DeferredRegisters (referencing each class loads its static registry content).
        EdenBlocks.BLOCKS.register(modEventBus);
        EdenBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        EdenItems.ITEMS.register(modEventBus);
        EdenDataComponents.DATA_COMPONENTS.register(modEventBus);
        EdenEffects.MOB_EFFECTS.register(modEventBus);
        EdenAttachments.ATTACHMENT_TYPES.register(modEventBus);
        EdenCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        com.srqingchen.eden.registry.EdenParticles.PARTICLES.register(modEventBus);
        com.srqingchen.eden.registry.EdenSounds.SOUNDS.register(modEventBus);
        // Custom loot conditions for the chest-injection system (eden:raid_dimension / eden:raid_difficulty).
        com.srqingchen.eden.system.LootInjectionSystem.Conditions.LOOT_CONDITIONS.register(modEventBus);

        // Game-bus listeners for game (NeoForge) events, wired explicitly via addListener(method refs).
        // NOTE: do NOT call NeoForge.EVENT_BUS.register(this) here - this class has no @SubscribeEvent
        // methods, and the bus throws IllegalArgumentException when registering such an object.
        NeoForge.EVENT_BUS.addListener(EdenCommands::registerCommands);
        NeoForge.EVENT_BUS.addListener(ErosionSystem::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(ErosionSystem::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(ErosionSystem::onIncomingDamage);   // out-of-combat regen: track last damage
        NeoForge.EVENT_BUS.addListener(AffixSystem::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(DimensionManager::onPlayerLogin);
        // World features (§3/§10): oasis ambience, pollution cores (proximity wake + break rewards + guard drops).
        NeoForge.EVENT_BUS.addListener(OasisSystem::onServerTick);
        NeoForge.EVENT_BUS.addListener(PollutionCoreSystem::onServerTick);
        NeoForge.EVENT_BUS.addListener(PollutionCoreSystem::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(PollutionCoreSystem::onLivingDrops);
        // End expedition apex objective (§2 打龙): polluted dragon bounty + campaign linkage.
        NeoForge.EVENT_BUS.addListener(DragonHunt::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(DragonHunt::onLivingDrops);
        // Crew scaling (§17): monsters in a raid world scale with the live crew size.
        NeoForge.EVENT_BUS.addListener(CrewScaler::onEntityJoinLevel);
        // Curse-card actives (§19.1/§19.2): ticks run with everyone else; damage/death hooks run HIGH so
        // ember invulnerability and glass->ember conversion resolve BEFORE the revive system's downed logic.
        NeoForge.EVENT_BUS.addListener(CurseCardSystem::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(CurseCardSystem::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, CurseCardSystem::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, CurseCardSystem::onDeathFinal);
        NeoForge.EVENT_BUS.addListener(CurseCardSystem::onServerTick);
        NeoForge.EVENT_BUS.addListener(CurseCardSystem::onChangedDimension);
        NeoForge.EVENT_BUS.addListener(CurseCardSystem::onLogout);
        // Daily market fluctuation + shortage good (§19.3), locked while a crew is in the raid world.
        NeoForge.EVENT_BUS.addListener(MarketSystem::onServerTick);
        // Periodic "did you know" guidance tips (§1 引导): slow, jittered, colour-coded chat lines.
        NeoForge.EVENT_BUS.addListener(TipsSystem::onServerTick);
        NeoForge.EVENT_BUS.addListener(TipsSystem::onLogin);
        NeoForge.EVENT_BUS.addListener(TipsSystem::onServerStopped);
        // Patchouli guide book handout (soft dependency): once per world on first join.
        NeoForge.EVENT_BUS.addListener(com.srqingchen.eden.system.EdenGuide::onLogin);
        // Event-triggered cinematics (v3): first join + first raid entry (+ season API).
        NeoForge.EVENT_BUS.addListener(com.srqingchen.eden.system.CinematicSystem::onLogin);
        NeoForge.EVENT_BUS.addListener(com.srqingchen.eden.system.CinematicSystem::onChangedDimension);
        // Season finale (批 D): arena flow (countdown/phases/cleanup), boss death -> ceremony,
        // arena deaths respawn home, and the ceremony beat scheduler.
        NeoForge.EVENT_BUS.addListener(com.srqingchen.eden.system.FinaleSystem::onServerTick);
        NeoForge.EVENT_BUS.addListener(com.srqingchen.eden.system.FinaleSystem::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(com.srqingchen.eden.system.FinaleSystem::onRespawn);
        NeoForge.EVENT_BUS.addListener(com.srqingchen.eden.system.FinaleCeremony::onServerTick);
        // Season & expedition contracts (S1 矿洞季, batch A): goal tracking + logout cleanup.
        NeoForge.EVENT_BUS.addListener(com.srqingchen.eden.season.SeasonTracker::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(com.srqingchen.eden.season.SeasonTracker::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(com.srqingchen.eden.season.SeasonTracker::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(com.srqingchen.eden.season.SeasonTracker::onLogout);
        // §19.5 gamble items: hunter spawn scheduling + bounty drops, relic challenge feats.
        NeoForge.EVENT_BUS.addListener(RaidGambitSystem::onServerTick);
        NeoForge.EVENT_BUS.addListener(RaidGambitSystem::onLivingDrops);
        NeoForge.EVENT_BUS.addListener(RaidGambitSystem::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(RaidGambitSystem::onLogout);
        // Trigger cards (leech / ember / mending / aegis) + remaining talent mechanics (field medic,
        // pollution resist, jump, slayer, break speed, fall reduction, once-per-raid totems).
        NeoForge.EVENT_BUS.addListener(TriggerCards::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(TriggerCards::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(TriggerCards::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(TalentMechanics::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(TalentMechanics::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(TalentMechanics::onBreakSpeed);
        NeoForge.EVENT_BUS.addListener(TalentMechanics::onLivingFall);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, TalentMechanics::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(TalentMechanics::onLogout);
        // Extraction climax: the life-support field must run BEFORE ReviveSystem, so a registered crew member's
        // first lethal blow is negated by the pod (energy cost) instead of dropping them into the downed state.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ExtractionClimaxSystem::onDeath);
        NeoForge.EVENT_BUS.addListener(ExtractionClimaxSystem::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(ReviveSystem::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(ReviveSystem::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(ReviveSystem::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(ReviveSystem::onRespawn);
        NeoForge.EVENT_BUS.addListener(EdenTooltips::onTooltip);
        NeoForge.EVENT_BUS.addListener(FailureRetention::onLivingDrops);
        // v2 浊潮生态: lair wake + acid rain tick, boss kill/drop rewards.
        NeoForge.EVENT_BUS.addListener(EcologySystem::onServerTick);
        NeoForge.EVENT_BUS.addListener(EcologySystem::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(EcologySystem::onLivingDrops);
        // Talent system (P2): transient attribute modifiers do not persist, so re-apply them on login/respawn/dimension
        // change; login also pushes the talent snapshot to the client (drives ClientTalentData + the talent screen).
        NeoForge.EVENT_BUS.addListener(TalentSystem::onLogin);
        NeoForge.EVENT_BUS.addListener(TalentSystem::onRespawn);
        NeoForge.EVENT_BUS.addListener(TalentSystem::onChangedDimension);
        // Chest-loot injection (§12): injects a per-difficulty pool into every matching loot table as it
        // loads (vanilla + mods + datapacks); after boot it re-applies the saved config if it differs.
        NeoForge.EVENT_BUS.addListener(com.srqingchen.eden.system.LootInjectionSystem::onLootTableLoad);
        NeoForge.EVENT_BUS.addListener(com.srqingchen.eden.system.LootInjectionSystem::onServerStarted);
        // S1 矿洞季 modifier package (batch B): underground spawn pressure + cave stock swaps, the
        // collapse event, and the two mechanical cave affixes (幽暗菌毯 / 矿脉共鸣).
        // 批 D: season behaviour now routes through the season registry — S0's definition carries
        // the cave modifiers and fragment seams; season add-on mods register their own definitions.
        com.srqingchen.eden.season.Seasons.register(new com.srqingchen.eden.season.S0CaveSeason());
        NeoForge.EVENT_BUS.addListener(com.srqingchen.eden.season.SeasonHooks::onServerTick);
        NeoForge.EVENT_BUS.addListener(com.srqingchen.eden.season.SeasonHooks::onEntityJoinLevel);
        NeoForge.EVENT_BUS.addListener(com.srqingchen.eden.season.SeasonHooks::onBlockBreak);
        // Fragment pools are injected at loot-table load (fires for every table, any season).
        NeoForge.EVENT_BUS.addListener(com.srqingchen.eden.system.FragmentSystem::onLootTableLoad);

        modContainer.registerConfig(ModConfig.Type.COMMON, EdenConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, EdenClientConfig.SPEC);

        LOGGER.info("[EdenProtocol] initializing (modid={})", MODID);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("[EdenProtocol] common setup complete");
    }
}
