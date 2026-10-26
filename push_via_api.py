#!/usr/bin/env python3
"""
Git Data API 推送脚本（绕过 github.com DNS 拦截，走 api.github.com）。

流程：远程 HEAD → base_local..HEAD 变更文件并集 → 逐文件创建 blob →
POST tree(base=远程 tree) → 逐 commit 创建 commit 对象 → PATCH ref。
"""
import os, sys, json, subprocess, base64, urllib.request, urllib.error

REPO = "FinnYu888/lifedesk"
BRANCH = "main"
WORK = r"C:\Users\14519\WorkBuddy\Worktrees\lifedesk\main-68c2317f"

# 从 ~/.git-credentials 读 PAT
with open(os.path.expanduser("~/.git-credentials")) as f:
    line = f.readline().strip()
# 格式: https://USER:TOKEN@github.com
TOKEN = line.split(":", 2)[2].split("@", 1)[0]
USER = line.split("://", 1)[1].split(":", 1)[0]

API = "https://api.github.com"


def api_call(method, path, body=None):
    url = API + path
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers={
        "Authorization": f"token {TOKEN}",
        "Accept": "application/vnd.github+json",
        "Content-Type": "application/json",
        "User-Agent": "push-via-api"
    })
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            txt = r.read()
            return r.status, json.loads(txt) if txt else None
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read())
        except Exception:
            return e.code, None


def git_text(args):
    return subprocess.run(["git"] + args, cwd=WORK, capture_output=True, text=True).stdout.strip()


def git_bytes(args):
    return subprocess.run(["git"] + args, cwd=WORK, capture_output=True).stdout


# 1. 远程 HEAD
st, ref = api_call("GET", f"/repos/{REPO}/git/refs/heads/{BRANCH}")
if st != 200:
    print("remote ref err:", st, ref)
    sys.exit(1)
remote_sha = ref["object"]["sha"]
print("remote main:", remote_sha[:10])

# 2. base_local：优先用自定义 ref refs/lifedesk/pushed-head（指向本地最后一个已推送的
#    commit，推送成功后 update-ref 到 HEAD，必然成功，因为它是本地对象）。
#    refs/remotes/origin/main 因本地对象库无 API 创建的远程 commit 而无法 update-ref，
#    会导致每次推送重放所有历史 commit（内容相同但产生重复 commit），故不再用它。
base_local = git_text(["rev-parse", "refs/lifedesk/pushed-head"])
if not base_local:
    base_local = git_text(["rev-parse", "refs/remotes/origin/main"])
print("base_local:", base_local[:10] if base_local else "(none)")
if not base_local:
    print("no local base ref, fallback to remote_sha")
    base_local = remote_sha

# 3. 取变更文件并集（base_local..HEAD）
files_raw = subprocess.run(
    ["git", "log", "--name-only", "--pretty=format:", f"{base_local}..HEAD"],
    cwd=WORK, capture_output=True, text=True
).stdout
files = sorted(set(f for f in files_raw.split("\n") if f.strip()))
print("changed files:", len(files))
if not files:
    print("nothing to push")
    sys.exit(0)

# 4. 对每个文件读 HEAD 内容 + 创建 blob
tree_items = []
for path in files:
    content = git_bytes(["show", f"HEAD:{path}"])
    b64 = base64.b64encode(content).decode()
    st, b = api_call("POST", f"/repos/{REPO}/git/blobs",
                     {"content": b64, "encoding": "base64"})
    if st not in (200, 201):
        print(f"blob err {path}: {st} {b}")
        sys.exit(1)
    tree_items.append({"path": path, "mode": "100644",
                       "type": "blob", "sha": b["sha"]})
print(f"created {len(tree_items)} blobs")

# 5. 远程 HEAD 的 tree sha（作为 base tree）
st, commit_obj = api_call("GET", f"/repos/{REPO}/git/commits/{remote_sha}")
if st != 200:
    print("remote commit err:", st, commit_obj)
    sys.exit(1)
remote_tree = commit_obj["tree"]["sha"]

# 6. POST tree（基于远程 tree 增量更新）
st, t = api_call("POST", f"/repos/{REPO}/git/trees",
                 {"base_tree": remote_tree, "tree": tree_items})
if st not in (200, 201):
    print("tree err:", st, t)
    sys.exit(1)
new_tree = t["sha"]
print("new tree:", new_tree[:10])

# 7. 逐 commit 创建 commit 对象（保持历史）
commits_raw = subprocess.run(
    ["git", "log", "--pretty=format:%H|%an|%ae|%aI|%s|%b", "--reverse", f"{base_local}..HEAD"],
    cwd=WORK, capture_output=True, text=True
).stdout.strip()

parent = remote_sha
new_commit = parent
count = 0
if commits_raw:
    for line in commits_raw.split("\n"):
        parts = line.split("|", 5)
        if len(parts) < 6:
            continue
        sha, an, ae, ad, subj, body = parts
        msg = subj + ("\n\n" + body if body else "")
        st, co = api_call("POST", f"/repos/{REPO}/git/commits", {
            "message": msg,
            "tree": new_tree,
            "parents": [parent],
            "author": {"name": an, "email": ae, "date": ad},
            "committer": {"name": an, "email": ae, "date": ad}
        })
        if st not in (200, 201):
            print("commit err:", st, co)
            sys.exit(1)
        new_commit = co["sha"]
        parent = new_commit
        count += 1
print(f"created {count} commits, latest:", new_commit[:10])

# 8. PATCH ref（更新 main 指向新 commit）
st, r = api_call("PATCH", f"/repos/{REPO}/git/refs/heads/{BRANCH}",
                 {"sha": new_commit, "force": False})
if st not in (200, 202):
    print("ref err:", st, r)
    sys.exit(1)
print("pushed! new main:", new_commit[:10])

# 9. 记录已推送位置：把自定义 ref 指向本地 HEAD（本地对象，必然成功），
#    下次推送 base_local..HEAD 只含真正未推送的 commit
subprocess.run(["git", "update-ref", "refs/lifedesk/pushed-head", "HEAD"], cwd=WORK)
print("pushed-head ref synced to HEAD")
