"""SSH helper for DispatchFlow deployment.

Usage:
    python ssh_helper.py probe          # Probe server: list /root, /opt, check docker
    python ssh_helper.py exec "cmd"     # Execute shell command on server
    python ssh_helper.py upload local remote   # Upload single file
    python ssh_helper.py upload_dir local_dir remote_dir  # Upload directory recursively
"""
import sys
import os
import io
import stat
import posixpath
import paramiko

HOST = "64.90.12.129"
PORT = 22
USER = "root"
PASSWORD = "XMnYC5wGyVz5"


def connect():
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(HOST, port=PORT, username=USER, password=PASSWORD, timeout=30)
    return client


def run(client, cmd, timeout=120):
    stdin, stdout, stderr = client.exec_command(cmd, timeout=timeout, get_pty=False)
    out = stdout.read().decode("utf-8", errors="replace")
    err = stderr.read().decode("utf-8", errors="replace")
    rc = stdout.channel.recv_exit_status()
    return rc, out, err


def probe(client):
    """Probe server: list common dirs and check docker."""
    cmds = [
        "uname -a",
        "whoami",
        "pwd",
        "ls -la /root",
        "ls -la /opt 2>/dev/null",
        "ls -la /www 2>/dev/null",
        "ls -la /home 2>/dev/null",
        "docker --version 2>&1",
        "docker compose version 2>&1 || docker-compose --version 2>&1",
        "docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' 2>&1",
        "df -h | head -20",
        "free -h",
    ]
    for c in cmds:
        rc, out, err = run(client, c)
        print(f"\n=== $ {c} (rc={rc}) ===")
        if out.strip():
            print(out.rstrip())
        if err.strip():
            print(f"[stderr] {err.rstrip()}")


def upload_file(client, local, remote):
    sftp = client.open_sftp()
    try:
        # ensure remote dir
        rdir = posixpath.dirname(remote)
        ensure_remote_dir(sftp, rdir)
        sftp.put(local, remote)
        print(f"uploaded {local} -> {remote}")
    finally:
        sftp.close()


def ensure_remote_dir(sftp, path):
    if path in ("", "/", "."):
        return
    try:
        sftp.stat(path)
    except FileNotFoundError:
        parent = posixpath.dirname(path)
        ensure_remote_dir(sftp, parent)
        sftp.mkdir(path)
        print(f"mkdir {path}")


def upload_dir(client, local_dir, remote_dir, exclude_names=None):
    if exclude_names is None:
        exclude_names = {".git", "node_modules", "target", "dist", ".idea", ".vscode", "build", "__pycache__", ".gradle"}
    sftp = client.open_sftp()
    try:
        ensure_remote_dir(sftp, remote_dir)
        local_dir = os.path.abspath(local_dir)
        count = 0
        for root, dirs, files in os.walk(local_dir):
            dirs[:] = [d for d in dirs if d not in exclude_names]
            rel = os.path.relpath(root, local_dir).replace("\\", "/")
            rdir = remote_dir if rel == "." else posixpath.join(remote_dir, rel)
            ensure_remote_dir(sftp, rdir)
            for f in files:
                lp = os.path.join(root, f)
                rp = posixpath.join(rdir, f)
                try:
                    sftp.put(lp, rp)
                    count += 1
                    if count % 50 == 0:
                        print(f"  uploaded {count} files...")
                except Exception as e:
                    print(f"  skip {lp}: {e}")
        print(f"upload_dir done: {count} files -> {remote_dir}")
    finally:
        sftp.close()


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return
    action = sys.argv[1]
    client = connect()
    try:
        if action == "probe":
            probe(client)
        elif action == "exec":
            cmd = sys.argv[2]
            rc, out, err = run(client, cmd, timeout=600)
            print(f"[rc={rc}]")
            if out:
                print(out)
            if err:
                print(f"[stderr]\n{err}", file=sys.stderr)
            sys.exit(0 if rc == 0 else 1)
        elif action == "upload":
            upload_file(client, sys.argv[2], sys.argv[3])
        elif action == "upload_dir":
            upload_dir(client, sys.argv[2], sys.argv[3])
        else:
            print(f"unknown action: {action}")
    finally:
        client.close()


if __name__ == "__main__":
    main()
