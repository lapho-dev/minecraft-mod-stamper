package io.github.lapho.stamper.core;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier 1 of docs/TESTING.md: the &sect;6 truth table (U1&ndash;U5) and the properties it does not
 * capture (U6&ndash;U11). Ids match that document exactly, so a failure names its own row.
 *
 * <p>No game runs here. {@code Bootstrap.bootStrap()} only populates the vanilla registries so
 * {@code Items.*} resolve; there is no world, no client and no loader.
 */
class StampOperationTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        // Guarded internally by an isBootstrapped flag, so repeat calls are harmless.
        Bootstrap.bootStrap();
    }

    // --- helpers -------------------------------------------------------------------------------

    private static ItemStack named(ItemStack stack, String name) {
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static Component customNameOf(ItemStack stack) {
        return stack.get(DataComponents.CUSTOM_NAME);
    }

    // --- U1-U5: the SPEC section 6 truth table, row for row ------------------------------------

    @Test
    @DisplayName("U1: empty template, plain input -> unchanged, still no custom name")
    void u1() {
        ItemStack out = StampOperation.apply(ItemStack.EMPTY, new ItemStack(Items.WHITE_CARPET));

        assertEquals(Items.WHITE_CARPET, out.getItem());
        assertNull(customNameOf(out), "a plain input must not gain a name");
    }

    @Test
    @DisplayName("U2: empty template strips an existing custom name")
    void u2() {
        ItemStack input = named(new ItemStack(Items.WHITE_CARPET), "hello");

        ItemStack out = StampOperation.apply(ItemStack.EMPTY, input);

        assertNull(customNameOf(out), "an empty template is a name eraser (SPEC section 11)");
    }

    @Test
    @DisplayName("U3: unnamed template copies the item's default name")
    void u3() {
        ItemStack out = StampOperation.apply(
                new ItemStack(Items.CHEST),
                named(new ItemStack(Items.WHITE_CARPET), "hello"));

        assertEquals(new ItemStack(Items.CHEST).getHoverName(), customNameOf(out));
    }

    @Test
    @DisplayName("U4: named template overwrites the input's name")
    void u4() {
        ItemStack out = StampOperation.apply(
                named(new ItemStack(Items.CHEST), "bye"),
                named(new ItemStack(Items.WHITE_CARPET), "hello"));

        assertEquals("bye", customNameOf(out).getString());
    }

    @Test
    @DisplayName("U5: named template names a previously unnamed input")
    void u5() {
        ItemStack out = StampOperation.apply(
                named(new ItemStack(Items.CHEST), "bye"),
                new ItemStack(Items.WHITE_CARPET));

        assertEquals("bye", customNameOf(out).getString());
    }

    // --- U6-U11: what the truth table does not say ---------------------------------------------

    @Test
    @DisplayName("U6: every component except custom_name survives untouched")
    void u6() {
        ItemStack input = new ItemStack(Items.DIAMOND_SWORD);
        input.setDamageValue(37);
        input.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("a line of lore"))));
        input.set(DataComponents.REPAIR_COST, 9);
        named(input, "hello");

        ItemStack out = StampOperation.apply(named(new ItemStack(Items.CHEST), "bye"), input);

        // Asserted generically rather than component by component: the guarantee is that the
        // operation touches nothing else, including components a future version might add.
        assertTrue(
                ItemStack.matchesIgnoringComponents(input, out,
                        (DataComponentType<?> type) -> type == DataComponents.CUSTOM_NAME),
                "only custom_name may differ between input and output");
        assertEquals(37, out.getDamageValue());
        assertEquals(9, out.get(DataComponents.REPAIR_COST));
        assertNotNull(out.get(DataComponents.LORE));
    }

    @Test
    @DisplayName("U7: a copied default name stays translatable, never a flattened literal")
    void u7() {
        ItemStack out = StampOperation.apply(
                new ItemStack(Items.CHEST),
                new ItemStack(Items.WHITE_CARPET));

        assertInstanceOf(TranslatableContents.class, customNameOf(out).getContents(),
                "flattening to a literal would break every non-English client (SPEC section 6)");
    }

    @Test
    @DisplayName("U8: the template is never written to")
    void u8() {
        ItemStack template = named(new ItemStack(Items.CHEST), "bye");
        ItemStack before = template.copy();

        StampOperation.apply(template, named(new ItemStack(Items.WHITE_CARPET), "hello"));

        assertTrue(ItemStack.matches(before, template),
                "slot 0 is a reusable die and must come out byte-identical (D3)");
    }

    @Test
    @DisplayName("U9: the name is copied by value - later template edits do not reach past outputs")
    void u9() {
        ItemStack template = named(new ItemStack(Items.CHEST), "bye");

        ItemStack out = StampOperation.apply(template, new ItemStack(Items.WHITE_CARPET));
        // Rename the template the way an anvil would, after the stamp has already happened.
        named(template, "changed");

        assertEquals("bye", customNameOf(out).getString(),
                "an already-stamped item must not track the template (D4)");
    }

    @Test
    @DisplayName("U10: exactly one item is produced; the caller owns the decrement")
    void u10() {
        ItemStack input = new ItemStack(Items.WHITE_CARPET, 64);

        ItemStack out = StampOperation.apply(named(new ItemStack(Items.CHEST), "bye"), input);

        assertEquals(1, out.getCount());
        assertEquals(64, input.getCount(), "apply() is pure; it does not consume the input");
    }

    @Test
    @DisplayName("U11: a template with item_name but no custom_name uses the item_name")
    void u11() {
        ItemStack template = new ItemStack(Items.CHEST);
        template.set(DataComponents.ITEM_NAME, Component.literal("Crate"));

        ItemStack out = StampOperation.apply(template, new ItemStack(Items.WHITE_CARPET));

        assertEquals("Crate", customNameOf(out).getString(),
                "getHoverName() falls through custom_name to item_name");
    }

    @Test
    @DisplayName("Empty input produces nothing at all")
    void emptyInputProducesNothing() {
        ItemStack out = StampOperation.apply(named(new ItemStack(Items.CHEST), "bye"), ItemStack.EMPTY);

        assertTrue(out.isEmpty(), "SPEC section 7: an empty slot 1 produces nothing");
    }
}
