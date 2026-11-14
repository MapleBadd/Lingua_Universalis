package com.linguauniversalis.client;

import com.linguauniversalis.core.encyclopedia.CodexEntry;
import com.linguauniversalis.core.encyclopedia.Encyclopedia;
import com.linguauniversalis.core.species.SpeciesProfile;
import com.linguauniversalis.core.species.SpeciesRegistry;
import com.linguauniversalis.menu.CodexMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 图鉴界面（P1 骨架）：左目录 + 右正文 + 右上模型预览框。
 *
 * <p>逻辑尺寸 **320 × 188**（MC 的 GUI 逻辑像素；原版箱子是 176 × 166）。
 *
 * <p>P1 已完成：双栏排布、物种列表（`●` 全解锁 / `◐` 部分 / `○` 未记录）、
 * 分类学标签行、7 个书签页签、行渲染、未解锁显示 `？`。
 * <b>待接</b>：右上角的 3D 模型预览（现在是占位框）、搜索与标签筛选（P2）、
 * 滚动与键盘导航（P2）。
 */
/**
 * 图鉴界面。
 *
 * <p>注意它**继承的是普通 {@code Screen}**（不是 {@code AbstractContainerScreen}）：
 * 容器菜单只用来传数据，但继承容器界面会让 JEI 之类的查看器把配方覆盖层盖到图鉴上。
 * 作为普通界面，JEI 不会再往上贴覆盖层；尺寸与居中由本类自己算（{@link #leftPos}/{@link #topPos}）。
 */
public class CodexScreen extends net.minecraft.client.gui.screens.Screen
        implements net.minecraft.client.gui.screens.inventory.MenuAccess<CodexMenu> {
    // ---------------------------------------------------------------- 布局常量（逻辑像素）
    private static final int PANEL_W = 320;
    private static final int PANEL_H = 188;
    private static final int HEADER_H = 16;
    private static final int FOOTER_H = 12;
    private static final int LEFT_W = 128;
    private static final int GAP = 6;
    /** 预览框边长（逻辑像素）：草图上是 96，实测太大，缩到 1/4 面积 = 48。 */
    private static final int PREVIEW_SIZE = 48;
    private static final int ROW_H = 11;
    /** 分类学标签 chip 的高度。 */
    private static final int CHIP_H = 11;
    private static final int TAB_H = 13;

    // 配色（书页纸感）
    private static final int C_PANEL = 0xFF3A2F24;
    private static final int C_PAPER = 0xFFECE3CE;
    private static final int C_PAPER_DARK = 0xFFE0D5BD;
    private static final int C_INK = 0xFF3A2C1E;
    private static final int C_INK_SOFT = 0xFF786852;
    private static final int C_ACCENT = 0xFF8C603A;
    private static final int C_LOCK = 0xFFA89E8C;
    private static final int C_SELECT = 0xFFD8CAB0;
    /** 分类学标签 chip 的底色（比纸略深，像贴上去的小纸条）。 */
    private static final int C_CHIP = 0xFFE7DCC2;
    /** 筛选 chip 选中态（比普通 chip 更暖）。 */
    private static final int C_CHIP_ON = 0xFFD9B98A;
    /** 左页可视高度（面板高 - 标题栏 - 底栏 - 上下留白）。 */
    private static final int LEFT_PAGE_HEIGHT = PANEL_H - HEADER_H - FOOTER_H - 8;
    private static final int C_HEADER = 0xFF56402C;

    private static final int TEXT_TITLE = 0xFFF6EEDE;
    private static final int TEXT_HEADER_SUB = 0xFFDCCEB6;

    /**
     * 页签 = **分组**（一节展示不下 7 个页签，所以合并成 3 页）：
     * <ul>
     *   <li>基础：身份 + 分类 + 战斗 + 喜好（名字、分类学、血量/速度/护甲/伤害、喜爱食物）</li>
     *   <li>行为：习性 + 敌意（活动时间、巢穴、领地、敌意规则）</li>
     *   <li>能力：能力（技能条目）</li>
     * </ul>
     */
    private static final CodexEntry.Section[][] PAGES = {
            {CodexEntry.Section.IDENTITY, CodexEntry.Section.TAXONOMY, CodexEntry.Section.COMBAT,
                    CodexEntry.Section.PREFERENCES},
            {CodexEntry.Section.LIFESTYLE, CodexEntry.Section.HOSTILITY, CodexEntry.Section.SPECIAL,
                    CodexEntry.Section.MOOD},
            {CodexEntry.Section.ABILITIES}
    };
    /** 三个分页的翻译键后缀（基础 / 行为 / 能力）。 */
    private static final String[] PAGE_KEYS = {"basics", "behavior", "abilities"};
    private final List<SpeciesProfile> allSpecies = new ArrayList<>();
    /** 过滤后的可见物种。 */
    private final List<SpeciesProfile> species = new ArrayList<>();
    /** 列表滚动偏移（行数）。 */
    private int scrollOffset;
    // 筛选状态：习性（夜行/昼行）、巢穴（有/无）、尺寸档（小/中/大/超大）
    private boolean filterNocturnal;
    private boolean filterDiurnal;
    private boolean filterNest;
    private boolean filterNoNest;
    private final boolean[] sizeFilter = new boolean[4];
    /** 悬停提示文本（本帧绘制；null = 不画）。 */
    private String hoverTooltip;
    /** 分类学筛选：族 → 选中的标签 id 集合（族内并集、族间交集）。 */
    private final java.util.Map<com.linguauniversalis.core.taxonomy.Taxonomy.Family,
            java.util.Set<String>> tagFilter = new java.util.EnumMap<>(com.linguauniversalis.core.taxonomy.Taxonomy.Family.class);
    /** 分类学筛选面板是否展开。 */
    private boolean taxonomyOpen;
    /** 分类学面板的滚动偏移（像素）。 */
    private int taxonomyScroll;
    /** 搜索框（在「筛选」按钮下方）。 */
    private net.minecraft.client.gui.components.EditBox searchBox;
    /** 当前搜索词（小写，空串 = 不过滤）。 */
    private String searchText = "";
    /** 「筛选」按钮的位置尺寸（浮层会在同一位置放一个隐形关闭按钮）。 */
    private int filterBtnX;
    private int filterBtnY;
    private int filterBtnW;
    private int filterBtnH;
    /** 详情页滚动偏移 / 内容高 / 可视高。 */
    private int detailScroll;
    private int detailContentHeight;
    private int detailViewHeight;
    /** 面板内容高度 / 可视高度（用于滚动条与夹取）。 */
    private int taxonomyContentHeight;
    private int taxonomyViewHeight;
    /** 分类学面板内的点击区域（与主界面分开，避免判定串味）。 */
    private final List<int[]> taxonomyRegions = new ArrayList<>();
    /** 点击区域：{x1,y1,x2,y2,action}。action: 100+i 选物种；200+i 切换筛选；301 打开分类学面板。 */
    private final List<int[]> clickRegions = new ArrayList<>();
    private int selectedIndex;
    private int selectedPage;
    /** 数据来源（服务端打包的解锁掩码）；本界面不做槽位，只读它。 */
    private final CodexMenu menu;

    /** 打开时刻（用于"书页展开"的入场动画）。 */
    private long openedAt;
    /** 上次切换页签的时刻（用于页签反馈）。 */
    private long pageSwitchedAt;

    /** 面板左上角（自己算，不用 AbstractContainerScreen 的布局）。 */
    private int leftPos;
    private int topPos;

    public CodexScreen(CodexMenu menu, Inventory playerInventory, Component title) {
        super(title);
        this.menu = menu;
        // 目录里跳过"图鉴隐藏"的物种（物种变量 species.hideInCodex）
        for (com.linguauniversalis.core.species.SpeciesProfile profile : SpeciesRegistry.all()) {
            if (!profile.templateFlag(
                    com.linguauniversalis.core.behavior.TemplateKeys.FLAG_SPECIES_HIDE_IN_CODEX, false)) {
                this.allSpecies.add(profile);
            }
        }
        applyFilters();
        // 已记录的排前面，便于玩家先看自己收集过的
        this.species.sort((a, b) -> {
            int ma = Integer.bitCount(menu.knownMask(a.id()));
            int mb = Integer.bitCount(menu.knownMask(b.id()));
            if (ma != mb) {
                return Integer.compare(mb, ma);
            }
            return a.id().compareTo(b.id());
        });
    }

    /**
     * 关闭界面：**必须通知服务端关掉容器菜单**。
     *
     * <p>为什么关键：这个图鉴借容器菜单传数据，但界面是普通 {@code Screen}（为了挡住 JEI 覆盖层），
     * 普通 Screen 关闭时**不会**替我们发 `ServerboundContainerClosePacket`。
     * 结果服务端一直以为 `player.containerMenu` 还是这个没有槽位的图鉴菜单，
     * 于是之后背包里的变化（比如捡起掉落的初稿）**不再同步给客户端** ——
     * 表现就是"物品实际到手了，背包里看不到，只有打开一次背包才刷出来"。
     */
    @Override
    public void removed() {
        super.removed();
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.connection.send(
                    new net.minecraft.network.protocol.game.ServerboundContainerClosePacket(
                            this.menu.containerId));
            this.minecraft.player.containerMenu = this.minecraft.player.inventoryMenu;
        }
    }

    @Override
    public CodexMenu getMenu() {
        return this.menu;
    }

    @Override
    protected void init() {
        super.init();
        this.openedAt = System.currentTimeMillis();
        this.leftPos = (this.width - PANEL_W) / 2;
        this.topPos = (this.height - PANEL_H) / 2;
        int boxX = this.leftPos + 4;
        int boxY = this.topPos + HEADER_H + 2 + 4 + CHIP_H + 3;
        int boxW = LEFT_W - 8;
        this.searchBox = new net.minecraft.client.gui.components.EditBox(
                this.font, boxX, boxY, boxW, 11,
                Component.translatable("gui.lingua_universalis.codex.search"));
        this.searchBox.setMaxLength(32);
        this.searchBox.setValue(this.searchText);
        this.searchBox.setResponder(text -> {
            this.searchText = text == null ? "" : text.trim().toLowerCase(java.util.Locale.ROOT);
            applyFilters();
        });
        this.searchBox.setVisible(!this.taxonomyOpen);
        this.addRenderableWidget(this.searchBox);
    }

    // ---------------------------------------------------------------- 绘制

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        this.clickRegions.clear();
        this.hoverTooltip = null;
        int x = this.leftPos;
        int y = this.topPos;

        // 入场动画：约 120ms 内从中心向上下展开（书页摊开的手感）
        float open = Math.min(1.0F, (System.currentTimeMillis() - this.openedAt) / 120.0F);
        boolean animating = open < 1.0F;
        if (animating) {
            int half = (int) (PANEL_H * 0.5F * open);
            g.enableScissor(x, y + PANEL_H / 2 - half, x + PANEL_W, y + PANEL_H / 2 + half);
        }

        // 面板 + 标题栏
        g.fill(x, y, x + PANEL_W, y + PANEL_H, C_PANEL);
        g.fill(x, y, x + PANEL_W, y + HEADER_H, C_HEADER);
        g.centeredText(this.font, Component.translatable("container.lingua_universalis.codex"),
                x + 8, y + 4, TEXT_TITLE);
        textNS(g, progressText(), x + PANEL_W - 78, y + 4, TEXT_HEADER_SUB);

        // 左右两页
        int pageTop = y + HEADER_H + 2;
        int pageBottom = y + PANEL_H - FOOTER_H - 2;
        int leftX = x + 4;
        int rightX = leftX + LEFT_W + GAP;
        int rightW = x + PANEL_W - 4 - rightX;
        g.fill(leftX, pageTop, leftX + LEFT_W, pageBottom, C_PAPER);
        g.fill(rightX, pageTop, rightX + rightW, pageBottom, C_PAPER);

        SpeciesProfile profile = selectedProfile();
        drawIndex(g, leftX, pageTop, mouseX, mouseY);
        if (profile == null) {
            g.centeredText(this.font, Component.translatable("gui.lingua_universalis.codex.empty"),
                    rightX + rightW / 2, pageTop + 40, C_INK_SOFT);
        } else {
            drawEntry(g, profile, rightX, pageTop, rightW, pageBottom - pageTop, mouseX, mouseY);
        }

        if (animating) {
            g.disableScissor();
        }

        // 底部提示
        textNS(g, Component.translatable("gui.lingua_universalis.codex.hint"),
                x + 6, y + PANEL_H - FOOTER_H + 1, TEXT_HEADER_SUB);

        // 分类学标签筛选浮层（盖在右页上）
        if (this.taxonomyOpen) {
            int panelX = x + 4 + LEFT_W + GAP;
            int panelW = x + PANEL_W - 4 - panelX;
            drawTaxonomyPanel(g, panelX, pageTop, panelW, pageBottom - pageTop, mouseX, mouseY);
        } else {
            this.taxonomyRegions.clear();
        }

        // 悬停提示（行自带说明优先，其次"未解锁"引导）：行绘制时已经把内容写进 hoverTooltip，
        // 这里只补"整个物种都没记录"的兜底，然后统一画出来。
        if (this.hoverTooltip == null && profile != null) {
            drawLockedRowTooltip(g, profile, mouseX, mouseY);
        }
        if (this.hoverTooltip != null) {
            int tw = this.font.width(this.hoverTooltip) + 8;
            int tx = Math.min(mouseX + 8, x + PANEL_W - tw - 2);
            int ty = mouseY + 10;
            g.fill(tx - 1, ty - 2, tx + tw, ty + 11, 0xFF2A211A);
            textNS(g, this.hoverTooltip, tx + 3, ty, 0xFFF0E6D2);
        }
    }

    private String progressText() {
        int recorded = 0;
        for (SpeciesProfile p : this.species) {
            if (this.menu.knownMask(p.id()) != 0) {
                recorded++;
            }
        }
        return Component.translatable("gui.lingua_universalis.codex.progress",
                recorded, this.species.size()).getString();
    }

    /** `●` 全解锁 / `◐` 部分 / `○` 只目击。 */
    private String lockMark(SpeciesProfile p) {
        Set<Encyclopedia.Kind> kinds = this.menu.knownKinds(p.id());
        if (kinds.containsAll(EnumSetHolder.ALL)) {
            return "\u25CF";
        }
        return kinds.size() > 1 ? "\u25D0" : "\u25CB";
    }

    /**
     * 左侧栏：顶部「筛选 ▾」按钮 + **按本质族分组的物种目录**（分组可折叠）+ 滚动条。
     *
     * <p>分组让"这一族里有哪些物种 / 还有几只没记录"一眼可见；点分组标题可折叠/展开。
     */
    private void drawIndex(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
        // 筛选按钮
        int chipY = y + 4;
        String filterLabel = Component.translatable("gui.lingua_universalis.codex.filter").getString() + " \u25BE";
        int filterW = this.font.width(filterLabel) + 8;
        int selectedCount = countSelectedTags() + countSimpleFilters();
        boolean hover = mouseX >= x + 4 && mouseX < x + 4 + filterW && mouseY >= chipY && mouseY < chipY + CHIP_H;
        g.fill(x + 4, chipY, x + 4 + filterW, chipY + CHIP_H, this.taxonomyOpen ? C_CHIP_ON : (hover ? C_SELECT : C_SELECT));
        textNS(g, filterLabel, x + 8, chipY + 2, this.taxonomyOpen ? C_INK : C_INK_SOFT);
        this.clickRegions.add(new int[]{x + 4, chipY, x + 4 + filterW, chipY + CHIP_H, 301});
        this.filterBtnX = x + 4;
        this.filterBtnY = chipY;
        this.filterBtnW = filterW;
        this.filterBtnH = CHIP_H;
        if (selectedCount > 0) {
            textNS(g, "+" + selectedCount, x + 10 + filterW, chipY + 2, C_ACCENT);
        }

        int titleY = chipY + CHIP_H + 3 + 14;
        textNS(g, Component.translatable("gui.lingua_universalis.codex.index").getString(), x + 4, titleY, C_INK_SOFT);

        int listTop = titleY + 11;
        int listBottom = y + LEFT_PAGE_HEIGHT - 2;
        int rowCount = Math.max(1, (listBottom - listTop) / (ROW_H + 4));

        if (this.species.isEmpty()) {
            textNS(g, Component.translatable("gui.lingua_universalis.codex.no_match").getString(),
                    x + 6, listTop + 2, C_LOCK);
            return;
        }

        int maxScroll = Math.max(0, this.species.size() - rowCount);
        if (this.scrollOffset > maxScroll) {
            this.scrollOffset = maxScroll;
        }
        if (this.scrollOffset < 0) {
            this.scrollOffset = 0;
        }

        g.enableScissor(x + 2, listTop - 1, x + LEFT_W - 6, listBottom);
        for (int i = 0; i < rowCount && i + this.scrollOffset < this.species.size(); i++) {
            int index = i + this.scrollOffset;
            SpeciesProfile p = this.species.get(index);
            int ry = listTop + i * (ROW_H + 4);
            boolean rowHover = mouseX >= x + 2 && mouseX < x + LEFT_W - 6
                    && mouseY >= ry - 1 && mouseY < ry + ROW_H + 1;
            if (index == this.selectedIndex || rowHover) {
                g.fill(x + 2, ry - 1, x + LEFT_W - 6, ry + ROW_H + 1, C_SELECT);
            }
            boolean recorded = this.menu.knownMask(p.id()) != 0;
            String name = recorded ? speciesName(p)
                    : Component.translatable("gui.lingua_universalis.codex.unknown").getString();
            textNS(g, name, x + 6, ry + 1, recorded ? C_INK : C_LOCK);
            if (recorded) {
                textNS(g, lockMark(p), x + LEFT_W - 18, ry + 1, C_ACCENT);
            } else if (rowHover) {
                this.hoverTooltip = spawnHint(p);
            }
            this.clickRegions.add(new int[]{x + 2, ry - 1, x + LEFT_W - 6, ry + ROW_H + 1, 100 + index});
        }
        g.disableScissor();
        drawScrollbar(g, x + LEFT_W - 5, listTop, listBottom - listTop,
                this.species.size(), rowCount, this.scrollOffset);
    }


    /**
     * 值/标签的本地化：以 {@code @} 开头表示"这是语言键"，可带 {@code |} 分隔的参数。
     *
     * <p>例：{@code @codex.value.nocturnal} → 「夜行」；
     * {@code @codex.value.slots|4} → 「4 格」。数据层保持纯文本，翻译只在 GUI 做。
     */
    private static String localize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        if (raw.charAt(0) != '@') {
            // 允许"内嵌键"：把串里出现的 @key 逐个翻译（例如「2 格：@codex.value.slot.main_hand、…」）
            return localizeEmbedded(raw);
        }
        String body = raw.substring(1);
        String[] parts = body.split("\\|", -1);
        String key = parts[0];
        Object[] args = new Object[parts.length - 1];
        for (int i = 1; i < parts.length; i++) {
            args[i - 1] = localizeEmbedded(parts[i]);   // 参数里也可能嵌 @key（槽位名等）
        }
        String translated = args.length == 0
                ? Component.translatable(key).getString()
                : Component.translatable(key, args).getString();
        return translated.equals(key) ? raw : translated;   // 缺翻译就原样显示，便于发现漏键
    }

    /**
     * 把 {@code %block:<id>} 解析成方块的本地化显示名。
     *
     * <p>数据层（core）不能依赖 MC 注册表，所以巢穴这类"方块 id"以标记形式传到 GUI，
     * 由这里查注册表取名 —— 名字本身会被语言文件翻译，因此切语言正确。
     */
    private static String resolveRegistryNames(String text) {
        if (text == null || text.indexOf('%') < 0) {
            return text;
        }
        String out = text;
        int i;
        while ((i = out.indexOf("%block:")) >= 0) {
            int end = i + 7;
            while (end < out.length() && (Character.isLetterOrDigit(out.charAt(end))
                    || out.charAt(end) == '_' || out.charAt(end) == ':' || out.charAt(end) == '.')) {
                end++;
            }
            String id = out.substring(i + 7, end);
            String name = id;
            var holder = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                    net.minecraft.resources.Identifier.parse(id.contains(":") ? id : "minecraft:" + id));
            if (holder.isPresent() && !holder.get().value().equals(net.minecraft.world.level.block.Blocks.AIR)) {
                name = holder.get().value().getName().getString();
            }
            out = out.substring(0, i) + name + out.substring(end);
        }
        return out;
    }

    /** 把一段文本里出现的 {@code @key}（可带 |参数）全部替换成翻译结果。 */
    private static String localizeEmbedded(String text) {
        if (text.indexOf('@') < 0) {
            return resolveRegistryNames(text);
        }
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            char ch = text.charAt(i);
            if (ch != '@') {
                out.append(ch);
                i++;
                continue;
            }
            int start = i;
            i++;
            while (i < text.length()) {
                char c = text.charAt(i);
                if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '.' || c == '_'
                        || c == '|' || (c >= '0' && c <= '9') || c == '-') {
                    i++;
                } else {
                    break;
                }
            }
            out.append(localize(text.substring(start, i)));
        }
        return resolveRegistryNames(out.toString());
    }

    /** 物种显示名：优先语言文件 {@code species.<id>.name}，缺翻译回退档案里的名字。 */
    private static String speciesName(SpeciesProfile profile) {
        String key = "species." + profile.id() + ".name";
        String translated = Component.translatable(key).getString();
        return translated.equals(key) ? profile.zhName() : translated;
    }

    /** 拉丁名同理（{@code species.<id>.latin}）。 */
    private static String speciesLatin(SpeciesProfile profile) {
        String key = "species." + profile.id() + ".latin";
        String translated = Component.translatable(key).getString();
        return translated.equals(key) ? profile.latinName() : translated;
    }

    /**
     * 标签显示名：优先语言文件 {@code taxonomy.<id>.name}（内置标签已外置，数据包标签也走这套），
     * 缺翻译时回退到 {@link Taxonomy.Taxon#zh()}。
     */
    private static String tagName(com.linguauniversalis.core.taxonomy.Taxonomy.Taxon taxon) {
        String key = "taxonomy." + taxon.id() + ".name";
        String translated = Component.translatable(key).getString();
        return translated.equals(key) ? taxon.zh() : translated;
    }

    /**
     * 标签块内部文字的左右滚动（块尺寸不变）。
     *
     * <p>外语标签常常比中文长，块宽又受限：这里把文字按时间循环横向平移，
     * 并用 scissor 裁在块内 —— 视觉上是"文字自己慢慢滑过"，不会撑破标签。
     */
    private void drawChipMarquee(GuiGraphicsExtractor g, String text, int x, int y, int width, int color) {
        int textWidth = this.font.width(text);
        int gap = 10;
        int span = textWidth + gap;
        int offset = (int) ((net.minecraft.util.Util.getMillis() / 40L) % span);
        g.enableScissor(x, y - 1, x + width, y + this.font.lineHeight + 1);
        textNS(g, text, x - offset, y, color);
        // 尾接首，形成连续滚动
        if (textWidth > width) {
            textNS(g, text, x - offset + span, y, color);
        }
        g.disableScissor();
    }

    /** 生成"可能在哪里遇到"的提示（地点标签 + 昼夜）。 */
    private String spawnHint(SpeciesProfile p) {
        java.util.List<String> parts = new java.util.ArrayList<>(p.spawnTags());
        parts.add(localize(com.linguauniversalis.core.behavior.SleepRules.nocturnal(p)
                ? "@codex.value.night" : "@codex.value.day"));
        return Component.translatable("gui.lingua_universalis.codex.tooltip.spawn",
                String.join(" · ", parts)).getString();
    }

    /** 搜索匹配：中文名(含拼音) / 拉丁名 / 物种 id / 六族标签名。 */
    private boolean matchesSearch(SpeciesProfile p) {
        String q = this.searchText;
        if (com.linguauniversalis.core.i18n.PinyinIndex.matches(q, p.zhName())) {
            return true;
        }
        if (p.latinName().toLowerCase(java.util.Locale.ROOT).contains(q)
                || p.id().toLowerCase(java.util.Locale.ROOT).contains(q)) {
            return true;
        }
        String[] tags = {p.domain().zh(), p.kingdom().zh(), p.magicClassis().zh(),
                p.socialOrdo().zh(), p.familia().zh(), p.sectio().zh()};
        for (String tag : tags) {
            if (com.linguauniversalis.core.i18n.PinyinIndex.matches(q, tag)) {
                return true;
            }
        }
        return false;
    }

    /** 无阴影文字（图鉴里所有文字都用它：非汉字字母带阴影会显得脏）。 */
    private void textNS(GuiGraphicsExtractor g, String text, int x, int y, int color) {
        g.text(this.font, text, x, y, color, false);
    }

    /** {@link Component} 版本。 */
    private void textNS(GuiGraphicsExtractor g, Component text, int x, int y, int color) {
        g.text(this.font, text, x, y, color, false);
    }

    /**
     * 横向滚动文字（marquee）：放得下就直接画，放不下就左右缓慢来回滚动。
     *
     * <p>注意：`enableScissor` / `disableScissor` 是**成对 push/pop**（栈式），
     * 这里 push 一个"列"裁剪、画完就 pop，栈顶会自动回到调用方的裁剪，不能再手动 enable 一次。
     */
    private void drawMarquee(GuiGraphicsExtractor g, String text, int x, int y, int width, int color) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int textWidth = this.font.width(text);
        if (textWidth <= width) {
            textNS(g, text, x, y, color);
            return;
        }
        int overflow = textWidth - width;
        long period = (long) (overflow + 20) * 40L;
        long phase = System.currentTimeMillis() % (period * 2L);
        int offset = phase < period ? (int) (phase / 40L) : (int) ((period * 2L - phase) / 40L);
        offset = Math.max(0, Math.min(overflow, offset - 10));
        g.enableScissor(x, y - 1, x + width, y + ROW_H - 1);
        g.text(this.font, text, x - offset, y, color, false);
        g.disableScissor();
    }

    /** 三组简单筛选里已选的数量（显示在「筛选」按钮上）。 */
    private int countSimpleFilters() {
        int n = 0;
        n += this.filterNocturnal ? 1 : 0;
        n += this.filterDiurnal ? 1 : 0;
        n += this.filterNest ? 1 : 0;
        n += this.filterNoNest ? 1 : 0;
        for (int i = 0; i < this.sizeFilter.length; i++) {
            n += this.sizeFilter[i] ? 1 : 0;
        }
        return n;
    }

    /** 画一条竖向滚动条（轨道 + 滑块）；内容不超出时不画。 */
    private void drawScrollbar(GuiGraphicsExtractor g, int x, int y, int height, int total, int visible, int offset) {
        if (total <= visible || height <= 2) {
            return;
        }
        g.fill(x, y, x + 3, y + height, 0x33806A4A);
        int thumbH = Math.max(8, height * visible / total);
        int maxTravel = height - thumbH;
        int travel = (int) ((long) maxTravel * offset / Math.max(1, total - visible));
        g.fill(x, y + travel, x + 3, y + travel + thumbH, 0xCC8C6A3E);
    }

    /** 右侧：条目正文（名字 / 分类学标签 chips / 页签 / 数据行 / 预览框）。 */
    private void drawEntry(GuiGraphicsExtractor g, SpeciesProfile profile, int x, int y, int w, int h,
                           int mouseX, int mouseY) {
        int previewX = x + w - PREVIEW_SIZE - 4;
        int previewY = y + 4;
        int previewBottom = previewY + PREVIEW_SIZE;

        // 名字两行同样受"基础观测"门槛控制：未解锁显示 ？？？，保持神秘感
        boolean nameKnown = this.menu.knownKinds(profile.id())
                .contains(Encyclopedia.Kind.BASIC_INFO);
        String unknown = Component.translatable("gui.lingua_universalis.codex.unknown").getString();
        textNS(g, nameKnown ? speciesName(profile) : unknown, x + 4, y + 3, nameKnown ? C_INK : C_LOCK);
        textNS(g, nameKnown ? speciesLatin(profile) : unknown, x + 4, y + 14, nameKnown ? C_INK_SOFT : C_LOCK);

        // 分类学标签：每个标签一个带背景的 chip，超出宽度自动换行（不会被截断、也不出框）
        int chipsBottom = drawTaxonomyChips(g, profile, x + 4, y + 26, x + w - 6, previewX - 4, previewBottom);

        // 预览框：实时渲染该物种模型（GeckoLib 动画 = main_idle；随鼠标转身）
        g.fill(previewX, previewY, previewX + PREVIEW_SIZE, previewY + PREVIEW_SIZE, C_PAPER_DARK);
        drawPreview(g, previewX, previewY, mouseX, mouseY, profile);

        // 页签（位置随标签行数下移；记住位置供点击命中）
        int tabY = Math.max(chipsBottom + 4, previewBottom + 2);
        // 页签：3 个分组（基础 / 行为 / 能力），全名直接排，不需要压缩
        int tx = x + 4;
        for (int page = 0; page < PAGES.length; page++) {
            String label = Component.translatable(pageKey(page)).getString();
            int tw = this.font.width(label) + 10;
            boolean isActive = page == this.selectedPage;
            g.fill(tx, tabY, tx + tw, tabY + TAB_H, isActive ? C_PAPER_DARK : C_SELECT);
            textNS(g, label, tx + 5, tabY + 2, isActive ? C_INK : C_INK_SOFT);
            this.clickRegions.add(new int[]{tx, tabY, tx + tw, tabY + TAB_H, 600 + page});
            tx += tw + 3;
        }

        // 内容：按该页的小节依次排；超出可视高度时可滚动（裁剪 + 右侧滚动条）
        int viewTop = tabY + TAB_H + 4;
        int viewBottom = y + h - 3;
        int viewHeight = viewBottom - viewTop;
        CodexEntry.Section[] sections = PAGES[this.selectedPage];

        // 先算内容总高，用于夹取滚动与滚动条
        int contentHeight = 0;
        for (int si = 0; si < sections.length; si++) {
            if (si > 0) {
                contentHeight += 4;
            }
            contentHeight += ROW_H;
            contentHeight += rowsOf(profile, sections[si]).size() * ROW_H;
        }
        this.detailContentHeight = contentHeight;
        this.detailViewHeight = viewHeight;
        int maxScroll = Math.max(0, contentHeight - viewHeight);
        this.detailScroll = Math.max(0, Math.min(maxScroll, this.detailScroll));

        g.enableScissor(x + 2, viewTop, x + w - 6, viewBottom);
        int rowY = viewTop - this.detailScroll;
        for (int si = 0; si < sections.length; si++) {
            CodexEntry.Section section = sections[si];
            if (si > 0) {
                g.fill(x + 4, rowY + 1, x + w - 8, rowY + 2, 0x33A09680);
                rowY += 4;
            }
            textNS(g, Component.translatable(sectionKey(section)).getString(),
                    x + 4, rowY, C_ACCENT);
            rowY += ROW_H;
            for (CodexEntry.Row row : rowsOf(profile, section)) {
                String label = Component.translatable(labelKey(row.labelKey())).getString();
                String value = row.locked() ? Component.translatable(
                        "gui.lingua_universalis.codex.locked_value").getString() : localize(row.value());
                int labelLeft = x + 9;
                int valueRight = x + w - 8;
                // 条目名占前 50%，内容占后 50%
                int valueLeft = x + 4 + (valueRight - (x + 4)) / 2;
                int labelWidth = Math.max(20, valueLeft - labelLeft - 4);
                int valueWidth = Math.max(20, valueRight - valueLeft);
                // 名字放不下时横向滚动
                this.drawMarquee(g, label, labelLeft, rowY, labelWidth, C_INK_SOFT);
                if (!row.tags().isEmpty()) {
                    // 标签块式（同族敌意 / 狩猎 这类枚举值）
                    int cx = valueLeft;
                    for (String tagRaw : row.tags()) {
                        String tag = localize(tagRaw);
                        int textW = this.font.width(tag);
                        // 标签块宽度按文字自适应；但**最大不超过可用宽度**（放不下就在块内滚动）
                        int cw = Math.min(textW + 8, Math.max(24, valueRight - cx));
                        if (cx + cw > valueRight) {
                            break;
                        }
                        int chipColor = row.locked() ? C_SELECT : C_CHIP;
                        int textColor = row.locked() ? C_LOCK : C_INK;
                        g.fill(cx, rowY - 1, cx + cw, rowY + CHIP_H - 2, chipColor);
                        if (textW <= cw - 8) {
                            textNS(g, tag, cx + 4, rowY + 1, textColor);
                        } else {
                            // 文字超宽：在标签块内部左右滚动（块尺寸不变）
                            this.drawChipMarquee(g, tag, cx + 4, rowY + 1, cw - 8, textColor);
                        }
                        cx += cw + 3;
                    }
                } else {
                    // 内容放不下时同样横向滚动
                    this.drawMarquee(g, value, valueLeft, rowY, valueWidth, row.locked() ? C_LOCK : C_INK);
                }
                g.fill(x + 4, rowY + ROW_H - 2, x + w - 8, rowY + ROW_H - 1, 0x2AA09680);
                // 悬浮提示：行自带说明（能力描述等）优先，其次才是"未解锁"的引导
                boolean rowHovered = mouseX >= x + 4 && mouseX < x + w - 8
                        && mouseY >= rowY - 1 && mouseY < rowY + ROW_H
                        && mouseY >= viewTop && mouseY < viewBottom;
                if (rowHovered) {
                    if (row.tooltip() != null && !row.tooltip().isEmpty()) {
                        this.hoverTooltip = localize(row.tooltip());
                    } else if (row.locked()) {
                        this.hoverTooltip = Component.translatable(
                                "gui.lingua_universalis.codex.tooltip.locked").getString();
                    }
                }
                rowY += ROW_H;
            }
        }
        g.disableScissor();
        drawScrollbar(g, x + w - 5, viewTop, viewHeight,
                this.detailContentHeight, this.detailViewHeight, this.detailScroll);
    }
    /**
     * 分类学标签 chips：六族按固定顺序，每个标签**自带背景块**；宽度不够就换行。
     *
     * <p>为什么这么做：标签文字长短不一（`外生息`/`拟态同化`/`触肢`），单行拼接会顶出右页边框。
     * 换行 + chip 背景既不会出框，也让"标签"这个东西在视觉上成立。
     *
     * @param maxRight      不可越过的右边界
     * @param previewLeft   预览框左边界（标签行在该高度内不能压到预览框）
     * @param previewBottom 预览框底边（超过这个 y 之后可以占满宽度）
     * @return 最后一行标签的底边 y
     */
    private int drawTaxonomyChips(GuiGraphicsExtractor g, SpeciesProfile profile, int x, int y,
                                  int maxRight, int previewLeft, int previewBottom) {
        Set<Encyclopedia.Kind> kinds = this.menu.knownKinds(profile.id());
        boolean unlocked = kinds.contains(Encyclopedia.Kind.TAXONOMY);
        String q = Component.translatable("gui.lingua_universalis.codex.locked_value").getString();
        String[] labels = unlocked
                ? new String[]{tagName(profile.domain()), tagName(profile.kingdom()),
                        tagName(profile.magicClassis()), tagName(profile.socialOrdo()),
                        tagName(profile.familia()), tagName(profile.sectio())}
                : new String[]{q, q, q, q, q, q};

        int cx = x;
        int cy = y;
        for (String label : labels) {
            int cw = this.font.width(label) + 8;
            int limit = (cy + CHIP_H <= previewBottom) ? (previewLeft - 2) : maxRight;
            if (cx + cw > limit) {
                cx = x;
                cy += CHIP_H + 2;
            }
            g.fill(cx, cy, cx + cw, cy + CHIP_H, unlocked ? C_CHIP : C_SELECT);
            textNS(g, label, cx + 4, cy + 2, unlocked ? C_INK : C_LOCK);
            cx += cw + 3;
        }
        return cy + CHIP_H;
    }
    /** 切换第 index 个分类学标签（按"六族顺序 + 族内顺序"展平）。 */
    private void toggleTag(int index) {
        int i = 0;
        for (com.linguauniversalis.core.taxonomy.Taxonomy.Family rank
                : com.linguauniversalis.core.taxonomy.Taxonomy.Family.values()) {
            for (com.linguauniversalis.core.taxonomy.Taxonomy.Taxon taxon
                    : com.linguauniversalis.core.taxonomy.Taxonomy.all(rank)) {
                if (i == index) {
                    java.util.Set<String> selected = tagsOf(rank);
                    if (!selected.remove(taxon.id())) {
                        selected.add(taxon.id());
                    }
                    applyFilters();
                    return;
                }
                i++;
            }
        }
    }

    /** 已选的分类学标签数量（显示在按钮上）。 */
    private int countSelectedTags() {
        int n = 0;
        for (java.util.Set<String> set : this.tagFilter.values()) {
            n += set.size();
        }
        return n;
    }

    /** 某一族的已选标签集合（懒创建）。 */
    private java.util.Set<String> tagsOf(com.linguauniversalis.core.taxonomy.Taxonomy.Family rank) {
        return this.tagFilter.computeIfAbsent(rank, r -> new java.util.LinkedHashSet<>());
    }

    /** 该物种在某一族的标签 id。 */
    private String tagId(SpeciesProfile profile, com.linguauniversalis.core.taxonomy.Taxonomy.Family rank) {
        return switch (rank) {
            case ESSENCE -> profile.domain().id();
            case STRUCTURE -> profile.kingdom().id();
            case MAGIC -> profile.magicClassis().id();
            case HABIT -> profile.socialOrdo().id();
            case ELEMENT -> profile.familia().id();
            case FORM -> profile.sectio().id();
        };
    }

    /**
     * 分类学标签筛选面板（浮在右页上）。
     *
     * <p>为什么用浮层：六族共 26 个标签，128px 的左栏塞不下。浮层里按族分组排 chips，
     * 族内多选 = 并集、族间 = 交集；选完立刻反映到左栏列表。
     */
    private void drawTaxonomyPanel(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                   int mouseX, int mouseY) {
        this.taxonomyRegions.clear();
        g.fill(x, y, x + w, y + h, C_PAPER);
        g.fill(x, y, x + w, y + 13, C_PAPER_DARK);
        textNS(g, Component.translatable("gui.lingua_universalis.codex.filter").getString(),
                x + 4, y + 3, C_INK);
        // 关闭 / 清除
        String close = Component.translatable("gui.lingua_universalis.codex.close").getString();
        int closeW = this.font.width(close) + 6;
        textNS(g, close, x + w - closeW - 3, y + 3, C_ACCENT);
        this.taxonomyRegions.add(new int[]{x + w - closeW - 3, y, x + w - 2, y + 13, 300});
        String clear = Component.translatable("gui.lingua_universalis.codex.clear").getString();
        int clearW = this.font.width(clear) + 6;
        textNS(g, clear, x + w - closeW - clearW - 8, y + 3, C_INK_SOFT);
        this.taxonomyRegions.add(new int[]{x + w - closeW - clearW - 8, y, x + w - closeW - 5, y + 13, 500});

        // 隐形按钮：位置/尺寸与左栏的「筛选」按钮完全一致，点它就关闭浮层
        // （浮层只盖住右页，按钮本身在左栏，所以这里单独登记一块命中区）
        if (this.filterBtnW > 0) {
            this.taxonomyRegions.add(new int[]{this.filterBtnX, this.filterBtnY,
                    this.filterBtnX + this.filterBtnW, this.filterBtnY + this.filterBtnH, 300});
        }

        int contentX = x + 4;
        int contentTop = y + 15;
        int contentBottom = y + h - 3;
        int rightLimit = x + w - 8;   // 留出滚动条位置
        int cy = contentTop - this.taxonomyScroll;

        g.enableScissor(x + 2, contentTop, x + w - 6, contentBottom);
        // --- 三组简单筛选（从左侧栏挪进来的）---
        cy = panelFilterRow(g, contentX, cy, rightLimit, contentTop, contentBottom, mouseX, mouseY, 200,
                Component.translatable("gui.lingua_universalis.codex.filter.activity").getString(),
                new String[]{
                        Component.translatable("gui.lingua_universalis.codex.activity.nocturnal").getString(),
                        Component.translatable("gui.lingua_universalis.codex.activity.diurnal").getString()},
                new boolean[]{this.filterNocturnal, this.filterDiurnal});
        cy = panelFilterRow(g, contentX, cy, rightLimit, contentTop, contentBottom, mouseX, mouseY, 210,
                Component.translatable("gui.lingua_universalis.codex.filter.nest").getString(),
                new String[]{
                        Component.translatable("gui.lingua_universalis.codex.nest.has").getString(),
                        Component.translatable("gui.lingua_universalis.codex.nest.none").getString()},
                new boolean[]{this.filterNest, this.filterNoNest});
        cy = panelFilterRow(g, contentX, cy, rightLimit, contentTop, contentBottom, mouseX, mouseY, 220,
                Component.translatable("gui.lingua_universalis.codex.filter.size").getString(),
                new String[]{
                        Component.translatable("gui.lingua_universalis.codex.size.small").getString(),
                        Component.translatable("gui.lingua_universalis.codex.size.medium").getString(),
                        Component.translatable("gui.lingua_universalis.codex.size.large").getString()},
                new boolean[]{this.sizeFilter[0], this.sizeFilter[1], this.sizeFilter[2]});
        cy += 2;

        // --- 分类学六族 ---
        int tagIndex = 0;
        for (com.linguauniversalis.core.taxonomy.Taxonomy.Family rank
                : com.linguauniversalis.core.taxonomy.Taxonomy.Family.values()) {
            if (cy + CHIP_H >= contentTop && cy <= contentBottom) {
                textNS(g, rankZh(rank), contentX, cy + 2, C_INK_SOFT);
            }
            cy += CHIP_H + 1;
            int cx = contentX;
            for (com.linguauniversalis.core.taxonomy.Taxonomy.Taxon taxon
                    : com.linguauniversalis.core.taxonomy.Taxonomy.all(rank)) {
                String label = tagName(taxon);
                int cw = this.font.width(label) + 8;
                if (cx + cw > rightLimit) {
                    cx = contentX;
                    cy += CHIP_H + 2;
                }
                if (cy + CHIP_H >= contentTop && cy <= contentBottom) {
                    boolean on = tagsOf(rank).contains(taxon.id());
                    boolean hover = mouseX >= cx && mouseX < cx + cw && mouseY >= cy && mouseY < cy + CHIP_H;
                    g.fill(cx, cy, cx + cw, cy + CHIP_H, on ? C_CHIP_ON : (hover ? C_SELECT : C_SELECT));
                    textNS(g, label, cx + 4, cy + 2, on ? C_INK : C_INK_SOFT);
                    this.taxonomyRegions.add(new int[]{cx, cy, cx + cw, cy + CHIP_H, 400 + tagIndex});
                }
                cx += cw + 3;
                tagIndex++;
            }
            cy += CHIP_H + 4;
        }
        g.disableScissor();
        this.taxonomyContentHeight = (cy + this.taxonomyScroll) - contentTop;
        this.taxonomyViewHeight = contentBottom - contentTop;
        // 面板滚动条
        drawScrollbar(g, x + w - 5, contentTop, contentBottom - contentTop,
                this.taxonomyContentHeight, this.taxonomyViewHeight, this.taxonomyScroll);
    }

    /**
     * 浮层里的一行多选 chip（习性 / 巢穴 / 尺寸）。
     *
     * @return 下一行的 y（未滚动坐标系）
     */
    private int panelFilterRow(GuiGraphicsExtractor g, int x, int y, int rightLimit,
                               int clipTop, int clipBottom, int mouseX, int mouseY, int actionBase,
                               String groupLabel, String[] labels, boolean[] states) {
        if (y + CHIP_H >= clipTop && y <= clipBottom) {
            textNS(g, groupLabel, x, y + 2, C_INK_SOFT);
        }
        int cx = x + this.font.width(groupLabel) + 5;
        for (int i = 0; i < labels.length; i++) {
            String label = labels[i];
            int cw = this.font.width(label) + 8;
            if (cx + cw > rightLimit) {
                cx = x;
                y += CHIP_H + 2;
            }
            boolean on = i < states.length && states[i];
            boolean hover = mouseX >= cx && mouseX < cx + cw && mouseY >= y && mouseY < y + CHIP_H;
            if (y + CHIP_H >= clipTop && y <= clipBottom) {
                g.fill(cx, y, cx + cw, y + CHIP_H, on ? C_CHIP_ON : (hover ? C_SELECT : C_SELECT));
                textNS(g, label, cx + 4, y + 2, on ? C_INK : C_INK_SOFT);
                this.taxonomyRegions.add(new int[]{cx, y, cx + cw, y + CHIP_H, actionBase + i});
            }
            cx += cw + 3;
        }
        return y + CHIP_H + 3;
    }
    /** 族的显示名（本质/构造/魔力/习性/元素/形态）。 */
    private static String rankZh(com.linguauniversalis.core.taxonomy.Taxonomy.Family family) {
        return net.minecraft.network.chat.Component.translatable(family.translationKey()).getString();
    }

    /** 按筛选条件重算可见物种列表（保持当前选中物种）。 */
    private void applyFilters() {
        String keepId = this.selectedIndex >= 0 && this.selectedIndex < this.species.size()
                ? this.species.get(this.selectedIndex).id() : null;
        this.species.clear();
        for (SpeciesProfile p : this.allSpecies) {
            if (!this.searchText.isEmpty() && !matchesSearch(p)) {
                continue;
            }
            boolean nocturnal = com.linguauniversalis.core.behavior.SleepRules.nocturnal(p);
            if ((this.filterNocturnal || this.filterDiurnal) && !(nocturnal && this.filterNocturnal)
                    && !(!nocturnal && this.filterDiurnal)) {
                continue;
            }
            boolean nest = p.hasNest();
            if ((this.filterNest || this.filterNoNest) && !(nest && this.filterNest)
                    && !(!nest && this.filterNoNest)) {
                continue;
            }
            boolean tagRejected = false;
            for (com.linguauniversalis.core.taxonomy.Taxonomy.Family rank
                    : com.linguauniversalis.core.taxonomy.Taxonomy.Family.values()) {
                java.util.Set<String> selected = this.tagFilter.get(rank);
                if (selected == null || selected.isEmpty()) {
                    continue;
                }
                if (!selected.contains(tagId(p, rank))) {
                    tagRejected = true;
                    break;
                }
            }
            if (tagRejected) {
                continue;
            }
            boolean anySize = this.sizeFilter[0] || this.sizeFilter[1] || this.sizeFilter[2] || this.sizeFilter[3];
            if (anySize) {
                int size = com.linguauniversalis.core.behavior.SleepRules.sizeOf(p).id();
                if (size < 0 || size >= this.sizeFilter.length || !this.sizeFilter[size]) {
                    continue;
                }
            }
            this.species.add(p);
        }
        this.selectedIndex = 0;
        if (keepId != null) {
            for (int i = 0; i < this.species.size(); i++) {
                if (this.species.get(i).id().equals(keepId)) {
                    this.selectedIndex = i;
                    break;
                }
            }
        }
        this.scrollOffset = 0;
    }

    /** 悬停在锁定行上时记下提示文本（行位置在 drawEntry 里算好，这里只判定一次）。 */
    private void drawLockedRowTooltip(GuiGraphicsExtractor g, SpeciesProfile profile, int mouseX, int mouseY) {
        // 具体命中在 drawEntry 里已经写入 hoverTooltip；这里只在"整节未解锁"时兜底
        if (this.hoverTooltip != null) {
            return;
        }
        Set<Encyclopedia.Kind> kinds = this.menu.knownKinds(profile.id());
        if (kinds.isEmpty()) {
            this.hoverTooltip = Component.translatable("gui.lingua_universalis.codex.tooltip.unrecorded").getString();
        }
    }

    private void toggleFilter(int action) {
        switch (action) {
            case 200 -> this.filterNocturnal = !this.filterNocturnal;
            case 201 -> this.filterDiurnal = !this.filterDiurnal;
            case 210 -> this.filterNest = !this.filterNest;
            case 211 -> this.filterNoNest = !this.filterNoNest;
            case 220 -> this.sizeFilter[0] = !this.sizeFilter[0];
            case 221 -> this.sizeFilter[1] = !this.sizeFilter[1];
            case 222 -> this.sizeFilter[2] = !this.sizeFilter[2];
            case 223 -> this.sizeFilter[3] = !this.sizeFilter[3];
            default -> {
                return;
            }
        }
        applyFilters();
    }
    private List<CodexEntry.Row> rowsOf(SpeciesProfile profile, CodexEntry.Section section) {
        Set<Encyclopedia.Kind> kinds = this.menu.knownKinds(profile.id());
        for (CodexEntry.Page page : CodexEntry.documentOf(profile, kinds)) {
            if (page.section() == section) {
                return page.rows();
            }
        }
        return List.of();
    }

    private SpeciesProfile selectedProfile() {
        if (this.species.isEmpty()) {
            return null;
        }
        if (this.selectedIndex < 0 || this.selectedIndex >= this.species.size()) {
            this.selectedIndex = 0;
        }
        return this.species.get(this.selectedIndex);
    }

    // ---------------------------------------------------------------- 模型预览
    /** 预览用的假实体（仅客户端渲染，不进世界；每次换物种重建）。 */
    private net.minecraft.world.entity.Entity previewEntity;
    private String previewSpeciesId;

    /** 预览缩放的除数（越小模型越大；原版背包玩家预览约 30）。 */
    /**
     * 预览模型大小。**数值越大模型越大**（之前注释写反了，导致两次"缩小"都变成了放大）。
     *
     * <p>实测标定：框 48×48 时 52 偏大 → 取 **26**（= 52 的一半）。
     * 还要更小就继续降（20 / 16），更大就升。
     */
    private static final int PREVIEW_ENTITY_SCALE = 26;
    /** 预览假实体的 id 基数（负数，避免与真实实体冲突；渲染管线需要非空 id）。 */
    private static final int PREVIEW_ENTITY_ID_BASE = -100000;

    /**
     * 在框里渲染该物种的模型：**GeckoLib 动画（待机）**，鼠标在框内左右移动会带着转身。
     *
     * <p>走原版背包"看自己角色"的同一套接口
     * （{@code InventoryScreen.extractEntityInInventoryFollowsMouse}），
     * 这样 GeckoLib 的实体渲染器与动画都按正常管线跑，不用自己摆 PoseStack。
     * 实体是**临时创建、不加入世界**的（{@code level} 只用来给渲染器读位置/光照）。
     */
    private void drawPreview(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY, SpeciesProfile profile) {
        boolean recorded = this.menu.knownMask(profile.id()) != 0;
        if (!recorded) {
            // 未解锁：不渲染模型，框里只有一个问号
            g.centeredText(this.font, Component.translatable("gui.lingua_universalis.codex.unknown"),
                    x + PREVIEW_SIZE / 2, y + PREVIEW_SIZE / 2 - 4, C_LOCK);
            return;
        }
        net.minecraft.world.entity.Entity entity = previewEntityFor(profile.id());
        if (entity == null) {
            return;
        }
        // 让渲染状态里的 ageInTicks 增长（GeckoLib 的动画进度依赖它，来自 tickCount + partialTick）。
        // 假实体不进世界，tickCount 不会自己涨，所以这里每帧补 1。
        entity.tickCount += 1;
        net.minecraft.client.gui.screens.inventory.InventoryScreen.extractEntityInInventoryFollowsMouse(
                g, x, y, x + PREVIEW_SIZE, y + PREVIEW_SIZE, PREVIEW_ENTITY_SCALE, 0.0F,
                (float) mouseX, (float) mouseY, (net.minecraft.world.entity.LivingEntity) entity);
    }

    /**
     * 只保留主控制器（{@code main}），其余动画层全部摘掉。
     *
     * <p>效果：预览显示 {@code main_idle} 的**第一帧**姿势，且**不受尾巴动画影响**
     * （尾巴控制器 {@code tail} 会持续播放 {@code tail_swing}，摘掉后尾巴保持模型默认姿态）。
     */
    private void prunePreviewControllers(com.linguauniversalis.entity.MonsterGirlEntity girl) {
        var manager = girl.getAnimatableInstanceCache().getManagerForId(girl.getId());
        if (manager == null) {
            return;
        }
        for (String name : new java.util.ArrayList<>(manager.getAnimationControllers().keySet())) {
            if (!"main".equals(name)) {
                manager.removeController(name);
            }
        }
    }

    /** 取（或创建）该物种的预览实体。 */
    private net.minecraft.world.entity.Entity previewEntityFor(String speciesId) {
        if (this.previewEntity != null && speciesId.equals(this.previewSpeciesId)) {
            return this.previewEntity;
        }
        net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        net.minecraft.world.entity.EntityType<? extends com.linguauniversalis.entity.MonsterGirlEntity> type =
                switch (speciesId) {
                    case "nekomata" -> com.linguauniversalis.registry.LUEntities.NEKOMATA.get();
                    case "arakne" -> com.linguauniversalis.registry.LUEntities.ARAKNE.get();
                    default -> com.linguauniversalis.registry.LUEntities.MONSTER_GIRL.get();
                };
        com.linguauniversalis.entity.MonsterGirlEntity girl =
                com.linguauniversalis.entity.MonsterGirlEntity.ofSpecies(type, level, speciesId);
        girl.setPos(0.0D, 0.0D, 0.0D);
        girl.setYRot(0.0F);
        girl.yRotO = 0.0F;
        girl.setNoAi(true);

        // 关键：假实体没有进世界，**没有分配过 entity id**；而原版渲染管线（物品模型解析）会调用 getId()
        // → "Tried to access entity ID before ID assignment" 崩溃。这里给一个固定的负数 id 兜住。
        girl.setId(PREVIEW_ENTITY_ID_BASE + Math.abs(speciesId.hashCode() % 900));
        // 注意顺序：必须在 setId 之后才能摘控制器（GeckoLib 的 manager 是按 entity id 索引的，
        // 提前调用会触发 "Tried to access entity ID before ID assignment" 崩溃）。
        prunePreviewControllers(girl);
        this.previewEntity = girl;
        this.previewSpeciesId = speciesId;
        return girl;
    }
    // ---------------------------------------------------------------- 交互
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        int x = this.leftPos;
        int y = this.topPos;
        int pageTop = y + HEADER_H + 2;
        // 目录点击
        if (button == 0) {
            // 分类学浮层展开时，点击先交给浮层
            if (this.taxonomyOpen) {
                for (int[] region : this.taxonomyRegions) {
                    if (mouseX >= region[0] && mouseX < region[2]
                            && mouseY >= region[1] && mouseY < region[3]) {
                        int action = region[4];
                        if (action == 300) {
                            this.taxonomyOpen = false;
                            if (this.searchBox != null) {
                                this.searchBox.setVisible(true);
                            }
                        } else if (action == 500) {
                            this.tagFilter.clear();
                            applyFilters();
                        } else if (action >= 400) {
                            toggleTag(action - 400);
                        } else if (action >= 200) {
                            // 浮层里的三组简单筛选（习性/巢穴/尺寸）
                            toggleFilter(action);
                        }
                        return true;
                    }
                }
                // 浮层没命中：什么都不做（**不**因为点到空白就关闭）。
                // 关闭只走两条路：浮层里的"关闭"，或与「筛选」按钮同位置的那个隐形按钮。
                return true;
            }
            // 绘制时记录的点击区域：筛选 chips（200+）/ 物种行（100+）
            for (int[] region : this.clickRegions) {
                if (mouseX >= region[0] && mouseX < region[2]
                        && mouseY >= region[1] && mouseY < region[3]) {
                    int action = region[4];
                    if (action >= 600) {
                        this.selectedPage = action - 600;
                        this.detailScroll = 0;
                    } else if (action == 301) {
                        // 再点一次「筛选」就收起浮层
                        this.taxonomyOpen = !this.taxonomyOpen;
                        if (this.searchBox != null) {
                            this.searchBox.setVisible(!this.taxonomyOpen);
                        }
                    } else if (action >= 200) {
                        toggleFilter(action);
                    } else if (action >= 100) {
                        this.selectedIndex = action - 100;
                        this.detailScroll = 0;
                    }
                    return true;
                }
            }
            // 页签命中走 clickRegions（600+），这里不再按坐标算
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int step = (int) Math.signum(scrollY);
        if (this.taxonomyOpen) {
            // 浮层打开时滚浮层
            int max = Math.max(0, this.taxonomyContentHeight - this.taxonomyViewHeight);
            this.taxonomyScroll = Math.max(0, Math.min(max, this.taxonomyScroll - step * (CHIP_H + 3)));
            return true;
        }
        int leftEdge = this.leftPos + 4 + LEFT_W + GAP;
        if (mouseX < leftEdge) {
            // 左栏：物种列表
            int rowCount = Math.max(1, (LEFT_PAGE_HEIGHT - 40) / (ROW_H + 4));
            int maxScroll = Math.max(0, this.species.size() - rowCount);
            this.scrollOffset = Math.max(0, Math.min(maxScroll, this.scrollOffset - step));
        } else {
            // 右页：详情内容
            int max = Math.max(0, this.detailContentHeight - this.detailViewHeight);
            this.detailScroll = Math.max(0, Math.min(max, this.detailScroll - step * ROW_H));
        }
        return true;
    }

    // ---------------------------------------------------------------- 翻译键
    /** 分页翻译键（基础 / 行为 / 能力）。 */
    private static String pageKey(int page) {
        return "gui.lingua_universalis.codex.page." + PAGE_KEYS[page];
    }
    private static String sectionKey(CodexEntry.Section section) {
        return "gui.lingua_universalis.codex.section." + section.id();
    }

    private static String labelKey(String labelId) {
        return "gui.lingua_universalis.codex.row." + labelId;
    }

    /** `EnumSet` 的静态持有者（避免每次分配）。 */
    private static final class EnumSetHolder {
        private static final Set<Encyclopedia.Kind> ALL = java.util.EnumSet.allOf(Encyclopedia.Kind.class);
    }
}
