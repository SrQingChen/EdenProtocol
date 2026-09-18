package com.srqingchen.eden.system;

import com.srqingchen.eden.EdenProtocol;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generates the season-finale boss's default entity_modifier profile (批 D). Stats are Eden's
 * own (crew-scaled at spawn); the profile carries the COMBAT EFFECT layer the user's entity
 * modifier provides out of the box - HP-threshold phase escalation, thorns, lifesteal, low-HP
 * frenzy - so the fight gets richer whenever a newer entity_modifier is installed, and server
 * admins can retune the boss visually in the modifier GUI without touching Eden.
 * <p>Written once to {@code config/entity_modifier/profiles/<name>.json} on demand; an existing
 * file is never overwritten (admin edits win). Effects unknown to an older entity_modifier are
 * ignored by it - Eden's own phase script guarantees the fight regardless.
 */
final class FinaleProfile {
    private FinaleProfile() {}

    /** Write the default finale-boss profile unless one already exists. */
    static void ensureDefault(String profileName) {
        try {
            Path dir = Paths.get("config", "entity_modifier", "profiles");
            Path file = dir.resolve(profileName + ".json");
            if (Files.exists(file)) {
                return;
            }
            Files.createDirectories(dir);
            String json = """
                    {
                      "modifications": {
                        "minecraft:warden": {
                          "fireImmune": true,
                          "persistenceRequired": true,
                          "advancedEffects": {
                            "phase_transition": {
                              "enabled": true,
                              "params": { "thresholds": [0.66, 0.33], "attack_bonus": 0.15, "speed_bonus": 0.2 }
                            },
                            "thorns": {
                              "enabled": true,
                              "params": { "ratio": 0.15 }
                            },
                            "lifesteal": {
                              "enabled": true,
                              "params": { "ratio": 0.08, "fixed": 0.0 }
                            },
                            "low_hp_damage_boost": {
                              "enabled": true,
                              "params": { "multiplier": 0.5 }
                            },
                            "low_hp_speed_boost": {
                              "enabled": true,
                              "params": { "multiplier": 0.4 }
                            },
                            "damage_reduction": {
                              "enabled": true,
                              "params": { "ratio": 0.2 }
                            }
                          }
                        }
                      },
                      "globalMultipliers": {
                        "multipliers": {},
                        "fluctuations": {},
                        "applyToPlayers": false
                      }
                    }
                    """;
            try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                    Files.newOutputStream(file), StandardCharsets.UTF_8))) {
                writer.write(json);
            }
            EdenProtocol.LOGGER.info("[Eden] generated default finale-boss profile '{}'", profileName);
        } catch (Exception e) {
            EdenProtocol.LOGGER.warn("[Eden] could not generate finale-boss profile '{}': {}",
                    profileName, e.getMessage());
        }
    }
}
