import os
import shutil
import subprocess
import sys

def run_cmd(cmd):
    result = subprocess.run(cmd, shell=True)
    if result.returncode != 0:
        print(f"Error executing: {cmd}")
        sys.exit(1)

def bundle():
    APP_PKG = "com.alevap"
    ASSET_DIR = "app/src/main/assets/bootstrap"
    JNILIBS = "app/src/main/jniLibs/arm64-v8a"
    TERMUX_PREFIX = "/data/data/com.termux/files/usr"

    print("Recreating asset directory...")
    if os.path.exists(ASSET_DIR):
        shutil.rmtree(ASSET_DIR)
    
    dirs = [
        "bin", "lib", "share/terminfo/x", "etc", "share/jwm", 
        "share/X11", "share/terminfo/r", "share/terminfo/l"
    ]
    for d in dirs:
        os.makedirs(os.path.join(ASSET_DIR, d), exist_ok=True)

    print("Recreating jniLibs directory for binaries...")
    if os.path.exists(JNILIBS):
        shutil.rmtree(JNILIBS)
    os.makedirs(JNILIBS, exist_ok=True)

    print("Collecting binaries (renaming to lib*.so for execution permissions)...")
    bins = ["htop", "xeyes", "jwm", "aterm", "xterm", "bash"]
    for b in bins:
        src = os.path.join(TERMUX_PREFIX, "bin", b)
        if os.path.exists(src):
            shutil.copy2(src, os.path.join(JNILIBS, f"lib{b}.so"))
    
    sh_src = os.path.join(TERMUX_PREFIX, "bin/sh")
    if os.path.exists(sh_src):
        shutil.copy(sh_src, os.path.join(JNILIBS, "libsh.so"))

    xwayland = os.path.join(TERMUX_PREFIX, "bin/Xwayland")
    if os.path.exists(xwayland):
        shutil.copy2(xwayland, os.path.join(JNILIBS, "libXwayland.so"))

    with open(os.path.join(ASSET_DIR, "bin/.keep"), 'w') as f:
        pass

    print("Collecting configs...")
    jwmrc = os.path.join(TERMUX_PREFIX, "etc/system.jwmrc")
    if os.path.exists(jwmrc):
        shutil.copy2(jwmrc, os.path.join(ASSET_DIR, "etc/"))
    
    jwm_share = os.path.join(TERMUX_PREFIX, "share/jwm")
    if os.path.exists(jwm_share):
        for item in os.listdir(jwm_share):
            s = os.path.join(jwm_share, item)
            d = os.path.join(ASSET_DIR, "share/jwm", item)
            if os.path.isdir(s):
                shutil.copytree(s, d, dirs_exist_ok=True)
            else:
                shutil.copy2(s, d)

    print("Collecting X11 assets (XKB & Fonts)...")
    xkb = os.path.join(TERMUX_PREFIX, "share/X11/xkb")
    if os.path.exists(xkb):
        shutil.copytree(xkb, os.path.join(ASSET_DIR, "share/X11/xkb"), dirs_exist_ok=True)
    
    fonts = os.path.join(TERMUX_PREFIX, "share/fonts")
    if os.path.exists(fonts):
        shutil.copytree(fonts, os.path.join(ASSET_DIR, "share/fonts"), dirs_exist_ok=True)

    print("Collecting libraries...")
    libs = [
        "libandroid-support.so", "libncursesw.so.6", "libXi.so", "libXext.so",
        "libXmu.so", "libXt.so", "libX11.so", "libXrender.so", "libX11-xcb.so",
        "libxcb.so", "libxcb-present.so", "libxcb-xfixes.so", "libxcb-damage.so",
        "libXau.so", "libXdmcp.so", "libSM.so", "libuuid.so", "libICE.so",
        "libandroid-posix-semaphore.so", "libandroid-shmem.so", "libpixman-1.so",
        "libXfont2.so", "libwayland-client.so", "libxcvt.so", "libdrm.so",
        "libxshmfence.so", "libcrypto.so.3", "libGL.so.1", "libz.so.1",
        "libfontenc.so", "libfreetype.so", "libbz2.so.1.0", "libpng16.so",
        "libbrotlidec.so", "libbrotlicommon.so", "libffi.so", "libGLdispatch.so.0",
        "libGLX.so.0", "librsvg-2.so", "libgobject-2.0.so.0", "libglib-2.0.so.0",
        "libcairo.so.2", "libjpeg.so.8", "libXft.so", "libpangoxft-1.0.so.0",
        "libpangoft2-1.0.so.0", "libpango-1.0.so.0", "libXpm.so", "libXinerama.so",
        "libgio-2.0.so.0", "libgdk_pixbuf-2.0.so.0", "libdav1d.so", "libxml2.so.16",
        "libpangocairo-1.0.so.0", "libiconv.so", "libpcre2-8.so", "libgmodule-2.0.so.0",
        "libfontconfig.so", "libxcb-render.so", "libxcb-shm.so", "libexpat.so.1",
        "libicuuc.so.78", "libicudata.so.78", "libharfbuzz.so", "libfribidi.so",
        "libgraphite2.so", "libc++_shared.so", "libreadline.so.8"
    ]
    for lib in libs:
        src = os.path.join(TERMUX_PREFIX, "lib", lib)
        if os.path.exists(src):
            shutil.copy2(src, os.path.join(ASSET_DIR, "lib/"), follow_symlinks=True)

    print("Collecting terminfo...")
    for d, pattern in {"x": "xterm*", "r": "rxvt*", "l": "linux"}.items():
        src_dir = os.path.join(TERMUX_PREFIX, "share/terminfo", d)
        if os.path.exists(src_dir):
            run_cmd(f"cp -r {src_dir}/{pattern} {os.path.join(ASSET_DIR, 'share/terminfo', d)}/")

    print("Patching configs...")
    jwmrc_path = os.path.join(ASSET_DIR, "etc/system.jwmrc")
    if os.path.exists(jwmrc_path):
        with open(jwmrc_path, 'r') as f:
            content = f.read()
        
        old_term = '<Program icon="utilities-terminal" label="Terminal">xterm</Program>'
        new_term = """<Program icon="utilities-terminal" label="Aterm">aterm</Program>
        <Program icon="utilities-terminal" label="Xterm">xterm</Program>
        <Program icon="utilities-terminal" label="Bash Shell">bash</Program>"""
        content = content.replace(old_term, new_term)
        
        old_tray = '<TrayButton label="JWM">root:1</TrayButton>'
        new_tray = '<TrayButton label="JWM">root:1</TrayButton>\n        <TrayButton label="Term">exec:aterm</TrayButton>'
        content = content.replace(old_tray, new_tray)
        
        with open(jwmrc_path, 'w') as f:
            f.write(content)

    print(f"Patching com.termux -> {APP_PKG} ...")
    run_cmd(f"find {ASSET_DIR} -type f -exec sed -i 's/com.termux/{APP_PKG}/g; s/com.alevjd/{APP_PKG}/g' {{}} +")

    print("Binary Patching...")
    # This line is targeted by renamepackage.py
    run_cmd(f"find {JNILIBS} -type f -name '*.so' -exec sed -i 's/com.termux/{APP_PKG}/g; s/com.alevjd/{APP_PKG}/g' {{}} +")

    print("Done.")

if __name__ == "__main__":
    bundle()