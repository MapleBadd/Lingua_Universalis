package com.linguauniversalis.block;

import com.linguauniversalis.registry.LURegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * 誊写台（设计汇总 §10 图鉴/誊写）：把<b>初稿</b>放上去的开台面。
 *
 * <p>美术：{@code 美术资源/誊写台.geo.json} 与 {@code 誊写台.png}（GeckoLib 渲染，见
 * {@code client/ScriptoriumRenderer}）。方块本体的 vanilla 模型是**空模型**（只有 particle），
 * 实际外观完全由 GeckoLib 画；物品栏用同一份 geo 模型（special 物品模型）。
 *
 * <p>朝向：{@link #FACING} 在放置时朝玩家（与熔炉/工作台一致），台面上的书也跟着转。
 *
 * <p>交互：
 * <ul>
 *   <li><b>手持初稿右键</b>（台面空）：把初稿放进 {@link ScriptoriumBlockEntity} 的内部容器；</li>
 *   <li><b>右键</b>（台面上有书）：打开图鉴 —— 目前是<b>占位界面</b>；</li>
 *   <li><b>Shift + 右键</b>：把初稿取回背包（保留物品 NBT）。</li>
 * </ul>
 */
public class ScriptoriumBlock extends HorizontalDirectionalBlock implements EntityBlock {
    /** 台面上是否有初稿（方块实体容器的**渲染镜像**，由方块实体维护）。 */
    public static final BooleanProperty HAS_BOOK = BooleanProperty.create("has_book");
    /** 朝向：放置时朝玩家。 */
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

    /** 碰撞外形：底座 + 立柱 + 台面（斜板按外接盒算）。 */
    private static final VoxelShape SHAPE = Shapes.join(
            Shapes.or(
                    Block.box(1.0, 0.0, 1.0, 15.0, 3.0, 15.0),
                    Block.box(3.0, 3.0, 3.0, 13.0, 14.0, 13.0)),
            Block.box(0.0, 14.0, 0.0, 16.0, 17.0, 16.0),
            BooleanOp.OR);

    /** 方块编解码器（与其它朝向方块一致：{@code simpleCodec}）。 */
    public static final com.mojang.serialization.MapCodec<ScriptoriumBlock> CODEC =
            simpleCodec(ScriptoriumBlock::new);

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    public ScriptoriumBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HAS_BOOK, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HAS_BOOK);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(HAS_BOOK, false);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                          CollisionContext context) {
        return SHAPE;
    }

    /** 外观完全由 GeckoLib 渲染器绘制。 */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ScriptoriumBlockEntity(LURegistries.SCRIPTORIUM_BE.get(), pos, state);
    }

    /** 方块状态里的"台面上有书"（客户端唯一可靠的来源）。 */
    public static boolean hasBookState(BlockState state) {
        return state.hasProperty(HAS_BOOK) && state.getValue(HAS_BOOK);
    }

    /** 取台面的容器（放书/取书都走它）。 */
    @Nullable
    private static ScriptoriumBlockEntity desk(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof ScriptoriumBlockEntity desk)) {
            return null;
        }
        // 状态自愈（仅服务端）：容器有书但方块状态镜像没跟上时，强制同步一次。
        // 客户端不能这么测：客户端方块实体**拿不到容器内容**（容器不同步），
        // 一旦按客户端 BE 判断就会把"有书"误判成"没书"，导致 Shift 取书失效。
        if (level.isClientSide()) {
            return desk;
        }
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(HAS_BOOK) && state.getValue(HAS_BOOK) != desk.hasBook()) {
            level.setBlock(pos, state.setValue(HAS_BOOK, desk.hasBook()),
                    net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
        return desk;
    }

    // ------------------------------------------------------------------ 交互（统一入口）
    /**
     * 交互的唯一入口：`useItemOn`（手持物品）与 `useWithoutItem`（空手）都转到这里。
     *
     * <p>规则表（Shift 永远只用于"取书"，不会放书）：
     * <pre>
     *   Shift + 台上有书   → 取书回背包
     *   Shift + 台上没书   → 什么都不做
     *   手持初稿 + 空台     → 放书
     *   空手 + 台上有书     → 打开图鉴
     *   其它               → 不处理
     * </pre>
     *
     * <p>两端数据来源不同，但**判定顺序一致**：
     * 客户端只能读方块状态（容器不同步），服务端以容器为准。为了让交互包一定发得出去，
     * 客户端对"Shift 取书 / 放书 / 开图鉴"这几种明确意图直接返回 SUCCESS，
     * 真正的状态变更与最终裁决都在服务端。
     */
    private static InteractionResult handleUse(BlockState state, Level level, BlockPos pos,
                                               Player player, ItemStack stack) {
        boolean shift = player.isShiftKeyDown();
        boolean holdsDraft = !stack.isEmpty() && stack.getItem() == LURegistries.FIRST_DRAFT.get();
        boolean clientSide = level.isClientSide();
        boolean hasBook = clientSide ? hasBookState(state) : hasBook(level, pos);

        if (clientSide) {
            // 只回答"这次交互我要不要"，不做任何状态变更
            if (shift) {
                return InteractionResult.SUCCESS;          // 取书：交给服务端裁决
            }
            if (hasBook) {
                return InteractionResult.SUCCESS;          // 开图鉴
            }
            return holdsDraft ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }

        // ---------------- 服务端（权威） ----------------
        if (!(level.getBlockEntity(pos) instanceof ScriptoriumBlockEntity desk)) {
            return InteractionResult.PASS;
        }
        if (shift) {
            if (!desk.hasBook()) {
                return InteractionResult.PASS;
            }
            giveBackBook(level, pos, player, desk);
            return InteractionResult.SUCCESS;
        }
        if (!desk.hasBook()) {
            if (!holdsDraft) {
                return InteractionResult.PASS;
            }
            if (!desk.putBook(stack)) {
                return InteractionResult.PASS;
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            level.playSound(null, pos, SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        }
        // 台上有书 + 非 Shift → 打开图鉴
        openCodex(level, pos, player);
        return InteractionResult.SUCCESS;
    }

    /** 台面是否有书（服务端以容器为准）。 */
    private static boolean hasBook(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ScriptoriumBlockEntity desk && desk.hasBook();
    }

    /** 打开该玩家的图鉴界面（把解锁掩码一起打包）。 */
    private static void openCodex(Level level, BlockPos pos, Player player) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return;
        }
        java.util.Map<String, Integer> masks = new java.util.LinkedHashMap<>();
        com.linguauniversalis.core.encyclopedia.Encyclopedia.PlayerEncyclopedia book =
                com.linguauniversalis.codex.ServerCodex.of(serverPlayer.getStringUUID());
        for (com.linguauniversalis.core.species.SpeciesProfile profile
                : com.linguauniversalis.core.species.SpeciesRegistry.all()) {
            int mask = 0;
            for (com.linguauniversalis.core.encyclopedia.Encyclopedia.Kind kind
                    : com.linguauniversalis.core.encyclopedia.Encyclopedia.Kind.values()) {
                if (book.isUnlocked(profile.id(), kind)) {
                    mask |= 1 << kind.ordinal();
                }
            }
            masks.put(profile.id(), mask);
        }
        com.linguauniversalis.menu.CodexMenu.open(serverPlayer, pos, masks);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        return handleUse(state, level, pos, player, stack);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        return handleUse(state, level, pos, player, ItemStack.EMPTY);
    }
    /**
     * 破坏方块：台面上有书就把它掉在原地（只掉这一次，掉完清空容器）。
     *
     * <p>为什么要在代码里做：方块实体实现了 {@code Container}，不同版本/不同加载器对
     * "容器内容是否自动掉落"的行为不一致，之前就出现过"空台子敲掉也掉初稿"的错觉。
     * 这里显式处理，保证 **有书才掉、掉完就空**。
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ScriptoriumBlockEntity desk) {
            ItemStack book = desk.takeBook();
            if (!book.isEmpty()) {
                net.minecraft.world.level.block.Block.popResource(level, pos, book);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** 把台面上的书还给玩家（背包放不下就掉在原地）。 */
    private static void giveBackBook(Level level, BlockPos pos, Player player, ScriptoriumBlockEntity desk) {
        ItemStack book = desk.takeBook();
        if (book.isEmpty()) {
            return;
        }
        // 用 placeItemBackInInventory：会立刻把物品放进背包并广播容器变化，
        // 客户端马上就能看到（直接 add() 不会主动同步，会出现"等一会才到手里"的错觉）
        player.getInventory().placeItemBackInInventory(book);
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            serverPlayer.containerMenu.broadcastChanges();
            serverPlayer.inventoryMenu.broadcastChanges();
        }
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8F, 1.2F);
    }

    /** 面向（渲染器据此绕 Y 轴旋转模型与台面上的书）。 */
    public static Direction facingOf(BlockState state) {
        return state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH;
    }
}
