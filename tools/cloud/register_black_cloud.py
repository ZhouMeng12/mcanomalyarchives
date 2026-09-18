# -*- coding: utf-8 -*-
"""把黑吸伪云补进"生成区"的四张注册表（MCreator 下次重新生成时会自己产出这些行，
这里只是让当前的树先能编译、能跑；行数写死成与小伪云同款，便于对照）。

插的四处：
  init/McanomalyarchivesModEntities.java         实体注册 + init(event) + 属性
  init/McanomalyarchivesModEntityRenderers.java  渲染器注册
  init/McanomalyarchivesModItems.java            刷怪蛋注册
  init/McanomalyarchivesModTabs.java             创造栏放入

幂等：已经含 BLACK_STANGE_CLOUD 就跳过。
"""
import io
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
os.chdir(ROOT)

E = "src/main/java/net/mcreator/mcanomalyarchives/init/McanomalyarchivesModEntities.java"
R = "src/main/java/net/mcreator/mcanomalyarchives/init/McanomalyarchivesModEntityRenderers.java"
I = "src/main/java/net/mcreator/mcanomalyarchives/init/McanomalyarchivesModItems.java"
T = "src/main/java/net/mcreator/mcanomalyarchives/init/McanomalyarchivesModTabs.java"


def read(p):
    return io.open(p, encoding="utf-8").read()


def write(p, s):
    io.open(p, "w", encoding="utf-8", newline="").write(s)


def insert_after(text, anchor, addition, path):
    assert anchor in text, "找不到锚点 %r（%s）" % (anchor[:60], path)
    assert addition.strip() not in text, "似乎已经插过了 %s" % path
    return text.replace(anchor, anchor + addition, 1)


def insert_before(text, anchor, addition, path):
    assert anchor in text, "找不到锚点 %r（%s）" % (anchor[:60], path)
    assert addition.strip() not in text, "似乎已经插过了 %s" % path
    return text.replace(anchor, addition + anchor, 1)


# ---- 实体 ----
# MCreator 生成的是 `import net.mcreator.mcanomalyarchives.entity.*;`（整包导入），
# 所以不用单独加 import —— 只要这个星号导入还在，BlackStangeCloudEntity 就在作用域里。
s = read(E)
assert "import net.mcreator.mcanomalyarchives.entity.*;" in s, "实体包不再是整包导入，请手工补 import"
s = insert_before(s, "\tpublic static final DeferredHolder<EntityType<?>, EntityType<AnbulaEntity>> ANBULA",
                  '''\tpublic static final DeferredHolder<EntityType<?>, EntityType<BlackStangeCloudEntity>> BLACK_STANGE_CLOUD = register("black_stange_cloud",
\t\t\tEntityType.Builder.<BlackStangeCloudEntity>of(BlackStangeCloudEntity::new, MobCategory.AMBIENT).setShouldReceiveVelocityUpdates(true).setTrackingRange(64).setUpdateInterval(3).fireImmune()

\t\t\t\t\t.sized(4f, 0.67f));
''', E)
s = insert_after(s, "\t\tSmallStangeCloudEntity.init(event);\n", "\t\tBlackStangeCloudEntity.init(event);\n", E)
s = insert_after(s, "\t\tevent.put(SMALL_STANGE_CLOUD.get(), SmallStangeCloudEntity.createAttributes().build());\n",
                 "\t\tevent.put(BLACK_STANGE_CLOUD.get(), BlackStangeCloudEntity.createAttributes().build());\n", E)
write(E, s)
print("OK 实体注册")

# ---- 渲染器 ----
s = read(R)
s = insert_after(s, "event.registerEntityRenderer(McanomalyarchivesModEntities.SMALL_STANGE_CLOUD.get(), SmallStangeCloudRenderer::new);\n",
                 "\t\tevent.registerEntityRenderer(McanomalyarchivesModEntities.BLACK_STANGE_CLOUD.get(), BlackStangeCloudRenderer::new);\n", R)
write(R, s)
print("OK 渲染器注册")

# ---- 刷怪蛋 ----
s = read(I)
s = insert_after(s, "\tpublic static final DeferredItem<Item> SMALL_STANGE_CLOUD_SPAWN_EGG;\n",
                 "\tpublic static final DeferredItem<Item> BLACK_STANGE_CLOUD_SPAWN_EGG;\n", I)
s = insert_after(s, '\t\tSMALL_STANGE_CLOUD_SPAWN_EGG = REGISTRY.register("small_stange_cloud_spawn_egg", () -> new DeferredSpawnEggItem(McanomalyarchivesModEntities.SMALL_STANGE_CLOUD, -1, -1, new Item.Properties()));\n',
                 '\t\tBLACK_STANGE_CLOUD_SPAWN_EGG = REGISTRY.register("black_stange_cloud_spawn_egg", () -> new DeferredSpawnEggItem(McanomalyarchivesModEntities.BLACK_STANGE_CLOUD, -1, -1, new Item.Properties()));\n', I)
write(I, s)
print("OK 刷怪蛋注册")

# ---- 创造栏 ----
s = read(T)
s = insert_after(s, "\t\ttabData.accept(McanomalyarchivesModItems.SMALL_STANGE_CLOUD_SPAWN_EGG.get());\n",
                 "\t\ttabData.accept(McanomalyarchivesModItems.BLACK_STANGE_CLOUD_SPAWN_EGG.get());\n", T)
write(T, s)
print("OK 创造栏")

print("DONE")
