/**
 * OpenGL implementation for Minecraft 1.21.1's integer framebuffer renderer.
 *
 * <p>This package owns concrete render target redirection, post-chain execution, MSAA FBO
 * management, temporal history resources, and post-chain uniforms. Keep algorithmic
 * intent in the planner/config layer; keep Minecraft hook descriptors in the platform layer.</p>
 */
package org.betterLostItems.salts_anti_aliasing.client.render.opengl;
