package com.linguauniversalis.event;

import com.linguauniversalis.core.behavior.SleepRules;
import com.linguauniversalis.core.species.SpeciesProfile;
import com.linguauniversalis.core.species.SpeciesRegistry;
import com.linguauniversalis.core.species.SpawnRule;
import com.linguauniversalis.entity.MonsterGirlEntity;
import com.linguauniversalis.registry.LUEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * **数据包物种的自然生成器**。
 *
 * <p>为什么不用原版刷怪表：原版 spawn placement 要在 mod 初始化时注册完毕，而数据包物种是运行期
 * 才出现的。所以这里由我们自己周期性尝试：按每位玩家所在位置随机取点，
 * 用一个物种档案里的 {@link SpawnRule}（群系 / 高度 / 光照 / 昼夜）判断能否生成。
 *
 * <p>节奏：每 {@value #INTERVAL_TICKS} tick 尝试一次，每次只有 {@value #CHANCE} 的概率真正生成，
 * 避免刷怪过密。只处理 {@code spawnType == NATURAL} 且带 {@code spawnRule} 的物种。
 */
public final class SpeciesSpawnHandler {
    /** 尝试间隔（tick）：20 秒一次。 */
    private static final int INTERVAL_TICKS = 400;
    /** 每次尝试的触发概率。 */
    private static final float CHANCE = 0.35F;
    /** 生成半径范围（距玩家）。 */
    private static final int MIN_DISTANCE = 24;
    private static final int MAX_DISTANCE = 48;

    private static int tickCounter;

    private SpeciesSpawnHandler() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter < INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.players().isEmpty()) {
                continue;
            }
            if (level.getRandom().nextFloat() > CHANCE) {
                continue;
            }
            for (ServerPlayer player : level.players()) {
                trySpawnNear(level, player);
            }
        }
    }

    /**
     * 群系是否允许（{@link SpawnRule#biomes()} 为空 = 不限）。
     *
     * <p>为什么这么做：26.2 的 {@code Level#getBiome} 返回<b>裸</b> {@code Holder}，直接取 id
     * 过不了泛型推断。这里改成"按 id 从注册表反查 Holder，再与当前群系做<b>实例比较</b>" ——
     * 注册表里群系是单例，实例相同即同一群系，完全不碰泛型。
     * 支持 {@code #namespace:tag} 形式（按标签匹配）。
     */
    private static boolean biomeAllowed(ServerLevel level, BlockPos pos, SpawnRule rule) {
        if (rule.biomes().isEmpty()) {
            return true;
        }
        var registry = level.registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.BIOME);
        var here = level.getBiome(pos).value();
        for (String entry : rule.biomes()) {
            boolean tag = entry.startsWith("#");
            String raw = tag ? entry.substring(1) : entry;
            net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(raw);
            if (id == null) {
                continue;
            }
            if (tag) {
                var tagKey = net.minecraft.tags.TagKey.create(
                        net.minecraft.core.registries.Registries.BIOME, id);
                for (var holder : registry.getTagOrEmpty(tagKey)) {
                    if (holder.value() == here) {
                        return true;
                    }
                }
            } else {
                var key = net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.BIOME, id);
                var found = registry.getOptional(key);
                if (found.isPresent() && found.get() == here) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void trySpawnNear(ServerLevel level, ServerPlayer player) {
        List<SpeciesProfile> candidates = new ArrayList<>();
        int totalWeight = 0;
        // 昼夜：用世界时间判定（13000–23000 视为夜晚）
        long dayTime = level.getGameTime() % 24000L;
        boolean night = dayTime >= 13000L && dayTime <= 23000L;
        for (SpeciesProfile profile : SpeciesRegistry.all()) {
            SpawnRule rule = profile.spawnRule();
            if (rule == null || profile.spawnType() != SpeciesProfile.SpawnType.NATURAL) {
                continue;
            }
            // 昼夜：夜行物种只在夜里刷，昼行只在白天刷
            boolean nocturnal = SleepRules.nocturnal(profile);
            if (nocturnal != night) {
                continue;
            }
            candidates.add(profile);
            totalWeight += Math.max(1, rule.weight());
        }
        if (candidates.isEmpty() || totalWeight <= 0) {
            return;
        }

        RandomSource random = level.getRandom();
        int pick = random.nextInt(totalWeight);
        SpeciesProfile chosen = null;
        for (SpeciesProfile profile : candidates) {
            pick -= Math.max(1, profile.spawnRule().weight());
            if (pick < 0) {
                chosen = profile;
                break;
            }
        }
        if (chosen == null) {
            return;
        }

        SpawnRule rule = chosen.spawnRule();
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
            BlockPos pos = BlockPos.containing(
                    player.getX() + Math.cos(angle) * distance,
                    player.getY(),
                    player.getZ() + Math.sin(angle) * distance);
            BlockPos surface = level.getHeightmapPos(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos);
            if (!level.isLoaded(surface)) {
                continue;
            }
            if (!rule.allowsY(surface.getY())) {
                continue;
            }
            if (!biomeAllowed(level, surface, rule)) {
                continue;
            }
            int light = level.getMaxLocalRawBrightness(surface);
            if (!rule.allowsLight(light)) {
                continue;
            }
            // 群体生成：spawn_group 决定一次刷几只（围绕基准点小范围散开，避免完全重叠）
            int count = Math.max(1, rule.group());
            for (int member = 0; member < count; member++) {
                double offsetX = member == 0 ? 0.0D : (random.nextDouble() - 0.5D) * 4.0D;
                double offsetZ = member == 0 ? 0.0D : (random.nextDouble() - 0.5D) * 4.0D;
                MonsterGirlEntity girl = MonsterGirlEntity.ofSpecies(
                        LUEntities.MONSTER_GIRL.get(), level, chosen.id());
                girl.snapTo(surface.getX() + 0.5D + offsetX, surface.getY(),
                        surface.getZ() + 0.5D + offsetZ,
                        random.nextFloat() * 360.0F, 0.0F);
                level.addFreshEntity(girl);
            }
            return;
        }
    }
}
