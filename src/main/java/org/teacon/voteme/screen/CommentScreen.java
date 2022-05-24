package org.teacon.voteme.screen;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableInt;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A partial replica of {@link BookEditScreen}, adapted for our own use case.
 * Used for allowing voter to provide additional comments.
 *
 * @author 3TUSK
 */
@OnlyIn(Dist.CLIENT)
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class CommentScreen extends Screen {

    private boolean isModified;
    private int frameTick;
    private int currentPage;
    private final List<String> pages;
    private final TextFieldHelper pageEdit;
    private long lastClickTime;
    private int lastIndex = -1;
    private PageButton forwardButton;
    private PageButton backButton;
    @Nullable
    private CommentScreen.Page displayCache = CommentScreen.Page.EMPTY;
    private Component pageMsg = TextComponent.EMPTY;

    private final VoterScreen parent;

    public CommentScreen(@Nonnull VoterScreen parent) {
        super(TextComponent.EMPTY);
        this.parent = parent;
        this.pages = new ArrayList<>(parent.currentComments);
        if (this.pages.isEmpty()) {
            this.pages.add("");
        }
        // Must be initialized after this.pages
        this.pageEdit = new TextFieldHelper(this::getCurrentPageText, this::setCurrentPageText,
                this::getClipboard, this::setClipboard, (p_238774_1_) -> {
            return p_238774_1_.length() < 1024 && this.font.wordWrapHeight(p_238774_1_, 114) <= 128;
        });
    }

    private void setClipboard(String srcText) {
        if (this.minecraft != null) {
            TextFieldHelper.setClipboardContents(this.minecraft, srcText);
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
        ++this.frameTick;
    }

    @Override
    protected void init() {
        this.clearDisplayCache();
        Objects.requireNonNull(this.minecraft).keyboardHandler.setSendRepeatsToGui(true);
        // Done button
        this.addWidget(new Button(this.width / 2 + 2, 196, 98, 20, CommonComponents.GUI_DONE, button -> this.handleExit(true)));
        // Cancel button
        this.addWidget(new Button(this.width / 2 - 100, 196, 98, 20, CommonComponents.GUI_CANCEL, button -> this.handleExit(false)));
        int x = (this.width - 192) / 2;
        int y = 159;
        this.forwardButton = this.addWidget(new PageButton(x + 116, y, true, button -> this.pageForward(), true));
        this.backButton = this.addWidget(new PageButton(x + 43, y, false, button -> this.pageBack(), true));
        this.backButton.visible = this.currentPage > 0;
    }

    private void handleExit(boolean publish) {
        if (publish && this.isModified) {
            // Eliminate all empty pages
            this.pages.removeIf(StringUtils::isBlank);
            this.parent.currentComments = new ArrayList<>(this.pages);
        }
        this.getMinecraft().setScreen(this.parent);
    }

    private void pageBack() {
        if (this.currentPage > 0) {
            --this.currentPage;
        }
        this.backButton.visible = this.currentPage > 0;
        this.clearDisplayCacheAfterPageChange();
    }

    private void pageForward() {
        if (this.currentPage < this.getNumPages() - 1) {
            ++this.currentPage;
        } else {
            this.appendPageToBook();
            if (this.currentPage < this.getNumPages() - 1) {
                ++this.currentPage;
            }
        }
        this.backButton.visible = this.currentPage > 0;
        this.clearDisplayCacheAfterPageChange();
    }

    @Override
    public void removed() {
        Objects.requireNonNull(this.minecraft).keyboardHandler.setSendRepeatsToGui(false);
    }

    @Override
    public void onClose() {
        this.handleExit(false);
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
        }
        if (this.handleFunctionalKey(keyCode, scanCode, modifiers)) {
            this.clearDisplayCache();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char p_231042_1_, int p_231042_2_) {
        if (super.charTyped(p_231042_1_, p_231042_2_)) {
            return true;
        } else if (SharedConstants.isAllowedChatCharacter(p_231042_1_)) {
            this.pageEdit.insertText(Character.toString(p_231042_1_));
            this.clearDisplayCache();
            return true;
        } else {
            return false;
        }
    }

    private boolean handleFunctionalKey(int keyCode, int scanCode, int modifiers) {
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
            switch (keyCode) {
                case GLFW.GLFW_KEY_ENTER: // 257
                case GLFW.GLFW_KEY_KP_ENTER: // 335
                    this.pageEdit.insertText("\n");
                    return true;
                case GLFW.GLFW_KEY_BACKSPACE: // 259
                    this.pageEdit.removeCharsFromCursor(-1);
                    return true;
                case GLFW.GLFW_KEY_DELETE: // 261
                    this.pageEdit.removeCharsFromCursor(1);
                    return true;
                case GLFW.GLFW_KEY_RIGHT: // 262
                    this.pageEdit.moveByChars(1, Screen.hasShiftDown());
                    return true;
                case GLFW.GLFW_KEY_LEFT: // 263
                    this.pageEdit.moveByChars(-1, Screen.hasShiftDown());
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

    private void changeLine(int p_238755_1_) {
        int i = this.pageEdit.getCursorPos();
        int j = this.getDisplayCache().changeLine(i, p_238755_1_);
        this.pageEdit.setCursorPos(j, Screen.hasShiftDown());
    }

    private void keyHome() {
        int i = this.pageEdit.getCursorPos();
        int j = this.getDisplayCache().findLineStart(i);
        this.pageEdit.setCursorPos(j, Screen.hasShiftDown());
    }

    private void keyEnd() {
        CommentScreen.Page editbookscreen$bookpage = this.getDisplayCache();
        int i = this.pageEdit.getCursorPos();
        int j = editbookscreen$bookpage.findLineEnd(i);
        this.pageEdit.setCursorPos(j, Screen.hasShiftDown());
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

    @SuppressWarnings("deprecation")
    @Override
    public void render(PoseStack xform, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(xform);
        this.setFocused(null);
        // 1.17: no longer needed due to programmable pipeline usage
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        Objects.requireNonNull(this.minecraft).getTextureManager().bindForSetup(BookViewScreen.BOOK_LOCATION);
        int x = (this.width - 192) / 2;
        int y = 2;
        this.blit(xform, x, 2, 0, 0, 192, 192);
        /* Begin rendering lines */
        {
            int pageIndicatorWidth = this.font.width(this.pageMsg);
            this.font.draw(xform, this.pageMsg, x - pageIndicatorWidth + 192 - 44, 18.0F, 0);

            CommentScreen.Page page = this.getDisplayCache();
            for (CommentScreen.Line line : page.lines) {
                this.font.draw(xform, line.asComponent, line.x, line.y, 0xFF000000);
            }
            this.renderHighlight(page.selection);
            this.renderCursor(xform, page.cursor, page.cursorAtEnd);
        }

        super.render(xform, mouseX, mouseY, partialTick);
    }

    private void renderCursor(PoseStack xform, CommentScreen.Point cursorPos, boolean p_238756_3_) {
        if (this.frameTick / 6 % 2 == 0) {
            cursorPos = this.convertLocalToScreen(cursorPos);
            if (!p_238756_3_) {
                Gui.fill(xform, cursorPos.x, cursorPos.y - 1, cursorPos.x + 1, cursorPos.y + 9, 0xFF000000);
            } else {
                this.font.draw(xform, "_", (float) cursorPos.x, (float) cursorPos.y, 0);
            }
        }

    }

    @SuppressWarnings("deprecation")
    private void renderHighlight(Rect2i[] highlights) {
        Tesselator t = Tesselator.getInstance();
        BufferBuilder builder = t.getBuilder();
        // 1.17: no longer needed due to programmable pipeline usage
        RenderSystem.setShaderColor(0.0F, 0.0F, 1.0F, 1.0F);
        RenderSystem.disableTexture();
        RenderSystem.enableColorLogicOp();
        RenderSystem.logicOp(GlStateManager.LogicOp.OR_REVERSE);
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);

        for (Rect2i highlight : highlights) {
            int minX = highlight.getX();
            int minY = highlight.getY();
            int maxX = minX + highlight.getWidth();
            int maxY = minY + highlight.getHeight();
            builder.vertex(minX, maxY, 0.0D).endVertex();
            builder.vertex(maxX, maxY, 0.0D).endVertex();
            builder.vertex(maxX, minY, 0.0D).endVertex();
            builder.vertex(minX, minY, 0.0D).endVertex();
        }

        t.end();
        RenderSystem.disableColorLogicOp();
        RenderSystem.enableTexture();
    }

    private CommentScreen.Point convertScreenToLocal(CommentScreen.Point point) {
        return new CommentScreen.Point(point.x - (this.width - 192) / 2 - 36, point.y - 32);
    }

    private CommentScreen.Point convertLocalToScreen(CommentScreen.Point point) {
        return new CommentScreen.Point(point.x + (this.width - 192) / 2 + 36, point.y + 32);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) { // 0
            long currentTime = Util.getMillis();
            CommentScreen.Page page = this.getDisplayCache();
            int index = page.getIndexAtPosition(this.font, this.convertScreenToLocal(new CommentScreen.Point((int) mouseX, (int) mouseY)));
            if (index >= 0) {
                if (index == this.lastIndex && currentTime - this.lastClickTime < 250L) {
                    if (!this.pageEdit.isSelecting()) {
                        this.selectWord(index);
                    } else {
                        this.pageEdit.selectAll();
                    }
                } else {
                    this.pageEdit.setCursorPos(index, Screen.hasShiftDown());
                }

                this.clearDisplayCache();
            }

            this.lastIndex = index;
            this.lastClickTime = currentTime;
        }

        return true;
    }

    private void selectWord(int p_238765_1_) {
        String currentText = this.getCurrentPageText();
        // getWordPosition -> getWordPosition
        this.pageEdit.setSelectionRange(StringSplitter.getWordPosition(currentText, -1, p_238765_1_, false),
                StringSplitter.getWordPosition(currentText, 1, p_238765_1_, false));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (super.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) { // 0
            CommentScreen.Page bookPage = this.getDisplayCache();
            int i = bookPage.getIndexAtPosition(this.font,
                    this.convertScreenToLocal(new CommentScreen.Point((int) mouseX, (int) mouseY)));
            this.pageEdit.setCursorPos(i, true);
            this.clearDisplayCache();
        }
        return true;
    }

    private CommentScreen.Page getDisplayCache() {
        if (this.displayCache == null) {
            this.displayCache = this.rebuildDisplayCache();
            this.pageMsg = new TranslatableComponent("book.pageIndicator", this.currentPage + 1, this.getNumPages());
        }
        return this.displayCache;
    }

    private void clearDisplayCache() {
        this.displayCache = null;
    }

    private void clearDisplayCacheAfterPageChange() {
        this.pageEdit.setCursorToEnd();
        this.clearDisplayCache();
    }

    private CommentScreen.Page rebuildDisplayCache() {
        String currentText = this.getCurrentPageText();
        if (currentText.isEmpty()) {
            return CommentScreen.Page.EMPTY;
        }
        int i = this.pageEdit.getCursorPos();
        int j = this.pageEdit.getSelectionPos();
        // List of indexes where a new line starts.
        IntList lineStarts = new IntArrayList();
        // All parsed lines.
        List<CommentScreen.Line> lines = new ArrayList<>();
        // 1-indexed line number.
        MutableInt lineNum = new MutableInt();
        // Tracking if the entire page is ending in a new line.
        // Used to properly shift the cursor down.
        MutableBoolean trailingNewLine = new MutableBoolean();
        StringSplitter splitter = this.font.getSplitter();
        // splitLines -> splitLines
        splitter.splitLines(currentText, 114, Style.EMPTY, true, (style, begin, endExclusive) -> {
            int currentLine = lineNum.getAndIncrement();
            String raw = currentText.substring(begin, endExclusive);
            trailingNewLine.setValue(raw.endsWith("\n"));
            String trimmed = StringUtils.stripEnd(raw, " \n");
            int localHeight = currentLine * 9;
            CommentScreen.Point screenPos = this.convertLocalToScreen(new CommentScreen.Point(0, localHeight));
            lineStarts.add(begin);
            lines.add(new CommentScreen.Line(style, trimmed, screenPos.x, screenPos.y));
        });
        int[] aint = lineStarts.toIntArray();
        boolean cursorAtEnd = i == currentText.length();
        CommentScreen.Point cursorPos;
        if (cursorAtEnd && trailingNewLine.isTrue()) {
            cursorPos = new CommentScreen.Point(0, lines.size() * 9);
        } else {
            int k = findLineFromPos(aint, i);
            int l = this.font.width(currentText.substring(aint[k], i));
            cursorPos = new CommentScreen.Point(l, k * 9);
        }

        List<Rect2i> list1 = new ArrayList<>();
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

                for (int j3 = j1 + 1; j3 < k1; ++j3) {
                    int j2 = j3 * 9;
                    String s1 = currentText.substring(aint[j3], aint[j3 + 1]);
                    int k2 = (int) splitter.stringWidth(s1); // stringWidth -> stringWidth
                    list1.add(
                            this.createSelection(new CommentScreen.Point(0, j2), new CommentScreen.Point(k2, j2 + 9)));
                }

                list1.add(this.createPartialLineSelection(currentText, splitter, aint[k1], i1, k1 * 9, aint[k1]));
            }
        }

        return new CommentScreen.Page(currentText, cursorPos, cursorAtEnd, aint,
                lines.toArray(new CommentScreen.Line[0]), list1.toArray(new Rect2i[0]));
    }

    private static int findLineFromPos(int[] p_238768_0_, int p_238768_1_) {
        int i = Arrays.binarySearch(p_238768_0_, p_238768_1_);
        return i < 0 ? -(i + 2) : i;
    }

    private Rect2i createPartialLineSelection(String p_238761_1_, StringSplitter splitter, int p_238761_3_,
                                              int p_238761_4_, int p_238761_5_, int p_238761_6_) {
        String s = p_238761_1_.substring(p_238761_6_, p_238761_3_);
        String s1 = p_238761_1_.substring(p_238761_6_, p_238761_4_);
        // stringWidth -> stringWidth
        CommentScreen.Point p1 = new CommentScreen.Point((int) splitter.stringWidth(s), p_238761_5_);
        CommentScreen.Point p2 = new CommentScreen.Point((int) splitter.stringWidth(s1), p_238761_5_ + 9);
        return this.createSelection(p1, p2);
    }

    private Rect2i createSelection(CommentScreen.Point p1, CommentScreen.Point p2) {
        CommentScreen.Point screenP1 = this.convertLocalToScreen(p1);
        CommentScreen.Point screenP2 = this.convertLocalToScreen(p2);
        int minX = Math.min(screenP1.x, screenP2.x);
        int maxX = Math.max(screenP1.x, screenP2.x);
        int minY = Math.min(screenP1.y, screenP2.y);
        int maxY = Math.max(screenP1.y, screenP2.y);
        return new Rect2i(minX, minY, maxX - minX, maxY - minY);
    }

    static class Line {
        final Style style;
        final String contents;
        final Component asComponent;
        final int x;
        final int y;

        public Line(Style style, String text, int x, int y) {
            this.style = style;
            this.contents = text;
            this.x = x;
            this.y = y;
            this.asComponent = new TextComponent(text).setStyle(style);
        }
    }

    static class Page {
        static final CommentScreen.Page EMPTY = new CommentScreen.Page("", new CommentScreen.Point(0, 0), true,
                new int[]{0}, new CommentScreen.Line[]{new CommentScreen.Line(Style.EMPTY, "", 0, 0)},
                new Rect2i[0]);
        final String fullText;
        final CommentScreen.Point cursor;
        final boolean cursorAtEnd;
        final int[] lineStarts;
        final CommentScreen.Line[] lines;
        final Rect2i[] selection;

        public Page(String text, CommentScreen.Point cursorPos, boolean isCursorAtEnd, int[] p_i232288_4_,
                    CommentScreen.Line[] lines, Rect2i[] p_i232288_6_) {
            this.fullText = text;
            this.cursor = cursorPos;
            this.cursorAtEnd = isCursorAtEnd;
            this.lineStarts = p_i232288_4_;
            this.lines = lines;
            this.selection = p_i232288_6_;
        }

        public int getIndexAtPosition(Font font, CommentScreen.Point p_238789_2_) {
            int i = p_238789_2_.y / 9;
            if (i < 0) {
                return 0;
            } else if (i >= this.lines.length) {
                return this.fullText.length();
            } else {
                CommentScreen.Line line = this.lines[i];
                // plainIndexAtWidth -> plainIndexAtWidth
                return this.lineStarts[i]
                        + font.getSplitter().plainIndexAtWidth(line.contents, p_238789_2_.x, line.style);
            }
        }

        public int changeLine(int p_238788_1_, int p_238788_2_) {
            int i = CommentScreen.findLineFromPos(this.lineStarts, p_238788_1_);
            int j = i + p_238788_2_;
            int k;
            if (0 <= j && j < this.lineStarts.length) {
                int l = p_238788_1_ - this.lineStarts[i];
                int i1 = this.lines[j].contents.length();
                k = this.lineStarts[j] + Math.min(l, i1);
            } else {
                k = p_238788_1_;
            }

            return k;
        }

        public int findLineStart(int p_238787_1_) {
            int i = CommentScreen.findLineFromPos(this.lineStarts, p_238787_1_);
            return this.lineStarts[i];
        }

        public int findLineEnd(int p_238791_1_) {
            int i = CommentScreen.findLineFromPos(this.lineStarts, p_238791_1_);
            return this.lineStarts[i] + this.lines[i].contents.length();
        }
    }

    record Point(int x, int y) {
        // nothing here
    }
}
