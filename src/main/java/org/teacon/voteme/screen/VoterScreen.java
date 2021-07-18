package org.teacon.voteme.screen;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.chat.NarratorChatListener;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.item.DyeColor;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.teacon.voteme.network.ShowVoterPacket;
import org.teacon.voteme.network.SubmitVotePacket;
import org.teacon.voteme.network.VoteMePacketManager;
import org.teacon.voteme.vote.VoteListHandler;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;

@OnlyIn(Dist.CLIENT)
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class VoterScreen extends Screen {
    private static final ResourceLocation TEXTURE = new ResourceLocation("voteme:textures/gui/voter.png");
    private static final ITextComponent EMPTY_ARTIFACT_TEXT = new TranslationTextComponent("gui.voteme.voter.empty_artifact").modifyStyle(style -> style.setItalic(true));

    private static final int BUTTON_TEXT_COLOR = 0xFFFFFFFF;
    private static final int TEXT_COLOR = 0xFF000000 | DyeColor.BLACK.getTextColor();
    private static final int SUGGESTION_COLOR = 0xFF000000 | DyeColor.GRAY.getTextColor();

    private static final float ARTIFACT_SCALE_FACTOR = 1.5F;

    private final UUID artifactID;
    private final String artifact;

    private final Map<ResourceLocation, Integer> votes;
    private final List<ShowVoterPacket.Info> infoCollection;

    private int slideBottom, slideTop;
    private BottomButton clearButton, unsetButton;

    public VoterScreen(UUID artifactID, List<ShowVoterPacket.Info> infos) {
        super(NarratorChatListener.EMPTY);
        this.artifactID = artifactID;
        this.votes = new LinkedHashMap<>(infos.size());
        this.infoCollection = ImmutableList.copyOf(infos);
        this.artifact = VoteListHandler.getArtifactName(artifactID);
    }

    @Override
    protected void init() {
        Objects.requireNonNull(this.minecraft);
        this.addButton(new SideSlider(this.width / 2 - 103, this.height / 2 - 55, 24 * this.infoCollection.size(), this::onSlideClick, this::onSliderChange, new StringTextComponent("Slider")));
        this.clearButton = this.addButton(new BottomButton(this.width / 2 - 104, this.height / 2 + 82, true, this::onClearButtonClick, new TranslationTextComponent("gui.voteme.voter.clear")));
        this.unsetButton = this.addButton(new BottomButton(this.width / 2 - 104, this.height / 2 + 82, true, this::onUnsetButtonClick, new TranslationTextComponent("gui.voteme.voter.unset")));
        this.addButton(new BottomButton(this.width / 2 + 52, this.height / 2 + 82, false, this::onOKButtonClick, new TranslationTextComponent("gui.voteme.voter.ok")));
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
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
    public void onClose() {
        if (!this.votes.isEmpty()) {
            SubmitVotePacket packet = SubmitVotePacket.create(this.artifactID, this.votes);
            VoteMePacketManager.CHANNEL.sendToServer(packet);
        }
    }

    private void onOKButtonClick(Button button) {
        this.closeScreen();
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
        int current = MathHelper.floor((this.slideTop + dy) / 24);
        if (current >= 0 && current < this.infoCollection.size()) {
            int offsetX = MathHelper.floor((dx - 91) / 15);
            int offsetY = MathHelper.floor((this.slideTop + dy - current * 24 - 4) / 15);
            if (offsetX >= 1 && offsetX <= 5 && offsetY == 0) {
                this.votes.put(this.infoCollection.get(current).id, offsetX);
            }
        }
    }

    private void onSliderChange(int top, int bottom) {
        this.slideTop = top;
        this.slideBottom = bottom;
    }

    private void drawTooltips(MatrixStack matrixStack, float partialTicks, int mouseX, int mouseY) {
        int dx = mouseX - this.width / 2, dy = mouseY - this.height / 2;
        if (dx >= -103 && dy >= -55 && dx < -6 && dy < 77) {
            int current = (this.slideTop + dy + 55) / 24;
            if (current >= 0 && current < this.infoCollection.size()) {
                ITextComponent desc = this.infoCollection.get(current).category.description;
                List<IReorderingProcessor> descList = this.font.trimStringToWidth(desc, 191);
                this.renderTooltip(matrixStack, descList.subList(0, Math.min(7, descList.size())), mouseX, mouseY);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void drawGuiContainerBackgroundLayer(MatrixStack matrixStack, float partialTicks, int mouseX, int mouseY) {
        Minecraft mc = Objects.requireNonNull(this.minecraft);
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        mc.getTextureManager().bindTexture(TEXTURE);
        this.blit(matrixStack, this.width / 2 - 111, this.height / 2 - 55, 0, 42, 234, 132);
        this.drawCategoriesInSlide(matrixStack, mc);
        this.blit(matrixStack, this.width / 2 - 111, this.height / 2 - 97, 0, 0, 234, 42);
        this.blit(matrixStack, this.width / 2 - 111, this.height / 2 + 77, 0, 174, 234, 32);
    }

    private void drawGuiContainerForegroundLayer(MatrixStack matrixStack, float partialTicks, int mouseX, int mouseY) {
        Minecraft mc = Objects.requireNonNull(this.minecraft);
        this.drawArtifactName(matrixStack, mc.fontRenderer);
    }

    private void drawCategoriesInSlide(MatrixStack matrixStack, Minecraft mc) {
        int top = Math.max(0, this.slideTop / 24);
        int bottom = Math.min(this.infoCollection.size(), (this.slideBottom + 24) / 24);
        for (int i = top; i < bottom; ++i) {
            int offset = i * 24 - this.slideTop;
            int x0 = this.width / 2 - 103, y0 = this.height / 2 - 55 + offset;
            // draw button texture
            this.blit(matrixStack, x0, y0, 8, 207, 192, 24);
            ShowVoterPacket.Info info = this.infoCollection.get(i);
            // draw category string
            int x1 = x0 + 48 - font.getStringPropertyWidth(info.category.name) / 2, y1 = y0 + 8;
            mc.fontRenderer.func_243248_b(matrixStack, info.category.name, x1, y1, TEXT_COLOR);
            // draw votes
            int voteLevel = this.votes.getOrDefault(info.id, info.level);
            mc.getTextureManager().bindTexture(TEXTURE);
            for (int j = 0; j < 5; ++j) {
                int x2 = x0 + 106 + 15 * j, y2 = y0 + 4, u2 = 221, v2 = voteLevel > j ? 239 : 206;
                this.blit(matrixStack, x2, y2, u2, v2, 15, 15);
            }
        }
    }

    private void drawArtifactName(MatrixStack matrixStack, FontRenderer font) {
        matrixStack.push();
        float scale = ARTIFACT_SCALE_FACTOR;
        matrixStack.scale(scale, scale, scale);
        int x3 = this.width / 2 + 1, y3 = this.height / 2 - 82;
        if (this.artifact.isEmpty()) {
            // draw hint text
            int dx0 = font.getStringPropertyWidth(EMPTY_ARTIFACT_TEXT) / 2;
            font.func_243248_b(matrixStack, EMPTY_ARTIFACT_TEXT, x3 / scale - dx0, y3 / scale, SUGGESTION_COLOR);
        } else {
            // draw actual text
            int dx = font.getStringWidth(this.artifact) / 2;
            font.func_243248_b(matrixStack, new StringTextComponent(this.artifact), x3 / scale - dx, y3 / scale, TEXT_COLOR);
        }
        matrixStack.pop();
    }

    private static class BottomButton extends Button {
        private final boolean isRed;

        public BottomButton(int x, int y, boolean isRed, IPressable onPress, ITextComponent title) {
            super(x, y, 51, 19, title, onPress);
            this.isRed = isRed;
        }

        @Override
        public void renderButton(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
            Minecraft mc = Minecraft.getInstance();
            mc.getTextureManager().bindTexture(VoterScreen.TEXTURE);
            // render button texture
            RenderSystem.enableDepthTest();
            int u0 = (this.isRed ? 7 : 60) + (this.isHovered() ? 106 : 0), v0 = 234;
            this.blit(matrixStack, this.x, this.y, u0, v0, this.width, this.height);
            // render button tooltip
            if (this.isHovered()) {
                this.renderToolTip(matrixStack, mouseX, mouseY);
            }
            // render button text
            FontRenderer font = Minecraft.getInstance().fontRenderer;
            float dx = font.getStringPropertyWidth(this.getMessage()) / 2F;
            float x = this.x + (this.width + 1) / 2F - dx, y = this.y + (this.height - 8) / 2F;
            font.func_243248_b(matrixStack, this.getMessage(), x, y, BUTTON_TEXT_COLOR);
        }
    }

    private static class SideSlider extends Widget {
        private final ChangeListener changeListener;
        private final ClickListener clickListener;

        private final int halfSliderHeight;
        private final int totalHeight;

        private double slideCenter;

        public SideSlider(int x, int y, int totalHeight, ClickListener clickListener, ChangeListener changeListener, ITextComponent title) {
            super(x, y, 205, 132, title);
            this.totalHeight = totalHeight;
            this.clickListener = clickListener;
            this.changeListener = changeListener;
            this.halfSliderHeight = MathHelper.clamp(Math.round(132F / totalHeight * 60F), 10, 60);
            this.slideCenter = 6 + this.halfSliderHeight;
            changeListener.onChange(0, 132);
        }

        private void changeSlideCenter(double center) {
            int min = 6 + this.halfSliderHeight, max = 126 - this.halfSliderHeight;
            center = MathHelper.clamp(center, min, max);
            if (this.slideCenter != center) {
                double ratio = MathHelper.func_233020_c_(this.slideCenter = center, min, max);
                int top = Math.toIntExact(Math.round(ratio * (this.totalHeight - 132)));
                this.changeListener.onChange(top, top + 132);
            }
        }

        @Override
        @SuppressWarnings("deprecation")
        public void renderButton(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
            RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            Minecraft.getInstance().getTextureManager().bindTexture(TEXTURE);
            double dx = mouseX - this.x, dy = mouseY - this.y - this.slideCenter;
            int x0 = this.x + 192, y0 = Math.toIntExact(Math.round(mouseY - dy));
            int v0 = this.isHovered && dx >= 192 && dy < this.halfSliderHeight && dy >= -this.halfSliderHeight ? 133 : 4;
            this.blit(matrixStack, x0, y0 - this.halfSliderHeight, 239, v0, 13, this.halfSliderHeight - 8);
            this.blit(matrixStack, x0, y0 - 8, 239, v0 + 52, 13, 16);
            this.blit(matrixStack, x0, y0 + 8, 239, v0 + 128 - this.halfSliderHeight, 13, this.halfSliderHeight - 8);
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
            double dx = mouseX - this.x, dy = mouseY - this.y;
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
            double dx = mouseX - this.x, dy = mouseY - this.y - this.slideCenter;
            if (dx >= 192 && dy < this.halfSliderHeight && dy >= -this.halfSliderHeight) {
                this.changeSlideCenter(this.slideCenter + dragY);
            }
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            super.playDownSound(Minecraft.getInstance().getSoundHandler());
        }

        @Override
        protected int getYImage(boolean isHovered) {
            return 0;
        }

        @Override
        public void playDownSound(SoundHandler handler) {
            // do nothing
        }

        @Override
        protected IFormattableTextComponent getNarrationMessage() {
            return new TranslationTextComponent("gui.narrate.slider", this.getMessage());
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
