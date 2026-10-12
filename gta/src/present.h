// Presenter half of MineGTA: a D3D11 hook that shows Minecraft's frame where GTA's own picture would be.
//
// No ReShade, no shader pack, no depth compositing: GTA still renders its world, and we simply replace
// the back buffer with Minecraft's picture (world colour, then the hand/HUD overlay on top with
// premultiplied alpha). The frames come from the shared memory the Fabric mod publishes:
//
//   Local\MCPassthroughFrame, header 4096 bytes, three slots:
//     slot i at 4096 + i * stride holds width*height*4 bytes of world colour RGBA8, the same again as
//     float32 depth, and the same again as the overlay RGBA8 (premultiplied alpha)
//   the slot descriptors at 256 + 128 * i carry width, height, flags (bit 1 = rows bottom-up) and a
//   sequence counter that is odd while the slot is being written
//
// The hook is installed into IDXGISwapChain's vtable (through a throwaway device and swapchain), so
// it lands on whatever GTA creates later.
#pragma once

namespace present {
	/// Installs the hook. Call it every frame; returns true once it is in.
	bool try_register(void *module);
	void unregister(void *module);
	/// Whether Minecraft's picture is drawn this frame (the script turns it off in menus and cutscenes).
	void set_active(bool active);
	/// GTA's camera clip planes, to turn its depth buffer into metres.
	void set_host_planes(float near_clip, float far_clip);
	/// GTA's current camera in Minecraft's convention (rotation in degrees, vertical fov, position in
	/// Minecraft coordinates): Minecraft's frame is re-projected (with its depth) from the pose it was
	/// rendered with to this one, hiding the link's latency on camera turns and moves.
	void set_host_pose(float yaw, float pitch, float roll, float fov, double x, double y, double z);
	/// How many poses back the presented picture is (1: the script reads the camera of the frame being
	/// prepared).
	void set_pose_lag(int frames);
	/// Minecraft's picture moves with the camera (the flight chase cam frames Steve): composite it as
	/// rendered, without re-projecting it to GTA's newer pose, so Steve stays exactly where the camera
	/// framed him.
	void set_camera_locked(bool locked);
	/// A scene's look: the effect's "Match GTA lighting", depth bias and slope bias (negative: the
	/// preset's own values).
	void set_look(float light_match, float depth_bias, float slope_bias);
	/// The camera shake, applied to the finished picture (x, y: fraction of the screen height; roll:
	/// radians), and the nether portal's warp (0..1).
	void set_screen_fx(float shake_x, float shake_y, float shake_roll, float portal_warp);
	/// GTA's backbuffer size as the presenter sees it (0 until the first frame).
	void backbuffer_size(int &width, int &height);
}
