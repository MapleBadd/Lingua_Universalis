package com.linguauniversalis.compat.jei;

import com.linguauniversalis.registry.LURegistries;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * JEI 适配：给"只能靠掉落获得"的物品提供一条**纯文字说明**的配方页。
 *
 * <p>为什么不用普通合成配方：命缕没有合成方式，玩家在 JEI 里搜到它时应该被告知"从哪来"。
 * 这里注册一个自定义类别（{@code lingua_universalis:drop_hint}），页面里只有一行字，
 * 并把物品本身登记为该页面的催化剂 —— 在 JEI 里点命缕就能看到"击败魔物娘必定掉落"。
 *
 * <p>JEI 是**可选前置**（客户端装才有用），服务端不会加载本类。
 */
@JeiPlugin
public class LuJeiPlugin implements IModPlugin {
    /** 自定义配方类型：掉落说明。 */
    public static final IRecipeType<DropHint> DROP_HINT_TYPE =
            IRecipeType.create("lingua_universalis", "drop_hint", DropHint.class);

    /** 一条掉落说明（物品 + 说明文本）。 */
    public record DropHint(ItemStack stack, Component text) {
    }

    @Override
    public Identifier getPluginUid() {
        return Identifier.parse("lingua_universalis:jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new DropHintCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(DROP_HINT_TYPE, List.of(new DropHint(
                new ItemStack(LURegistries.FATUM_FILUM.get()),
                Component.translatable("jei.lingua_universalis.drop_hint.fatum_filum"))));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        // 以物品本身为催化剂：在 JEI 里点它就会显示这一页
        registration.addCraftingStation(DROP_HINT_TYPE, LURegistries.FATUM_FILUM.get());
    }

    /** "掉落说明"页面：左边物品、右边一行文字。 */
    private static final class DropHintCategory implements IRecipeCategory<DropHint> {
        private static final int WIDTH = 150;
        private static final int HEIGHT = 26;
        private final IDrawable icon;

        private DropHintCategory(IGuiHelper guiHelper) {
            this.icon = guiHelper.createDrawableItemStack(new ItemStack(LURegistries.FATUM_FILUM.get()));
        }

        @Override
        public IRecipeType<DropHint> getRecipeType() {
            return DROP_HINT_TYPE;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("jei.lingua_universalis.category.drop_hint");
        }

        @Override
        public int getWidth() {
            return WIDTH;
        }

        @Override
        public int getHeight() {
            return HEIGHT;
        }

        @Override
        public IDrawable getIcon() {
            return this.icon;
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, DropHint recipe, IFocusGroup focuses) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, 1, 5).addItemStacks(List.of(recipe.stack()));
        }

        @Override
        public void draw(DropHint recipe, IRecipeSlotsView slotsView, GuiGraphicsExtractor graphics,
                         double mouseX, double mouseY) {
            graphics.text(Minecraft.getInstance().font, recipe.text(), 24, 9, 0xFF404040, false);
        }
    }
}