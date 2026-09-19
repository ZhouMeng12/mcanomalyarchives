# -*- coding: utf-8 -*-
"""收尾：把"看红伪云"的识别改成按变种标记过滤，并清掉最后的独立实体残留。

- PlayerLookAtCornPoppyListener：RedStangeCloudEntity → SmallStangeCloudEntity + isRed() 过滤
- McanomalyarchivesModTabs：创造栏里那行刷怪蛋（MCreator 重生成后缩进变了，正则没咬到）
- 渲染器：SmallStangeCloudRenderer 按 isRed() 换贴图（红变种用 cloud_red.png）
"""
import io
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
os.chdir(ROOT)


def read(p):
    return io.open(p, encoding="utf-8").read()


def write(p, t):
    io.open(p, "w", encoding="utf-8", newline="").write(t)


# ---- 1) 上瘾识别改成按标记过滤 ----
p = "src/main/java/net/mcreator/mcanomalyarchives/events/PlayerLookAtCornPoppyListener.java"
s = read(p)
s = s.replace("import net.mcreator.mcanomalyarchives.entity.RedStangeCloudEntity;",
              "import net.mcreator.mcanomalyarchives.entity.SmallStangeCloudEntity;")
s = s.replace("""        RedStangeCloudEntity cloud = findRedCloudInView(player.level(), player.getEyePosition(1f), player.getViewVector(1f));""",
              """        SmallStangeCloudEntity cloud = findRedCloudInView(player.level(), player.getEyePosition(1f), player.getViewVector(1f));""")
s = s.replace("""        RedStangeCloudEntity cloud = findRedCloudInView(entity.level(), entity.getEyePosition(1f), entity.getViewVector(1f));""",
              """        SmallStangeCloudEntity cloud = findRedCloudInView(entity.level(), entity.getEyePosition(1f), entity.getViewVector(1f));""")
s = s.replace("""    /** 视线锥里最近的一只红伪云（够近 + 夹角够小）。 */
    private static RedStangeCloudEntity findRedCloudInView(Level level, Vec3 eye, Vec3 viewVector) {
        Vec3 look = viewVector.normalize();
        RedStangeCloudEntity best = null;
        double bestDistSqr = Double.MAX_VALUE;
        for (RedStangeCloudEntity cloud : level.getEntitiesOfClass(RedStangeCloudEntity.class,
                new net.minecraft.world.phys.AABB(eye, eye).inflate(CLOUD_VIEW_RANGE))) {""",
              """    /**
     * 视线锥里最近的一只**红伪云**（够近 + 夹角够小）。
     *
     * 红伪云不是独立生物：它是 SmallStangeCloudEntity 的变种，靠 isRed() 这个同步标记区分
     * （作者 2026-09-18：红伪云作为伪云的一个变种，不要再加一个生物）。
     */
    private static SmallStangeCloudEntity findRedCloudInView(Level level, Vec3 eye, Vec3 viewVector) {
        Vec3 look = viewVector.normalize();
        SmallStangeCloudEntity best = null;
        double bestDistSqr = Double.MAX_VALUE;
        for (SmallStangeCloudEntity cloud : level.getEntitiesOfClass(SmallStangeCloudEntity.class,
                new net.minecraft.world.phys.AABB(eye, eye).inflate(CLOUD_VIEW_RANGE))) {
            if (!cloud.isRed()) {
                continue; // 白的/黑的都不算
            }""")
write(p, s)
print("上瘾识别已改成按 isRed() 过滤；残留 RedStangeCloudEntity:", "RedStangeCloudEntity" in s)

# ---- 2) 创造栏残留 ----
p = "src/main/java/net/mcreator/mcanomalyarchives/init/McanomalyarchivesModTabs.java"
s = read(p)
s2, n = re.subn(r'\n\t+tabData\.accept\(McanomalyarchivesModItems\.RED_STANGE_CLOUD_SPAWN_EGG\.get\(\)\);', "", s)
write(p, s2)
print("创造栏删掉 %d 行" % n)

# ---- 3) 渲染器按标记换贴图 ----
p = "src/main/java/net/mcreator/mcanomalyarchives/client/renderer/SmallStangeCloudRenderer.java"
s = read(p)
old = '''	private final ResourceLocation entityTexture = ResourceLocation.parse("mcanomalyarchives:textures/entities/texture.png");'''
new = '''	private final ResourceLocation entityTexture = ResourceLocation.parse("mcanomalyarchives:textures/entities/texture.png");
	/**
	 * 红伪云用的是**同一个模型、同一个生物**，只是换了这张红贴图
	 * （作者 2026-09-18：红伪云作为伪云的一个变种，不要再加一个生物）。
	 * 颜色是虞美人贴图里的主色 #E02020，见 tools/cloud/make_red_texture.py。
	 */
	private final ResourceLocation redTexture = ResourceLocation.parse("mcanomalyarchives:textures/entities/cloud_red.png");'''
assert old in s
s = s.replace(old, new, 1)
old2 = '''	@Override
	public ResourceLocation getTextureLocation(SmallStangeCloudEntity entity) {
		return entityTexture;
	}'''
new2 = '''	@Override
	public ResourceLocation getTextureLocation(SmallStangeCloudEntity entity) {
		return entity.isRed() ? redTexture : entityTexture;
	}'''
assert old2 in s, "找不到 getTextureLocation"
s = s.replace(old2, new2, 1)
write(p, s)
print("渲染器已按 isRed() 换贴图")
