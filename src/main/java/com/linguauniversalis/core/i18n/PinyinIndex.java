package com.linguauniversalis.core.i18n;

import java.util.HashMap;
import java.util.Map;

/**
 * 极简拼音索引：把中文名转成拼音串，供图鉴搜索框做"打拼音找中文"。
 *
 * <p>为什么不用第三方拼音库：本项目的中文字符集非常有限（物种名 + 六族标签 + 界面词，
 * 一共几十个字），硬编码一张"字 → 拼音"表就够，零依赖、纯规则层、可冒烟。
 * 遇到表里没有的字，原样保留（仍能用中文搜）。
 *
 * <p>匹配口径：
 * <ul>
 *   <li>全拼：{@code maoyou} → 「猫又」</li>
 *   <li>首字母：{@code my} → 「猫又」</li>
 *   <li>中文原串：{@code 猫} 仍可搜</li>
 * </ul>
 */
public final class PinyinIndex {
    private PinyinIndex() {
    }

    /** 字 → 无声调拼音。只收录本项目实际用到的字，后续加物种时补进来即可。 */
    private static final Map<Character, String> PINYIN = new HashMap<>();

    static {
        // 物种名
        put("阿a拉la克ke涅nie猫mao又you");
        // 六族标签
        put("物wu质zhi元yuan素su混hun沌dun脊ji索suo外wai壳ke流liu形xing植zhi生sheng");
        put("内nei燃ran交jiao感gan熵shang噬shi拟ni态tai同tong化hua领ling地di巡xun游you");
        put("独du行xing自zi巢chao穴xue共gong牧mu契qi约yue炽chi焰yan深shen规gui序xu");
        put("变bian辉hui耀yao幽you暗an涣huan鳞lin绒rong羽yu触chu肢zhi胶jiao菌jun丝si");
        // 界面词（习性/昼夜/尺寸等，方便搜"ye xing"）
        put("性xing夜ye昼zhou小xiao中zhong大da超chao有you无wu身shen份fen类lei习xi战zhan");
        put("敌di能neng力li喜xi好hao特te殊shu为wei行xing巢chao床chuang");
    }

    private static void put(String spec) {
        // spec 形如 "阿a拉la克ke涅nie"：汉字后面紧跟它的拼音（到下一个汉字为止）
        int i = 0;
        while (i < spec.length()) {
            char ch = spec.charAt(i);
            if (isHan(ch)) {
                int j = i + 1;
                while (j < spec.length() && !isHan(spec.charAt(j))) {
                    j++;
                }
                PINYIN.put(ch, spec.substring(i + 1, j));
                i = j;
            } else {
                i++;
            }
        }
    }

    private static boolean isHan(char ch) {
        return ch >= 0x4E00 && ch <= 0x9FFF;
    }

    /**
     * 把一段文本转成拼音（全拼、无分隔）。
     *
     * @param text 可能包含中文/英文/数字
     * @return 小写拼音串；英文与数字原样保留
     */
    public static String toPinyin(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length() * 3);
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            String py = PINYIN.get(ch);
            out.append(py != null ? py : String.valueOf(ch));
        }
        return out.toString().toLowerCase(java.util.Locale.ROOT);
    }

    /** 首字母串（「猫又」→ {@code my}）。 */
    public static String toInitials(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            String py = PINYIN.get(ch);
            if (py != null && !py.isEmpty()) {
                out.append(py.charAt(0));
            } else if (!isHan(ch)) {
                out.append(Character.toLowerCase(ch));
            }
        }
        return out.toString();
    }

    /**
     * 搜索匹配：中文原串 / 全拼 / 首字母 任一命中即算匹配。
     *
     * @param query    已小写的搜索词
     * @param candidate 候选文本（如物种中文名、标签名）
     */
    public static boolean matches(String query, String candidate) {
        if (query == null || query.isEmpty() || candidate == null || candidate.isEmpty()) {
            return false;
        }
        String q = query.toLowerCase(java.util.Locale.ROOT);
        if (candidate.toLowerCase(java.util.Locale.ROOT).contains(q)) {
            return true;
        }
        if (toPinyin(candidate).contains(q)) {
            return true;
        }
        return toInitials(candidate).contains(q);
    }
}
