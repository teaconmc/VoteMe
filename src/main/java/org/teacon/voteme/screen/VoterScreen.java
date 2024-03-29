package org.teacon.voteme.screen;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import org.teacon.voteme.network.ShowVoterPacket;
import org.teacon.voteme.network.SubmitCommentPacket;
import org.teacon.voteme.network.SubmitVotePacket;
import org.teacon.voteme.network.VoteMePacketManager;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class VoterScreen extends Screen {
    private static final ResourceLocation TEXTURE = new ResourceLocation("voteme:textures/gui/voter.png");

    private static final int BUTTON_TEXT_COLOR = 0xFFFFFFFF;
    private static final int TEXT_COLOR = 0xFF000000 | DyeColor.BLACK.getTextColor();
    private static final int HINT_COLOR = 0xFF000000 | DyeColor.WHITE.getTextColor();

    private static final float ARTIFACT_SCALE_FACTOR = 1.5F;

    private final UUID artifactID;
    private final String artifact;

    private final Map<ResourceLocation, Integer> votes;
    private final List<ShowVoterPacket.Info> infoCollection;
    final List<String> oldComments;
    List<String> currentComments;

    private int slideBottom, slideTop;
    private BottomButton clearButton, unsetButton;

    public VoterScreen(UUID artifactID, String artifactName, List<ShowVoterPacket.Info> infos, List<String> comments) {
        super(GameNarrator.NO_TITLE);
        this.artifactID = artifactID;
        this.artifact = artifactName;
        this.currentComments = (this.oldComments = comments);
        this.votes = new LinkedHashMap<>(infos.size());
        this.infoCollection = ImmutableList.copyOf(infos);
    }

    @Override
    protected void init() {
        Objects.requireNonNull(this.minecraft);
        this.addRenderableWidget(new SideSlider(this.width / 2 - 103, this.height / 2 - 55, 24 * this.infoCollection.size(), this::onSlideClick, this::onSliderChange, Component.literal("Slider")));
        this.clearButton = this.addRenderableWidget(new BottomButton(this.width / 2 - 104, this.height / 2 + 82, true, this::onClearButtonClick, Component.translatable("gui.voteme.voter.clear")));
        this.unsetButton = this.addRenderableWidget(new BottomButton(this.width / 2 - 104, this.height / 2 + 82, true, this::onUnsetButtonClick, Component.translatable("gui.voteme.voter.unset")));
        this.addRenderableWidget(new BottomButton(this.width / 2 + 52, this.height / 2 + 82, false, this::onOKButtonClick, Component.translatable("gui.voteme.voter.ok")));
        this.addRenderableWidget(new BottomButton(this.width / 2 - 26, this.height / 2 + 82, false, this::onCommentButtonClick, Component.translatable("gui.voteme.voter.comment")));
    }

    @Override
    public void render(GuiGraphics matrixStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(matrixStack);
        this.drawGuiContainerBackgroundLayer(matrixStack, partialTicks, mouseX, mouseY);
        super.render(matrixStack, mouseX, mouseY, partialTicks);
        this.drawGuiContainerForegroundLayer(matrixStack, partialTicks, mouseX, mouseY);
        this.drawTooltips(matrixStack, partialTicks, mouseX, mouseY);
    }

    @Override
    public void tick() {
        this.clearButton.visible = this.votes.isEmpty();
        this.unsetButton.visible = !this.votes.isEmpty();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        if (!this.votes.isEmpty()) {
            SubmitVotePacket packet = SubmitVotePacket.create(this.artifactID, this.votes);
            VoteMePacketManager.CHANNEL.sendToServer(packet);
        }
        if (!this.currentComments.equals(this.oldComments)) {
            SubmitCommentPacket packet = SubmitCommentPacket.create(this.artifactID, this.currentComments);
            VoteMePacketManager.CHANNEL.sendToServer(packet);
        }
    }

    private void onOKButtonClick(Button button) {
        this.onClose();
    }

    private void onCommentButtonClick(Button button) {
        Objects.requireNonNull(this.minecraft).setScreen(new CommentScreen(this));
    }

    private void onClearButtonClick(Button button) {
        for (ShowVoterPacket.Info info : this.infoCollection) {
            if (info.level != 0) {
                this.votes.put(info.id, 0);
            } else {
                this.votes.remove(info.id);
            }
        }
    }

    private void onUnsetButtonClick(Button button) {
        this.votes.clear();
    }

    private void onSlideClick(double dx, double dy) {
        int current = Mth.floor((this.slideTop + dy) / 24);
        if (current >= 0 && current < this.infoCollection.size()) {
            int offsetX = Mth.floor((dx - 91) / 15);
            int offsetY = Mth.floor((this.slideTop + dy - current * 24 - 4) / 15);
            if (offsetX >= 1 && offsetX <= 5 && offsetY == 0) {
                this.votes.put(this.infoCollection.get(current).id, offsetX);
            }
        }
    }

    private void onSliderChange(int top, int bottom) {
        this.slideTop = top;
        this.slideBottom = bottom;
    }

    private void drawTooltips(GuiGraphics matrixStack, float partialTicks, int mouseX, int mouseY) {
        Minecraft mc = Objects.requireNonNull(this.minecraft);
        int dx = mouseX - this.width / 2, dy = mouseY - this.height / 2;
        if (dx >= -103 && dy >= -55 && dx < -6 && dy < 77) {
            int current = (this.slideTop + dy + 55) / 24;
            if (current >= 0 && current < this.infoCollection.size()) {
                Component desc = this.infoCollection.get(current).category.description;
                List<FormattedCharSequence> descList = this.font.split(desc, 191);
                matrixStack.renderTooltip(mc.font, descList.subList(0, Math.min(7, descList.size())), mouseX, mouseY);
            }
        }
    }

    private void drawGuiContainerBackgroundLayer(GuiGraphics matrixStack, float partialTicks, int mouseX, int mouseY) {
        Minecraft mc = Objects.requireNonNull(this.minecraft);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        matrixStack.blit(TEXTURE, this.width / 2 - 111, this.height / 2 - 55, 0, 42, 234, 132);
        this.drawCategoriesInSlide(matrixStack, mc);
        matrixStack.blit(TEXTURE, this.width / 2 - 111, this.height / 2 - 97, 0, 0, 234, 42);
        matrixStack.blit(TEXTURE, this.width / 2 - 111, this.height / 2 + 77, 0, 174, 234, 32);
    }

    private void drawGuiContainerForegroundLayer(GuiGraphics matrixStack, float partialTicks, int mouseX, int mouseY) {
        Minecraft mc = Objects.requireNonNull(this.minecraft);
        this.drawArtifactName(matrixStack, mc.font);
    }

    private void drawCategoriesInSlide(GuiGraphics matrixStack, Minecraft mc) {
        int infoSize = this.infoCollection.size();
        if (infoSize > 0) {
            int top = Math.max(0, this.slideTop / 24);
            int bottom = Math.min(infoSize, (this.slideBottom + 24) / 24);
            for (int i = top; i < bottom; ++i) {
                int offset = i * 24 - this.slideTop;
                int x0 = this.width / 2 - 103, y0 = this.height / 2 - 55 + offset;
                // draw button texture
                matrixStack.blit(TEXTURE, x0, y0, 8, 207, 192, 24);
                ShowVoterPacket.Info info = this.infoCollection.get(i);
                // draw category string
                int x1 = x0 + 48 - font.width(info.category.name) / 2, y1 = y0 + 8;
                matrixStack.drawString(mc.font, info.category.name, x1, y1, TEXT_COLOR, false);
                // draw votes
                int voteLevel = this.votes.getOrDefault(info.id, info.level);
                for (int j = 0; j < 5; ++j) {
                    int x2 = x0 + 106 + 15 * j, y2 = y0 + 4, u2 = 221, v2 = voteLevel > j ? 239 : 206;
                    matrixStack.blit(TEXTURE, x2, y2, u2, v2, 15, 15);
                }
            }
        } else {
            MutableComponent next = Component.translatable("gui.voteme.voter.no_category.next");
            int x1 = this.width / 2 - 7, dx1 = mc.font.width(next) / 2, y1 = this.height / 2 + 15;
            matrixStack.drawString(mc.font, next, x1 - dx1, y1, HINT_COLOR, false);

            matrixStack.pose().pushPose();
            float scale = ARTIFACT_SCALE_FACTOR;
            matrixStack.pose().scale(scale, scale, scale);

            MutableComponent prev = Component.translatable("gui.voteme.voter.no_category.prev");
            int x2 = this.width / 2 - 7, dx2 = mc.font.width(prev) / 2, y2 = this.height / 2 - 9;
            matrixStack.drawString(mc.font, prev, (int) (x2 / scale - dx2), (int) (y2 / scale), HINT_COLOR, false);

            matrixStack.pose().popPose();
        }
    }

    private void drawArtifactName(GuiGraphics matrixStack, Font font) {
        matrixStack.pose().pushPose();
        float scale = ARTIFACT_SCALE_FACTOR;
        matrixStack.pose().scale(scale, scale, scale);
        int x3 = this.width / 2 + 1, y3 = this.height / 2 - 82, dx = font.width(this.artifact) / 2;
        matrixStack.drawString(font, Component.literal(this.artifact), (int) (x3 / scale - dx), (int) (y3 / scale), TEXT_COLOR, false);
        matrixStack.pose().popPose();
    }

    private static class BottomButton extends Button {
        private final boolean isRed;

        public BottomButton(int x, int y, boolean isRed, Button.OnPress onPress, Component title) {
            super(x, y, 51, 19, title, onPress, DEFAULT_NARRATION);
            this.isRed = isRed;
        }

        @Override
        public void renderWidget(GuiGraphics matrixStack, int mouseX, int mouseY, float partialTicks) {
            // render button texture
            RenderSystem.enableDepthTest();
            int u0 = (this.isRed ? 7 : 60) + (this.isHovered ? 106 : 0), v0 = 234;
            matrixStack.blit(TEXTURE, this.getX(), this.getY(), u0, v0, this.width, this.height);
            // render button text
            Font font = Minecraft.getInstance().font;
            float dx = font.width(this.getMessage()) / 2F;
            float x = this.getX() + (this.width + 1) / 2F - dx, y = this.getY() + (this.height - 8) / 2F;
            Component p_283140_ = this.getMessage();
            matrixStack.drawString(font, p_283140_, (int) x, (int) y, BUTTON_TEXT_COLOR, false);
        }
    }

    private static class SideSlider extends AbstractWidget {
        private final ChangeListener changeListener;
        private final ClickListener clickListener;

        private final int halfSliderHeight;
        private final int totalHeight;

        private double slideCenter;

        public SideSlider(int x, int y, int totalHeight, ClickListener clickListener, ChangeListener changeListener, Component title) {
            super(x, y, 205, 132, title);
            this.totalHeight = totalHeight;
            this.clickListener = clickListener;
            this.changeListener = changeListener;
            this.halfSliderHeight = Mth.clamp(Math.round(132F / totalHeight * 60F), 10, 60);
            this.slideCenter = 6 + this.halfSliderHeight;
            changeListener.onChange(0, 132);
        }

        private void changeSlideCenter(double center) {
            int min = 6 + this.halfSliderHeight, max = 126 - this.halfSliderHeight;
            center = Mth.clamp(center, min, max);
            if (this.slideCenter != center) {
                double ratio = Mth.inverseLerp(this.slideCenter = center, min, max);
                int top = Math.toIntExact(Math.round(ratio * (this.totalHeight - 132)));
                this.changeListener.onChange(top, top + 132);
            }
        }

        @Override
        public void renderWidget(GuiGraphics matrixStack, int mouseX, int mouseY, float partialTicks) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            double dx = mouseX - this.getX(), dy = mouseY - this.getY() - this.slideCenter;
            int x0 = this.getX() + 192, y0 = Math.toIntExact(Math.round(mouseY - dy));
            int v0 = this.isHovered && dx >= 192 && dy < this.halfSliderHeight && dy >= -this.halfSliderHeight ? 133 : 4;
            matrixStack.blit(TEXTURE, x0, y0 - this.halfSliderHeight, 239, v0, 13, this.halfSliderHeight - 8);
            matrixStack.blit(TEXTURE, x0, y0 - 8, 239, v0 + 52, 13, 16);
            matrixStack.blit(TEXTURE, x0, y0 + 8, 239, v0 + 128 - this.halfSliderHeight, 13, this.halfSliderHeight - 8);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            if (delta != 0) {
                this.changeSlideCenter(this.slideCenter - 12 * delta);
                return true;
            }
            return false;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            double dx = mouseX - this.getX(), dy = mouseY - this.getY();
            if (dx >= 192 && dy >= this.slideCenter + this.halfSliderHeight) {
                this.changeSlideCenter(this.slideCenter + 1);
            }
            if (dx >= 192 && dy < this.slideCenter - this.halfSliderHeight) {
                this.changeSlideCenter(this.slideCenter - 1);
            }
            if (dx >= 0 && dx < 192) {
                this.clickListener.onClick(dx, dy);
            }
        }

        @Override
        protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
            double dx = mouseX - this.getX(), dy = mouseY - this.getY() - this.slideCenter;
            if (dx >= 192 && dy < this.halfSliderHeight && dy >= -this.halfSliderHeight) {
                this.changeSlideCenter(this.slideCenter + dragY);
            }
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            super.playDownSound(Minecraft.getInstance().getSoundManager());
        }

        @Override
        public void playDownSound(SoundManager handler) {
            // do nothing
        }

        @Override
        protected MutableComponent createNarrationMessage() {
            return Component.translatable("gui.narrate.slider", this.getMessage());
        }

        @Override
        public void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, this.createNarrationMessage());
            if (this.active) {
                if (this.isFocused()) {
                    output.add(NarratedElementType.USAGE, Component.translatable("narration.slider.usage.focused"));
                } else {
                    output.add(NarratedElementType.USAGE, Component.translatable("narration.slider.usage.hovered"));
                }
            }
        }

        @FunctionalInterface
        public interface ClickListener {
            void onClick(double dx, double dy);
        }

        @FunctionalInterface
        public interface ChangeListener {
            void onChange(int top, int bottom);
        }
    }
}
