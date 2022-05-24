package org.teacon.voteme;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.ParametersAreNonnullByDefault;

@Mod("voteme")
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class VoteMe {
    public static final Logger LOGGER = LogManager.getLogger(VoteMe.class);
}
