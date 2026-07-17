"""Create tar.gz archives of back/ and front/ source code, excluding build artifacts."""
import tarfile
import os
import sys

PROJECT = r"d:\Administrator\Desktop\Project\DispatchFlow"
OUT_DIR = os.path.join(PROJECT, "scripts")

EXCLUDE_DIRS = {"target", "node_modules", "dist", ".git", ".idea", ".vscode", "dev-dist", ".gradle", "__pycache__", "build", ".m2", ".mvn", ".gradle"}
EXCLUDE_FILES = {".env", ".env.local"}


def should_exclude(name, path):
    base = os.path.basename(name.rstrip("/\\"))
    if base in EXCLUDE_DIRS:
        return True
    if base in EXCLUDE_FILES:
        return True
    if base.endswith(".tar.gz"):
        return True
    return False


def make_tar(src_dir, out_path, prefix):
    count = 0
    with tarfile.open(out_path, "w:gz") as tar:
        for root, dirs, files in os.walk(src_dir):
            dirs[:] = [d for d in dirs if d not in EXCLUDE_DIRS]
            for f in files:
                if f in EXCLUDE_FILES:
                    continue
                full = os.path.join(root, f)
                rel = os.path.relpath(full, src_dir).replace("\\", "/")
                arcname = f"{prefix}/{rel}"
                try:
                    tar.add(full, arcname=arcname)
                    count += 1
                except Exception as e:
                    print(f"  skip {full}: {e}")
    size_mb = os.path.getsize(out_path) / 1024 / 1024
    print(f"created {out_path}: {count} files, {size_mb:.2f} MB")


def main():
    back_src = os.path.join(PROJECT, "back")
    front_src = os.path.join(PROJECT, "front")
    back_out = os.path.join(OUT_DIR, "back-src.tar.gz")
    front_out = os.path.join(OUT_DIR, "front-src.tar.gz")

    print("Creating back-src.tar.gz ...")
    make_tar(back_src, back_out, "back")
    print("Creating front-src.tar.gz ...")
    make_tar(front_src, front_out, "front")
    print("Done.")


if __name__ == "__main__":
    main()
