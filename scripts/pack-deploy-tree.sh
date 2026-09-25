#!/usr/bin/env bash
# =====================================================================
# 把**当前工作树**打成可上传的部署包（整树部署模式：含未提交的在制改动）。
#
# 为什么要有这个脚本而不是每次手敲 tar：
#   排除清单是安全边界，不是排版偏好。仓库里 `.env.production` 带着两个像真密钥的值
#   （HMAC key / MQTT 口令），`tmp/` 里存过 admin token，`data/backup/` 是整库备份。
#   手敲 `--exclude` 在这台机器上被证明不可靠（GNU tar 的 `--exclude=./x` 匹配不上
#   相对路径写法，实测会把 `.env` 一起打包）。所以这里改成"先算清单、再按清单打包、
#   最后按清单反证"，任何一步不对就直接非零退出，不产出一个"看起来成功了"的包。
#
# 用法：
#   bash scripts/pack-deploy-tree.sh              # 产出 dist/deploy/fsd-tree-<ts>.tgz
#   bash scripts/pack-deploy-tree.sh --list       # 只打清单，不打包
# =====================================================================
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO"
LIST_ONLY=0
[ "${1:-}" = "--list" ] && LIST_ONLY=1

# 这些前缀/模式绝不进包。逐条写出来是为了让"为什么排除"能被读出来，
# 而不是一行 --exclude 谁也看不懂。
is_excluded() {
  case "$1" in
    .env) return 0 ;;                       # 本机/生产真实口令
    .env.production) return 0 ;;            # 含 HMAC key 与 MQTT 口令
    .env.local) return 0 ;;
    .env.*.local) return 0 ;;
    data/backup/*|data/mysql/*) return 0 ;; # 整库备份 / 数据目录
    tmp/*|dist/*|.git/*|.qoder/*) return 0 ;;
    node_modules/*|*/node_modules/*) return 0 ;;
    target/*|*/target/*) return 0 ;;        # maven 产物，服务器自己 build
    front/dist/*|*/__pycache__/*) return 0 ;;
    test-results/*|playwright-report/*|blob_storage/*) return 0 ;;
    *.sql.gz|*.tgz|*.zip|*.jar|*.pem|*.key|*.p12|*.pfx) return 0 ;;
  esac
  return 1
}

MANIFEST="$(mktemp)"
trap 'rm -f "$MANIFEST"' EXIT

# --cached = 索引里的（含"工作树已删"的）；--others --exclude-standard = 未跟踪且未被 gitignore。
# 之后逐个 [ -f ] 过滤：工作树里已删除的文件不能进清单，否则 tar 会 "Cannot stat"。
while IFS= read -r f; do
  [ -f "$f" ] || continue
  is_excluded "$f" && continue
  printf '%s\n' "$f"
done < <(git -c core.quotePath=false ls-files --cached --others --exclude-standard | sort -u) > "$MANIFEST"

N="$(wc -l < "$MANIFEST" | tr -d ' ')"

# 反证：清单里出现任何一条命中禁止模式的行，就是排除逻辑漏了。
# 注意 `.env.example` 是合法模板，白名单它。
LEAK="$(grep -Ev '^(\.env\.example|front/\.env\.example)$' "$MANIFEST" \
        | grep -E '(^|/)\.env($|\.production$|\.local$)|\.pem$|\.key$|\.jar$|\.sql\.gz$|^tmp/|^data/backup/|^node_modules/|/node_modules/|^\.git/' || true)"
if [ -n "$LEAK" ]; then
  printf '[ABORT] 清单里有不该出现的文件：\n%s\n' "$LEAK" >&2
  exit 1
fi

# 反证 2：地理 seed 必须**在**包里 —— 部署不搬运地理内容这件事已经坑过一次，
# 少一个 seed 就是"部署成功但地图上什么都没变"。
for want in back/sql/seed/zjf_facility_v2.sql back/sql/seed/zjf_charging_points.sql \
            back/sql/seed/zjf_swap_cabinets.sql back/sql/seed/zjf_service_area.sql \
            back/sql/seed/zjf_standby_slots.sql back/sql/seed/zjf_energy_sites.sql; do
  if [ -f "$want" ] && ! grep -Fxq "$want" "$MANIFEST"; then
    printf '[ABORT] 期望在包里的 seed 不见了：%s\n' "$want" >&2
    exit 1
  fi
done

printf '清单 %s 个文件；已排除 .env/.env.production、data/backup、tmp、node_modules、target、dist、*.sql.gz/*.jar\n' "$N"
[ "$LIST_ONLY" = "1" ] && { cat "$MANIFEST"; exit 0; }

OUT_DIR="dist/deploy"
TS="$(date +%Y%m%d-%H%M%S)"
OUT="$OUT_DIR/fsd-tree-$TS.tgz"
mkdir -p "$OUT_DIR"
tar -czf "$OUT" -T "$MANIFEST"

# 最后一道：包内容按包本身再核一次（清单对 != 打进来了；打包参数错也会"成功"）
IN_PKG="$(tar tzf "$OUT" | sed 's#^\./##')"
printf '%s\n' "$IN_PKG" | grep -E '(^|/)\.env($|\.production$|\.local$)|\.pem$|\.jar$|\.sql\.gz$|^tmp/' && {
  printf '[ABORT] 包里出现了禁止文件（上面的行）\n' >&2; exit 1; }
PKG_N="$(printf '%s\n' "$IN_PKG" | grep -c . )"
if [ "$PKG_N" != "$N" ]; then
  printf '[ABORT] 包内 %s 个文件 != 清单 %s 个 —— tar 少打了东西，别上传\n' "$PKG_N" "$N" >&2
  exit 1
fi
printf '[OK] %s（%s 个文件，%s）\n' "$OUT" "$PKG_N" "$(du -h "$OUT" | cut -f1)"
