#!/usr/bin/env python3
"""§7.5/§10.1：把 DEFAULT-BOUNDARY「展示范围」重画成**路网可达边界外扩固定米数**，
替代 V44 那个 17.44 km²、填充率 51%、两处凹角的假包络（它让 Tracking 初始视野取错图层、车挤成一点）。

DEFAULT-BOUNDARY 是 `fence_type=BOUNDARY`、scope=L1_CANDIDATE_ENVELOPE（非派单围栏，仅展示 + 越界告警），
改它不影响派单可派范围（派单看 ZJF-ZONE-*）。默认按 ACTIVE 节点经纬度外接框 + pad 外扩。
只打本机 fsd-mysql；utf-8 写盘避 GBK。
"""
import argparse, math, subprocess, sys
CONTAINER = "fsd-mysql"


def q(sql):
    cmd = 'exec mysql -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" -D fsd_core -e ' + "'" + sql + "'"
    r = subprocess.run(["docker", "exec", CONTAINER, "sh", "-c", cmd], capture_output=True, text=True)
    return r.stdout.strip()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--pad-meters", type=float, default=120.0)
    ap.add_argument("--apply", action="store_true")
    a = ap.parse_args()
    row = q('SELECT MIN(coord_lng),MAX(coord_lng),MIN(coord_lat),MAX(coord_lat) '
            'FROM t_road_node WHERE status="ACTIVE" AND deleted=0 AND coord_lng IS NOT NULL;')
    minl, maxl, minla, maxla = (float(x) for x in row.split("\t"))
    mid = (minla + maxla) / 2
    pl = a.pad_meters / (111320 * math.cos(math.radians(mid))); pa = a.pad_meters / 111320
    minl -= pl; maxl += pl; minla -= pa; maxla += pa
    r = lambda v: round(v, 6)
    poly = (f"[[{r(minl)},{r(minla)}],[{r(maxl)},{r(minla)}],[{r(maxl)},{r(maxla)}],[{r(minl)},{r(maxla)}]]")
    w = (maxl - minl) * 111320 * math.cos(math.radians(mid)); h = (maxla - minla) * 111320
    print(f"新 DEFAULT-BOUNDARY：lng[{r(minl)},{r(maxl)}] lat[{r(minla)},{r(maxla)}] "
          f"-> {w/1000:.2f} x {h/1000:.2f} km = {w*h/1e6:.2f} km^2（外扩 {a.pad_meters:.0f} m）")
    if not a.apply:
        print("(dry-run；--apply 落库)"); return 0
    sql = ("UPDATE t_park_geofence SET polygon_json='" + poly + "', "
           "remark='路网可达边界外扩120m（§7.5/§10.1 重画，替代 V44 的 17.44 km2 假包络）' "
           "WHERE fence_code='DEFAULT-BOUNDARY';")
    cmd = 'exec mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" -D fsd_core'
    p = subprocess.run(["docker", "exec", "-i", CONTAINER, "sh", "-c", cmd],
                       input=sql.encode("utf-8"), capture_output=True, text=True)
    if p.returncode != 0:
        print("ERROR", p.stderr[:300], file=sys.stderr); return 1
    print("已落库 DEFAULT-BOUNDARY")
    return 0


if __name__ == "__main__":
    sys.exit(main())
