/**
 * The protected centre: pure stamping logic, and nothing else.
 *
 * <p>Everything here must obey the rules in docs/ARCHITECTURE.md and D13:
 * <ul>
 *   <li>imports limited to {@code net.minecraft.world.item}, {@code net.minecraft.core.component}
 *       and {@code net.minecraft.network.chat};</li>
 *   <li>zero Stonecutter directives &mdash; if this package needs one, the abstraction is in the
 *       wrong place and the difference belongs in an adapter;</li>
 *   <li>no static mutable state, no mod id, no registry, no world, no config;</li>
 *   <li>unit-testable with no running Minecraft instance.</li>
 * </ul>
 *
 * <p>This package compiling unchanged across all six build targets is the health check for the
 * whole architecture. It holds exactly one class, {@code StampOperation}.
 */
package io.github.lapho.stamper.core;
