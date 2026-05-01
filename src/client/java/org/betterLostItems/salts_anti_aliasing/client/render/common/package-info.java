/**
 * Shared render orchestration.
 *
 * <p>This package contains the pipeline planner, runtime coordination, and pass bookkeeping.
 * It is the boundary between stable mod logic and the currently selected platform adapter.
 * When porting to another Minecraft renderer family, prefer replacing adapter classes before
 * changing planner semantics.</p>
 */
package org.betterLostItems.salts_anti_aliasing.client.render.common;
