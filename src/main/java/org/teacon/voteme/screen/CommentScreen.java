package org.teacon.voteme.screen;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.Util;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.font.TextFieldHelper.CursorStep;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.DyeColor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableInt;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static net.minecraft.network.chat.CommonComponents.EMPTY;

/**
 * A partial replica of {@link BookEditScreen}, adapted for our own use case.
 * Used for allowing voter to provide additional comments.
 *
 * @author 3TUSK
 */
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class CommentScreen extends Screen {
    private static final Component DONE = CommonComponents.GUI_DONE;
    private static final Component CLEAR = Component.translatable("gui.voteme.voter.clear");
    private static final Component UNSET = Component.translatable("gui.voteme.voter.unset");
    private static final ResourceLocation TEXTURE = ResourceLocation.parse("voteme:textures/gui/comment.png");

    private static final int TEXT_COLOR = 0xFF000000 | DyeColor.WHITE.getTextColor();
    private static final int HIGHLIGHT_COLOR = 0xFF000000 | DyeColor.BLUE.getTextColor();

    private boolean isModified;
    private int frameTick;
    private int currentPage;
    private final List<String> pages;
    private final TextFieldHelper pageEdit;
    private long lastClickTime;
    private int lastIndex = -1;
    private PageButton forwardButton;
    private PageButton backButton;
    private Button doneButton;
    private Button clearButton;
    private Button unsetButton;
    private @Nullable DisplayCache displayCache = DisplayCache.EMPTY;
    private Component pageMsg = EMPTY;

    private final List<String> parentComments;

    public CommentScreen(List<String> parentComments) {
        super(GameNarrator.NO_TITLE);
        this.parentComments = parentComments;
        this.pages = new ArrayList<>(Math.max(1, parentComments.size()));
        this.pages.addAll(parentComments.isEmpty() ? List.of("") : parentComments);
        // Must be initialized after this.pages
        this.pageEdit = new TextFieldHelper(
                this::getCurrentPageText, this::setCurrentPageText, this::getClipboard, this::setClipboard,
                unfiltered -> unfiltered.length() < 1024 && this.font.wordWrapHeight(unfiltered, 200) <= 163);
    }

    private void setClipboard(String clipboardValue) {
        if (this.minecraft != null) {
            TextFieldHelper.setClipboardContents(this.minecraft, clipboardValue);
        }
    }

    private String getClipboard() {
        return this.minecraft != null ? TextFieldHelper.getClipboardContents(this.minecraft) : "";
    }

    private int getNumPages() {
        return this.pages.size();
    }

    @Override
    public void tick() {
        super.tick();
        this.frameTick++;
    }

    @Override
    protected void init() {
        this.clearDisplayCache();
        // Done button
        this.doneButton = this.addRenderableWidget(new VoterScreen.BottomButton(
                this.width / 2 + 52, this.height / 2 + 82, false, this::onOKButtonClick, DONE));
        // Clear button
        this.clearButton = this.addRenderableWidget(new VoterScreen.BottomButton(
                this.width / 2 - 104, this.height / 2 + 82, true, this::onClearButtonClick, CLEAR));
        // Unset button
        this.unsetButton = this.addRenderableWidget(new VoterScreen.BottomButton(
                this.width / 2 - 104, this.height / 2 + 82, true, this::onUnsetButtonClick, UNSET));
        // Forward button
        this.forwardButton = this.addRenderableWidget(new PageButton(
                this.width / 2 + 36, this.height / 2 + 82, 1, button -> this.pageForward(), EMPTY));
        // Back button
        this.backButton = this.addRenderableWidget(new PageButton(
                this.width / 2 - 49, this.height / 2 + 82, -1, button -> this.pageBack(), EMPTY));
        this.updateButtonVisibility();
    }

    @Override
    public void removed() {
        if (this.isModified) {
            // Eliminate all empty pages
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

        this.updateButtonVisibility();
        this.clearDisplayCacheAfterPageChange();
    }

    private void onUnsetButtonClick(Button button) {
        this.pages.clear();
        this.pages.addAll(this.parentComments.isEmpty() ? List.of("") : this.parentComments);
        this.isModified = false;
        this.currentPage = Math.min(this.currentPage, this.getNumPages() - 1);

        this.updateButtonVisibility();
        this.clearDisplayCacheAfterPageChange();
    }

    private void pageBack() {
        if (this.currentPage > 0) {
            this.currentPage--;
        }

        this.updateButtonVisibility();
        this.clearDisplayCacheAfterPageChange();
    }

    private void pageForward() {
        if (this.currentPage < this.getNumPages() - 1) {
            this.currentPage++;
        } else {
            this.appendPageToBook();
            if (this.currentPage < this.getNumPages() - 1) {
                this.currentPage++;
            }
        }

        this.updateButtonVisibility();
        this.clearDisplayCacheAfterPageChange();
    }

    private void updateButtonVisibility() {
        this.backButton.visible = this.currentPage > 0;
        this.forwardButton.visible = true;
        this.doneButton.visible = true;
        this.clearButton.visible = !this.isModified;
        this.unsetButton.visible = this.isModified;
    }

    private static final int MAX_PAGES = 10; // TODO(3TUSK): Configurable value

    private void appendPageToBook() {
        if (this.getNumPages() < MAX_PAGES) {
            this.pages.add("");
            this.isModified = true;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        } else {
            boolean flag = this.bookKeyPressed(keyCode);
            if (flag) {
                this.clearDisplayCache();
                return true;
            } else {
                return false;
            }
        }
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (super.charTyped(codePoint, modifiers)) {
            return true;
        } else if (StringUtil.isAllowedChatCharacter(codePoint)) {
            this.pageEdit.insertText(Character.toString(codePoint));
            this.clearDisplayCache();
            return true;
        } else {
            return false;
        }
    }

    private boolean bookKeyPressed(int keyCode) {
        if (Screen.isSelectAll(keyCode)) {
            this.pageEdit.selectAll();
            return true;
        } else if (Screen.isCopy(keyCode)) {
            this.pageEdit.copy();
            return true;
        } else if (Screen.isPaste(keyCode)) {
            this.pageEdit.paste();
            return true;
        } else if (Screen.isCut(keyCode)) {
            this.pageEdit.cut();
            return true;
        } else {
            CursorStep cursorStep = Screen.hasControlDown() ? CursorStep.WORD : CursorStep.CHARACTER;
            // noinspection EnhancedSwitchMigration
            switch (keyCode) {
                case GLFW.GLFW_KEY_ENTER: // 257
                case GLFW.GLFW_KEY_KP_ENTER: // 335
                    this.pageEdit.insertText("\n");
                    return true;
                case GLFW.GLFW_KEY_BACKSPACE: // 259
                    this.pageEdit.removeFromCursor(-1, cursorStep);
                    return true;
                case GLFW.GLFW_KEY_DELETE: // 261
                    this.pageEdit.removeFromCursor(1, cursorStep);
                    return true;
                case GLFW.GLFW_KEY_RIGHT: // 262
                    this.pageEdit.moveBy(1, Screen.hasShiftDown(), cursorStep);
                    return true;
                case GLFW.GLFW_KEY_LEFT: // 263
                    this.pageEdit.moveBy(-1, Screen.hasShiftDown(), cursorStep);
                    return true;
                case GLFW.GLFW_KEY_DOWN: // 264
                    this.keyDown();
                    return true;
                case GLFW.GLFW_KEY_UP: // 265
                    this.keyUp();
                    return true;
                case GLFW.GLFW_KEY_PAGE_UP: // 266
                    this.backButton.onPress();
                    return true;
                case GLFW.GLFW_KEY_PAGE_DOWN: // 267
                    this.forwardButton.onPress();
                    return true;
                case GLFW.GLFW_KEY_HOME: // 268
                    this.keyHome();
                    return true;
                case GLFW.GLFW_KEY_END: // 269
                    this.keyEnd();
                    return true;
                default:
                    return false;
            }
        }
    }

    private void keyUp() {
        this.changeLine(-1);
    }

    private void keyDown() {
        this.changeLine(1);
    }

    private void changeLine(int yChange) {
        int i = this.pageEdit.getCursorPos();
        int j = this.getDisplayCache().changeLine(i, yChange);
        this.pageEdit.setCursorPos(j, Screen.hasShiftDown());
    }

    private void keyHome() {
        if (Screen.hasControlDown()) {
            this.pageEdit.setCursorToStart(Screen.hasShiftDown());
        } else {
            int i = this.pageEdit.getCursorPos();
            int j = this.getDisplayCache().findLineStart(i);
            this.pageEdit.setCursorPos(j, Screen.hasShiftDown());
        }
    }

    private void keyEnd() {
        if (Screen.hasControlDown()) {
            this.pageEdit.setCursorToEnd(Screen.hasShiftDown());
        } else {
            DisplayCache bookeditscreen$displaycache = this.getDisplayCache();
            int i = this.pageEdit.getCursorPos();
            int j = bookeditscreen$displaycache.findLineEnd(i);
            this.pageEdit.setCursorPos(j, Screen.hasShiftDown());
        }
    }

    private String getCurrentPageText() {
        return this.currentPage >= 0 && this.currentPage < this.pages.size() ? this.pages.get(this.currentPage) : "";
    }

    private void setCurrentPageText(String text) {
        if (this.currentPage >= 0 && this.currentPage < this.pages.size()) {
            this.pages.set(this.currentPage, text);
            this.isModified = true;
            this.clearDisplayCache();
        }
    }

    @Override
    public void render(GuiGraphics matrixStack, int mouseX, int mouseY, float partialTick) {
        super.render(matrixStack, mouseX, mouseY, partialTick);
        this.setFocused(null);
        {
            int pageIndicatorWidth = this.font.width(this.pageMsg);
            matrixStack.drawString(this.font, this.pageMsg, (this.width - pageIndicatorWidth) / 2, this.height / 2 + 87, 0, false);

            DisplayCache page = this.getDisplayCache();
            for (LineInfo line : page.lines) {
                matrixStack.drawString(this.font, line.asComponent, line.x, line.y, TEXT_COLOR, false);
            }
            this.renderHighlight(matrixStack, page.selection);
            this.renderCursor(matrixStack, page.cursor, page.cursorAtEnd);
        }
    }

    @Override
    public void renderBackground(GuiGraphics matrixStack, int mouseX, int mouseY, float partialTick) {
        matrixStack.blit(TEXTURE, this.width / 2 - 111, this.height / 2 - 97, 0, 0, 234, 206);
        matrixStack.blit(TEXTURE, this.width / 2 - 49, this.height / 2 + 82, 7, 211, 96, 19);
    }

    private void renderCursor(GuiGraphics matrixStack, Pos2i cursorPos, boolean isEndOfText) {
        if (this.frameTick / 6 % 2 == 0) {
            cursorPos = this.convertLocalToScreen(cursorPos);
            if (!isEndOfText) {
                matrixStack.fill(cursorPos.x, cursorPos.y - 1, cursorPos.x + 1, cursorPos.y + 9, TEXT_COLOR);
            } else {
                matrixStack.drawString(this.font, "_", cursorPos.x, cursorPos.y, TEXT_COLOR, false);
            }
        }

    }

    private void renderHighlight(GuiGraphics guiGraphics, Rect2i[] highlightAreas) {
        for (Rect2i rect2i : highlightAreas) {
            int i = rect2i.getX();
            int j = rect2i.getY();
            int k = i + rect2i.getWidth();
            int l = j + rect2i.getHeight();
            guiGraphics.fill(RenderType.guiTextHighlight(), i, j, k, l, HIGHLIGHT_COLOR);
        }
    }

    private Pos2i convertScreenToLocal(Pos2i screenPos) {
        return new Pos2i(screenPos.x - this.width / 2 + 100, screenPos.y - this.height / 2 + 86);
    }

    private Pos2i convertLocalToScreen(Pos2i localScreenPos) {
        return new Pos2i(localScreenPos.x + this.width / 2 - 100, localScreenPos.y + this.height / 2 - 86);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) { // 0
            long currentTime = Util.getMillis();
            DisplayCache page = this.getDisplayCache();
            int index = page.getIndexAtPosition(this.font, this.convertScreenToLocal(new Pos2i((int) mouseX, (int) mouseY)));
            if (index >= 0) {
                if (index != this.lastIndex || currentTime - this.lastClickTime >= 250L) {
                    this.pageEdit.setCursorPos(index, Screen.hasShiftDown());
                } else if (!this.pageEdit.isSelecting()) {
                    this.selectWord(index);
                } else {
                    this.pageEdit.selectAll();
                }

                this.clearDisplayCache();
            }

            this.lastIndex = index;
            this.lastClickTime = currentTime;
        }

        return true;
    }

    private void selectWord(int index) {
        String currentText = this.getCurrentPageText();
        this.pageEdit.setSelectionRange(StringSplitter.getWordPosition(currentText, -1, index, false),
                StringSplitter.getWordPosition(currentText, 1, index, false));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (super.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        if (button == 0) {
            DisplayCache bookPage = this.getDisplayCache();
            int i = bookPage.getIndexAtPosition(this.font,
                    this.convertScreenToLocal(new Pos2i((int) mouseX, (int) mouseY)));
            this.pageEdit.setCursorPos(i, true);
            this.clearDisplayCache();
        }
        return true;
    }

    private DisplayCache getDisplayCache() {
        DisplayCache displayCache = this.displayCache;
        if (displayCache == null) {
            this.displayCache = displayCache = this.rebuildDisplayCache();
            this.pageMsg = Component.translatable("book.pageIndicator", this.currentPage + 1, this.getNumPages());
        }
        return displayCache;
    }

    private void clearDisplayCache() {
        this.displayCache = null;
    }

    private void clearDisplayCacheAfterPageChange() {
        this.pageEdit.setCursorToEnd();
        this.clearDisplayCache();
    }

    private DisplayCache rebuildDisplayCache() {
        String currentText = this.getCurrentPageText();
        if (currentText.isEmpty()) {
            return DisplayCache.EMPTY;
        }
        int i = this.pageEdit.getCursorPos();
        int j = this.pageEdit.getSelectionPos();
        // List of indexes where a new line starts.
        IntList lineStarts = new IntArrayList();
        // All parsed lines.
        List<LineInfo> lines = Lists.newArrayList();
        // 1-indexed line number.
        MutableInt lineNum = new MutableInt();
        // Tracking if the entire page is ending in a new line.
        // Used to properly shift the cursor down.
        MutableBoolean trailingNewLine = new MutableBoolean();
        StringSplitter splitter = this.font.getSplitter();
        // splitLines -> splitLines
        splitter.splitLines(currentText, 200, Style.EMPTY, true, (style, begin, endExclusive) -> {
            int currentLine = lineNum.getAndIncrement();
            String raw = currentText.substring(begin, endExclusive);
            trailingNewLine.setValue(raw.endsWith("\n"));
            String trimmed = StringUtils.stripEnd(raw, " \n");
            int localHeight = currentLine * 9;
            Pos2i screenPos = this.convertLocalToScreen(new Pos2i(0, localHeight));
            lineStarts.add(begin);
            lines.add(new LineInfo(style, trimmed, screenPos.x, screenPos.y));
        });
        int[] aint = lineStarts.toIntArray();
        boolean cursorAtEnd = i == currentText.length();
        Pos2i cursorPos;
        if (cursorAtEnd && trailingNewLine.isTrue()) {
            cursorPos = new Pos2i(0, lines.size() * 9);
        } else {
            int k = findLineFromPos(aint, i);
            int l = this.font.width(currentText.substring(aint[k], i));
            cursorPos = new Pos2i(l, k * 9);
        }

        List<Rect2i> list1 = Lists.newArrayList();
        if (i != j) {
            int l2 = Math.min(i, j);
            int i1 = Math.max(i, j);
            int j1 = findLineFromPos(aint, l2);
            int k1 = findLineFromPos(aint, i1);
            if (j1 == k1) {
                int l1 = j1 * 9;
                int i2 = aint[j1];
                list1.add(this.createPartialLineSelection(currentText, splitter, l2, i1, l1, i2));
            } else {
                int i3 = j1 + 1 > aint.length ? currentText.length() : aint[j1 + 1];
                list1.add(this.createPartialLineSelection(currentText, splitter, l2, i3, j1 * 9, aint[j1]));

                for (int j3 = j1 + 1; j3 < k1; j3++) {
                    int j2 = j3 * 9;
                    String s1 = currentText.substring(aint[j3], aint[j3 + 1]);
                    int k2 = (int) splitter.stringWidth(s1); // stringWidth -> stringWidth
                    list1.add(this.createSelection(new Pos2i(0, j2), new Pos2i(k2, j2 + 9)));
                }

                list1.add(this.createPartialLineSelection(currentText, splitter, aint[k1], i1, k1 * 9, aint[k1]));
            }
        }

        return new DisplayCache(
                currentText, cursorPos, cursorAtEnd, aint, lines.toArray(new LineInfo[0]), list1.toArray(new Rect2i[0])
        );
    }

    static int findLineFromPos(int[] lineStarts, int find) {
        int i = Arrays.binarySearch(lineStarts, find);
        return i < 0 ? -(i + 2) : i;
    }

    private Rect2i createPartialLineSelection(String input, StringSplitter splitter, int startPos,
                                              int endPos, int y, int lineStart) {
        String s = input.substring(lineStart, startPos);
        String s1 = input.substring(lineStart, endPos);
        // stringWidth -> stringWidth
        Pos2i p1 = new Pos2i((int) splitter.stringWidth(s), y);
        Pos2i p2 = new Pos2i((int) splitter.stringWidth(s1), y + 9);
        return this.createSelection(p1, p2);
    }

    private Rect2i createSelection(Pos2i corner1, Pos2i corner2) {
        Pos2i screenP1 = this.convertLocalToScreen(corner1);
        Pos2i screenP2 = this.convertLocalToScreen(corner2);
        int minX = Math.min(screenP1.x, screenP2.x);
        int maxX = Math.max(screenP1.x, screenP2.x);
        int minY = Math.min(screenP1.y, screenP2.y);
        int maxY = Math.max(screenP1.y, screenP2.y);
        return new Rect2i(minX, minY, maxX - minX, maxY - minY);
    }

    static class LineInfo {
        final Style style;
        final String contents;
        final Component asComponent;
        final int x;
        final int y;

        public LineInfo(Style style, String contents, int x, int y) {
            this.style = style;
            this.contents = contents;
            this.x = x;
            this.y = y;
            this.asComponent = Component.literal(contents).setStyle(style);
        }
    }

    static class DisplayCache {
        static final DisplayCache EMPTY = new DisplayCache("", new Pos2i(0, 0), true,
                new int[]{0}, new LineInfo[]{new LineInfo(Style.EMPTY, "", 0, 0)},
                new Rect2i[0]
        );
        private final String fullText;
        final Pos2i cursor;
        final boolean cursorAtEnd;
        private final int[] lineStarts;
        final LineInfo[] lines;
        final Rect2i[] selection;

        public DisplayCache(String fullText, Pos2i cursor, boolean cursorAtEnd, int[] lineStarts,
                            LineInfo[] lines, Rect2i[] selection) {
            this.fullText = fullText;
            this.cursor = cursor;
            this.cursorAtEnd = cursorAtEnd;
            this.lineStarts = lineStarts;
            this.lines = lines;
            this.selection = selection;
        }

        public int getIndexAtPosition(Font font, Pos2i cursorPosition) {
            int i = cursorPosition.y / 9;
            if (i < 0) {
                return 0;
            } else if (i >= this.lines.length) {
                return this.fullText.length();
            } else {
                LineInfo line = this.lines[i];
                return this.lineStarts[i]
                        + font.getSplitter().plainIndexAtWidth(line.contents, cursorPosition.x, line.style);
            }
        }

        public int changeLine(int xChange, int yChange) {
            int i = findLineFromPos(this.lineStarts, xChange);
            int j = i + yChange;
            int k;
            if (0 <= j && j < this.lineStarts.length) {
                int l = xChange - this.lineStarts[i];
                int i1 = this.lines[j].contents.length();
                k = this.lineStarts[j] + Math.min(l, i1);
            } else {
                k = xChange;
            }

            return k;
        }

        public int findLineStart(int line) {
            int i = findLineFromPos(this.lineStarts, line);
            return this.lineStarts[i];
        }

        public int findLineEnd(int line) {
            int i = findLineFromPos(this.lineStarts, line);
            return this.lineStarts[i] + this.lines[i].contents.length();
        }
    }

    public static class PageButton extends Button {
        private final int sign;

        public PageButton(int x, int y, int sign, Button.OnPress onPress, Component title) {
            super(x, y, 11, 19, title, onPress, DEFAULT_NARRATION);
            Preconditions.checkArgument(sign == 1 || sign == -1);
            this.sign = sign;
        }

        @Override
        public void renderWidget(GuiGraphics matrixStack, int mouseX, int mouseY, float partialTicks) {
            // render button texture
            RenderSystem.enableDepthTest();
            int u0 = 227 + Mth.sign(this.sign) * 7, v0 = 234;
            matrixStack.blit(TEXTURE, this.getX(), this.getY(), u0, v0, this.width, this.height);
        }
    }

    record Pos2i(int x, int y) {
        // nothing here
    }
}
