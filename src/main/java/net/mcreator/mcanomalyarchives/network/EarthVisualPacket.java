package net.mcreator.mcanomalyarchives.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.client.earth.EarthClient;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = McanomalyarchivesMod.MODID)
public record EarthVisualPacket(ResourceLocation dimension, BlockPos center, int age, boolean rewind, boolean clearing) implements CustomPacketPayload {
    public EarthVisualPacket(ResourceLocation dimension, BlockPos center, int age, boolean rewind) {
        this(dimension, center, age, rewind, false);
    }
    public static final Type<EarthVisualPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "earth_visual"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EarthVisualPacket> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeResourceLocation(p.dimension); buf.writeBlockPos(p.center); buf.writeVarInt(p.age); buf.writeBoolean(p.rewind); buf.writeBoolean(p.clearing);
    }, buf -> {
        var dimension = buf.readResourceLocation(); var center = buf.readBlockPos(); int age = buf.readVarInt();
        if (age < -1 || age > 1100) throw new IllegalArgumentException("Invalid earth stage");
        return new EarthVisualPacket(dimension, center, age, buf.readBoolean(), buf.readBoolean());
    });
    @Override public Type<EarthVisualPacket> type() { return TYPE; }
    public static void handle(EarthVisualPacket p, IPayloadContext context) {
        if (context.flow() == net.minecraft.network.protocol.PacketFlow.CLIENTBOUND) context.enqueueWork(() -> EarthClient.accept(p));
    }
    @SubscribeEvent public static void register(FMLCommonSetupEvent event) {
        McanomalyarchivesMod.addNetworkMessage(TYPE, CODEC, EarthVisualPacket::handle);
    }
}
