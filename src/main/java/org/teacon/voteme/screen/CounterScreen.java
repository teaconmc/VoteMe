package org.teacon.voteme.screen;

import com.google.common.base.Preconditions;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.DyeColor;
import org.apache.commons.lang3.tuple.Pair;
import org.teacon.voteme.network.ChangeNameByCounterPacket;
import org.teacon.voteme.network.ChangePropsByCounterPacket;
import org.teacon.voteme.network.ShowCounterPacket;
import org.teacon.voteme.network.VoteMePacketManager;
import org.teacon.voteme.vote.VoteList;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class CounterScreen extends Screen {
    private static final ResourceLocation TEXTURE = new ResourceLocation("voteme:textures/gui/counter.png");
    private static final Component EMPTY_ARTIFACT_TEXT = Component.translatable("gui.voteme.counter.empty_artifact").withStyle(style -> style.withItalic(true));

    private static final int BUTTON_TEXT_COLOR = 0xFF9DA95D;
    private static final int TEXT_COLOR = 0xFF000000 | DyeColor.BLACK.getTextColor();
    private static final int SELECTION_COLOR = 0xFF000000 | DyeColor.BLUE.getTextColor();
    private static final int SUGGESTION_COLOR = 0xFF000000 | DyeColor.GRAY.getTextColor();

    private static final float ARTIFACT_SCALE_FACTOR = 1.5F;

    private String artifact;
    private String oldArtifact;
    private int artifactCursorTick;

    private final UUID artifactUUID;
    private final int inventoryIndex;
    private final SortedSet<ResourceLocation> enabledInfos;
    private final List<ShowCounterPacket.Info> infoCollection;

    private BottomButton okButton;
    private BottomButton cancelButton;
    private BottomButton renameButton;
    private BottomSwitch bottomSwitch;
    private TextFieldHelper artifactInput;

    public CounterScreen(UUID artifactUUID, String artifactName, int inventoryIndex,
                         ResourceLocation category, List<ShowCounterPacket.Info> infos) {
        super(GameNarrator.NO_TITLE);
        this.artifactUUID = artifactUUID;
        this.inventoryIndex = inventoryIndex;
        this.artifact = this.oldArtifact = artifactName;
        Preconditions.checkArgument(infos.size() > 0);
        this.infoCollection = rotateAsFirst(infos, info -> category.equals(info.id));
        this.enabledInfos = infos.stream().filter(i -> i.enabledCurrently).map(i -> i.id).collect(Collectors.toCollection(TreeSet::new));
    }

    @Override
    protected void init() {
        Minecraft mc = Objects.requireNonNull(this.minecraft);
        this.addRenderableWidget(new ImageButton(this.width / 2 - 99, this.height / 2 - 20, 18, 19, 12, 207, 0, TEXTURE, this::onPrevButtonClick));
        this.addRenderableWidget(new ImageButton(this.width / 2 - 79, this.height / 2 - 20, 18, 19, 32, 207, 0, TEXTURE, this::onNextButtonClick));
        this.okButton = this.addRenderableWidget(new BottomButton(this.width / 2 + 61, this.height / 2 + 77, this::onOKButtonClick, Component.translatable("gui.voteme.counter.ok")));
        this.cancelButton = this.addRenderableWidget(new BottomButton(this.width / 2 + 61, this.height / 2 + 77, this::onCancelButtonClick, Component.translatable("gui.voteme.counter.cancel")));
        this.renameButton = this.addRenderableWidget(new BottomButton(this.width / 2 + 19, this.height / 2 + 77, this::onRenameButtonClick, Component.translatable("gui.voteme.counter.rename")));
        this.bottomSwitch = this.addRenderableWidget(new BottomSwitch(this.width / 2 - 98, this.height / 2 + 76, () -> this.enabledInfos.contains(this.infoCollection.iterator().next().id), this::onSwitchClick, Component.translatable("gui.voteme.counter.switch")));
        this.artifactInput = new TextFieldHelper(() -> this.artifact, text -> this.artifact = text, TextFieldHelper.createClipboardGetter(mc), TextFieldHelper.createClipboardSetter(mc), text -> mc.font.width(text) * ARTIFACT_SCALE_FACTOR <= 199);
        this.cancelButton.visible = this.renameButton.visible = this.bottomSwitch.visible = false;
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
        ++this.artifactCursorTick;
        this.bottomSwitch.visible = this.infoCollection.iterator().next().category.enabledModifiable;
        boolean canRename = !this.artifact.isEmpty() && !Objects.equals(this.artifact, this.oldArtifact);
        this.renameButton.visible = this.cancelButton.visible = canRename;
        this.okButton.visible = !canRename;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        // unicode texts input
        return this.artifactInput.charTyped(codePoint);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // physical keys input
        return this.artifactInput.keyPressed(keyCode) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        if (!this.oldArtifact.isEmpty()) {
            ShowCounterPacket.Info info = this.infoCollection.iterator().next();
            Iterable<ResourceLocation> enabled = () -> this.infoCollection.stream()
                    .filter(i -> !i.enabledCurrently && this.enabledInfos.contains(i.id)).map(i -> i.id).iterator();
            Iterable<ResourceLocation> disabled = () -> this.infoCollection.stream()
                    .filter(i -> i.enabledCurrently && !this.enabledInfos.contains(i.id)).map(i -> i.id).iterator();
            VoteMePacketManager.CHANNEL.sendToServer(ChangePropsByCounterPacket.create(
                    this.inventoryIndex, this.artifactUUID, info.id, enabled, disabled));
        }
    }

    private void onPrevButtonClick(Button button) {
        Collections.rotate(this.infoCollection, 1);
    }

    private void onNextButtonClick(Button button) {
        Collections.rotate(this.infoCollection, -1);
    }

    private void onOKButtonClick(Button button) {
        this.onClose();
    }

    private void onCancelButtonClick(Button button) {
        this.artifact = this.oldArtifact;
    }

    private void onRenameButtonClick(Button button) {
        ChangeNameByCounterPacket packet = ChangeNameByCounterPacket.create(this.inventoryIndex, this.artifactUUID, this.oldArtifact = this.artifact);
        VoteMePacketManager.CHANNEL.sendToServer(packet);
    }

    private void onSwitchClick(Button button) {
        ResourceLocation id = this.infoCollection.iterator().next().id;
        if (!this.enabledInfos.add(id)) {
            this.enabledInfos.remove(id);
        }
    }

    private void drawTooltips(GuiGraphics matrixStack, float partialTicks, int mouseX, int mouseY) {
        Minecraft mc = Objects.requireNonNull(this.minecraft);
        int dx = mouseX - this.width / 2, dy = mouseY - this.height / 2;
        if (dx >= -79 && dy >= -20 && dx < -61 && dy < -1) {
            matrixStack.renderTooltip(mc.font, Component.translatable("gui.voteme.counter.next"), mouseX, mouseY);
        }
        if (dx >= -99 && dy >= -20 && dx < -81 && dy < -1) {
            matrixStack.renderTooltip(mc.font, Component.translatable("gui.voteme.counter.prev"), mouseX, mouseY);
        }
        if (dx >= 73 && dy >= -19 && dx < 99 && dy < -2) {
            List<Component> tooltipList = new ArrayList<>();
            ShowCounterPacket.Info info = this.infoCollection.iterator().next();
            float finalWeight = info.finalStat.getWeight();
            int finalCount = info.finalStat.getVoteCount(), finalEffective = info.finalStat.getEffectiveCount();
            tooltipList.add(Component.translatable("gui.voteme.counter.score", finalCount, finalEffective));
            if (finalCount > 0) {
                for (int i = 5; i >= 1; --i) {
                    int voteCount = info.finalStat.getVoteCount(i);
                    String votePercentage = String.format("%.1f%%", 100.0F * voteCount / finalCount);
                    tooltipList.add(Component.translatable("gui.voteme.counter.score." + i, voteCount, votePercentage));
                }
                for (Pair<Component, VoteList.Stats> entry : info.scores) {
                    tooltipList.add(Component.empty());
                    VoteList.Stats childInfo = entry.getValue();
                    int childCount = childInfo.getVoteCount(), childEffective = childInfo.getEffectiveCount();
                    String weightPercentage = finalWeight > 0F ? String.format("%.1f%%", 100.0F * childInfo.getWeight() / finalWeight) : "--.-%";
                    tooltipList.add(Component.translatable("gui.voteme.counter.score.subgroup", entry.getKey(), weightPercentage, childCount, childEffective));
                    if (childCount > 0) {
                        for (int i = 5; i >= 1; --i) {
                            int voteCount = childInfo.getVoteCount(i);
                            String votePercentage = String.format("%.1f%%", 100.0F * voteCount / childCount);
                            tooltipList.add(Component.translatable("gui.voteme.counter.score." + i, voteCount, votePercentage));
                        }
                    }
                }
            }
            matrixStack.renderComponentTooltip(mc.font, tooltipList, mouseX, mouseY);
        }
        if (dx >= -98 && dy >= 76 && dx < -61 && dy < 96) {
            matrixStack.renderTooltip(mc.font, Component.translatable("gui.voteme.counter.switch"), mouseX, mouseY);
        }
    }

    private void drawGuiContainerBackgroundLayer(GuiGraphics matrixStack, float partialTicks, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        matrixStack.blit(TEXTURE, this.width / 2 - 111, this.height / 2 - 97, 0, 0, 234, 206);
    }

    private void drawGuiContainerForegroundLayer(GuiGraphics matrixStack, float partialTicks, int mouseX, int mouseY) {
        Minecraft mc = Objects.requireNonNull(this.minecraft);
        ShowCounterPacket.Info info = this.infoCollection.iterator().next();
        this.drawCategoryName(matrixStack, info, mc.font);
        this.drawCategoryDescription(matrixStack, info, mc.font);
        this.drawCategoryScore(matrixStack, info, mc.font);
        this.drawArtifactName(matrixStack, mc.font);
    }

    private void drawCategoryName(GuiGraphics matrixStack, ShowCounterPacket.Info info, Font font) {
        int x0 = this.width / 2 - 52, y0 = this.height / 2 - 14;
        matrixStack.drawString(font, info.category.name, x0, y0, TEXT_COLOR, false);
    }

    private void drawCategoryDescription(GuiGraphics matrixStack, ShowCounterPacket.Info info, Font font) {
        List<FormattedCharSequence> descriptions = font.split(info.category.description, 191);
        for (int size = Math.min(7, descriptions.size()), i = 0; i < size; ++i) {
            FormattedCharSequence description = descriptions.get(i);
            int x1 = this.width / 2 - 95, y1 = 9 * i + this.height / 2 + 6;
            matrixStack.drawString(font, description, x1, y1, TEXT_COLOR);
        }
    }

    private void drawCategoryScore(GuiGraphics matrixStack, ShowCounterPacket.Info info, Font font) {
        Component score = Component.literal(this.enabledInfos.contains(info.id) ? String.format("%.1f", info.finalStat.getFinalScore(6.0F)) : "--");
        int x2 = this.width / 2 - font.width(score) / 2 + 87, y2 = this.height / 2 - 14;
        matrixStack.drawString(font, score, x2, y2, TEXT_COLOR, false);
    }

    private void drawArtifactName(GuiGraphics matrixStack, Font font) {
        matrixStack.pose().pushPose();
        float scale = ARTIFACT_SCALE_FACTOR;
        matrixStack.pose().scale(scale, scale, scale);
        int x3 = this.width / 2 + 1, y3 = this.height / 2 - 43;
        int start = this.artifactInput.getSelectionPos(), end = this.artifactInput.getCursorPos();
        if (this.artifact.isEmpty()) {
            // draw hint text
            int dx0 = font.width(EMPTY_ARTIFACT_TEXT) / 2;
            matrixStack.drawString(font, EMPTY_ARTIFACT_TEXT, (int) (x3 / scale - dx0), (int) (y3 / scale), SUGGESTION_COLOR, false);
        } else {
            // draw actual text
            int dx = font.width(this.artifact) / 2;
            boolean renderArtifactCursor = this.artifactCursorTick / 6 % 2 == 0;
            matrixStack.drawString(font, Component.literal(this.artifact), (int) (x3 / scale - dx), (int) (y3 / scale), TEXT_COLOR, false);
            if (end >= 0) {
                // draw cursor
                if (renderArtifactCursor) {
                    if (end >= this.artifact.length()) {
                        int dx1 = font.width(this.artifact);
                        matrixStack.drawString(font, Component.literal("_"), (int) (x3 / scale - dx + dx1), (int) (y3 / scale), TEXT_COLOR, false);
                    } else {
                        int dx1 = font.width(this.artifact.substring(0, end));
                        matrixStack.fill((int) (x3 / scale - dx + dx1), (int) (y3 / scale) - 1, (int) (x3 / scale - dx + dx1) + 1, (int) (y3 / scale) + 9, TEXT_COLOR);
                    }
                }
                // draw selection
                if (start != end) {
                    // RenderSystem.disableTexture();
                    RenderSystem.enableColorLogicOp();
                    RenderSystem.logicOp(GlStateManager.LogicOp.OR_REVERSE);
                    int dx2 = font.width(this.artifact.substring(0, end));
                    int dx3 = font.width(this.artifact.substring(0, start));
                    matrixStack.fill((int) (x3 / scale - dx + dx2), (int) (y3 / scale), (int) (x3 / scale - dx + dx3), (int) (y3 / scale) + 9, SELECTION_COLOR);
                    RenderSystem.disableColorLogicOp();
                    // RenderSystem.enableTexture();
                }
            }
        }
        matrixStack.pose().popPose();
    }

    private static <T> List<T> rotateAsFirst(List<T> initial, Predicate<T> filter) {
        int dist = IntStream.range(0, initial.size()).filter(i -> filter.test(initial.get(i))).findFirst().orElse(0);
        ArrayList<T> result = new ArrayList<>(initial);
        Collections.rotate(result, -dist);
        return result;
    }

    private static class BottomButton extends ImageButton {
        public BottomButton(int x, int y, Button.OnPress onPress, Component title) {
            super(x, y, 39, 19, 136, 207, 0, CounterScreen.TEXTURE, 256, 256, onPress, title);
        }

        @Override
        public void renderWidget(GuiGraphics matrixStack, int mouseX, int mouseY, float partialTicks) {
            super.renderWidget(matrixStack, mouseX, mouseY, partialTicks);
            Font font = Minecraft.getInstance().font;
            float dx = font.width(this.getMessage()) / 2F;
            float x = this.getX() + (this.width + 1) / 2F - dx, y = this.getY() + (this.height - 9) / 2F;
            Component p_283140_ = this.getMessage();
            matrixStack.drawString(font, p_283140_, (int) x, (int) y, BUTTON_TEXT_COLOR, false);
        }
    }

    private static class BottomSwitch extends Button {
        private final BooleanSupplier enabled;
        private float ticksFromPressing;

        public BottomSwitch(int x, int y, BooleanSupplier enabled, Button.OnPress onPress, Component title) {
            super(x, y, 37, 20, title, onPress, DEFAULT_NARRATION);
            this.ticksFromPressing = Float.POSITIVE_INFINITY;
            this.enabled = enabled;
        }

        @Override
        public void onPress() {
            super.onPress();
            this.ticksFromPressing = 0F;
        }

        @Override
        public void renderWidget(GuiGraphics matrixStack, int mouseX, int mouseY, float partialTicks) {
            // calculate offset and alpha
            double progress = Math.tanh((this.ticksFromPressing += partialTicks) / 3);
            double transition = this.enabled.getAsBoolean() ? progress : 1 - progress;
            int offset = (int) Math.round(17 * transition);
            float alpha = (float) transition;

            // render background and switch-off button
            RenderSystem.enableDepthTest();
            matrixStack.blit(TEXTURE, this.getX(), this.getY(), 13, 228, this.width, this.height, 256, 256);
            matrixStack.blit(TEXTURE, this.getX() + offset + 2, this.getY() + 2, 69, 230, 16, 16, 256, 256);

            // render switch-on button with blend
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
            matrixStack.blit(TEXTURE, this.getX() + offset + 2, this.getY() + 2, 52, 230, 16, 16, 256, 256);
        }
    }
}
