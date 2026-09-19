package net.mcreator.mcanomalyarchives.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * 红伪云（UO-001 衍生个体）。
 *
 * <p>来源只有一个：**普通小伪云碰到虞美人**时把那朵花的异常吸走、自己变红
 * （见 {@code SmallStangeCloudEntity.absorbPoppyAndTurnRed}）。所以它不从传送门出现，
 * 也没有自然生成 —— 换来的能力是"**盯着它看会上瘾**"：
 * 那条判定接在既有的虞美人上瘾系统里（{@code events/PlayerLookAtCornPoppyListener}
 * 同时认虞美人与红伪云），于是上瘾的整套都照旧生效：
 * 盯满 10 秒上瘾 → 每 10 秒升一级 → 10 级定身、再盯 10 秒丧命；移开视线转为悲伤。
 *
 * <p>其余行为与普通小伪云一致（同一个模型与尺寸、直线匀速、60 秒寿命、伤害盒与染水）——
 * 只把 {@link #canAbsorbPoppy()} 关掉：否则它再碰到下一朵虞美人就会变成第二只红伪云，
 * 一朵花换一只云，没完没了。
 */
public class RedStangeCloudEntity extends SmallStangeCloudEntity {

	public RedStangeCloudEntity(EntityType<? extends RedStangeCloudEntity> type, Level world) {
		super(type, world);
	}

	@Override
	protected boolean canAbsorbPoppy() {
		return false;
	}
}
