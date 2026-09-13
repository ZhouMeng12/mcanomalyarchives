# -*- coding: utf-8 -*-
"""
离线验证剧情解析器：把 StoryLoader 里的默认脚本抠出来，用真正的 Story 解析一遍。
（Story.java 不依赖 Minecraft，只需要把 DialogueLog 换成打印版就能脱离游戏编译运行）
"""
import io, os, re, shutil, subprocess, sys

ROOT = os.path.abspath('.')
JAVA_SRC = os.path.join(ROOT, 'src/main/java/net/mcreator/mcanomalyarchives/dialogue')
OUT = os.path.join(ROOT, 'build/parsertest')
PKG = os.path.join(OUT, 'net/mcreator/mcanomalyarchives/dialogue')
os.makedirs(PKG, exist_ok=True)

# 1) 直接读模组内置剧本（现在是资源的正式来源）
src_story = os.path.join(ROOT, 'src/main/resources/data/mcanomalyarchives/story/svan_codex.txt')
script = io.open(src_story, encoding='utf-8').read()
io.open(os.path.join(OUT, 'default.txt'), 'w', encoding='utf-8', newline='\n').write(script)
print('模组内置剧本 %d 行' % len(script.splitlines()))

# 2) 拷贝真正的 Story.java，并写一个不依赖 slf4j 的 DialogueLog
shutil.copy(os.path.join(JAVA_SRC, 'Story.java'), os.path.join(PKG, 'Story.java'))
io.open(os.path.join(PKG, 'DialogueLog.java'), 'w', encoding='utf-8').write('''package net.mcreator.mcanomalyarchives.dialogue;

public final class DialogueLog {
    private DialogueLog() {}
    public static void info(String m) { System.out.println("[info] " + m); }
    public static void warn(String s, String m) { System.out.println("[warn] " + s + ": " + m); }
    public static void error(String s, String m, Throwable t) { System.out.println("[error] " + s + ": " + m); }
}
''')

# 3) 测试主类
io.open(os.path.join(OUT, 'Main.java'), 'w', encoding='utf-8').write('''import net.mcreator.mcanomalyarchives.dialogue.Story;
import java.nio.file.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        String raw = Files.readString(Paths.get("default.txt"));
        Story story = Story.parse(raw, "svan_codex");
        System.out.println("可用: " + story.isUsable() + "  段落数: " + story.labels().size());
        for (Map.Entry<String, List<Story.Node>> e : story.labels().entrySet()) {
            System.out.println("[" + e.getKey() + "] " + e.getValue().size() + " 个节点");
            for (Story.Node n : e.getValue()) {
                String detail = switch (n.kind()) {
                    case LINE -> "\\"" + n.text() + "\\"" + (n.image() != null ? " 图=" + n.image() : "");
                    case JUMP -> "-> " + n.text();
                    case CHOICE -> "提问:" + n.prompt() + " " + n.options();
                    case ACTION -> n.text();
                    case WAIT -> n.ticks() + " tick";
                    case END -> "结束";
                };
                System.out.println("    " + n.kind() + "  " + detail);
            }
        }
        // 校验所有跳转/选项目标都存在
        int bad = 0;
        for (var e : story.labels().entrySet()) {
            for (Story.Node n : e.getValue()) {
                if (n.kind() == Story.Kind.JUMP && story.nodes(n.text()).isEmpty()) { System.out.println("!! 跳转目标缺失: " + n.text()); bad++; }
                if (n.kind() == Story.Kind.CHOICE) for (Story.Option o : n.options())
                    if (story.nodes(o.target()).isEmpty()) { System.out.println("!! 选项目标缺失: " + o.target()); bad++; }
            }
        }
        System.out.println(bad == 0 ? "跳转校验: 全部通过" : ("跳转校验: " + bad + " 处问题"));
    }
}
''')

# 4) 编译运行（Story.java 里只有 javax.annotation.Nullable 这个外部引用，去掉它即可）
sp = os.path.join(PKG, 'Story.java')
src = io.open(sp, encoding='utf-8').read().replace('import javax.annotation.Nullable;', '').replace('@Nullable', '')
io.open(sp, 'w', encoding='utf-8').write(src)

javac = shutil.which('javac') or r'C:\Program Files\Pylo\MCreator\jdk\bin\javac.exe'
java = shutil.which('java') or r'C:\Program Files\Pylo\MCreator\jdk\bin\java.exe'
r = subprocess.run([javac, '-encoding', 'UTF-8', '-d', OUT,
                    os.path.join(PKG, 'Story.java'), os.path.join(PKG, 'DialogueLog.java'),
                    os.path.join(OUT, 'Main.java')],
                   capture_output=True, text=True, encoding='utf-8', errors='replace')
if r.returncode != 0:
    print('编译失败:'); print(r.stdout); print(r.stderr); sys.exit(1)
r2 = subprocess.run([java, '-Dfile.encoding=UTF-8', '-cp', OUT, 'Main'],
                    cwd=OUT, capture_output=True, text=True, encoding='utf-8', errors='replace')
print(r2.stdout)
if r2.stderr.strip():
    print('stderr:', r2.stderr[:800])
