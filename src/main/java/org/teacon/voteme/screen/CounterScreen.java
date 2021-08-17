package org.teacon.voteme.screen;

import com.google.common.base.Preconditions;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.chat.NarratorChatListener;
import net.minecraft.client.gui.fonts.TextInputUtil;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.widget.button.ImageButton;
import net.minecraft.item.DyeColor;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.commons.lang3.tuple.Pair;
import org.teacon.voteme.network.ApplyCounterPacket;
import org.teacon.voteme.network.EditCounterPacket;
import org.teacon.voteme.network.EditNamePacket;
import org.teacon.voteme.network.VoteMePacketManager;
import org.teacon.voteme.vote.VoteList;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@OnlyIn(Dist.CLIENT)
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class CounterScreen extends Screen {
    private static final ResourceLocation TEXTURE = new ResourceLocation("voteme:textures/gui/counter.png");
    private static final ITextComponent EMPTY_ARTIFACT_TEXT = new TranslationTextComponent("gui.voteme.counter.empty_artifact").modifyStyle(style -> style.setItalic(true));

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
    private final List<EditCounterPacket.Info> infoCollection;

    private BottomButton okButton;
    private BottomButton cancelButton;
    private BottomButton renameButton;
    private BottomSwitch bottomSwitch;
    private TextInputUtil artifactInput;

    public CounterScreen(UUID artifactUUID, String artifactName, int inventoryIndex,
                         ResourceLocation category, List<EditCounterPacket.Info> infos) {
        super(NarratorChatListener.EMPTY);
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
        this.addButton(new ImageButton(this.width / 2 - 99, this.height / 2 - 20, 18, 19, 12, 207, 0, TEXTURE, this::onPrevButtonClick));
        this.addButton(new ImageButton(this.width / 2 - 79, this.height / 2 - 20, 18, 19, 32, 207, 0, TEXTURE, this::onNextButtonClick));
        this.okButton = this.addButton(new BottomButton(this.width / 2 + 61, this.height / 2 + 77, this::onOKButtonClick, new TranslationTextComponent("gui.voteme.counter.ok")));
        this.cancelButton = this.addButton(new BottomButton(this.width / 2 + 61, this.height / 2 + 77, this::onCancelButtonClick, new TranslationTextComponent("gui.voteme.counter.cancel")));
        this.renameButton = this.addButton(new BottomButton(this.width / 2 + 19, this.height / 2 + 77, this::onRenameButtonClick, new TranslationTextComponent("gui.voteme.counter.rename")));
        this.bottomSwitch = this.addButton(new BottomSwitch(this.width / 2 - 98, this.height / 2 + 76, () -> this.enabledInfos.contains(this.infoCollection.iterator().next().id), this::onSwitchClick, new TranslationTextComponent("gui.voteme.counter.switch")));
        this.artifactInput = new TextInputUtil(() -> this.artifact, text -> this.artifact = text, TextInputUtil.getClipboardTextSupplier(mc), TextInputUtil.getClipboardTextSetter(mc), text -> mc.fontRenderer.getStringWidth(text) * ARTIFACT_SCALE_FACTOR <= 199);
        this.cancelButton.visible = this.renameButton.visible = this.bottomSwitch.visible = false;
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
        return this.artifactInput.putChar(codePoint);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // physical keys input
        return this.artifactInput.specialKeyPressed(keyCode) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (!this.oldArtifact.isEmpty()) {
            EditCounterPacket.Info info = this.infoCollection.iterator().next();
            Iterable<ResourceLocation> enabled = () -> this.infoCollection.stream()
                    .filter(i -> !i.enabledCurrently && this.enabledInfos.contains(i.id)).map(i -> i.id).iterator();
            Iterable<ResourceLocation> disabled = () -> this.infoCollection.stream()
                    .filter(i -> i.enabledCurrently && !this.enabledInfos.contains(i.id)).map(i -> i.id).iterator();
            VoteMePacketManager.CHANNEL.sendToServer(ApplyCounterPacket.create(
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
        this.closeScreen();
    }

    private void onCancelButtonClick(Button button) {
        this.artifact = this.oldArtifact;
    }

    private void onRenameButtonClick(Button button) {
        EditNamePacket packet = EditNamePacket.create(this.artifactUUID, this.oldArtifact = this.artifact);
        VoteMePacketManager.CHANNEL.sendToServer(packet);
    }

    private void onSwitchClick(Button button) {
        ResourceLocation id = this.infoCollection.iterator().next().id;
        if (!this.enabledInfos.add(id)) {
            this.enabledInfos.remove(id);
        }
    }

    private void drawTooltips(MatrixStack matrixStack, float partialTicks, int mouseX, int mouseY) {
        int dx = mouseX - this.width / 2, dy = mouseY - this.height / 2;
        if (dx >= -79 && dy >= -20 && dx < -61 && dy < -1) {
            this.renderTooltip(matrixStack, new TranslationTextComponent("gui.voteme.counter.next"), mouseX, mouseY);
        }
        if (dx >= -99 && dy >= -20 && dx < -81 && dy < -1) {
            this.renderTooltip(matrixStack, new TranslationTextComponent("gui.voteme.counter.prev"), mouseX, mouseY);
        }
        if (dx >= 73 && dy >= -19 && dx < 99 && dy < -2) {
            List<ITextComponent> tooltipList = new ArrayList<>();
            EditCounterPacket.Info info = this.infoCollection.iterator().next();
            float finalWeight = info.finalStat.getWeight();
            int finalCount = info.finalStat.getVoteCount(), finalEffective = info.finalStat.getEffectiveCount();
            tooltipList.add(new TranslationTextComponent("gui.voteme.counter.score", finalCount, finalEffective));
            if (finalCount > 0) {
                for (int i = 5; i >= 1; --i) {
                    int voteCount = info.finalStat.getVoteCount(i);
                    String votePercentage = String.format("%.1f%%", 100.0F * voteCount / finalCount);
                    tooltipList.add(new TranslationTextComponent("gui.voteme.counter.score." + i, voteCount, votePercentage));
                }
                for (Pair<ITextComponent, VoteList.Stats> entry : info.scores) {
                    tooltipList.add(StringTextComponent.EMPTY);
                    VoteList.Stats childInfo = entry.getValue();
                    int childCount = childInfo.getVoteCount(), childEffective = childInfo.getEffectiveCount();
                    String weightPercentage = finalWeight > 0F ? String.format("%.1f%%", 100.0F * childInfo.getWeight() / finalWeight) : "--.-%";
                    tooltipList.add(new TranslationTextComponent("gui.voteme.counter.score.subgroup", entry.getKey(), weightPercentage, childCount, childEffective));
                    if (childCount > 0) {
                        for (int i = 5; i >= 1; --i) {
                            int voteCount = childInfo.getVoteCount(i);
                            String votePercentage = String.format("%.1f%%", 100.0F * voteCount / childCount);
                            tooltipList.add(new TranslationTextComponent("gui.voteme.counter.score." + i, voteCount, votePercentage));
                        }
                    }
                }
            }
            this.func_243308_b(matrixStack, tooltipList, mouseX, mouseY);
        }
        if (dx >= -98 && dy >= 76 && dx < -61 && dy < 96) {
            this.renderTooltip(matrixStack, new TranslationTextComponent("gui.voteme.counter.switch"), mouseX, mouseY);
        }
    }

    @SuppressWarnings("deprecation")
    private void drawGuiContainerBackgroundLayer(MatrixStack matrixStack, float partialTicks, int mouseX, int mouseY) {
        Minecraft mc = Objects.requireNonNull(this.minecraft);
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        mc.getTextureManager().bindTexture(TEXTURE);
        this.blit(matrixStack, this.width / 2 - 111, this.height / 2 - 97, 0, 0, 234, 206);
    }

    private void drawGuiContainerForegroundLayer(MatrixStack matrixStack, float partialTicks, int mouseX, int mouseY) {
        Minecraft mc = Objects.requireNonNull(this.minecraft);
        EditCounterPacket.Info info = this.infoCollection.iterator().next();
        this.drawCategoryName(matrixStack, info, mc.fontRenderer);
        this.drawCategoryDescription(matrixStack, info, mc.fontRenderer);
        this.drawCategoryScore(matrixStack, info, mc.fontRenderer);
        this.drawArtifactName(matrixStack, mc.fontRenderer);
    }

    private void drawCategoryName(MatrixStack matrixStack, EditCounterPacket.Info info, FontRenderer font) {
        int x0 = this.width / 2 - 52, y0 = this.height / 2 - 14;
        font.func_243248_b(matrixStack, info.category.name, x0, y0, TEXT_COLOR);
    }

    private void drawCategoryDescription(MatrixStack matrixStack, EditCounterPacket.Info info, FontRenderer font) {
        List<IReorderingProcessor> descriptions = font.trimStringToWidth(info.category.description, 191);
        for (int size = Math.min(7, descriptions.size()), i = 0; i < size; ++i) {
            IReorderingProcessor description = descriptions.get(i);
            int x1 = this.width / 2 - 95, y1 = 9 * i + this.height / 2 + 6;
            font.func_238422_b_(matrixStack, description, x1, y1, TEXT_COLOR);
        }
    }

    private void drawCategoryScore(MatrixStack matrixStack, EditCounterPacket.Info info, FontRenderer font) {
        ITextComponent score = new StringTextComponent(this.enabledInfos.contains(info.id) ? String.format("%.1f", info.finalStat.getFinalScore(6.0F)) : "--");
        int x2 = this.width / 2 - font.getStringPropertyWidth(score) / 2 + 87, y2 = this.height / 2 - 14;
        font.func_243248_b(matrixStack, score, x2, y2, TEXT_COLOR);
    }

    private void drawArtifactName(MatrixStack matrixStack, FontRenderer font) {
        matrixStack.push();
        float scale = ARTIFACT_SCALE_FACTOR;
        matrixStack.scale(scale, scale, scale);
        int x3 = this.width / 2 + 1, y3 = this.height / 2 - 43;
        int start = this.artifactInput.getStartIndex(), end = this.artifactInput.getEndIndex();
        if (this.artifact.isEmpty()) {
            // draw hint text
            int dx0 = font.getStringPropertyWidth(EMPTY_ARTIFACT_TEXT) / 2;
            font.func_243248_b(matrixStack, EMPTY_ARTIFACT_TEXT, x3 / scale - dx0, y3 / scale, SUGGESTION_COLOR);
        } else {
            // draw actual text
            int dx = font.getStringWidth(this.artifact) / 2;
            boolean renderArtifactCursor = this.artifactCursorTick / 6 % 2 == 0;
            font.func_243248_b(matrixStack, new StringTextComponent(this.artifact), x3 / scale - dx, y3 / scale, TEXT_COLOR);
            if (end >= 0) {
                // draw cursor
                if (renderArtifactCursor) {
                    if (end >= this.artifact.length()) {
                        int dx1 = font.getStringWidth(this.artifact);
                        font.func_243248_b(matrixStack, new StringTextComponent("_"), x3 / scale - dx + dx1, y3 / scale, TEXT_COLOR);
                    } else {
                        int dx1 = font.getStringWidth(this.artifact.substring(0, end));
                        fill(matrixStack, (int) (x3 / scale - dx + dx1), (int) (y3 / scale) - 1, (int) (x3 / scale - dx + dx1) + 1, (int) (y3 / scale) + 9, TEXT_COLOR);
                    }
                }
                // draw selection
                if (start != end) {
                    RenderSystem.disableTexture();
                    RenderSystem.enableColorLogicOp();
                    RenderSystem.logicOp(GlStateManager.LogicOp.OR_REVERSE);
                    int dx2 = font.getStringWidth(this.artifact.substring(0, end));
                    int dx3 = font.getStringWidth(this.artifact.substring(0, start));
                    fill(matrixStack, (int) (x3 / scale - dx + dx2), (int) (y3 / scale), (int) (x3 / scale - dx + dx3), (int) (y3 / scale) + 9, SELECTION_COLOR);
                    RenderSystem.disableColorLogicOp();
                    RenderSystem.enableTexture();
                }
            }
        }
        matrixStack.pop();
    }

    private static <T> List<T> rotateAsFirst(List<T> initial, Predicate<T> filter) {
        int dist = IntStream.range(0, initial.size()).filter(i -> filter.test(initial.get(i))).findFirst().orElse(0);
        ArrayList<T> result = new ArrayList<>(initial);
        Collections.rotate(result, dist);
        return result;
    }

    private static class BottomButton extends ImageButton {
        public BottomButton(int x, int y, IPressable onPress, ITextComponent title) {
            super(x, y, 39, 19, 136, 207, 0, CounterScreen.TEXTURE, 256, 256, onPress, title);
        }

        @Override
        public void renderButton(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
            super.renderButton(matrixStack, mouseX, mouseY, partialTicks);
            FontRenderer fontRenderer = Minecraft.getInstance().fontRenderer;
            float dx = fontRenderer.getStringPropertyWidth(this.getMessage()) / 2F;
            float x = this.x + (this.width + 1) / 2F - dx, y = this.y + (this.height - 9) / 2F;
            fontRenderer.func_243248_b(matrixStack, this.getMessage(), x, y, BUTTON_TEXT_COLOR);
        }
    }

    private static class BottomSwitch extends Button {
        private final BooleanSupplier enabled;
        private float ticksFromPressing;

        public BottomSwitch(int x, int y, BooleanSupplier enabled, IPressable onPress, ITextComponent title) {
            super(x, y, 37, 20, title, onPress);
            this.ticksFromPressing = Float.POSITIVE_INFINITY;
            this.enabled = enabled;
        }

        @Override
        public void onPress() {
            super.onPress();
            this.ticksFromPressing = 0F;
        }

        @Override
        @SuppressWarnings("deprecation")
        public void renderButton(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
            // calculate offset and alpha
            double progress = Math.tanh((this.ticksFromPressing += partialTicks) / 3);
            double transition = this.enabled.getAsBoolean() ? progress : 1 - progress;
            int offset = (int) Math.round(17 * transition);
            float alpha = (float) transition;

            // render background and switch-off button
            RenderSystem.enableDepthTest();
            Minecraft mc = Minecraft.getInstance();
            mc.getTextureManager().bindTexture(CounterScreen.TEXTURE);
            blit(matrixStack, this.x, this.y, 13, 228, this.width, this.height, 256, 256);
            blit(matrixStack, this.x + offset + 2, this.y + 2, 69, 230, 16, 16, 256, 256);

            // render switch-on button with blend
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.color4f(1F, 1F, 1F, alpha);
            blit(matrixStack, this.x + offset + 2, this.y + 2, 52, 230, 16, 16, 256, 256);
        }
    }
}
