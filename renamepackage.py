import os
import sys
import shutil
import subprocess

def run_cmd(cmd, cwd=None):
    print(f"Running: {cmd}")
    result = subprocess.run(cmd, shell=True, cwd=cwd)
    if result.returncode != 0:
        print(f"Error executing: {cmd}")
        sys.exit(1)

def rename_package():
    if len(sys.argv) != 2:
        print("Usage: python3 renamepackage.py <new_name>")
        print("Example: python3 renamepackage.py alevjd")
        sys.exit(1)

    new_name = sys.argv[1].lower()
    if len(new_name) != 6:
        print(f"Error: New name '{new_name}' must be exactly 6 characters.")
        sys.exit(1)

    old_name = os.path.basename(os.getcwd())
    
    current_dir = os.getcwd()
    parent_dir = os.path.dirname(current_dir)
    new_dir = os.path.join(parent_dir, new_name)

    if os.path.exists(new_dir):
        print(f"Error: Directory {new_dir} already exists.")
        sys.exit(1)

    print(f"Cleaning build artifacts...")
    run_cmd(r"rm -rf app/build shell-loader/build app/.cxx .gradle build.index")
    run_cmd(r"rm -f *_debug.apk")

    print(f"Cloning to {new_dir}...")
    shutil.copytree(current_dir, new_dir, ignore=shutil.ignore_patterns('.git', '.gradle', 'build', '.cxx'))

    os.chdir(new_dir)

    print(f"Replacing strings with strict separator handling...")
    # 1. Dots: com.alevap -> com.testrp
    run_cmd(fr"find . -type f -not -path '*/.*' -exec sed -i 's/com\.{old_name}/com.{new_name}/g' {{}} +")
    
    # 2. Slashes: com/alevap -> com/testrp
    run_cmd(fr"find . -type f -not -path '*/.*' -exec sed -i 's/com\/{old_name}/com\/{new_name}/g' {{}} +")
    
    # 3. Underscores: com_alevap -> com_testrp (JNI)
    run_cmd(fr"find . -type f -not -path '*/.*' -exec sed -i 's/com_{old_name}/com_{new_name}/g' {{}} +")
    
    # 4. Capitalized: Alevap -> Testrp
    run_cmd(fr"find . -type f -not -path '*/.*' -exec sed -i 's/{old_name.capitalize()}/{new_name.capitalize()}/g' {{}} +")

    # 5. Upper case: ALEVAP -> TESTRP
    run_cmd(fr"find . -type f -not -path '*/.*' -exec sed -i 's/{old_name.upper()}/{new_name.upper()}/g' {{}} +")

    print(f"Moving directory structures...")
    java_paths = [
        f"app/src/main/java/com/{old_name}",
        f"shell-loader/src/main/java/com/{old_name}",
        f"app/src/main/aidl/com/{old_name}"
    ]
    for path in java_paths:
        if os.path.exists(path):
            new_path = path.replace(f"com/{old_name}", f"com/{new_name}")
            os.makedirs(os.path.dirname(new_path), exist_ok=True)
            run_cmd(f"mv {path} {new_path}")

    print(f"Updating build environment...")
    # Update APP_PKG in bundlelibs.py
    run_cmd(fr"sed -i 's/APP_PKG = \".*\"/APP_PKG = \"com.{new_name}\"/' bundlelibs.py")
    
    # Fix bundlelibs.py patching logic to be universal (adding more com.old patterns)
    old_patch_line = f"run_cmd(f\"find {{JNILIBS}} -type f -name '*.so' -exec sed -i 's/com.termux/{{APP_PKG}}/g' {{}} +\")"
    new_patch_line = f"run_cmd(f\"find {{JNILIBS}} -type f -name '*.so' -exec sed -i 's/com.termux/{{APP_PKG}}/g; s/com.{old_name}/{{APP_PKG}}/g' {{}} +\")"
    
    # Use different delimiter for sed since we have many slashes
    run_cmd(f"sed -i 's|s/com.termux/{{APP_PKG}}/g|s/com.termux/{{APP_PKG}}/g; s/com.{old_name}/{{APP_PKG}}/g|' bundlelibs.py")

    print(f"Building...")
    run_cmd(r"python3 bundlelibs.py")
    run_cmd(r"./build.sh")

    print(f"\nDONE: {new_dir}")

if __name__ == "__main__":
    rename_package()
