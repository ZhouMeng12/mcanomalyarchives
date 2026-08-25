# Purplehand 双段攻击 + AOE 范围伤害

## 概述
Purplehand 武器近战攻击时，在原有伤害基础上追加第二次伤害。第二次伤害无视无敌帧，并对目标周围 3 格半径内的所有生物造成等额范围伤害。

## 参数
| 参数 | 值 |
|------|-----|
| AOE 半径 | 3 格 |
| 第二次伤害 | 11（与武器基础伤害相同） |
| 无敌帧策略 | 每次 `hurt()` 前设置 `invulnerableTime = 0` |
| 防递归 | 通过 `ATTACK_TIME` DataComponent 标记已处理，递归调用直接 return |

## 实现
只修改 `PurplehandItem.java` 中的 `hurtEnemy()` 方法。

### 流程
1. 检查 `ATTACK_TIME` 是否已存在 → 是则直接 return（防递归）
2. `super.hurtEnemy()` — 原版第一次伤害
3. 写入 `ATTACK_TIME` 时间戳标记
4. 服务端：AABB 扫描 target 周围 3 格所有 LivingEntity
   - 排除 attacker 自身
   - 排除已死亡实体
5. 对每个实体：`invulnerableTime = 0` → `hurt(mobAttack, 11)`

### 递归防护
内部 `hurt()` 会再次触发 `hurtEnemy()`，但此时 `ATTACK_TIME` 已存在，步骤 1 直接跳过。
