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
import org.teacon.voteme.network.EditCounterPacket;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
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
    private final int inventoryIndex;
    private final List<EditCounterPacket.Info> infoCollection;

    private BottomButton renameButton;
    private TextInputUtil artifactInput;

    public CounterScreen(String artifact, int inventoryIndex, ResourceLocation category, List<EditCounterPacket.Info> infos) {
        super(NarratorChatListener.EMPTY);
        this.inventoryIndex = inventoryIndex;
        this.artifact = this.oldArtifact = artifact;
        Preconditions.checkArgument(infos.size() > 0);
        this.infoCollection = rotateAsFirst(infos, info -> category.equals(info.id));
    }

    @Override
    protected void init() {
        Minecraft mc = Objects.requireNonNull(this.minecraft);
        this.addButton(new ImageButton(this.width / 2 - 99, this.height / 2 - 20, 18, 19, 12, 207, 0, TEXTURE, this::onPrevButtonClick));
        this.addButton(new ImageButton(this.width / 2 - 79, this.height / 2 - 20, 18, 19, 32, 207, 0, TEXTURE, this::onNextButtonClick));
        this.addButton(new BottomButton(this.width / 2 + 61, this.height / 2 + 77, this::onOKButtonClick, new TranslationTextComponent("gui.voteme.counter.ok")));
        this.renameButton = this.addButton(new BottomButton(CounterScreen.this.width / 2 + 19, CounterScreen.this.height / 2 + 77, CounterScreen.this::onRenameButtonClick, new TranslationTextComponent("gui.voteme.counter.rename")));
        this.artifactInput = new TextInputUtil(() -> this.artifact, text -> this.artifact = text, TextInputUtil.getClipboardTextSupplier(mc), TextInputUtil.getClipboardTextSetter(mc), text -> mc.fontRenderer.getStringWidth(text) * ARTIFACT_SCALE_FACTOR <= 199);
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(matrixStack);
        this.drawGuiContainerBackgroundLayer(matrixStack, partialTicks, mouseX, mouseY);
        super.render(matrixStack, mouseX, mouseY, partialTicks);
        this.drawGuiContainerForegroundLayer(matrixStack, partialTicks, mouseX, mouseY);
    }

    @Override
    public void tick() {
        ++this.artifactCursorTick;
        this.renameButton.visible = !this.artifact.isEmpty() && !Objects.equals(this.artifact, this.oldArtifact);
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

    private void onPrevButtonClick(Button button) {
        Collections.rotate(this.infoCollection, -1);
    }

    private void onNextButtonClick(Button button) {
        Collections.rotate(this.infoCollection, 1);
    }

    private void onOKButtonClick(Button button) {
        this.closeScreen();
    }

    private void onRenameButtonClick(Button button) {
        // TODO
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

        // step 1: draw category name
        int x0 = this.width / 2 - 52, y0 = this.height / 2 - 14;
        mc.fontRenderer.func_243248_b(matrixStack, info.name, x0, y0, TEXT_COLOR);

        // step 2: draw category description
        List<IReorderingProcessor> descriptions = mc.fontRenderer.trimStringToWidth(info.desc, 191);
        for (int size = Math.min(7, descriptions.size()), i = 0; i < size; ++i) {
            IReorderingProcessor description = descriptions.get(i);
            int x1 = this.width / 2 - 95, y1 = 9 * i + this.height / 2 + 6;
            mc.fontRenderer.func_238422_b_(matrixStack, description, x1, y1, TEXT_COLOR);
        }

        // step 3: draw category score
        ITextComponent score = new StringTextComponent(String.format("%.1f", info.score));
        int x2 = this.width / 2 - mc.fontRenderer.getStringPropertyWidth(score) / 2 + 87, y2 = this.height / 2 - 14;
        mc.fontRenderer.func_243248_b(matrixStack, score, x2, y2, TEXT_COLOR);

        // step 4: draw artifact name
        matrixStack.push();
        float scale = ARTIFACT_SCALE_FACTOR;
        matrixStack.scale(scale, scale, scale);
        int x3 = this.width / 2 + 1, y3 = this.height / 2 - 43;
        int start = this.artifactInput.getStartIndex(), end = this.artifactInput.getEndIndex();
        if (this.artifact.isEmpty()) {
            // draw hint text
            int dx0 = mc.fontRenderer.getStringPropertyWidth(EMPTY_ARTIFACT_TEXT) / 2;
            mc.fontRenderer.func_243248_b(matrixStack, EMPTY_ARTIFACT_TEXT, x3 / scale - dx0, y3 / scale, SUGGESTION_COLOR);
        } else {
            // draw actual text
            int dx = mc.fontRenderer.getStringWidth(this.artifact) / 2;
            boolean renderArtifactCursor = this.artifactCursorTick / 6 % 2 == 0;
            mc.fontRenderer.func_243248_b(matrixStack, new StringTextComponent(this.artifact), x3 / scale - dx, y3 / scale, TEXT_COLOR);
            if (end >= 0) {
                // draw cursor
                if (renderArtifactCursor) {
                    if (end >= this.artifact.length()) {
                        int dx1 = mc.fontRenderer.getStringWidth(this.artifact);
                        mc.fontRenderer.func_243248_b(matrixStack, new StringTextComponent("_"), x3 / scale - dx + dx1, y3 / scale, TEXT_COLOR);
                    } else {
                        int dx1 = mc.fontRenderer.getStringWidth(this.artifact.substring(0, end));
                        fill(matrixStack, (int) (x3 / scale - dx + dx1), (int) (y3 / scale) - 1, (int) (x3 / scale - dx + dx1) + 1, (int) (y3 / scale) + 9, TEXT_COLOR);
                    }
                }
                // draw selection
                if (start != end) {
                    RenderSystem.disableTexture();
                    RenderSystem.enableColorLogicOp();
                    RenderSystem.logicOp(GlStateManager.LogicOp.OR_REVERSE);
                    int dx2 = mc.fontRenderer.getStringWidth(this.artifact.substring(0, end));
                    int dx3 = mc.fontRenderer.getStringWidth(this.artifact.substring(0, start));
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
}
