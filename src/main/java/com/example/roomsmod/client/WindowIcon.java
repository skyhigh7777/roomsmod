package com.example.roomsmod.client;

import com.example.roomsmod.util.RoomsDebug;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * ゲームのウィンドウ（とタスクバー）のアイコンをこの MOD の絵にする（クライアントだけ）。
 * assets/roomsmod/icons/icon_{16,32,48,128}.png を GLFW の glfwSetWindowIcon に渡す。ウィンドウができた後、描画スレッドで呼ぶこと
 * （ClientSetup.onClientSetup の enqueueWork）。macOS は GLFW がウィンドウのアイコンに対応しないので何もしない。失敗してもゲームは続ける。
 */
public final class WindowIcon {
    private WindowIcon() {}

    static final int[] SIZES = {16, 32, 48, 128};

    public static void apply() {
        if (Minecraft.ON_OSX) return;
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (window == 0L) return;
        ByteBuffer[] files = new ByteBuffer[SIZES.length], pixels = new ByteBuffer[SIZES.length];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            GLFWImage.Buffer images = GLFWImage.malloc(SIZES.length, stack);
            IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1), c = stack.mallocInt(1);
            for (int i = 0; i < SIZES.length; i++) {
                String path = "/assets/roomsmod/icons/icon_" + SIZES[i] + ".png";
                byte[] bytes;
                try (InputStream in = WindowIcon.class.getResourceAsStream(path)) {
                    if (in == null) throw new IllegalStateException("missing " + path);
                    bytes = in.readAllBytes();
                }
                files[i] = MemoryUtil.memAlloc(bytes.length).put(bytes).flip();
                pixels[i] = STBImage.stbi_load_from_memory(files[i], w, h, c, 4);
                if (pixels[i] == null) throw new IllegalStateException("could not decode " + path + ": " + STBImage.stbi_failure_reason());
                images.position(i).width(w.get(0)).height(h.get(0)).pixels(pixels[i]);
            }
            images.position(0);
            GLFW.glfwSetWindowIcon(window, images);
            RoomsDebug.info("window icon set ({} sizes)", SIZES.length);
        } catch (Exception | LinkageError e) {
            RoomsDebug.warn("window icon not set: {}", e.toString());
        } finally {
            for (ByteBuffer p : pixels) if (p != null) STBImage.stbi_image_free(p);
            for (ByteBuffer f : files) if (f != null) MemoryUtil.memFree(f);
        }
    }
}
