package dev.xetyk.miniassist.gui;

import dev.xetyk.miniassist.config.ConfigManager;
import dev.xetyk.miniassist.modules.api.BooleanSetting;
import dev.xetyk.miniassist.modules.api.Category;
import dev.xetyk.miniassist.modules.api.Module;
import dev.xetyk.miniassist.modules.api.ModuleManager;
import dev.xetyk.miniassist.modules.api.MultiBooleanSetting;
import dev.xetyk.miniassist.modules.api.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Modern dark ClickGUI with category navigation, setting sliders and in-menu key capture. */
public final class MiniAssistMenuScreen extends Screen {
    private static final int MARGIN = 16;
    private static final int HEADER_Y = 14;
    private static final int HEADER_H = 34;
    private static final int SIDEBAR_X = 16;
    private static final int SIDEBAR_Y = 58;
    private static final int SIDEBAR_W = 138;
    private static final int CONTENT_TOP = 96;
    private static final int CONTENT_BOTTOM_GAP = 18;
    private static final int CARD_GAP = 10;

    private static final int BACKDROP = 0xE8080C14;
    private static final int HEADER_BG = 0xF0131A28;
    private static final int SIDEBAR_BG = 0xF0101724;
    private static final int CARD_BG = 0xF2151E2D;
    private static final int CARD_HOVER = 0xFF202C3E;
    private static final int ROW_LINE = 0xFF2A3548;
    private static final int ACCENT = 0xFF9B83FF;
    private static final int ACCENT_DARK = 0xFF6654C8;
    private static final int ACCENT_BLUE = 0xFF59C6EE;
    private static final int TEXT = 0xFFF2F4FA;
    private static final int TEXT_DIM = 0xFF9AA6B9;
    private static final int TEXT_FAINT = 0xFF68768B;
    private static final int OFF = 0xFF313B4D;

    private static final class CardBox {
        final Module module;
        final int x;
        final int y;
        final int w;
        final int h;

        CardBox(Module module, int x, int y, int w, int h) {
            this.module = module;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    private static final class HitTarget {
        final int x;
        final int y;
        final int w;
        final int h;
        final Runnable action;
        final Module bindModule;
        final NumberSetting slider;

        HitTarget(int x, int y, int w, int h, Runnable action, Module bindModule, NumberSetting slider) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.action = action;
            this.bindModule = bindModule;
            this.slider = slider;
        }

        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        }
    }

    private final List<CardBox> cards = new ArrayList<>();
    private final List<HitTarget> hitTargets = new ArrayList<>();
    private Category selectedCategory = Category.COMBAT;
    private Module bindingModule;
    private String bindNotice;
    private NumberSetting draggingSlider;
    private HitTarget activeSliderArea;
    private int scrollOffset;
    private int maxScroll;
    private int contentX;
    private int contentW;
    private int contentBottom;

    public MiniAssistMenuScreen() {
        super(Text.literal("MiniAssist"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, width, height, BACKDROP, 0xF5060910);
        drawHeader(context);
        buildCards();
        drawSidebar(context, mouseX, mouseY);

        context.fill(contentX, SIDEBAR_Y, contentX + contentW, height - CONTENT_BOTTOM_GAP,
                0x440D1420);
        drawContentHeading(context);
        context.enableScissor(contentX, CONTENT_TOP, contentX + contentW, height - CONTENT_BOTTOM_GAP);
        for (CardBox card : cards) drawModuleCard(context, card, mouseX, mouseY);
        if (maxScroll > 0) {
            int trackX = contentX + contentW - 4;
            int trackTop = CONTENT_TOP + 4;
            int trackHeight = Math.max(1, contentBottom - CONTENT_TOP - 8);
            int thumbHeight = Math.max(24, (int) ((double) trackHeight * trackHeight / (trackHeight + maxScroll)));
            int thumbY = trackTop + (int) ((double) (trackHeight - thumbHeight) * scrollOffset / maxScroll);
            context.fill(trackX, trackTop, trackX + 2, trackTop + trackHeight, 0xFF263247);
            context.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, ACCENT);
        }
        context.disableScissor();

        drawFooter(context);
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawHeader(DrawContext context) {
        context.fill(MARGIN, HEADER_Y, width - MARGIN, HEADER_Y + HEADER_H, HEADER_BG);
        context.fillGradient(MARGIN, HEADER_Y, width - MARGIN, HEADER_Y + 2, ACCENT, ACCENT_BLUE);
        context.fillGradient(25, 21, 44, 40, ACCENT, ACCENT_DARK);
        context.drawTextWithShadow(textRenderer, "M", 31, 26, TEXT);
        context.drawTextWithShadow(textRenderer, "MINIASSIST", 52, 21, TEXT);
        context.drawTextWithShadow(textRenderer, "CLIENT  /  FABRIC 1.21.4", 52, 33, TEXT_DIM);

        int active = 0;
        for (Module module : ModuleManager.getModules()) if (module.isEnabled()) active++;
        String status = active + " ACTIVE";
        int statusW = textRenderer.getWidth(status) + 18;
        int statusX = width - MARGIN - statusW - 12;
        context.fill(statusX, 22, statusX + statusW, 39, 0xFF1D2B3A);
        context.fill(statusX, 22, statusX + 2, 39, ACCENT_BLUE);
        context.drawTextWithShadow(textRenderer, status, statusX + 9, 27, TEXT);
        String close = "ESC  /  RIGHT SHIFT";
        context.drawTextWithShadow(textRenderer, close,
                width - MARGIN - textRenderer.getWidth(close) - 12, 46, TEXT_FAINT);
    }

    private void drawContentHeading(DrawContext context) {
        String title = selectedCategory.title.toUpperCase(java.util.Locale.ROOT) + " MODULES";
        int count = 0;
        int enabled = 0;
        for (Module module : ModuleManager.getModules()) {
            if (module.getCategory() == selectedCategory) {
                count++;
                if (module.isEnabled()) enabled++;
            }
        }
        context.drawTextWithShadow(textRenderer, title, contentX + 2, 75, TEXT);
        String subtitle = count + " modules   /   " + enabled + " enabled";
        context.drawTextWithShadow(textRenderer, subtitle, contentX + 2, 86, TEXT_FAINT);
    }

    private void drawSidebar(DrawContext context, int mouseX, int mouseY) {
        context.fill(SIDEBAR_X, SIDEBAR_Y, SIDEBAR_X + SIDEBAR_W, height - CONTENT_BOTTOM_GAP, SIDEBAR_BG);
        context.drawTextWithShadow(textRenderer, "NAVIGATION", SIDEBAR_X + 12, SIDEBAR_Y + 12, TEXT_FAINT);

        int index = 0;
        for (Category category : Category.values()) {
            int y = SIDEBAR_Y + 34 + index * 34;
            int rowX = SIDEBAR_X + 7;
            int rowW = SIDEBAR_W - 14;
            boolean active = selectedCategory == category;
            boolean hovered = mouseX >= rowX && mouseX < rowX + rowW && mouseY >= y && mouseY < y + 27;
            if (active) {
                context.fill(rowX, y, rowX + rowW, y + 27, 0xFF262342);
                context.fill(rowX, y, rowX + 2, y + 27, ACCENT);
            } else if (hovered) {
                context.fill(rowX, y, rowX + rowW, y + 27, CARD_HOVER);
            }
            String number = String.format(java.util.Locale.ROOT, "%02d", index + 1);
            context.drawTextWithShadow(textRenderer, number, rowX + 8, y + 9,
                    active ? ACCENT : TEXT_FAINT);
            context.drawTextWithShadow(textRenderer, category.title, rowX + 31, y + 9,
                    active ? TEXT : TEXT_DIM);
            hitTargets.add(new HitTarget(rowX, y, rowW, 27,
                    () -> { selectedCategory = category; scrollOffset = 0; }, null, null));
            index++;
        }

        int footerY = height - 65;
        context.fill(SIDEBAR_X + 8, footerY - 8, SIDEBAR_X + SIDEBAR_W - 8, footerY - 7, ROW_LINE);
        context.drawTextWithShadow(textRenderer, "MODULE BINDS", SIDEBAR_X + 12, footerY, TEXT_FAINT);
        context.drawTextWithShadow(textRenderer, "Кнопка BIND → клавиша", SIDEBAR_X + 12, footerY + 13, TEXT_DIM);
        context.drawTextWithShadow(textRenderer, "DEL — очистить", SIDEBAR_X + 12, footerY + 25, TEXT_DIM);
    }

    private void buildCards() {
        cards.clear();
        hitTargets.clear();
        contentX = SIDEBAR_X + SIDEBAR_W + 12;
        contentW = width - contentX - MARGIN;
        contentBottom = height - CONTENT_BOTTOM_GAP;

        int columns = contentW >= 620 ? 2 : 1;
        int cardW = Math.max(210, (contentW - (columns - 1) * CARD_GAP) / columns);
        List<Module> modules = new ArrayList<>();
        for (Module module : ModuleManager.getModules()) {
            if (module.getCategory() == selectedCategory) modules.add(module);
        }

        int rowY = CONTENT_TOP - scrollOffset;
        int totalHeight = 0;
        for (int start = 0; start < modules.size(); start += columns) {
            int rowHeight = 0;
            List<CardBox> rowCards = new ArrayList<>();
            for (int col = 0; col < columns && start + col < modules.size(); col++) {
                Module module = modules.get(start + col);
                int cardX = contentX + col * (cardW + CARD_GAP);
                int cardH = measureCardHeight(module, cardW);
                rowHeight = Math.max(rowHeight, cardH);
                rowCards.add(new CardBox(module, cardX, rowY, cardW, cardH));
            }
            cards.addAll(rowCards);
            rowY += rowHeight + CARD_GAP;
            totalHeight += rowHeight + CARD_GAP;
        }

        int viewHeight = Math.max(1, contentBottom - CONTENT_TOP);
        maxScroll = Math.max(0, totalHeight - CARD_GAP - viewHeight);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
    }

    private int measureCardHeight(Module module, int cardW) {
        int y = 62;
        for (BooleanSetting setting : module.getBooleanSettings()) if (setting.isVisible()) y += 27;
        for (NumberSetting setting : module.getNumberSettings()) if (setting.isVisible()) y += 41;
        for (MultiBooleanSetting multi : module.getMultiSettings()) {
            int usableW = cardW - 24;
            int lineW = 0;
            int lines = 1;
            for (String option : multi.getOptions()) {
                int chipW = Math.max(44, textRenderer.getWidth(option) + 17);
                if (lineW > 0 && lineW + chipW > usableW) {
                    lines++;
                    lineW = 0;
                }
                lineW += chipW + 5;
            }
            y += 18 + lines * 21;
        }
        return y + 8;
    }

    private void drawModuleCard(DrawContext context, CardBox card, int mouseX, int mouseY) {
        int x = card.x;
        int y = card.y;
        int w = card.w;
        int h = card.h;
        boolean enabled = card.module.isEnabled();
        context.fill(x, y, x + w, y + h, CARD_BG);
        context.fillGradient(x, y, x + w, y + 2, enabled ? ACCENT : ACCENT_BLUE, 0xFF202A3B);
        if (enabled) context.fill(x, y + 2, x + 3, y + h, ACCENT);

        context.drawTextWithShadow(textRenderer, card.module.getName(), x + 12, y + 10, TEXT);

        String bindText = bindingModule == card.module ? "PRESS" : card.module.getBindName();
        int bindW = Math.max(38, textRenderer.getWidth(bindText) + 10);
        int bindX = x + w - 48 - bindW;
        String description = card.module.getDescription();
        int descriptionWidth = Math.max(0, bindX - (x + 12) - 7);
        while (!description.isEmpty() && textRenderer.getWidth(description) > descriptionWidth) {
            description = description.substring(0, description.length() - 1);
        }
        if (description.length() < card.module.getDescription().length() && description.length() > 3) {
            description = description.substring(0, description.length() - 3) + "...";
        }
        context.drawTextWithShadow(textRenderer, description, x + 12, y + 24, TEXT_DIM);
        boolean bindHover = mouseX >= bindX && mouseX < bindX + bindW && mouseY >= y + 9 && mouseY < y + 28;
        context.fill(bindX, y + 8, bindX + bindW, y + 27, bindHover ? 0xFF30394E : 0xFF202A3A);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(bindText), bindX + bindW / 2, y + 14,
                bindingModule == card.module ? ACCENT : TEXT_DIM);
        hitTargets.add(new HitTarget(bindX, y + 8, bindW, 19, null, card.module, null));

        int toggleX = x + w - 40;
        drawSwitch(context, toggleX, y + 10, enabled, mouseX >= toggleX && mouseX < toggleX + 28
                && mouseY >= y + 8 && mouseY < y + 28);
        hitTargets.add(new HitTarget(toggleX - 3, y + 7, 34, 23, card.module::toggle, null, null));

        context.fill(x + 10, y + 43, x + w - 10, y + 44, ROW_LINE);
        int cursorY = y + 51;
        for (BooleanSetting setting : card.module.getBooleanSettings()) {
            if (!setting.isVisible()) continue;
            context.drawTextWithShadow(textRenderer, setting.getName(), x + 12, cursorY + 7, TEXT_DIM);
            int sx = x + w - 39;
            drawSwitch(context, sx, cursorY + 3, setting.isEnabled(),
                    mouseX >= sx && mouseX < sx + 28 && mouseY >= cursorY && mouseY < cursorY + 24);
            hitTargets.add(new HitTarget(sx - 4, cursorY, 36, 25, setting::toggle, null, null));
            cursorY += 27;
        }

        for (NumberSetting setting : card.module.getNumberSettings()) {
            if (!setting.isVisible()) continue;
            context.drawTextWithShadow(textRenderer, setting.getName(), x + 12, cursorY + 2, TEXT_DIM);
            String value = setting.getDisplayValue();
            context.drawTextWithShadow(textRenderer, value,
                    x + w - 12 - textRenderer.getWidth(value), cursorY + 2, ACCENT_BLUE);
            int sx = x + 12;
            int sy = cursorY + 20;
            int sw = w - 24;
            context.fill(sx, sy, sx + sw, sy + 4, OFF);
            int filled = (int) Math.round(sw * setting.getRatio());
            if (filled > 0) context.fillGradient(sx, sy, sx + filled, sy + 4, ACCENT, ACCENT_BLUE);
            int knobX = sx + filled;
            context.fill(knobX - 2, sy - 3, knobX + 3, sy + 7, TEXT);
            hitTargets.add(new HitTarget(sx, sy - 6, sw, 16, null, null, setting));
            cursorY += 41;
        }

        for (MultiBooleanSetting multi : card.module.getMultiSettings()) {
            context.drawTextWithShadow(textRenderer, multi.getName(), x + 12, cursorY + 1, TEXT_DIM);
            cursorY += 17;
            int chipX = x + 12;
            int usableW = w - 24;
            for (String option : multi.getOptions()) {
                int chipW = Math.max(44, textRenderer.getWidth(option) + 17);
                if (chipX + chipW > x + 12 + usableW) {
                    chipX = x + 12;
                    cursorY += 21;
                }
                boolean selected = multi.isSelected(option);
                int chipColor = selected ? 0xFF302B52 : 0xFF202A3A;
                context.fill(chipX, cursorY, chipX + chipW, cursorY + 17,
                        mouseX >= chipX && mouseX < chipX + chipW && mouseY >= cursorY && mouseY < cursorY + 17
                                ? CARD_HOVER : chipColor);
                context.drawCenteredTextWithShadow(textRenderer, Text.literal(option), chipX + chipW / 2,
                        cursorY + 5, selected ? ACCENT : TEXT_DIM);
                hitTargets.add(new HitTarget(chipX, cursorY, chipW, 17,
                        () -> multi.toggle(option), null, null));
                chipX += chipW + 5;
            }
            cursorY += 21;
        }
    }

    private void drawSwitch(DrawContext context, int x, int y, boolean enabled, boolean hovered) {
        int color = enabled ? 0xFF6657B8 : (hovered ? 0xFF46536A : OFF);
        context.fill(x, y, x + 28, y + 14, color);
        int knobX = enabled ? x + 16 : x + 2;
        context.fill(knobX, y + 2, knobX + 10, y + 12, enabled ? TEXT : 0xFFADB7C7);
    }

    private void drawFooter(DrawContext context) {
        String footer = bindingModule != null
                ? "Назначение клавиши для " + bindingModule.getName() + "  —  нажми клавишу, DEL очистить, ESC отмена"
                : (bindNotice != null ? bindNotice
                : "RIGHT SHIFT / ESC  —  закрыть     •     Колесо мыши  —  прокрутка");
        int boxW = Math.min(width - 32, textRenderer.getWidth(footer) + 22);
        int x = (width - boxW) / 2;
        int y = height - 17;
        context.fill(x, y - 3, x + boxW, y + 12, 0xF0131A28);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(footer), width / 2, y,
                bindingModule == null && bindNotice == null ? TEXT_FAINT : ACCENT);
    }

    private void updateSlider(HitTarget area, double mouseX) {
        if (area == null || area.slider == null) return;
        double ratio = (mouseX - area.x) / Math.max(1.0, area.w);
        area.slider.setFromRatio(ratio);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (mouseX >= contentX && mouseX < contentX + contentW
                && mouseY >= CONTENT_TOP && mouseY < contentBottom) {
            for (int i = hitTargets.size() - 1; i >= 0; i--) {
                HitTarget target = hitTargets.get(i);
                if (!target.contains(mouseX, mouseY)) continue;
                if (target.bindModule != null) {
                    bindingModule = target.bindModule;
                    bindNotice = null;
                    return true;
                }
                if (target.slider != null) {
                    draggingSlider = target.slider;
                    activeSliderArea = target;
                    updateSlider(target, mouseX);
                    ConfigManager.save();
                    return true;
                }
                if (target.action != null) {
                    target.action.run();
                    ConfigManager.save();
                    return true;
                }
            }
        }
        // Navigation buttons are placed in the sidebar, outside the content scissor.
        for (int i = hitTargets.size() - 1; i >= 0; i--) {
            HitTarget target = hitTargets.get(i);
            if (target.x < contentX && target.bindModule == null && target.slider == null
                    && target.action != null && target.contains(mouseX, mouseY)) {
                target.action.run();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && draggingSlider != null && activeSliderArea != null) {
            updateSlider(activeSliderArea, mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingSlider != null) {
            draggingSlider = null;
            activeSliderArea = null;
            ConfigManager.save();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= contentX && mouseX < contentX + contentW
                && mouseY >= CONTENT_TOP && mouseY < contentBottom && maxScroll > 0) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) Math.round(verticalAmount * 28.0)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (bindingModule != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                bindingModule = null;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                String clearedName = bindingModule.getName();
                bindingModule.setKeyCode(GLFW.GLFW_KEY_UNKNOWN);
                bindingModule = null;
                bindNotice = clearedName + " — клавиша очищена";
                ConfigManager.save();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
                bindingModule = null;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_G) {
                // G is reserved for the global HUD visibility toggle.
                bindingModule = null;
                bindNotice = "G занята переключением HUD";
                return true;
            }
            if (keyCode >= 0) {
                Module changedModule = bindingModule;
                boolean clearedConflict = false;
                for (Module other : ModuleManager.getModules()) {
                    if (other != changedModule && other.getKeyCode() == keyCode) {
                        other.setKeyCode(GLFW.GLFW_KEY_UNKNOWN);
                        clearedConflict = true;
                    }
                }
                changedModule.setKeyCode(keyCode);
                bindNotice = changedModule.getName() + " → " + changedModule.getBindName()
                        + (clearedConflict ? "  •  прежний конфликтующий бинд очищен" : "");
                bindingModule = null;
                ConfigManager.save();
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        draggingSlider = null;
        activeSliderArea = null;
        bindingModule = null;
        ConfigManager.save();
        super.removed();
    }
}
