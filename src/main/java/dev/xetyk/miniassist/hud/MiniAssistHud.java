package dev.xetyk.miniassist.hud;

import dev.xetyk.miniassist.ClientKeybinds;
import dev.xetyk.miniassist.modules.api.Module;
import dev.xetyk.miniassist.modules.api.ModuleManager;
import dev.xetyk.miniassist.modules.impl.combat.AimAssist;
import dev.xetyk.miniassist.modules.impl.render.ClientHudModule;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Modular, compact HUD with dark panels and violet/cyan accents. */
public final class MiniAssistHud {
    private static final int PANEL = 0xD90B101A;
    private static final int PANEL_ALT = 0xE0111825;
    private static final int ACCENT = 0xFF9B83FF;
    private static final int ACCENT_BLUE = 0xFF59C6EE;
    private static final int TEXT = 0xFFF2F4FA;
    private static final int MUTED = 0xFF9AA6B9;
    private static final int FAINT = 0xFF68768B;
    private static final int TRACK = 0xFF293447;

    private MiniAssistHud() {}

    public static void init() {
        HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));
    }

    private static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (ClientKeybinds.isHidden() || client.options.hudHidden || client.player == null || client.world == null) return;

        Module module = ModuleManager.get("HUD");
        if (!(module instanceof ClientHudModule hud) || !hud.isEnabled()) return;

        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        if (width < 180 || height < 100) return;

        if (hud.showWatermark()) drawWatermark(context, client, width);
        if (hud.showActiveModules()) drawActiveModules(context, client, width);
        if (hud.showEffects() && height >= 250) drawEffects(context, client);
        if (hud.showCoordinates()) drawCoordinates(context, client, height);
        // On narrow windows, stack armor above the bottom panels instead of letting them overlap.
        if (hud.showArmor() && (width >= 460 || height >= 190)) drawArmor(context, client, width, height);
        if (hud.showKeystrokes() && width >= 380 && height >= 200) drawKeystrokes(context, client, width, height);
        if (hud.showTarget() && width >= 360) drawTarget(context, client, width, height);
    }

    private static void drawWatermark(DrawContext context, MinecraftClient client, int screenWidth) {
        int x = 7;
        int y = 7;
        boolean compact = screenWidth < 220;
        int w = compact ? Math.max(150, screenWidth - 14) : 184;
        int h = compact ? 27 : 34;
        panel(context, x, y, w, h);
        context.fillGradient(x, y, x + 3, y + h, ACCENT, ACCENT_BLUE);
        int logoBottom = compact ? y + 21 : y + 26;
        context.fillGradient(x + 10, y + 6, x + 29, logoBottom, ACCENT, 0xFF6758C2);
        context.drawTextWithShadow(client.textRenderer, "M", x + 16, y + (compact ? 10 : 12), TEXT);
        context.drawTextWithShadow(client.textRenderer, "MINIASSIST", x + 36, y + (compact ? 9 : 7), TEXT);
        if (!compact) {
            context.drawTextWithShadow(client.textRenderer, "CLIENT  /  FABRIC 1.21.4", x + 36, y + 19, MUTED);
        }

        if (screenWidth >= 520) {
            String fps = client.getCurrentFps() + " FPS";
            String ping = getLocalPing(client);
            String stats = fps + "   " + ping;
            int statsW = client.textRenderer.getWidth(stats) + 14;
            context.fill(x + w + 5, y, x + w + statsW + 5, y + h, PANEL_ALT);
            context.drawTextWithShadow(client.textRenderer, stats, x + w + 12, y + 12, ACCENT_BLUE);
        }
    }

    private static String getLocalPing(MinecraftClient client) {
        if (client.getNetworkHandler() == null || client.player == null) return "SINGLEPLAYER";
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
        return entry == null ? "PING —" : entry.getLatency() + " MS";
    }

    private static void drawActiveModules(DrawContext context, MinecraftClient client, int screenWidth) {
        List<Module> active = new ArrayList<>();
        for (Module module : ModuleManager.getModules()) {
            if (module.isEnabled() && !module.getName().equalsIgnoreCase("HUD")) active.add(module);
        }
        active.sort(Comparator.comparingInt((Module m) ->
                client.textRenderer.getWidth(m.getName()) + client.textRenderer.getWidth(m.getBindName())).reversed());

        int y = 7;
        for (Module module : active.stream().limit(8).toList()) {
            String name = module.getName();
            String bind = module.getBindName();
            int nameW = client.textRenderer.getWidth(name);
            int bindW = client.textRenderer.getWidth(bind);
            int w = nameW + bindW + 24;
            int x = screenWidth - w - 7;
            panel(context, x, y, w, 15);
            context.fill(x + w - 2, y, x + w, y + 15, ACCENT);
            context.drawTextWithShadow(client.textRenderer, name, x + 7, y + 4, TEXT);
            context.drawTextWithShadow(client.textRenderer, bind, x + w - bindW - 7, y + 4, ACCENT_BLUE);
            y += 18;
        }
    }

    private static void drawEffects(DrawContext context, MinecraftClient client) {
        List<StatusEffectInstance> effects = client.player.getStatusEffects().stream()
                .sorted(Comparator.comparing(effect -> effect.getEffectType().value().getName().getString()))
                .limit(5)
                .toList();
        if (effects.isEmpty()) return;

        int x = 7;
        int y = 48;
        int w = 166;
        int h = 16 + effects.size() * 14;
        panel(context, x, y, w, h);
        context.drawTextWithShadow(client.textRenderer, "EFFECTS", x + 7, y + 5, ACCENT);
        int rowY = y + 19;
        for (StatusEffectInstance effect : effects) {
            String name = effect.getEffectType().value().getName().getString();
            String level = effect.getAmplifier() > 0 ? " " + (effect.getAmplifier() + 1) : "";
            String duration = StatusEffectUtil.getDurationText(effect, 1.0F, 20.0F).getString();
            String safeDuration = trimToWidth(client, duration, w - 14 - 24 - 5);
            int durationW = client.textRenderer.getWidth(safeDuration);
            int maxNameWidth = Math.max(24, w - 14 - durationW - 5);
            String left = trimToWidth(client, name + level, maxNameWidth);
            context.drawTextWithShadow(client.textRenderer, left, x + 7, rowY, TEXT);
            context.drawTextWithShadow(client.textRenderer, safeDuration,
                    x + w - durationW - 7, rowY, MUTED);
            rowY += 14;
        }
    }

    private static void drawCoordinates(DrawContext context, MinecraftClient client, int screenHeight) {
        var player = client.player;
        int x = 7;
        int y = screenHeight - 47;
        panel(context, x, y, 158, 39);
        context.drawTextWithShadow(client.textRenderer, "PLAYER INFO", x + 7, y + 5, ACCENT);

        String coords = String.format(Locale.ROOT, "X %.1f  Y %.1f  Z %.1f",
                player.getX(), player.getY(), player.getZ());
        coords = trimToWidth(client, coords, 144);
        double speed = Math.sqrt(player.getVelocity().x * player.getVelocity().x
                + player.getVelocity().z * player.getVelocity().z) * 20.0;
        String info = direction(player.getYaw()) + "   " + String.format(Locale.ROOT, "%.1f m/s", speed);
        context.drawTextWithShadow(client.textRenderer, coords, x + 7, y + 17, TEXT);
        context.drawTextWithShadow(client.textRenderer, info, x + 7, y + 28, MUTED);
    }

    private static String direction(float yaw) {
        float angle = MathHelper.wrapDegrees(yaw);
        if (angle >= -45.0f && angle < 45.0f) return "S";
        if (angle >= 45.0f && angle < 135.0f) return "W";
        if (angle >= -135.0f && angle < -45.0f) return "E";
        return "N";
    }

    private static void drawArmor(DrawContext context, MinecraftClient client, int screenWidth, int screenHeight) {
        List<ItemStack> armor = new ArrayList<>();
        for (ItemStack stack : client.player.getArmorItems()) armor.add(stack);
        // The inventory iterable is boots -> helmet; display it in the familiar helmet -> boots order.
        java.util.Collections.reverse(armor);
        int slotW = 22;
        int w = armor.size() * slotW + 12;
        int x = (screenWidth - w) / 2;
        int y = screenWidth < 460 ? screenHeight - 94 : screenHeight - 48;
        panel(context, x, y, w, 38);

        for (int i = 0; i < armor.size(); i++) {
            ItemStack stack = armor.get(i);
            int sx = x + 7 + i * slotW;
            if (stack.isEmpty()) {
                context.fill(sx + 2, y + 9, sx + 18, y + 25, 0x55313B4D);
                continue;
            }
            context.drawItem(stack, sx + 1, y + 4);
            context.drawStackOverlay(client.textRenderer, stack, sx + 1, y + 4);
            if (stack.isDamageable()) {
                double durability = 1.0 - (double) stack.getDamage() / Math.max(1, stack.getMaxDamage());
                int color = durability > 0.5 ? 0xFF70D7A2 : (durability > 0.2 ? 0xFFFFC56B : 0xFFFF6B83);
                context.fill(sx + 2, y + 32, sx + 18, y + 34, TRACK);
                context.fill(sx + 2, y + 32, sx + 2 + (int) Math.round(16 * durability), y + 34, color);
            }
        }
    }


    private static void drawKeystrokes(DrawContext context, MinecraftClient client, int screenWidth, int screenHeight) {
        int w = 104;
        int h = 70;
        int x = screenWidth - w - 7;
        int y = screenHeight - 135;
        panel(context, x, y, w, h);
        context.drawTextWithShadow(client.textRenderer, "KEYSTROKES", x + 7, y + 5, ACCENT);

        drawKey(context, client, "W", x + 41, y + 18, client.options.forwardKey.isPressed());
        drawKey(context, client, "A", x + 17, y + 34, client.options.leftKey.isPressed());
        drawKey(context, client, "S", x + 41, y + 34, client.options.backKey.isPressed());
        drawKey(context, client, "D", x + 65, y + 34, client.options.rightKey.isPressed());
        drawWideKey(context, client, "JUMP", x + 8, y + 52, 40, client.options.jumpKey.isPressed());
        drawMouseKey(context, client, "LMB", x + 51, y + 52, client.options.attackKey.isPressed());
        drawMouseKey(context, client, "RMB", x + 77, y + 52, client.options.useKey.isPressed());
    }

    private static void drawKey(DrawContext context, MinecraftClient client, String label,
                                int x, int y, boolean pressed) {
        int background = pressed ? 0xFF40366D : 0xFF202A3A;
        context.fill(x, y, x + 22, y + 14, background);
        if (pressed) context.fill(x, y, x + 2, y + 14, ACCENT_BLUE);
        context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(label), x + 11, y + 3,
                pressed ? TEXT : MUTED);
    }

    private static void drawWideKey(DrawContext context, MinecraftClient client, String label,
                                    int x, int y, int width, boolean pressed) {
        int background = pressed ? 0xFF40366D : 0xFF202A3A;
        context.fill(x, y, x + width, y + 12, background);
        context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(label), x + width / 2, y + 2,
                pressed ? ACCENT_BLUE : MUTED);
    }

    private static void drawMouseKey(DrawContext context, MinecraftClient client, String label,
                                     int x, int y, boolean pressed) {
        int background = pressed ? 0xFF40366D : 0xFF202A3A;
        context.fill(x, y, x + 22, y + 12, background);
        context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(label), x + 11, y + 2,
                pressed ? ACCENT_BLUE : MUTED);
    }

    private static void drawTarget(DrawContext context, MinecraftClient client, int screenWidth, int screenHeight) {
        LivingEntity target = findHudTarget(client);
        if (target == null || !target.isAlive()) return;

        int w = 164;
        int h = 48;
        int x = screenWidth - w - 7;
        int y = screenHeight - 57;
        panel(context, x, y, w, h);
        context.fillGradient(x, y, x + 2, y + h, ACCENT, ACCENT_BLUE);

        String healthText = String.format(Locale.ROOT, "%.1f / %.0f", target.getHealth(), target.getMaxHealth());
        int healthTextWidth = client.textRenderer.getWidth(healthText);
        // Reserve room for health even when the target has a very long name or an unusual max-health value.
        int nameMaxWidth = Math.max(28, w - 24 - healthTextWidth - 8);
        String name = trimToWidth(client, target.getDisplayName().getString(), nameMaxWidth);
        context.drawTextWithShadow(client.textRenderer, "TARGET", x + 8, y + 5, ACCENT);
        context.drawTextWithShadow(client.textRenderer, name, x + 8, y + 17, TEXT);
        context.drawTextWithShadow(client.textRenderer, healthText,
                x + w - healthTextWidth - 8, y + 17, MUTED);

        int barX = x + 8;
        int barY = y + 31;
        int barW = w - 16;
        context.fill(barX, barY, barX + barW, barY + 4, TRACK);
        float healthRatio = target.getMaxHealth() <= 0.0f ? 0.0f
                : MathHelper.clamp(target.getHealth() / target.getMaxHealth(), 0.0f, 1.0f);
        int healthW = Math.round(barW * healthRatio);
        if (healthW > 0) context.fillGradient(barX, barY, barX + healthW, barY + 4, 0xFF67D99A, 0xFF3CA879);

        String meta = String.format(Locale.ROOT, "%.1fm", client.player.distanceTo(target));
        if (target instanceof PlayerEntity targetPlayer && client.getNetworkHandler() != null) {
            PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(targetPlayer.getUuid());
            if (entry != null) meta += "   " + entry.getLatency() + "ms";
        }
        context.drawTextWithShadow(client.textRenderer, meta, x + 8, y + 37, MUTED);
    }

    private static LivingEntity findHudTarget(MinecraftClient client) {
        Module module = ModuleManager.get("Aim Assist");
        if (module instanceof AimAssist aimAssist && aimAssist.isEnabled()) {
            LivingEntity target = aimAssist.getLockedTarget();
            if (target != null && target.isAlive()) return target;
        }
        if (client.crosshairTarget instanceof EntityHitResult hit
                && hit.getEntity() instanceof LivingEntity living && living != client.player) return living;
        return null;
    }

    private static String trimToWidth(MinecraftClient client, String value, int maxWidth) {
        if (client.textRenderer.getWidth(value) <= maxWidth) return value;
        String suffix = "…";
        String result = value;
        while (!result.isEmpty() && client.textRenderer.getWidth(result + suffix) > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + suffix;
    }

    private static void panel(DrawContext context, int x, int y, int width, int height) {
        context.fill(x, y, x + width, y + height, PANEL);
        context.fillGradient(x, y, x + width, y + 1, 0xFF354258, 0xFF192536);
        context.fill(x, y + height - 1, x + width, y + height, 0x6639475D);
    }
}
