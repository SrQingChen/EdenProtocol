package com.srqingchen.eden.season;

import net.minecraft.resources.Identifier;

/**
 * A season's finale spec (批 D): the arena challenge (a void-dimension colosseum and a
 * multi-phase boss) followed by the ending ceremony. Carried by {@link SeasonDefinition#finale()}
 * so each season mod authors its own ending without engine changes.
 *
 * @param bossEntity   base entity type of the finale boss (stats/phases come from the
 *                     entity_modifier profile + Eden-side phase scripting)
 * @param bossProfile  entity_modifier profile name; Eden generates a default profile JSON under
 *                     {@code config/entity_modifier/profiles/} when missing, so a fresh install
 *                     works out of the box and server admins can retune the boss visually
 * @param bossNameKey  display-name lang key of the boss
 * @param arenaRadius  arena radius in blocks (code-generated, ark-style)
 * @param videoSlot    optional cinematic asset id for the ceremony's video beat (".ecine";
 *                     absent assets auto-skip, the text+sound staging carries the ceremony)
 */
public record SeasonFinale(Identifier bossEntity, String bossProfile, String bossNameKey,
                           int arenaRadius, String videoSlot) {
}
