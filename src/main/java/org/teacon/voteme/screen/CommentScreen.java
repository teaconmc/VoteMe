package org.teacon.voteme.screen;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.font.TextFieldHelper.CursorStep;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
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
@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class CommentScreen extends Screen {
    private static final Component DONE = CommonComponents.GUI_DONE;
    private static final Component CLEAR = Component.translatable("gui.voteme.voter.clear");
    private static final Component UNSET = Component.translatable("gui.voteme.voter.unset");
    private static final Identifier TEXTURE = Identifier.parse("voteme:textures/gui/comment.png");

    private static final int TEXT_COLOR = 0xFF000000 | DyeColor.WHITE.getTextColor();
    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 256;
    private static final int MAX_PAGES = 10; // TODO(3TUSK): Configurable value

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

    private final List<String> parentComments;

    public CommentScreen(List<String> parentComments) {
        super(GameNarrator.NO_TITLE);
        this.parentComments = parentComments;
        this.pages = new ArrayList<>(Math.max(1, parentComments.size()));
        this.pages.addAll(parentComments.isEmpty() ? List.of("") : parentComments);
        this.pageEdit = new TextFieldHelper(
                this::getCurrentPageText, this::setCurrentPageText, this::getClipboard, this::setClipboard,
                unfiltered -> unfiltered.length() < 1024 && this.font.wordWrapHeight(Component.literal(unfiltered), 200) <= 163);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void setClipboard(String clipboardValue) {
        TextFieldHelper.setClipboardContents(this.minecraft, clipboardValue);
    }

    private String getClipboard() {
        return TextFieldHelper.getClipboardContents(this.minecraft);
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
        this.doneButton = this.addRenderableWidget(new VoterScreen.BottomButton(
                this.width / 2 + 52, this.height / 2 + 82, false, this::onOKButtonClick, DONE));
        this.clearButton = this.addRenderableWidget(new VoterScreen.BottomButton(
                this.width / 2 - 104, this.height / 2 + 82, true, this::onClearButtonClick, CLEAR));
        this.unsetButton = this.addRenderableWidget(new VoterScreen.BottomButton(
                this.width / 2 - 104, this.height / 2 + 82, true, this::onUnsetButtonClick, UNSET));
        this.forwardButton = this.addRenderableWidget(new PageButton(
                this.width / 2 + 36, this.height / 2 + 82, 1, button -> this.pageForward(), EMPTY));
        this.backButton = this.addRenderableWidget(new PageButton(
                this.width / 2 - 49, this.height / 2 + 82, -1, button -> this.pageBack(), EMPTY));
        this.updateButtonVisibility();
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

    private void appendPageToBook() {
        if (this.getNumPages() < MAX_PAGES) {
            this.pages.add("");
            this.isModified = true;
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (super.keyPressed(event)) {
            return true;
        }
        if (this.bookKeyPressed(event)) {
            this.clearDisplayCache();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (super.charTyped(event)) {
            return true;
        }
        if (event.isAllowedChatCharacter()) {
            this.pageEdit.insertText(event.codepointAsString());
            this.clearDisplayCache();
            return true;
        }
        return false;
    }

    private boolean bookKeyPressed(KeyEvent event) {
        CursorStep cursorStep = event.hasControlDownWithQuirk() ? CursorStep.WORD : CursorStep.CHARACTER;
        if (event.isSelectAll()) {
            this.pageEdit.selectAll();
            return true;
        }
        if (event.isCopy()) {
            this.pageEdit.copy();
            return true;
        }
        if (event.isPaste()) {
            this.pageEdit.paste();
            return true;
        }
        if (event.isCut()) {
            this.pageEdit.cut();
            return true;
        }
        return switch (event.key()) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                this.pageEdit.insertText("\n");
                yield true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                this.pageEdit.removeFromCursor(-1, cursorStep);
                yield true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                this.pageEdit.removeFromCursor(1, cursorStep);
                yield true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                this.pageEdit.moveBy(1, event.hasShiftDown(), cursorStep);
                yield true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                this.pageEdit.moveBy(-1, event.hasShiftDown(), cursorStep);
                yield true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                this.changeLine(1, event.hasShiftDown());
                yield true;
            }
            case GLFW.GLFW_KEY_UP -> {
                this.changeLine(-1, event.hasShiftDown());
                yield true;
            }
            case GLFW.GLFW_KEY_PAGE_UP -> {
                this.pageBack();
                yield true;
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                this.pageForward();
                yield true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                this.keyHome(event.hasControlDownWithQuirk(), event.hasShiftDown());
                yield true;
            }
            case GLFW.GLFW_KEY_END -> {
                this.keyEnd(event.hasControlDownWithQuirk(), event.hasShiftDown());
                yield true;
            }
            default -> false;
        };
    }

    private void changeLine(int yChange, boolean selecting) {
        int cursor = this.pageEdit.getCursorPos();
        int next = this.getDisplayCache().changeLine(cursor, yChange);
        this.pageEdit.setCursorPos(next, selecting);
    }

    private void keyHome(boolean hasControlDown, boolean selecting) {
        if (hasControlDown) {
            this.pageEdit.setCursorToStart(selecting);
        } else {
            int cursor = this.pageEdit.getCursorPos();
            int next = this.getDisplayCache().findLineStart(cursor);
            this.pageEdit.setCursorPos(next, selecting);
        }
    }

    private void keyEnd(boolean hasControlDown, boolean selecting) {
        if (hasControlDown) {
            this.pageEdit.setCursorToEnd(selecting);
        } else {
            DisplayCache displayCache = this.getDisplayCache();
            int cursor = this.pageEdit.getCursorPos();
            int next = displayCache.findLineEnd(cursor);
            this.pageEdit.setCursorPos(next, selecting);
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
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        graphics.textRenderer().accept(TextAlignment.CENTER, this.width / 2, this.height / 2 + 87, this.getPageMessage());
        DisplayCache page = this.getDisplayCache();
        for (LineInfo line : page.lines) {
            graphics.text(this.font, line.asComponent, line.x, line.y, TEXT_COLOR, false);
        }
        this.renderHighlight(graphics, page.selection);
        this.renderCursor(graphics, page.cursor, page.cursorAtEnd);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.width / 2 - 111, this.height / 2 - 97, 0, 0, 234, 206, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.width / 2 - 49, this.height / 2 + 82, 7, 211, 96, 19, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    private void renderCursor(GuiGraphicsExtractor graphics, Pos2i cursorPos, boolean isEndOfText) {
        if (this.frameTick / 6 % 2 == 0) {
            Pos2i screenCursorPos = this.convertLocalToScreen(cursorPos);
            if (!isEndOfText) {
                graphics.fill(screenCursorPos.x, screenCursorPos.y - 1, screenCursorPos.x + 1, screenCursorPos.y + 9, TEXT_COLOR);
            } else {
                graphics.text(this.font, "_", screenCursorPos.x, screenCursorPos.y, TEXT_COLOR, false);
            }
        }
    }

    private void renderHighlight(GuiGraphicsExtractor graphics, Rect2i[] highlightAreas) {
        for (Rect2i rect2i : highlightAreas) {
            int x0 = rect2i.getX();
            int y0 = rect2i.getY();
            int x1 = x0 + rect2i.getWidth();
            int y1 = y0 + rect2i.getHeight();
            graphics.textHighlight(x0, y0, x1, y1, false);
        }
    }

    private Pos2i convertScreenToLocal(Pos2i screenPos) {
        return new Pos2i(screenPos.x - this.width / 2 + 100, screenPos.y - this.height / 2 + 86);
    }

    private Pos2i convertLocalToScreen(Pos2i localScreenPos) {
        return new Pos2i(localScreenPos.x + this.width / 2 - 100, localScreenPos.y + this.height / 2 - 86);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            long currentTime = Util.getMillis();
            DisplayCache page = this.getDisplayCache();
            int index = page.getIndexAtPosition(this.font, this.convertScreenToLocal(new Pos2i((int) event.x(), (int) event.y())));
            if (index >= 0) {
                if (index != this.lastIndex || currentTime - this.lastClickTime >= 250L) {
                    this.pageEdit.setCursorPos(index, event.hasShiftDown());
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
        this.pageEdit.setSelectionRange(
                StringSplitter.getWordPosition(currentText, -1, index, false),
                StringSplitter.getWordPosition(currentText, 1, index, false)
        );
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (super.mouseDragged(event, dragX, dragY)) {
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            DisplayCache page = this.getDisplayCache();
            int index = page.getIndexAtPosition(this.font, this.convertScreenToLocal(new Pos2i((int) event.x(), (int) event.y())));
            this.pageEdit.setCursorPos(index, true);
            this.clearDisplayCache();
        }
        return true;
    }

    private DisplayCache getDisplayCache() {
        DisplayCache currentDisplayCache = this.displayCache;
        if (currentDisplayCache == null) {
            this.displayCache = currentDisplayCache = this.rebuildDisplayCache();
        }
        return currentDisplayCache;
    }

    private Component getPageMessage() {
        return Component.translatable("book.pageIndicator", this.currentPage + 1, this.getNumPages())
                .withColor(0xFF000000)
                .withoutShadow();
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
        int cursor = this.pageEdit.getCursorPos();
        int selection = this.pageEdit.getSelectionPos();
        IntList lineStarts = new IntArrayList();
        List<LineInfo> lines = Lists.newArrayList();
        MutableInt lineNum = new MutableInt();
        MutableBoolean trailingNewLine = new MutableBoolean();
        StringSplitter splitter = this.font.getSplitter();
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
        int[] lineStartArray = lineStarts.toIntArray();
        boolean cursorAtEnd = cursor == currentText.length();
        Pos2i cursorPos;
        if (cursorAtEnd && trailingNewLine.isTrue()) {
            cursorPos = new Pos2i(0, lines.size() * 9);
        } else {
            int lineIndex = findLineFromPos(lineStartArray, cursor);
            int lineWidth = this.font.width(currentText.substring(lineStartArray[lineIndex], cursor));
            cursorPos = new Pos2i(lineWidth, lineIndex * 9);
        }

        List<Rect2i> selections = Lists.newArrayList();
        if (cursor != selection) {
            int selectionStart = Math.min(cursor, selection);
            int selectionEnd = Math.max(cursor, selection);
            int startLine = findLineFromPos(lineStartArray, selectionStart);
            int endLine = findLineFromPos(lineStartArray, selectionEnd);
            if (startLine == endLine) {
                int y = startLine * 9;
                int lineStart = lineStartArray[startLine];
                selections.add(this.createPartialLineSelection(currentText, splitter, selectionStart, selectionEnd, y, lineStart));
            } else {
                int nextLineStart = startLine + 1 > lineStartArray.length ? currentText.length() : lineStartArray[startLine + 1];
                selections.add(this.createPartialLineSelection(currentText, splitter, selectionStart, nextLineStart, startLine * 9, lineStartArray[startLine]));

                for (int i = startLine + 1; i < endLine; i++) {
                    int y = i * 9;
                    String lineText = currentText.substring(lineStartArray[i], lineStartArray[i + 1]);
                    int lineWidth = (int) splitter.stringWidth(lineText);
                    selections.add(this.createSelection(new Pos2i(0, y), new Pos2i(lineWidth, y + 9)));
                }

                selections.add(this.createPartialLineSelection(currentText, splitter, lineStartArray[endLine], selectionEnd, endLine * 9, lineStartArray[endLine]));
            }
        }

        return new DisplayCache(
                currentText, cursorPos, cursorAtEnd, lineStartArray, lines.toArray(new LineInfo[0]), selections.toArray(new Rect2i[0])
        );
    }

    static int findLineFromPos(int[] lineStarts, int find) {
        int index = Arrays.binarySearch(lineStarts, find);
        return index < 0 ? -(index + 2) : index;
    }

    private Rect2i createPartialLineSelection(String input, StringSplitter splitter, int startPos, int endPos, int y, int lineStart) {
        String left = input.substring(lineStart, startPos);
        String right = input.substring(lineStart, endPos);
        Pos2i p1 = new Pos2i((int) splitter.stringWidth(left), y);
        Pos2i p2 = new Pos2i((int) splitter.stringWidth(right), y + 9);
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

        LineInfo(Style style, String contents, int x, int y) {
            this.style = style;
            this.contents = contents;
            this.x = x;
            this.y = y;
            this.asComponent = Component.literal(contents).setStyle(style);
        }
    }

    static class DisplayCache {
        static final DisplayCache EMPTY = new DisplayCache(
                "", new Pos2i(0, 0), true, new int[]{0}, new LineInfo[]{new LineInfo(Style.EMPTY, "", 0, 0)}, new Rect2i[0]
        );

        private final String fullText;
        final Pos2i cursor;
        final boolean cursorAtEnd;
        private final int[] lineStarts;
        final LineInfo[] lines;
        final Rect2i[] selection;

        DisplayCache(String fullText, Pos2i cursor, boolean cursorAtEnd, int[] lineStarts, LineInfo[] lines, Rect2i[] selection) {
            this.fullText = fullText;
            this.cursor = cursor;
            this.cursorAtEnd = cursorAtEnd;
            this.lineStarts = lineStarts;
            this.lines = lines;
            this.selection = selection;
        }

        int getIndexAtPosition(Font font, Pos2i cursorPosition) {
            int lineIndex = cursorPosition.y / 9;
            if (lineIndex < 0) {
                return 0;
            }
            if (lineIndex >= this.lines.length) {
                return this.fullText.length();
            }
            LineInfo line = this.lines[lineIndex];
            return this.lineStarts[lineIndex] + font.getSplitter().plainIndexAtWidth(line.contents, cursorPosition.x, line.style);
        }

        int changeLine(int cursor, int yChange) {
            int lineIndex = findLineFromPos(this.lineStarts, cursor);
            int nextLineIndex = lineIndex + yChange;
            if (0 <= nextLineIndex && nextLineIndex < this.lineStarts.length) {
                int column = cursor - this.lineStarts[lineIndex];
                int nextLineLength = this.lines[nextLineIndex].contents.length();
                return this.lineStarts[nextLineIndex] + Math.min(column, nextLineLength);
            }
            return cursor;
        }

        int findLineStart(int cursor) {
            int lineIndex = findLineFromPos(this.lineStarts, cursor);
            return this.lineStarts[lineIndex];
        }

        int findLineEnd(int cursor) {
            int lineIndex = findLineFromPos(this.lineStarts, cursor);
            return this.lineStarts[lineIndex] + this.lines[lineIndex].contents.length();
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
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            int u0 = 227 + Mth.sign(this.sign) * 7;
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.getX(), this.getY(), u0, 234, this.width, this.height, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        }
    }

    record Pos2i(int x, int y) {
        // nothing here
    }
}
