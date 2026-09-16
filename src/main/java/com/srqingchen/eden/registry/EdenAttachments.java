package com.srqingchen.eden.registry;

import com.srqingchen.eden.EdenProtocol;
import com.srqingchen.eden.attachment.RaidState;
import com.srqingchen.eden.attachment.TalentData;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Data Attachments. Raid state is serialized with the player so a mid-raid relog / server restart resumes
 * the expedition (erosion, difficulty, collapse timer) instead of silently resetting it.
 */
public class EdenAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, EdenProtocol.MODID);

    /** Per-player raid state: erosion value/level, timer, difficulty. Persisted via {@link RaidState#CODEC}. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<RaidState>> RAID_STATE =
            ATTACHMENT_TYPES.register("raid_state",
                    () -> AttachmentType.builder(RaidState::new).serialize(RaidState.CODEC).build());

    /** Per-player class / talent meta-progression: talent points, chosen class, unlocked nodes per tree. Persisted via {@link TalentData#CODEC}. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<TalentData>> TALENT_DATA =
            ATTACHMENT_TYPES.register("talent_data",
                    () -> AttachmentType.builder(TalentData::new).serialize(TalentData.CODEC).copyOnDeath().build());
}
