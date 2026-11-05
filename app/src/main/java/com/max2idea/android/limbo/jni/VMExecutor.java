/* JNI ABI adapter for Limbo 6.0.1, Copyright Max Kastanas 2012, GPL-2.0-or-later.
 * Android lifecycle, storage, display and control are supplied by OSify.
 */
package com.max2idea.android.limbo.jni;

public final class VMExecutor {
    static {
        for (String name : new String[]{"compat-limbo", "compat-musl", "glib-2.0", "pixman-1", "compat-SDL2-addons", "SDL2", "compat-SDL2-ext", "osifyvm"}) {
            System.loadLibrary(name);
        }
    }
    public native String start(String storageDir, String baseDir, String library, String libraryPath, int scale, Object[] arguments);
    public static void onVMResolutionChanged(int width, int height) { }
    public int get_fd(String path) { return -1; } // SAF files are copied into OSify storage before launch.
    public int close_fd(int descriptor) { return -1; }
}
