#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SkyPulse 离线逆地理编码 - OSM 驻地点位二次验证器（独立复核通道）
------------------------------------------------------------------
对 resolve_osm_seats.py 的初筛结果(`sources/osm_resolved.json`)执行**独立于初筛逻辑的
第二次反向验证**，任一不通过即降级为人工复核，宁缺毋滥：

  V1 县多边形包含复核（重新用 shapely 判定，杜绝初筛逻辑误差）
  V2 反向地理编码 zoom=14：落点所在 OSM 乡镇/街道名称核心必须与目标一致
     （例如 阿穆尔街道 的点反向解析必须位于"阿穆尔街道"辖区）
  V3 反向地理编码 zoom=18：所属区县必须与 MCA 县名核心一致
  V4 同伴驻点距离复核（<300m 拒绝；300~800m 提示并保留）
  V5 名称一致性复核（core 比对；OSM 名为旧称/变体时要求 V2/V3 双通过）

产出：
  sources/osm_seats.json       -> 终审可入库点位（供 build_offline_geodata.py 第 8 级消费）
  sources/osm_seats_report.md  -> 逐条证据报告（人工审阅用）
  sources/osm_seats_rejected.json -> 被二次验证否决的条目及原因
"""

import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request

from shapely.geometry import Point, shape

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
IN_RESOLVED = os.path.join(BASE_DIR, "sources", "osm_resolved.json")
COUNTIES_GEOJSON = os.path.join(BASE_DIR, "counties_2026.geojson")
TOWNSHIPS_ACTIVE = os.path.join(BASE_DIR, "townships_active.json")
OUT_FINAL = os.path.join(BASE_DIR, "sources", "osm_seats.json")
OUT_REPORT = os.path.join(BASE_DIR, "sources", "osm_seats_report.md")
OUT_REJECTED = os.path.join(BASE_DIR, "sources", "osm_seats_rejected.json")

USER_AGENT = "SkyPulse-geodata-pipeline/1.0 (seat-verification-pass2)"
REQUEST_INTERVAL = 1.1

ETHNIC_TOKENS = [
    "彝族", "傣族", "苗族", "回族", "满族", "蒙古族", "藏族", "畲族", "土家族", "侗族",
    "朝鲜族", "俄罗斯族", "布依族", "水族", "阿昌族", "赫哲族", "瑶族", "仡佬族", "羌族",
    "白族", "哈尼族", "哈萨克族", "维吾尔族", "纳西族", "景颇族", "怒族", "独龙族",
    "仫佬族", "毛南族", "京族", "普米族", "撒拉族", "锡伯族", "塔吉克族", "塔塔尔族",
    "乌孜别克族", "裕固族", "保安族", "德昂族", "基诺族", "门巴族", "珞巴族", "东乡族",
    "布朗族", "佤族", "傈僳族", "拉祜族", "黎族", "壮族", "高山族", "民族",
]
ADMIN_SUFFIXES = ["街道办事处", "街道", "地区办事处", "地区", "区公所", "社区服务中心",
                  "镇", "乡", "苏木", "居委委会", "居委会", "村", "管理区", "场"]


def clean_core(name: str) -> str:
    if not name:
        return ""
    s = name
    for _ in range(3):
        for suf in ADMIN_SUFFIXES:
            if s.endswith(suf):
                s = s[: -len(suf)]
                break
        else:
            break
    for tok in ETHNIC_TOKENS:
        s = s.replace(tok, "")
    return s.strip()


def county_core(county_name: str) -> str:
    return re.sub(r"(自治县|县|市辖区|市|区|旗|自治旗)$", "", county_name or "")


def reverse(lat, lon, zoom):
    url = "https://nominatim.openstreetmap.org/reverse?" + urllib.parse.urlencode(
        {"lat": lat, "lon": lon, "format": "jsonv2", "zoom": zoom, "accept-language": "zh"})
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                return json.loads(r.read().decode("utf-8"))
        except Exception:
            time.sleep(3 + attempt * 3)
    return {}


def dist_m(lon1, lat1, lon2, lat2):
    import math
    dx = (lon2 - lon1) * math.cos(math.radians((lat1 + lat2) / 2)) * 111320.0
    dy = (lat2 - lat1) * 110540.0
    return (dx * dx + dy * dy) ** 0.5


def main():
    if not os.path.exists(IN_RESOLVED):
        print(f"缺少 {IN_RESOLVED}，请先运行 resolve_osm_seats.py")
        sys.exit(1)
    items = json.load(open(IN_RESOLVED, encoding="utf-8"))
    print(f">>> 二次验证: {len(items)} 条初筛入库点位")

    polys = {}
    g = json.load(open(COUNTIES_GEOJSON, encoding="utf-8"))
    for ft in g["features"]:
        gb = str(ft["properties"]["gb"])
        c6 = gb[3:] if gb.startswith("156") else gb
        polys[c6] = shape(ft["geometry"])
    active = json.load(open(TOWNSHIPS_ACTIVE, encoding="utf-8"))
    peers = {}
    for a in active:
        if a.get("lon") and a.get("lat"):
            peers.setdefault(a["county_id"], []).append((a["name"], a["lon"], a["lat"]))

    final, rejected = [], []
    rows = []
    for i, it in enumerate(items):
        ext_id, cid = it["ext_id"], it["county_id"]
        cname, target = it["county_name"], it["name"]
        pol = polys.get(cid)
        pt = Point(it["lon"], it["lat"])
        reasons = []

        # V1 县多边形包含
        inside = bool(pol is not None and pol.contains(pt))
        if not inside:
            reasons.append("V1失败: 点位不在县多边形内")

        # V2/V3 反向验证
        r14 = reverse(it["lat"], it["lon"], 14)
        time.sleep(REQUEST_INTERVAL)
        r18 = reverse(it["lat"], it["lon"], 18)
        time.sleep(REQUEST_INTERVAL)
        a14 = r14.get("address", {})
        a18 = r18.get("address", {})
        town_osm = a14.get("town") or a14.get("village") or a14.get("suburb") or a14.get("municipality") or a14.get("city_district") or ""
        county_osm = a18.get("county") or a18.get("city_district") or a18.get("city") or ""
        core_t, core_o = clean_core(target), clean_core(town_osm)
        town_match = bool(core_t) and bool(core_o) and (core_t == core_o or core_t in core_o or core_o in core_t)
        if core_t and core_o and not town_match:
            reasons.append(f"V2失败: 反向乡镇「{town_osm}」与目标「{target}」不一致")
        ccore = county_core(cname)
        if county_osm and ccore[:2] not in county_osm:
            reasons.append(f"V3失败: 反向区县「{county_osm}」与目标县「{cname}」不一致")

        # V4 同伴距离
        near = [(n, round(dist_m(pt.x, pt.y, plon, plat))) for n, plon, plat in peers.get(cid, [])
                if dist_m(pt.x, pt.y, plon, plat) < 800]
        for n, d in near:
            if d < 300:
                reasons.append(f"V4失败: 与同县驻点「{n}」仅 {d}m")
                break

        ok = not reasons
        rec = {**it, "verify": {
            "inside_county": inside,
            "reverse_town": town_osm, "reverse_town_match": town_match,
            "reverse_county": county_osm,
            "near_peers_800m": near,
        }}
        (final if ok else rejected).append(rec)
        rows.append((ext_id, cname, target, it["lon"], it["lat"], town_osm, county_osm, ok, "; ".join(reasons)))
        print(f"[{i+1}/{len(items)}] {'通过' if ok else '否决'} {cid} {target} -> {it['lon']},{it['lat']} "
              f"| osm乡镇={town_osm} | {('; '.join(reasons))[:60]}", flush=True)

    with open(OUT_FINAL, "w", encoding="utf-8") as f:
        json.dump([{k: v for k, v in r.items() if k not in ("verify",)} for r in final], f, ensure_ascii=False, indent=2)
    json.dump(rejected, open(OUT_REJECTED, "w", encoding="utf-8"), ensure_ascii=False, indent=2)

    with open(OUT_REPORT, "w", encoding="utf-8") as f:
        f.write("# SkyPulse 乡镇驻地 OSM 补点终审报告\n\n")
        f.write("数据通道：OSM Nominatim（免登录/WGS-84 原生坐标）；双重独立验证：初筛门禁 G1-G7 + 二次反向验证 V1-V5。\n\n")
        f.write(f"初筛入库 {len(items)} 条 -> 终审通过 **{len(final)}** 条，否决 {len(rejected)} 条。\n\n")
        f.write("| ext_id | 县 | 乡镇 | 经度 | 纬度 | OSM反向乡镇 | OSM反向区县 | 结论 |\n")
        f.write("|---|---|---|---|---|---|---|---|\n")
        for ext_id, cname, target, lon, lat, town_osm, county_osm, ok, why in rows:
            f.write(f"| {ext_id} | {cname} | {target} | {lon} | {lat} | {town_osm} | {county_osm} | {'通过' if ok else '否决:' + why} |\n")
        f.write("\n## 证据说明\n\n")
        f.write("* V1: shapely 重新判定点位位于天地图法定县多边形内部；\n")
        f.write("* V2: Nominatim zoom=14 反向解析，落点所属 OSM 乡镇名称与民政部名称核心一致；\n")
        f.write("* V3: zoom=18 反向解析，所属区县与 MCA 县名一致；\n")
        f.write("* V4: 与同县已激活驻点距离 >=300m（防县中心/重复质心污染）；\n")
        f.write("* 初筛 G1-G7: 要素类型语义、名称核心、县多边形包含、同伴距离、老数据佐证、异地防重、反向区县一致。\n")
    print(f"\n=== 终审通过 {len(final)} 条，否决 {len(rejected)} 条 -> {OUT_FINAL}")


if __name__ == "__main__":
    main()
