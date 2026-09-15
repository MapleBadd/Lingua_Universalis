package com.linguauniversalis.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 箱型 UV（Box UV）→ 逐面 UV（Per-face UV）转换器（GeckoLib 5.5.5 用）。
 *
 * <p><b>为什么要转</b>：GeckoLib 的箱型 UV 实现会把 cube 尺寸 <b>向下取整</b> 后计算 UV 布局：
 * <pre>
 * // com.geckolib.loading.definition.geometry.GeometryUvPair#bakeQuad
 * final Vec3 uvSize = new Vec3(Mth.floor(cubeSize.x), Mth.floor(cubeSize.y), Mth.floor(cubeSize.z));
 * </pre>
 * 因此凡是含小数的 cube（如 {@code size:[1, 0.4, 1]}、{@code [1.4, 2, 1]}、{@code [2, 6, 4.5]}）
 * 都会被按错误尺寸展开，整模型 UV 错位。逐面 UV 没有这个问题（GeckoLib 逐面路径直接用给定值）。
 *
 * <p><b>转换规则</b>：使用 GeckoLib 自己的箱型布局
 * （{@code GeometryQuadUvs#ofBoxUv}），但用<b>真实（不取整）</b>的 cube 尺寸：
 * <pre>
 * WEST  = (u+d+w,   v+d) size (d, h)
 * EAST  = (u,       v+d) size (d, h)
 * NORTH = (u+d,     v+d) size (w, h)
 * SOUTH = (u+d+w+d, v+d) size (w, h)
 * UP    = (u+d,     v)   size (w, d)
 * DOWN  = (u+d+w,   v+d) size (w, -d)
 * </pre>
 * 其中 {@code w/h/d = size[0/1/2]}。这样得到的逐面 UV 在 GeckoLib 下渲染结果
 * ==「箱型 UV 若不取整」的结果，也就是 Blockbench 里看到的布局。
 *
 * <p>镜像 cube（{@code mirror:true}，cube 级优先，否则取所属 bone 的）的 UP/DOWN 需要再翻一次 V：
 * GeckoLib 逐面路径在 mirror 时会交换上下顶点序（{@code VertexSet#verticesForQuad}），
 * 翻 V 正好抵消，保持与箱型路径一致。
 *
 * <p>用法：
 * <pre>
 * javac -cp &lt;含 gson 的 classpath&gt; -d tools/boxuv-to-faceuv/out tools/boxuv-to-faceuv/BoxUvToFaceUv.java
 * java -cp "tools/boxuv-to-faceuv/out;&lt;含 gson 的 classpath&gt;" com.linguauniversalis.tools.BoxUvToFaceUv &lt;输入.geo.json&gt; &lt;输出.geo.json&gt; [identifier]
 * </pre>
 * 第三个参数可选：同时把 {@code description.identifier} 改成给定值（例如 {@code geometry.nekomata}）。
 */
public final class BoxUvToFaceUv {
    /** 输出面顺序（与 Blockbench 导出一致）。 */
    private static final String[] FACES = {"north", "east", "south", "west", "up", "down"};

    private BoxUvToFaceUv() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: BoxUvToFaceUv <input.geo.json> <output.geo.json> [identifier]");
            System.exit(2);
        }
        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);
        String identifier = args.length > 2 ? args[2] : null;

        String text = Files.readString(input, StandardCharsets.UTF_8);
        JsonObject root = JsonParser.parseString(text).getAsJsonObject();

        int cubes = 0;
        int converted = 0;
        int alreadyFace = 0;
        for (JsonElement geometryElement : root.getAsJsonArray("minecraft:geometry")) {
            JsonObject geometry = geometryElement.getAsJsonObject();
            if (identifier != null && geometry.has("description")) {
                geometry.getAsJsonObject("description").addProperty("identifier", identifier);
            }
            for (JsonElement boneElement : geometry.getAsJsonArray("bones")) {
                JsonObject bone = boneElement.getAsJsonObject();
                Boolean boneMirror = optionalBoolean(bone, "mirror");
                if (!bone.has("cubes")) {
                    continue;
                }
                for (JsonElement cubeElement : bone.getAsJsonArray("cubes")) {
                    JsonObject cube = cubeElement.getAsJsonObject();
                    cubes++;
                    JsonElement uv = cube.get("uv");
                    if (uv == null || uv.isJsonObject()) {
                        alreadyFace++; // 已是逐面 UV：原样保留
                        continue;
                    }
                    JsonArray uvPair = uv.getAsJsonArray();
                    double u = uvPair.get(0).getAsDouble();
                    double v = uvPair.get(1).getAsDouble();
                    JsonArray size = cube.getAsJsonArray("size");
                    double w = size.get(0).getAsDouble();
                    double h = size.get(1).getAsDouble();
                    double d = size.get(2).getAsDouble();
                    Boolean cubeMirror = optionalBoolean(cube, "mirror");
                    boolean mirror = cubeMirror != null ? cubeMirror : Boolean.TRUE.equals(boneMirror);
                    cube.add("uv", faceUvs(u, v, w, h, d, mirror));
                    converted++;
                }
            }
        }

        String json = new GsonBuilder().setPrettyPrinting().create().toJson(root);
        Files.writeString(output, compactNumberArrays(json) + System.lineSeparator(), StandardCharsets.UTF_8);
        System.out.printf("cubes=%d converted(box->face)=%d keptFaceUv=%d -> %s%n",
                cubes, converted, alreadyFace, output);
    }

    private static JsonObject faceUvs(double u, double v, double w, double h, double d, boolean mirror) {
        double[][] rect = {
                {u + d, v + d, w, h},         // north
                {u, v + d, d, h},             // east
                {u + d + w + d, v + d, w, h}, // south
                {u + d + w, v + d, d, h},     // west
                {u + d, v, w, d},             // up
                {u + d + w, v + d, w, -d},    // down
        };
        if (mirror) {
            // GeckoLib 逐面路径在 mirror 时交换 up/down 顶点序 → 这里把 V 再翻一次以保持一致
            rect[4] = new double[] {u + d, v + d, w, -d};
            rect[5] = new double[] {u + d + w, v, w, d};
        }
        JsonObject out = new JsonObject();
        for (int i = 0; i < FACES.length; i++) {
            JsonObject face = new JsonObject();
            face.add("uv", numberPair(rect[i][0], rect[i][1]));
            face.add("uv_size", numberPair(rect[i][2], rect[i][3]));
            out.add(FACES[i], face);
        }
        return out;
    }

    /** 整数写成整数（{@code 16}），小数保留（{@code 0.4}）——便于人工比对。 */
    private static JsonArray numberPair(double a, double b) {
        JsonArray array = new JsonArray();
        array.add(number(a));
        array.add(number(b));
        return array;
    }

    /**
     * 把 Gson 美化输出里的纯数字数组收成一行（{@code [0, 16]}），
     * 让每个 cube 与其 uv 保持紧凑，便于与 Blockbench 导出对照；其余格式不动。
     */
    private static String compactNumberArrays(String json) {
        return java.util.regex.Pattern
                .compile("\\[\\s*((?:-?\\d+(?:\\.\\d+)?\\s*,\\s*)*-?\\d+(?:\\.\\d+)?)\\s*\\]",
                        java.util.regex.Pattern.DOTALL)
                .matcher(json)
                .replaceAll(match -> "[" + match.group(1).replaceAll("\\s+", "") + "]");
    }

    private static JsonElement number(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return new com.google.gson.JsonPrimitive((long) value);
        }
        return new com.google.gson.JsonPrimitive(value);
    }

    private static Boolean optionalBoolean(JsonObject object, String key) {
        if (!object.has(key)) {
            return null;
        }
        JsonElement element = object.get(key);
        return element.isJsonNull() ? null : element.getAsBoolean();
    }
}
