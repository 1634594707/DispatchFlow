#!/bin/bash
# 部署前的基线提交：把本次里程碑工作落到可追溯的提交上，并排除三类不该进产物的东西。
set -euo pipefail
cd "$(dirname "$0")/../.."

# ---------- 1. .gitignore：报告证据进仓库、地理侧车的环境产物不进 ----------
python - <<'PY'
import io
p = '.gitignore'
s = io.open(p, encoding='utf-8').read()
# 证据文件（*.md）进仓库；同目录下的 csv/sql 预测产物继续忽略。
s = s.replace('reports/\n', 'reports/**\n!reports/**/\n!reports/**/*.md\n')
add = ('\n# 部署与算法结论的证据文件必须可追溯（路线图 §13.x 直接引用这些路径）。\n'
       'geo-py/.venv/\ngeo-py/.pytest_cache/\ngeo-py/.ruff_cache/\n'
       'geo-py/**/*.egg-info/\ngeo-py/**/__pycache__/\ntmp/\n')
if 'geo-py/.venv/' not in s:
    s = s + add
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('gitignore 更新完成')
PY

# ---------- 2. 分组暂存（不用 git add -A，逐项指名） ----------
git add .gitignore
git add back/sql/migrations back/sql/seed
git add back/fsd-dispatch/src/main/java/com/fsd/dispatch/core \
        back/fsd-dispatch/src/main/java/com/fsd/dispatch/config/DecisionPolicyConfiguration.java \
        back/fsd-dispatch/src/main/java/com/fsd/dispatch/service \
        back/fsd-dispatch/src/main/java/com/fsd/dispatch/dispatch \
        back/fsd-dispatch/src/main/java/com/fsd/dispatch/entity \
        back/fsd-dispatch/src/main/java/com/fsd/dispatch/mapper \
        back/fsd-dispatch/src/main/java/com/fsd/dispatch/metrics \
        back/fsd-dispatch/src/main/java/com/fsd/dispatch/mapf \
        back/fsd-dispatch/src/main/java/com/fsd/dispatch/road \
        back/fsd-dispatch/src/main/java/com/fsd/dispatch/fleet \
        back/fsd-dispatch/src/main/java/com/fsd/dispatch/sim \
        back/fsd-dispatch/src/test \
        back/fsd-admin-api/src \
        back/fsd-bootstrap/src \
        back/fsd-dispatch/src/main/resources 2>/dev/null || true

# 前端与运维脚本、证据与文档
git add front/src front/Dockerfile front/.dockerignore
git add scripts data reports docs
git add back/fsd-bootstrap/src/main/resources/application.yml

# ---------- 3. 有意排除：未接线的地理查询三件套（进 jar 就是死代码） ----------
for f in back/fsd-dispatch/src/main/java/com/fsd/dispatch/geo/GeoQueryService.java \
         back/fsd-dispatch/src/main/java/com/fsd/dispatch/geo/GeoServiceClient.java \
         back/fsd-dispatch/src/main/java/com/fsd/dispatch/config/GeoServiceProperties.java \
         back/fsd-dispatch/src/test/java/com/fsd/dispatch/geo/GeoQueryServiceTest.java; do
  git restore --staged "$f" 2>/dev/null || true
done

echo "=== 已暂存文件数：$(git diff --cached --name-only | wc -l) ==="
git diff --cached --name-only | sed -E 's#/[^/]+$##' | sort | uniq -c | sort -rn | head -12
