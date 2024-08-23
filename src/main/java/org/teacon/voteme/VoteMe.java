package org.teacon.voteme;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.ParametersAreNonnullByDefault;

@Mod("voteme")
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class VoteMe {
    public static final Logger LOGGER = LogManager.getLogger(VoteMe.class);

    public static VoteMeConfig CONFIG;

    public VoteMe(ModContainer modContainer) {
        Pair<VoteMeConfig, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(VoteMeConfig::new);
        modContainer.registerConfig(ModConfig.Type.SERVER, pair.getRight());
        CONFIG = pair.getLeft();
    }
}
