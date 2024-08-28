package org.teacon.voteme.network;

import com.google.common.primitives.ImmutableIntArray;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.apache.commons.lang3.tuple.Pair;

public class VoteMeStreamUtils {

    public static final StreamCodec<FriendlyByteBuf, ImmutableIntArray> IMMUTABLE_INT_ARRAY = new StreamCodec<>() {
        @Override
        public void encode(FriendlyByteBuf buffer, ImmutableIntArray value) {
            buffer.writeVarIntArray(value.toArray());
        }

        @Override
        public ImmutableIntArray decode(FriendlyByteBuf buffer) {
            return ImmutableIntArray.copyOf(buffer.readVarIntArray());
        }
    };

    public static <BUFFER, T1, T2> StreamCodec<BUFFER, Pair<T1, T2>> pair(
            StreamCodec<? super BUFFER, T1> streamCodec1,
            StreamCodec<? super BUFFER, T2> streamCodec2
    ) {
        return StreamCodec.composite(
                streamCodec1, Pair::getLeft,
                streamCodec2, Pair::getRight,
                Pair::of
        );
    }
}
