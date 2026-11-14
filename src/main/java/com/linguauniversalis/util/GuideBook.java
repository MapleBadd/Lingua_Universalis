package com.linguauniversalis.util;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

/**
 * 玩法介绍成书：{@code /lu guide} 发给执行指令的玩家。
 *
 * <p>为什么写成成书而不是聊天提示：玩法信息量偏大（图鉴、誓约、伙伴、宝箱、指令、数据包），
 * 塞进聊天会被刷走；成书可以随时翻、也能放进箱子/书与笔里继续编辑。
 *
 * <p>书页内容用 {@link Component} 拼装（支持颜色与粗体），每页不超过 {@code WrittenBookContent.PAGE_LENGTH}。
 */
public final class GuideBook {

    /** 书的标题与作者。 */
    public static final String TITLE = "万象牧语 · 生存手记";
    public static final String AUTHOR = "释卷者";

    private GuideBook() {
    }

    /** 生成一本全新的引导书（数量 1）。 */
    public static ItemStack create() {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        List<Filterable<Component>> pages = new ArrayList<>();
        for (Component page : pages()) {
            pages.add(Filterable.passThrough(page));
        }
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough(TITLE), AUTHOR, 0, pages, true));
        return book;
    }

    // ---------------------------------------------------------------- 正文

    private static List<Component> pages() {
        List<Component> pages = new ArrayList<>();

        // 1 总览
        pages.add(page(
                h("万象牧语 · 玩法说明"),
                t("这是一个养魔物娘的模组。"),
                t("她们不是宠物，是能谈、能带、能一起过日子的伙伴。"),
                t(""),
                t("出门会遇到她们，观察认识她们，"),
                t("好感够了就成为伙伴，之后可以指挥、一起住。"),
                t(""),
                s("下面是具体操作，一步一步照着做就行。")));

        // 2 遇见
        pages.add(page(
                h("一、遇见"),
                t("猫又：夜里在森林、平原自然生成。"),
                t("她们看到你会警觉，别一上来就打。"),
                t(""),
                t("手里拿食物右键 = 投喂（加好感，看喜好加得更快）。"),
                t("空手右键 = 摸头（好感高才会给摸）。"),
                t("打她们 = 掉好感，还可能被打。")));

        // 3 图鉴
        pages.add(page(
                h("二、图鉴（初稿）"),
                t("开局会发一份「初稿」。"),
                t("拿着它对着魔物娘右键，就能把看到的信息记下来。"),
                t(""),
                t("· 看见一次：记下名字"),
                t("· 继续观察：解锁身份、分类、习性"),
                t("· 被打 / 看她打架：记下战斗和敌意"),
                t("· 投她爱吃的：记下喜好"),
                t(""),
                s("没看到的部分显示 ？？？。")));

        // 4 誊写台
        pages.add(page(
                h("三、誊写台（看图鉴界面）"),
                t("合成：木板/木台阶 + 雕纹书架。"),
                t("放下来，空手右键 = 打开图鉴界面。"),
                t(""),
                t("左栏是物种列表，点开看右侧正文。"),
                t("可以按分类筛选、用拼音搜名字。"),
                t("图鉴是逐存档记录的，换存档不会带过去。")));

        // 5 好感与誓约
        pages.add(page(
                h("四、好感与伙伴"),
                t("好感 0–200。"),
                t("喂爱吃的、摸头、陪着她都会涨；打她、饿着会掉。"),
                t(""),
                t("好感到 100 = 成为伙伴：可以指挥、能睡床、会用宝箱。"),
                t("好感到 200 = 可以誓约（用 /lu bond 或后续仪式）。"),
                t(""),
                s("好感低的野生个体不会听指挥。")));

        // 6 指挥
        pages.add(page(
                h("五、指挥她"),
                t("成为伙伴之后，潜行(Shift) + 空手右键 = 循环切换命令："),
                t(""),
                t("· 跟随：跟着你走"),
                t("· 待机：站原地不动，不做自己的事"),
                t("· 游荡：自由活动（会自己睡觉、跑腿）"),
                t(""),
                t("魔物哨右键 = 切换战斗模式（主动 / 被动）。")));

        // 7 床
        pages.add(page(
                h("六、魔物床"),
                t("合成：任意床 + 七根原木（上面一排空着）。"),
                t(""),
                t("· 中型魔物床 = 中体型（猫又）用"),
                t("· 她自己会找尺寸合适的床睡"),
                t("· 你也能当普通床睡"),
                t(""),
                s("放下的那格是床尾，床头朝你放的方向。")));

        // 8 宝箱
        pages.add(page(
                h("七、伙伴宝箱"),
                t("合成：原木 + 箱子（配方看 JEI）。"),
                t(""),
                t("· 54 格，几只共用同一个"),
                t("· 伙伴会自己把捡到的东西存进去"),
                t("· 箱子上方被实心方块挡住就打不开"),
                t("· 猫又坐在上面时也打不开")));

        // 9 指令
        pages.add(page(
                h("八、常用指令"),
                t("/lu help          命令清单"),
                t("/lu check         我的进度（图鉴/伙伴/誓约）"),
                t("/lu guide         再拿一本这本说明书"),
                t(""),
                t("管理员用："),
                t("/lu codex export  导出图鉴（跨存档用）"),
                t("/lu codex import  导入图鉴"),
                t("/lu selftest      自检"),
                t("/lu species list  数据包物种加载情况")));

        // 10 加内容
        pages.add(page(
                h("九、自己加一只"),
                t("不写代码也能加："),
                t(""),
                t("1. data/<命名空间>/species/<id>.json（物种档案）"),
                t("2. taxonomy 分类学标签"),
                t("3. 模型与贴图（放 GeckoLib 的 geo/json）"),
                t(""),
                s("放好文件执行 /reload 即可。")));

        // 11 结尾
        pages.add(page(
                h("最后"),
                t("这版还是测试版，功能会变。"),
                t("完整玩法说明书后面单独做。"),
                t(""),
                t("遇到问题就在存档里 /lu selftest 看一眼。")));

        return pages;
    }

    // ---------------------------------------------------------------- 构造小工具

    /** 一页：第一行是标题，其余按顺序排。 */
    private static Component page(Component... lines) {
        net.minecraft.network.chat.MutableComponent text = net.minecraft.network.chat.Component.empty().copy();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                text.append("\n");
            }
            text.append(lines[i]);
        }
        return text;
    }

    /** 标题行（加粗）。 */
    private static Component h(String text) {
        return net.minecraft.network.chat.Component.literal(text)
                .withStyle(net.minecraft.ChatFormatting.BOLD);
    }

    /** 正文行。 */
    private static Component t(String text) {
        return net.minecraft.network.chat.Component.literal(text);
    }

    /** 强调行（灰色）。 */
    private static Component s(String text) {
        return net.minecraft.network.chat.Component.literal(text)
                .withStyle(net.minecraft.ChatFormatting.GRAY);
    }
}
