package org.teacon.voteme.screen;

import com.google.common.base.Preconditions;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.glfw.GLFW;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.List;

import static net.minecraft.network.chat.CommonComponents.EMPTY;

/**
 * A book-style editor for vote comments.
 *
 * @author 3TUSK
 */
@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class CommentScreen extends Screen {
    private static final Component DONE = CommonComponents.GUI_DONE;
    private static final Component CLEAR = Component.translatable("gui.voteme.voter.clear");
    private static final Component UNSET = Component.translatable("gui.voteme.voter.unset");
    private static final Identifier TEXTURE = Identifier.parse("voteme:textures/gui/comment.png");

    private static final int TEXT_COLOR = 0xFF000000 | DyeColor.WHITE.getTextColor();
    private static final int TEXT_WIDTH = 192;
    private static final int TEXT_HEIGHT = 160;
    private static final int MAX_PAGES = 10;
    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 256;

    private boolean isModified;
    private int currentPage;
    private final List<String> pages;
    private MultiLineEditBox pageEdit;
    private PageButton backButton;
    private Button clearButton;
    private Button unsetButton;
    private final List<String> parentComments;

    public CommentScreen(List<String> parentComments) {
        super(GameNarrator.NO_TITLE);
        this.parentComments = parentComments;
        this.pages = new ArrayList<>(Math.max(1, parentComments.size()));
        this.pages.addAll(parentComments.isEmpty() ? List.of("") : parentComments);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        this.pageEdit = this.addRenderableWidget(MultiLineEditBox.builder()
                .setShowBackground(false)
                .setShowDecorations(false)
                .setTextColor(TEXT_COLOR)
                .setCursorColor(TEXT_COLOR)
                .setTextShadow(false)
                .setX(this.width / 2 - 104)
                .setY(this.height / 2 - 90)
                .build(this.font, TEXT_WIDTH + 8, TEXT_HEIGHT + 8, EMPTY));
        this.addRenderableWidget(new VoterScreen.BottomButton(
                this.width / 2 + 52, this.height / 2 + 82, false, this::onOKButtonClick, DONE));
        this.clearButton = this.addRenderableWidget(new VoterScreen.BottomButton(
                this.width / 2 - 104, this.height / 2 + 82, true, this::onClearButtonClick, CLEAR));
        this.unsetButton = this.addRenderableWidget(new VoterScreen.BottomButton(
                this.width / 2 - 104, this.height / 2 + 82, true, this::onUnsetButtonClick, UNSET));
        this.addRenderableWidget(new PageButton(
                this.width / 2 + 36, this.height / 2 + 82, 1, button -> this.pageForward(), EMPTY));
        this.backButton = this.addRenderableWidget(new PageButton(
                this.width / 2 - 49, this.height / 2 + 82, -1, button -> this.pageBack(), EMPTY));
        this.pageEdit.setValueListener(text -> {
            if (!text.equals(this.pages.get(this.currentPage))) {
                this.pages.set(this.currentPage, text);
                this.isModified = true;
                this.updateButtonVisibility();
            }
        });
        this.pageEdit.setValue(this.pages.get(this.currentPage), true);
        this.updateButtonVisibility();
    }

    @Override
    protected void setInitialFocus() {
        this.setInitialFocus(this.pageEdit);
    }

    @Override
    public void removed() {
        if (this.isModified) {
            this.pages.removeIf(StringUtils::isBlank);
            this.parentComments.clear();
            this.parentComments.addAll(this.pages);
        }
    }

    private void onOKButtonClick(Button button) {
        this.onClose();
    }

    private void onClearButtonClick(Button button) {
        this.pages.clear();
        this.pages.add("");
        this.isModified = this.parentComments.stream().anyMatch(StringUtils::isNotBlank);
        this.currentPage = 0;
        this.pageEdit.setValue("", true);
        this.setFocused(this.pageEdit);
        this.updateButtonVisibility();
    }

    private void onUnsetButtonClick(Button button) {
        this.pages.clear();
        this.pages.addAll(this.parentComments.isEmpty() ? List.of("") : this.parentComments);
        this.isModified = false;
        this.currentPage = Math.min(this.currentPage, this.pages.size() - 1);
        this.pageEdit.setValue(this.pages.get(this.currentPage), true);
        this.setFocused(this.pageEdit);
        this.updateButtonVisibility();
    }

    private void pageBack() {
        if (this.currentPage > 0) {
            this.currentPage--;
        }
        this.pageEdit.setValue(this.pages.get(this.currentPage), true);
        this.setFocused(this.pageEdit);
        this.updateButtonVisibility();
    }

    private void pageForward() {
        if (this.currentPage < this.pages.size() - 1) {
            this.currentPage++;
        } else if (this.pages.size() < MAX_PAGES) {
            this.pages.add("");
            this.isModified = true;
            this.currentPage++;
        }
        this.pageEdit.setValue(this.pages.get(this.currentPage), true);
        this.setFocused(this.pageEdit);
        this.updateButtonVisibility();
    }

    private void updateButtonVisibility() {
        this.backButton.visible = this.currentPage > 0;
        this.clearButton.visible = !this.isModified;
        this.unsetButton.visible = this.isModified;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_PAGE_UP) {
            this.pageBack();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_PAGE_DOWN) {
            this.pageForward();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        graphics.textRenderer().accept(TextAlignment.CENTER, this.width / 2, this.height / 2 + 87, this.getPageMessage());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.width / 2 - 111, this.height / 2 - 97, 0, 0, 234, 206, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.width / 2 - 49, this.height / 2 + 82, 7, 211, 96, 19, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    private Component getPageMessage() {
        return Component.translatable("book.pageIndicator", this.currentPage + 1, this.pages.size())
                .withColor(0xFF000000)
                .withoutShadow();
    }

    public static class PageButton extends Button {
        private final int sign;

        public PageButton(int x, int y, int sign, Button.OnPress onPress, Component title) {
            super(x, y, 11, 19, title, onPress, DEFAULT_NARRATION);
            Preconditions.checkArgument(sign == 1 || sign == -1);
            this.sign = sign;
        }

        @Override
        public boolean shouldTakeFocusAfterInteraction() {
            return false;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            int u0 = 227 + Mth.sign(this.sign) * 7;
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.getX(), this.getY(), u0, 234, this.width, this.height, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        }
    }
}
