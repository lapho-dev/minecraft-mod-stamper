/**
 * Container menu and the ghost result slot.
 *
 * <p>docs/SPEC.md &sect;4 and &sect;10, and D7: the result slot has no backing storage at the
 * container level, which is what guarantees that nothing can ever extract it. This package holds
 * no reference to the block or its block entity &mdash; it works over any two-slot
 * {@code Container} &mdash; so the ghost-slot machinery can be reused by composition
 * (docs/ARCHITECTURE.md rule 6).
 */
package io.github.lapho.stamper.menu;
