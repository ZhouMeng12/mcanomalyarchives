package net.mcreator.mcanomalyarchives.anomaly.purple;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.network.AdvanceToPhase2Packet;
import net.mcreator.mcanomalyarchives.network.AdvanceToPhase4Packet;
import net.mcreator.mcanomalyarchives.network.GivePurplehandPacket;
import net.mcreator.mcanomalyarchives.network.OpenPurpleGuiPacket;
import net.mcreator.mcanomalyarchives.network.PetPurpleDogPacket;
import net.mcreator.mcanomalyarchives.network.PettingCompletePacket;
import net.mcreator.mcanomalyarchives.network.PurpleStoryEndedPacket;
import net.mcreator.mcanomalyarchives.network.RemovePurpleMonsterPacket;
import net.mcreator.mcanomalyarchives.network.StartPettingClientPacket;
import net.mcreator.mcanomalyarchives.network.TransitionToPhase2Packet;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** 紫怪模块拥有自己的通信方向和协议版本，其他模组功能的网络注册不受影响。 */
@EventBusSubscriber(modid = McanomalyarchivesMod.MODID)
public final class PurpleStoryNetworking {
	private PurpleStoryNetworking() {
	}

	@SubscribeEvent
	public static void register(RegisterPayloadHandlersEvent event) {
		// 与原双向注册区分；服务端和客户端必须同时使用这一轮构建，旧载荷字段/注册名不变。
		PayloadRegistrar registrar = event.registrar("purple-story-2");
		registrar.playToServer(AdvanceToPhase2Packet.TYPE, AdvanceToPhase2Packet.STREAM_CODEC, AdvanceToPhase2Packet::handleData);
		registrar.playToServer(TransitionToPhase2Packet.TYPE, TransitionToPhase2Packet.STREAM_CODEC, TransitionToPhase2Packet::handleData);
		registrar.playToServer(AdvanceToPhase4Packet.TYPE, AdvanceToPhase4Packet.STREAM_CODEC, AdvanceToPhase4Packet::handleData);
		registrar.playToServer(PetPurpleDogPacket.TYPE, PetPurpleDogPacket.STREAM_CODEC, PetPurpleDogPacket::handleData);
		registrar.playToServer(PettingCompletePacket.TYPE, PettingCompletePacket.STREAM_CODEC, PettingCompletePacket::handleData);
		registrar.playToServer(GivePurplehandPacket.TYPE, GivePurplehandPacket.STREAM_CODEC, GivePurplehandPacket::handleData);
		registrar.playToServer(RemovePurpleMonsterPacket.TYPE, RemovePurpleMonsterPacket.STREAM_CODEC, RemovePurpleMonsterPacket::handleData);
		registrar.playToClient(OpenPurpleGuiPacket.TYPE, OpenPurpleGuiPacket.STREAM_CODEC, OpenPurpleGuiPacket::handleData);
		registrar.playToClient(StartPettingClientPacket.TYPE, StartPettingClientPacket.STREAM_CODEC, StartPettingClientPacket::handleData);
		registrar.playToClient(PurpleStoryEndedPacket.TYPE, PurpleStoryEndedPacket.STREAM_CODEC, PurpleStoryEndedPacket::handleData);
	}
}
