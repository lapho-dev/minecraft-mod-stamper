package io.github.lapho.stamper.core;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The stamping rule, and the whole point of the mod. See docs/SPEC.md &sect;6.
 *
 * <p>Pure by design (D13): no world, no block entity, no registry, no mod id, no loader, no
 * version directives, no static mutable state. Its three imports are the three docs/ARCHITECTURE.md
 * permits. That this class compiles and behaves identically across all six build targets is the
 * health check for the entire architecture &mdash; if a port ever needs to edit it, something has
 * gone wrong elsewhere.
 */
public final class StampOperation {
    private StampOperation() {
    }

    /**
     * Produces the stamped result of one item from {@code input}, named after {@code template}.
     *
     * <p>Neither argument is modified. In particular the template is never written to, ever
     * (docs/SPEC.md &sect;4, D3) &mdash; it is a reusable die, not an ingredient.
     *
     * @param template the naming die; when empty, the input's custom name is stripped instead
     * @param input    the stack to stamp; only one item is consumed, and decrementing it is the
     *                 caller's job (test U10)
     * @return a stack of exactly one stamped item, or {@link ItemStack#EMPTY} if there is no input
     */
    public static ItemStack apply(ItemStack template, ItemStack input) {
        if (input.isEmpty()) {
            return ItemStack.EMPTY;
        }

        // Copy first, then adjust exactly one component. Every other component the input carries —
        // damage, enchantments, lore, block entity data, anything a future version adds — rides
        // along untouched, because we never enumerate them (SPEC §6).
        ItemStack output = input.copyWithCount(1);

        if (template.isEmpty()) {
            // An empty template is a name eraser. Intended, and a documented footgun (SPEC §11).
            output.remove(DataComponents.CUSTOM_NAME);
        } else {
            // getHoverName() already resolves "custom name, else default name" in one call, and
            // returns a *translatable* component for the default case, which is what localises the
            // result on non-English clients. Do not hand-branch it (SPEC §6, D5).
            //
            // .copy() is load-bearing, not defensive noise: getHoverName() hands back the
            // template's own CUSTOM_NAME component when it has one, so storing that reference
            // would alias the two stacks' names. Components are immutable values today, which
            // makes the aliasing bug impossible — SPEC §4 still mandates this call so that a
            // future refactor cannot quietly reintroduce it. Tests U8 and U9 guard it.
            Component name = template.getHoverName().copy();
            output.set(DataComponents.CUSTOM_NAME, name);
        }

        return output;
    }
}
