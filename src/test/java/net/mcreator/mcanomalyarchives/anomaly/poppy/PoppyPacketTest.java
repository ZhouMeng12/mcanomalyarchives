package net.mcreator.mcanomalyarchives.anomaly.poppy;

import io.netty.buffer.Unpooled;
import net.mcreator.mcanomalyarchives.network.PoppyAddictionPacket;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PoppyPacketTest {
    @Test void theVisualRouteSurvivesTheActualNetworkCodecIncludingInactiveState() {
        for(int route:new int[]{0,1,-1}) {
            var packet=new PoppyAddictionPacket(1200,route,600,14.5,-61,8.5,-1,Long.MIN_VALUE);
            var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
            try {
                PoppyAddictionPacket.STREAM_CODEC.encode(buffer,packet);
                assertEquals(packet,PoppyAddictionPacket.STREAM_CODEC.decode(buffer));
                assertFalse(buffer.isReadable());
            } finally { buffer.release(); }
        }
    }
}
