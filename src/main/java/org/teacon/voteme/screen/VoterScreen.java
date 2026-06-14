package org.teacon.voteme.screen;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.teacon.voteme.network.ShowVoterPacket;
import org.teacon.voteme.network.SubmitCommentPacket;
import org.teacon.voteme.network.SubmitVotePacket;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class VoterScreen extends Screen {
    private static final Identifier TEXTURE = Identifier.parse("voteme:textures/gui/voter.png");
    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 256;

    private static final int BUTTON_TEXT_COLOR = 0xFFFFFFFF;
    private static final int TEXT_COLOR = 0xFF000000 | DyeColor.BLACK.getTextColor();
    private static final int HINT_COLOR = 0xFF000000 | DyeColor.WHITE.getTextColor();

    private static final float ARTIFACT_SCALE_FACTOR = 1.5F;

    private final UUID artifactID;
    private final String artifact;

    private final Map<Identifier, Integer> votes;
    private final ImmutableList<ShowVoterPacket.Info> infoCollection;
    private final ImmutableList<String> oldComments;
    private final List<String> currentComments;

    private int slideBottom;
    private int slideTop;
    private BottomButton clearButton;
    private BottomButton unsetButton;

    public VoterScreen(UUID artifactID, String artifactName, List<ShowVoterPacket.Info> infos, List<String> comments) {
        super(GameNarrator.NO_TITLE);
        this.artifactID = artifactID;
        this.artifact = artifactName;
        this.oldComments = ImmutableList.copyOf(comments);
        this.currentComments = Lists.newArrayList(comments);
        this.votes = new LinkedHashMap<>(infos.size());
        this.infoCollection = ImmutableList.copyOf(infos);
    }

    @Override
    protected void init() {
        this.addRenderableWidget(new SideSlider(
                this.width / 2 - 103, this.height / 2 - 55, 24 * this.infoCollection.size(),
                this::onSlideClick, this::onSliderChange, Component.literal("Slider")
        ));
        this.clearButton = this.addRenderableWidget(new BottomButton(
                this.width / 2 - 104, this.height / 2 + 82, true, this::onClearButtonClick, Component.translatable("gui.voteme.voter.clear")
        ));
        this.unsetButton = this.addRenderableWidget(new BottomButton(
                this.width / 2 - 104, this.height / 2 + 82, true, this::onUnsetButtonClick, Component.translatable("gui.voteme.voter.unset")
        ));
        this.addRenderableWidget(new BottomButton(
                this.width / 2 + 52, this.height / 2 + 82, false, this::onOKButtonClick, Component.translatable("gui.voteme.voter.ok")
        ));
        this.addRenderableWidget(new BottomButton(
                this.width / 2 - 26, this.height / 2 + 82, false, this::onCommentButtonClick, Component.translatable("gui.voteme.voter.comment")
        ));
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
    public boolean isInGameUi() {
        return true;
    }

    @Override
    public void removed() {
        if (!this.votes.isEmpty()) {
            ClientPacketDistributor.sendToServer(SubmitVotePacket.create(this.artifactID, this.votes));
        }
        if (!this.currentComments.equals(this.oldComments)) {
            ClientPacketDistributor.sendToServer(SubmitCommentPacket.create(this.artifactID, this.currentComments));
        }
    }

    private void onOKButtonClick(Button button) {
        this.onClose();
    }

    private void onCommentButtonClick(Button button) {
        this.minecraft.pushGuiLayer(new CommentScreen(this.currentComments));
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

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        this.renderForeground(graphics);
        this.extractTooltip(graphics, mouseX, mouseY);
    }

    private void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int dx = mouseX - this.width / 2;
        int dy = mouseY - this.height / 2;
        if (dx >= -103 && dy >= -55 && dx < -6 && dy < 77) {
            int current = (this.slideTop + dy + 55) / 24;
            if (current >= 0 && current < this.infoCollection.size()) {
                Component desc = this.infoCollection.get(current).category.description;
                List<FormattedCharSequence> descList = this.font.split(desc, 191);
                graphics.setTooltipForNextFrame(this.font, descList.subList(0, Math.min(7, descList.size())), mouseX, mouseY);
            }
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.width / 2 - 111, this.height / 2 - 55, 0, 42, 234, 132, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        this.drawCategoriesInSlide(graphics, this.minecraft);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.width / 2 - 111, this.height / 2 - 97, 0, 0, 234, 42, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.width / 2 - 111, this.height / 2 + 77, 0, 174, 234, 32, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    private void renderForeground(GuiGraphicsExtractor graphics) {
        this.drawArtifactName(graphics, this.font);
    }

    private void drawCategoriesInSlide(GuiGraphicsExtractor graphics, Minecraft mc) {
        int infoSize = this.infoCollection.size();
        if (infoSize > 0) {
            int top = Math.max(0, this.slideTop / 24);
            int bottom = Math.min(infoSize, (this.slideBottom + 24) / 24);
            for (int i = top; i < bottom; ++i) {
                int offset = i * 24 - this.slideTop;
                int x0 = this.width / 2 - 103;
                int y0 = this.height / 2 - 55 + offset;
                graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x0, y0, 8, 207, 192, 24, TEXTURE_WIDTH, TEXTURE_HEIGHT);

                ShowVoterPacket.Info info = this.infoCollection.get(i);
                int x1 = x0 + 48 - this.font.width(info.category.name) / 2;
                int y1 = y0 + 8;
                graphics.text(mc.font, info.category.name, x1, y1, TEXT_COLOR, false);

                int voteLevel = this.votes.getOrDefault(info.id, info.level);
                for (int j = 0; j < 5; ++j) {
                    int x2 = x0 + 106 + 15 * j;
                    int y2 = y0 + 4;
                    int u2 = 221;
                    int v2 = voteLevel > j ? 239 : 206;
                    graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x2, y2, u2, v2, 15, 15, TEXTURE_WIDTH, TEXTURE_HEIGHT);
                }
            }
        } else {
            MutableComponent next = Component.translatable("gui.voteme.voter.no_category.next");
            int x1 = this.width / 2 - 7;
            int dx1 = mc.font.width(next) / 2;
            int y1 = this.height / 2 + 15;
            graphics.text(mc.font, next, x1 - dx1, y1, HINT_COLOR, false);

            graphics.pose().pushMatrix();
            float scale = ARTIFACT_SCALE_FACTOR;
            graphics.pose().scale(scale, scale);

            MutableComponent prev = Component.translatable("gui.voteme.voter.no_category.prev");
            int x2 = this.width / 2 - 7;
            int dx2 = mc.font.width(prev) / 2;
            int y2 = this.height / 2 - 9;
            graphics.text(mc.font, prev, (int) (x2 / scale - dx2), (int) (y2 / scale), HINT_COLOR, false);
            graphics.pose().popMatrix();
        }
    }

    private void drawArtifactName(GuiGraphicsExtractor graphics, Font font) {
        graphics.pose().pushMatrix();
        float scale = ARTIFACT_SCALE_FACTOR;
        graphics.pose().scale(scale, scale);
        int x = this.width / 2 + 1;
        int y = this.height / 2 - 82;
        int dx = font.width(this.artifact) / 2;
        graphics.text(font, Component.literal(this.artifact), (int) (x / scale - dx), (int) (y / scale), TEXT_COLOR, false);
        graphics.pose().popMatrix();
    }

    public static class BottomButton extends Button {
        private final boolean isRed;

        public BottomButton(int x, int y, boolean isRed, Button.OnPress onPress, Component title) {
            super(x, y, 51, 19, title, onPress, DEFAULT_NARRATION);
            this.isRed = isRed;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            int u0 = (this.isRed ? 7 : 60) + (this.isHoveredOrFocused() ? 106 : 0);
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.getX(), this.getY(), u0, 234, this.width, this.height, TEXTURE_WIDTH, TEXTURE_HEIGHT);

            Font font = Minecraft.getInstance().font;
            float dx = font.width(this.getMessage()) / 2F;
            float x = this.getX() + (this.width + 1) / 2F - dx;
            float y = this.getY() + (this.height - 8) / 2F;
            graphics.text(font, this.getMessage(), (int) x, (int) y, BUTTON_TEXT_COLOR, false);
        }
    }

    private static class SideSlider extends AbstractWidget {
        private final ChangeListener changeListener;
        private final ClickListener clickListener;
        private final int halfSliderHeight;
        private final int totalHeight;

        private double slideCenter;

        private SideSlider(int x, int y, int totalHeight, ClickListener clickListener, ChangeListener changeListener, Component title) {
            super(x, y, 205, 132, title);
            this.totalHeight = totalHeight;
            this.clickListener = clickListener;
            this.changeListener = changeListener;
            this.halfSliderHeight = Mth.clamp(Math.round(132F / Math.max(totalHeight, 1) * 60F), 10, 60);
            this.slideCenter = 6 + this.halfSliderHeight;
            changeListener.onChange(0, 132);
        }

        private void changeSlideCenter(double center) {
            int min = 6 + this.halfSliderHeight;
            int max = 126 - this.halfSliderHeight;
            center = Mth.clamp(center, min, max);
            if (this.slideCenter != center) {
                double ratio = Mth.inverseLerp(this.slideCenter = center, min, max);
                int top = Math.toIntExact(Math.round(ratio * (this.totalHeight - 132)));
                this.changeListener.onChange(top, top + 132);
            }
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            double dx = mouseX - this.getX();
            double dy = mouseY - this.getY() - this.slideCenter;
            int x0 = this.getX() + 192;
            int y0 = Math.toIntExact(Math.round(mouseY - dy));
            int v0 = this.isHovered && dx >= 192 && dy < this.halfSliderHeight && dy >= -this.halfSliderHeight ? 133 : 4;
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x0, y0 - this.halfSliderHeight, 239, v0, 13, this.halfSliderHeight - 8, TEXTURE_WIDTH, TEXTURE_HEIGHT);
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x0, y0 - 8, 239, v0 + 52, 13, 16, TEXTURE_WIDTH, TEXTURE_HEIGHT);
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x0, y0 + 8, 239, v0 + 128 - this.halfSliderHeight, 13, this.halfSliderHeight - 8, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            if (scrollY != 0) {
                this.changeSlideCenter(this.slideCenter - 12 * scrollY);
                return true;
            }
            return false;
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            double dx = event.x() - this.getX();
            double dy = event.y() - this.getY();
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
        protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
            double dx = event.x() - this.getX();
            double dy = event.y() - this.getY() - this.slideCenter;
            if (dx >= 192 && dy < this.halfSliderHeight && dy >= -this.halfSliderHeight) {
                this.changeSlideCenter(this.slideCenter + dragY);
            }
        }

        @Override
        public void onRelease(MouseButtonEvent event) {
            super.playDownSound(Minecraft.getInstance().getSoundManager());
        }

        @Override
        public void playDownSound(SoundManager handler) {
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
        private interface ClickListener {
            void onClick(double dx, double dy);
        }

        @FunctionalInterface
        private interface ChangeListener {
            void onChange(int top, int bottom);
        }
    }
}
