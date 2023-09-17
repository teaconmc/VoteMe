package org.teacon.voteme;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraftforge.common.ForgeConfigSpec;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class VoteMeConfig {
    public final ForgeConfigSpec.ConfigValue<String> REDIS_ATTACH_URI;

    public VoteMeConfig(ForgeConfigSpec.Builder builder) {
        REDIS_ATTACH_URI = builder
                .comment(
                        "Redis attach uri (example: redis://password@localhost:6379/0), env substitution supported",
                        "Set to empty (after env variables resolved) to disable redis attaching")
                .define("redis_attach_uri", "${VOTEME_REDIS_ATTACH_URI:-}");
    }
}
