package org.teacon.voteme;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.ParametersAreNonnullByDefault;

@Mod("voteme")
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class VoteMe {
    public static final Logger LOGGER = LogManager.getLogger(VoteMe.class);

    public static final VoteMeConfig CONFIG;

    static {
        Pair<VoteMeConfig, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(VoteMeConfig::new);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, pair.getRight());
        CONFIG = pair.getLeft();
    }
}
