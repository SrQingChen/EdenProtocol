package com.srqingchen.eden.registry;

import com.srqingchen.eden.EdenProtocol;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Cinematic audio slots (v3 沉浸模块). Each EVENT TYPE owns one fixed sound slot whose .ogg lives at
 * {@code assets/eden/sounds/cinematic/<slot>.ogg} - drop the file next to the packed .ecine video and
 * the slot starts working; both are optional assets (nothing ships by default, nothing breaks when
 * absent). Vanilla's sound engine does all the decoding, so the module adds no audio code at all.
 */
public class EdenSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, EdenProtocol.MODID);

    /** First-join intro cinematic (video: eden:cinematics/intro). */
    public static final DeferredHolder<SoundEvent, SoundEvent> CINEMATIC_INTRO =
            SOUNDS.register("cinematic_intro", () -> SoundEvent.createVariableRangeEvent(
                    Identifier("cinematic_intro")));

    /** First raid-dimension entry cinematic (video: eden:cinematics/raid_enter). */
    public static final DeferredHolder<SoundEvent, SoundEvent> CINEMATIC_RAID =
            SOUNDS.register("cinematic_raid", () -> SoundEvent.createVariableRangeEvent(
                    Identifier("cinematic_raid")));

    /** Season-change cinematic (video: eden:cinematics/season_change) - fired by the future season system. */
    public static final DeferredHolder<SoundEvent, SoundEvent> CINEMATIC_SEASON =
            SOUNDS.register("cinematic_season", () -> SoundEvent.createVariableRangeEvent(
                    Identifier("cinematic_season")));

    private static net.minecraft.resources.Identifier Identifier(String path) {
        return net.minecraft.resources.Identifier.fromNamespaceAndPath(EdenProtocol.MODID, path);
    }
}
