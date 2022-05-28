package org.teacon.voteme;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraftforge.common.ForgeConfigSpec;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class VoteMeConfig {
    public final ForgeConfigSpec.ConfigValue<Integer> HTTP_SERVER_PORT;
    public final ForgeConfigSpec.ConfigValue<String> REDIS_ATTACH_URI;

    public VoteMeConfig(ForgeConfigSpec.Builder builder) {
        HTTP_SERVER_PORT = builder
                .comment("HTTP server port", "Set to 0 to disable the server")
                .defineInRange("http_server_port", 19970, 0, 65535);
        REDIS_ATTACH_URI = builder
                .comment("Redis attach uri (example: redis://password@localhost:6379/0)", "Set to empty to disable redis attaching")
                .define("redis_attach_uri", "");
    }
}
