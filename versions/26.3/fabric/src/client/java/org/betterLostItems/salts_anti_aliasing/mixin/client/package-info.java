/**
 * Modern-branch mixins.
 *
 * <p>Mixins should stay thin: inject into Minecraft, collect parameters, and delegate to
 * {@code client.platform.modern}. Avoid placing render policy or mode logic here so version
 * ports can swap hook descriptors without rewriting the mod's behavior.</p>
 */
package org.betterLostItems.salts_anti_aliasing.mixin.client;
