package org.teacon.voteme.screen;

import com.google.common.base.Preconditions;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.apache.commons.lang3.tuple.Pair;
import org.teacon.voteme.network.ChangeNameByCounterPacket;
import org.teacon.voteme.network.ChangePropsByCounterPacket;
import org.teacon.voteme.network.ShowCounterPacket;
import org.teacon.voteme.vote.VoteList;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class CounterScreen extends Screen {
    private static final Identifier TEXTURE = Identifier.parse("voteme:textures/gui/counter.png");
    private static final Identifier PREV_BUTTON_TEX = Identifier.parse("voteme:counter_prev_button");
    private static final Identifier NEXT_BUTTON_TEX = Identifier.parse("voteme:counter_next_button");
    private static final Identifier BLANK_BUTTON_TEX = Identifier.parse("voteme:counter_blank_button");
    private static final Component EMPTY_ARTIFACT_TEXT = Component.translatable("gui.voteme.counter.empty_artifact").withStyle(style -> style.withItalic(true));
    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 256;

    public static final WidgetSprites PREV_BUTTON_SPRITE = new WidgetSprites(PREV_BUTTON_TEX, PREV_BUTTON_TEX);
    public static final WidgetSprites NEXT_BUTTON_SPRITE = new WidgetSprites(NEXT_BUTTON_TEX, NEXT_BUTTON_TEX);

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
    private final SortedSet<Identifier> enabledInfos;
    private final List<ShowCounterPacket.Info> infoCollection;

    private BottomButton okButton;
    private BottomButton cancelButton;
    private BottomButton renameButton;
    private BottomSwitch bottomSwitch;
    private TextFieldHelper artifactInput;

    public CounterScreen(UUID artifactUUID, String artifactName, int inventoryIndex, Identifier category, List<ShowCounterPacket.Info> infos) {
        super(GameNarrator.NO_TITLE);
        this.artifactUUID = artifactUUID;
        this.inventoryIndex = inventoryIndex;
        this.artifact = this.oldArtifact = artifactName;
        Preconditions.checkArgument(!infos.isEmpty());
        this.infoCollection = rotateAsFirst(infos, info -> category.equals(info.id));
        this.enabledInfos = infos.stream().filter(i -> i.enabledCurrently).map(i -> i.id).collect(Collectors.toCollection(TreeSet::new));
    }

    @Override
    protected void init() {
        Minecraft mc = this.minecraft;
        var prevCategoryButton = new ImageButton(this.width / 2 - 99, this.height / 2 - 20, 18, 19, PREV_BUTTON_SPRITE, this::onPrevButtonClick);
        prevCategoryButton.setTooltip(Tooltip.create(Component.translatable("gui.voteme.counter.prev")));
        this.addRenderableWidget(prevCategoryButton);

        var nextCategoryButton = new ImageButton(this.width / 2 - 79, this.height / 2 - 20, 18, 19, NEXT_BUTTON_SPRITE, this::onNextButtonClick);
        nextCategoryButton.setTooltip(Tooltip.create(Component.translatable("gui.voteme.counter.next")));
        this.addRenderableWidget(nextCategoryButton);

        this.okButton = this.addRenderableWidget(new BottomButton(
                this.width / 2 + 61, this.height / 2 + 77, this::onOKButtonClick, Component.translatable("gui.voteme.counter.ok")
        ));
        this.cancelButton = this.addRenderableWidget(new BottomButton(
                this.width / 2 + 61, this.height / 2 + 77, this::onCancelButtonClick, Component.translatable("gui.voteme.counter.cancel")
        ));
        this.renameButton = this.addRenderableWidget(new BottomButton(
                this.width / 2 + 19, this.height / 2 + 77, this::onRenameButtonClick, Component.translatable("gui.voteme.counter.rename")
        ));
        this.bottomSwitch = this.addRenderableWidget(new BottomSwitch(
                this.width / 2 - 98, this.height / 2 + 76,
                () -> this.enabledInfos.contains(this.infoCollection.getFirst().id),
                this::onSwitchClick,
                Component.translatable("gui.voteme.counter.switch")
        ));
        this.bottomSwitch.setTooltip(Tooltip.create(Component.translatable("gui.voteme.counter.switch")));
        this.artifactInput = new TextFieldHelper(
                () -> this.artifact,
                text -> this.artifact = text,
                TextFieldHelper.createClipboardGetter(mc),
                TextFieldHelper.createClipboardSetter(mc),
                text -> mc.font.width(text) * ARTIFACT_SCALE_FACTOR <= 199
        );
        this.cancelButton.visible = false;
        this.renameButton.visible = false;
        this.bottomSwitch.visible = false;
    }

    @Override
    public void tick() {
        ++this.artifactCursorTick;
        this.bottomSwitch.visible = this.infoCollection.getFirst().category.enabledModifiable;
        boolean canRename = !this.artifact.isEmpty() && !Objects.equals(this.artifact, this.oldArtifact);
        this.renameButton.visible = canRename;
        this.cancelButton.visible = canRename;
        this.okButton.visible = !canRename;
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
    public boolean charTyped(CharacterEvent event) {
        return this.artifactInput.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return this.artifactInput.keyPressed(event) || super.keyPressed(event);
    }

    @Override
    public void removed() {
        if (!this.oldArtifact.isEmpty()) {
            ShowCounterPacket.Info info = this.infoCollection.getFirst();
            Iterable<Identifier> enabled = () -> this.infoCollection.stream()
                    .filter(i -> !i.enabledCurrently && this.enabledInfos.contains(i.id))
                    .map(i -> i.id)
                    .iterator();
            Iterable<Identifier> disabled = () -> this.infoCollection.stream()
                    .filter(i -> i.enabledCurrently && !this.enabledInfos.contains(i.id))
                    .map(i -> i.id)
                    .iterator();
            ClientPacketDistributor.sendToServer(ChangePropsByCounterPacket.create(
                    this.inventoryIndex, this.artifactUUID, info.id, enabled, disabled
            ));
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
        this.oldArtifact = this.artifact;
        ClientPacketDistributor.sendToServer(ChangeNameByCounterPacket.create(this.inventoryIndex, this.artifactUUID, this.oldArtifact));
    }

    private void onSwitchClick(Button button) {
        Identifier id = this.infoCollection.getFirst().id;
        if (!this.enabledInfos.add(id)) {
            this.enabledInfos.remove(id);
        }
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
        if (dx >= 73 && dy >= -19 && dx < 99 && dy < -2) {
            List<Component> tooltipList = new ArrayList<>();
            ShowCounterPacket.Info info = this.infoCollection.getFirst();
            float finalWeight = info.finalStat.getWeight();
            int finalCount = info.finalStat.getVoteCount();
            int finalEffective = info.finalStat.getEffectiveCount();
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
                    int childCount = childInfo.getVoteCount();
                    int childEffective = childInfo.getEffectiveCount();
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
            graphics.setComponentTooltipForNextFrame(this.font, tooltipList, mouseX, mouseY);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.width / 2 - 111, this.height / 2 - 97, 0, 0, 234, 206, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    private void renderForeground(GuiGraphicsExtractor graphics) {
        ShowCounterPacket.Info info = this.infoCollection.getFirst();
        this.drawCategoryName(graphics, info, this.font);
        this.drawCategoryDescription(graphics, info, this.font);
        this.drawCategoryScore(graphics, info, this.font);
        this.drawArtifactName(graphics, this.font);
    }

    private void drawCategoryName(GuiGraphicsExtractor graphics, ShowCounterPacket.Info info, Font font) {
        graphics.text(font, info.category.name, this.width / 2 - 52, this.height / 2 - 14, TEXT_COLOR, false);
    }

    private void drawCategoryDescription(GuiGraphicsExtractor graphics, ShowCounterPacket.Info info, Font font) {
        List<FormattedCharSequence> descriptions = font.split(info.category.description, 191);
        for (int size = Math.min(7, descriptions.size()), i = 0; i < size; ++i) {
            graphics.text(font, descriptions.get(i), this.width / 2 - 95, 9 * i + this.height / 2 + 6, TEXT_COLOR, false);
        }
    }

    private void drawCategoryScore(GuiGraphicsExtractor graphics, ShowCounterPacket.Info info, Font font) {
        Component score = Component.literal(this.enabledInfos.contains(info.id) ? String.format("%.1f", info.finalStat.getFinalScore(6.0F)) : "--");
        int x = this.width / 2 - font.width(score) / 2 + 87;
        int y = this.height / 2 - 14;
        graphics.text(font, score, x, y, TEXT_COLOR, false);
    }

    private void drawArtifactName(GuiGraphicsExtractor graphics, Font font) {
        graphics.pose().pushMatrix();
        float scale = ARTIFACT_SCALE_FACTOR;
        graphics.pose().scale(scale, scale);
        int x = this.width / 2 + 1;
        int y = this.height / 2 - 43;
        int start = this.artifactInput.getSelectionPos();
        int end = this.artifactInput.getCursorPos();
        if (this.artifact.isEmpty()) {
            int dx = font.width(EMPTY_ARTIFACT_TEXT) / 2;
            graphics.text(font, EMPTY_ARTIFACT_TEXT, (int) (x / scale - dx), (int) (y / scale), SUGGESTION_COLOR, false);
        } else {
            int dx = font.width(this.artifact) / 2;
            boolean renderCursor = this.artifactCursorTick / 6 % 2 == 0;
            graphics.text(font, Component.literal(this.artifact), (int) (x / scale - dx), (int) (y / scale), TEXT_COLOR, false);
            if (end >= 0) {
                if (renderCursor) {
                    if (end >= this.artifact.length()) {
                        int dx1 = font.width(this.artifact);
                        graphics.text(font, Component.literal("_"), (int) (x / scale - dx + dx1), (int) (y / scale), TEXT_COLOR, false);
                    } else {
                        int dx1 = font.width(this.artifact.substring(0, end));
                        graphics.fill((int) (x / scale - dx + dx1), (int) (y / scale) - 1, (int) (x / scale - dx + dx1) + 1, (int) (y / scale) + 9, TEXT_COLOR);
                    }
                }
                if (start != end) {
                    int dx2 = font.width(this.artifact.substring(0, end));
                    int dx3 = font.width(this.artifact.substring(0, start));
                    int xMin = (int) (x / scale - dx + Math.min(dx2, dx3));
                    int xMax = (int) (x / scale - dx + Math.max(dx2, dx3));
                    graphics.fill(xMin, (int) (y / scale), xMax, (int) (y / scale) + 9, SELECTION_COLOR);
                }
            }
        }
        graphics.pose().popMatrix();
    }

    private static <T> List<T> rotateAsFirst(List<T> initial, Predicate<T> filter) {
        int dist = IntStream.range(0, initial.size()).filter(i -> filter.test(initial.get(i))).findFirst().orElse(0);
        ArrayList<T> result = new ArrayList<>(initial);
        Collections.rotate(result, -dist);
        return result;
    }

    private static class BottomButton extends ImageButton {
        public static final WidgetSprites BOTTOM_BUTTON_SPRITE = new WidgetSprites(CounterScreen.BLANK_BUTTON_TEX, CounterScreen.BLANK_BUTTON_TEX);

        private BottomButton(int x, int y, Button.OnPress onPress, Component title) {
            super(x, y, 39, 19, BOTTOM_BUTTON_SPRITE, onPress, title);
        }

        @Override
        public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            super.extractContents(graphics, mouseX, mouseY, a);
            Font font = Minecraft.getInstance().font;
            float dx = font.width(this.getMessage()) / 2F;
            float x = this.getX() + (this.width + 1) / 2F - dx;
            float y = this.getY() + (this.height - 8) / 2F;
            graphics.text(font, this.getMessage(), (int) x, (int) y, BUTTON_TEXT_COLOR, false);
        }
    }

    private static class BottomSwitch extends Button {
        private final BooleanSupplier enabled;
        private float ticksFromPressing;

        private BottomSwitch(int x, int y, BooleanSupplier enabled, Button.OnPress onPress, Component title) {
            super(x, y, 37, 20, title, onPress, DEFAULT_NARRATION);
            this.ticksFromPressing = Float.POSITIVE_INFINITY;
            this.enabled = enabled;
        }

        @Override
        public void onPress(InputWithModifiers input) {
            super.onPress(input);
            this.ticksFromPressing = 0F;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            double progress = Math.tanh((this.ticksFromPressing += a) / 3);
            double transition = this.enabled.getAsBoolean() ? progress : 1 - progress;
            int offset = (int) Math.round(17 * transition);
            int color = ARGB.white((float) transition);

            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.getX(), this.getY(), 13, 228, this.width, this.height, TEXTURE_WIDTH, TEXTURE_HEIGHT);
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.getX() + offset + 2, this.getY() + 2, 69, 230, 16, 16, TEXTURE_WIDTH, TEXTURE_HEIGHT);
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.getX() + offset + 2, this.getY() + 2, 52, 230, 16, 16, TEXTURE_WIDTH, TEXTURE_HEIGHT, color);
        }
    }
}
