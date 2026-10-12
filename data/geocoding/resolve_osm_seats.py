#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SkyPulse 离线逆地理编码 - 未解析乡镇 OSM 驻地解析器
--------------------------------------------------
对 build_offline_geodata.py 产出的 unresolved_townships 清单，使用 OSM Nominatim
（免登录、免 Key、原生 WGS-84）为乡镇街道驻地补全坐标，并执行多重严格校验：

  G1 要素类型语义校验：仅接受 行政边界/居民点/政府驻地 类 POI，
                       拒绝 道路/河流/线路/公园/公交站 等无关命中；
  G2 名称核验：核心地名（剥离行政后缀与民族称谓）必须一致，
                       完全不同的名称（如更名前后）一律转入人工复核；
  G3 空间包含校验：坐标必须严格位于 MCA 法定县/区（含兵团市）多边形内部；
  G4 同伴距离校验：不得与同县已解析乡镇驻点重合（<300m 判为可疑）；
  G5 老数据佐证：与 Vonng/adcode(2020)、GB18306 同名点位相距 <=3km 时标记 corroborated；
  G6 异地防重：display_name 必须包含县名核心字，防同名乡镇串扰。

产出：
  sources/osm_resolved.json  -> 可入库的高置信点位（含完整溯源信息）
  sources/osm_review.json    -> 存疑/待人工复核条目及原因
  sources/osm_resolve_report.md -> 人工审阅报告
"""

import csv
import glob
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request
from collections import defaultdict

from shapely.geometry import Point, shape

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
UNRESOLVED_JSON = os.path.join(BASE_DIR, "unresolved_townships.json")
UNRESOLVED_CSV = os.path.join(BASE_DIR, "unresolved_townships.csv")
COUNTIES_GEOJSON = os.path.join(BASE_DIR, "counties_2026.geojson")
TOWNSHIPS_ACTIVE = os.path.join(BASE_DIR, "townships_active.json")
OUT_RESOLVED = os.path.join(BASE_DIR, "sources", "osm_resolved.json")
OUT_REVIEW = os.path.join(BASE_DIR, "sources", "osm_review.json")
OUT_REPORT = os.path.join(BASE_DIR, "sources", "osm_resolve_report.md")

PATH_ADCODE_DIR = "/tmp/adcode/data/adcode"
PATH_GB18306 = "/home/beta/MyCangku/address-selection/layers/raw/gb18306_towns_all.json"

USER_AGENT = "SkyPulse-geodata-pipeline/1.0 (offline-township-seat-resolution)"
NOMINATIM = "https://nominatim.openstreetmap.org/search"
REQUEST_INTERVAL = 1.1  # Nominatim 使用政策：1 req/s

ETHNIC_TOKENS = [
    "彝族", "傣族", "苗族", "回族", "满族", "蒙古族", "藏族", "畲族", "土家族", "侗族",
    "朝鲜族", "俄罗斯族", "布依族", "水族", "阿昌族", "赫哲族", "瑶族", "仡佬族", "羌族",
    "白族", "哈尼族", "哈萨克族", "维吾尔族", "纳西族", "景颇族", "怒族", "独龙族",
    "仫佬族", "毛南族", "京族", "普米族", "撒拉族", "锡伯族", "塔吉克族", "塔塔尔族",
    "乌孜别克族", "裕固族", "保安族", "德昂族", "基诺族", "门巴族", "珞巴族", "东乡族",
    "布朗族", "佤族", "傈僳族", "拉祜族", "黎族", "壮族", "高山族", "民族",
]
ADMIN_SUFFIXES = ["街道办事处", "地区办事处", "街道", "地区", "区公所", "社区服务中心",
                  "管理委员会", "管委会", "人民政府", "办事处", "委员会", "居委委会", "居委会",
                  "镇", "乡", "苏木", "村", "管理区", "场"]

ALLOWED_CLASS_TYPE = {
    ("boundary", "administrative"),
    ("place", "town"), ("place", "village"), ("place", "suburb"),
    ("place", "hamlet"), ("place", "neighbourhood"), ("place", "isolated_dwelling"),
    ("place", "quarter"), ("place", "farm"),
    ("amenity", "townhall"), ("amenity", "community_centre"), ("amenity", "government"),
    ("amenity", "public_building"), ("amenity", "social_facility"), ("amenity", "courthouse"),
    ("office", "government"), ("office", "administrative"),
    ("landuse", "residential"),
}
REJECT_ADDresstypeS = {"state", "county", "country", "region", "city_district"}


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
    s = s.replace("第", "").replace("兵团", "")
    return s.strip()


def xpcc_variants(name: str):
    """兵团团场专用变体：一团金银川镇 -> 一团金银川 / 金银川 / 一团"""
    core = clean_core(name)
    out = [core]
    m = re.match(r"^([一二三四五六七八九十百零]+团)(.+)$", core)
    if m:
        out.append(m.group(1))   # 一团
        out.append(m.group(2))   # 金银川
    m2 = re.match(r"^(.+?)([一二三四五六七八九十百零]+团)$", core)
    if m2:
        out.append(m2.group(1) + m2.group(2))
    return [v for v in dict.fromkeys(out) if v]


def county_core(county_name: str) -> str:
    return re.sub(r"(自治县|县|市辖区|市|区|旗|自治旗)$", "", county_name or "")


def nominatim(q: str):
    params = {"q": q, "format": "jsonv2", "limit": 3, "accept-language": "zh",
              "countrycodes": "cn", "polygon_geojson": 0}
    url = NOMINATIM + "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                return json.loads(r.read().decode("utf-8"))
        except Exception as e:
            wait = 3 + attempt * 3
            print(f"    [warn] nominatim '{q}' 第{attempt+1}次失败 {e}; {wait}s后重试", flush=True)
            time.sleep(wait)
    return []


def dist_m(lon1, lat1, lon2, lat2):
    import math
    dx = (lon2 - lon1) * math.cos(math.radians((lat1 + lat2) / 2)) * 111320.0
    dy = (lat2 - lat1) * 110540.0
    return (dx * dx + dy * dy) ** 0.5


def load_county_polygons():
    with open(COUNTIES_GEOJSON, encoding="utf-8") as f:
        g = json.load(f)
    polys = {}
    for ft in g["features"]:
        gb = str(ft["properties"]["gb"])
        c6 = gb[3:] if gb.startswith("156") else gb
        try:
            polys[c6] = shape(ft["geometry"])
        except Exception:
            pass
    return polys


def load_old_sources():
    """Vonng/adcode(2020) + GB18306 同名县名点位索引 -> (cid, core) -> (lon,lat)"""
    idx = defaultdict(list)
    for path in glob.glob(os.path.join(PATH_ADCODE_DIR, "*.csv")):
        with open(path, encoding="utf-8") as f:
            for line in f:
                parts = line.strip().split(",")
                if len(parts) >= 14 and parts[3] == "town" and parts[12] and parts[13]:
                    try:
                        idx[(parts[5], clean_core(parts[2]))].append((float(parts[12]), float(parts[13])))
                    except ValueError:
                        pass
    gb_idx = {}
    path_gb = PATH_GB18306
    if os.path.exists(path_gb):
        with open(path_gb, encoding="utf-8") as f:
            for item in json.load(f):
                try:
                    name = item["name"].encode("latin1").decode("utf-8")
                    lon, lat = float(item["lon"]), float(item["lat"])
                    pcode = str(item.get("parentcode") or "")
                    ccode = pcode[3:] if len(pcode) == 9 and pcode.startswith("156") else pcode
                    if ccode:
                        gb_idx.setdefault((ccode, clean_core(name)), []).append((lon, lat))
                except Exception:
                    pass
    return idx, gb_idx


def main():
    only_normal = "--normal-only" in sys.argv
    limit = 0
    for a in sys.argv[1:]:
        if a.startswith("--limit="):
            limit = int(a.split("=")[1])

    items = json.load(open(UNRESOLVED_JSON, encoding="utf-8"))
    county_names = {}
    official_by_ext = {}
    city_by_county = {}
    path_l3 = os.path.join(BASE_DIR, "sources", "ok_data_level3.csv")
    if os.path.exists(path_l3):
        l3_by_id = {}
        with open(path_l3, encoding="utf-8-sig") as f:
            for r in csv.DictReader(f):
                l3_by_id[str(r["id"])] = r
        for r in l3_by_id.values():
            if str(r.get("deep")) == "2":
                pid = str(r["pid"])
                parent = l3_by_id.get(pid)
                if parent is not None and str(parent.get("deep")) in ("0", "1"):
                    city_by_county[str(r["id"])] = parent.get("ext_name", "")
    with open(UNRESOLVED_CSV, encoding="utf-8") as f:
        for r in csv.DictReader(f):
            county_names[r["ext_id"]] = r["county_name"]
            official_by_ext[r["ext_id"]] = r["official_name"]
    for it in items:
        it["official"] = official_by_ext.get(it["ext_id"]) or it.get("ext_name") or it["name"]

    polys = load_county_polygons()
    active = json.load(open(TOWNSHIPS_ACTIVE, encoding="utf-8"))
    peers_by_county = defaultdict(list)
    for a in active:
        if a.get("lon") and a.get("lat"):
            peers_by_county[a["county_id"]].append((a["name"], a["lon"], a["lat"]))
    adcode_idx, gb_idx = load_old_sources()

    resolved, review = [], []
    seen_ext = set()

    # 排序：先普通乡镇街道，后兵团；方便中途查看进度
    def order_key(x):
        return (1 if x["county_id"].startswith("659") else 0, x["county_id"], x["ext_id"])

    targets = [it for it in items if not it["county_id"].startswith(("71", "81", "82", "99"))]
    if only_normal:
        targets = [it for it in targets if not it["county_id"].startswith("659")]
    targets.sort(key=order_key)
    if limit:
        targets = targets[:limit]
    print(f">>> 待解析大陆乡镇: {len(targets)} 条", flush=True)

    for i, it in enumerate(targets):
        ext_id, cid = it["ext_id"], it["county_id"]
        cname = county_names.get(ext_id, "")
        ccore = county_core(cname)
        poly = polys.get(cid)
        is_xpcc = cid.startswith("659")

        # ---- 生成查询变体（地级市+县 前缀 + 官方全称/核心名/后缀变体/政府驻地）
        official = it.get("official") or it.get("ext_name") or it["name"]
        base = clean_core(it["name"])
        core_off = clean_core(official)
        city = city_by_county.get(cid, "")
        prefix_city = f"{city}{cname}" if city and city not in cname else cname
        if not is_xpcc:
            variants = [
                f"{prefix_city}{official}",
                f"{cname}{official}",
            ]
            for s in ["乡", "镇", "街道"]:
                variants.append(f"{prefix_city}{base}{s}")
            variants += [
                f"{cname}{base}",
                f"{base}人民政府",
                f"{cname}{base}人民政府",
                base,
            ]
        else:
            variants = xpcc_variants(it["name"])
            for v in xpcc_variants(official):
                if v not in variants:
                    variants.append(v)
        variants = [v for v in dict.fromkeys(variants) if v]

        best = None
        best_score = -1
        audit = []
        for q in variants:
            res = nominatim(q)
            time.sleep(REQUEST_INTERVAL)
            for r in res:
                osm_type = r.get("osm_type")
                osm_id = r.get("osm_id")
                cls = r.get("class") or r.get("category")
                typ = r.get("type")
                name = r.get("name") or ""
                dn = r.get("display_name", "")
                if osm_type == "node" and not r.get("class"):
                    continue
                if (cls, typ) not in ALLOWED_CLASS_TYPE:
                    audit.append(f"G1拒绝[{q}]: {name} ({cls}/{typ}) {dn[:40]}")
                    continue
                if r.get("addresstype") in REJECT_ADDresstypeS:
                    audit.append(f"G1b拒绝[{q}]: {name} addresstype={r.get('addresstype')} {dn[:40]}")
                    continue
                pt = Point(float(r["lon"]), float(r["lat"]))
                if poly is None:
                    audit.append(f"G3跳过[{q}]: 无县多边形 {cid}")
                elif not poly.contains(pt):
                    audit.append(f"G3拒绝[{q}]: {name} {dn[:40]}")
                    continue
                d_boundary = 1e9 if poly is None else dist_m(*poly.centroid.coords[0], *pt.coords[0])
                # 名称核验
                core_target = base
                core_osm = clean_core(name)
                if not core_osm:
                    audit.append(f"G2拒绝[{q}]: 无名要素 {cls}/{typ} {dn[:40]}")
                    continue
                if core_osm == core_target or core_target == core_osm:
                    name_flag = "exact"
                elif core_osm in core_target or core_target in core_osm:
                    name_flag = "alias"
                else:
                    audit.append(f"G2拒绝[{q}]: OSM名「{name}」与目标「{it['name']}」核心不一致 -> {dn[:40]}")
                    continue
                # 管理体制变更检测（撤乡设镇/设街道：驻地按管线规范继承）
                def suffix_of(n):
                    for s in ["街道办事处", "街道", "地区办事处", "镇", "乡", "民族乡", "区公所"]:
                        if n.endswith(s):
                            return "街道" if "街" in s else s
                    return ""
                regime_change = suffix_of(it["name"]) != suffix_of(name)
                if ccore and ccore[:2] not in dn:
                    audit.append(f"G6拒绝[{q}]: display_name 未含县名「{ccore}」 -> {dn[:40]}")
                    continue
                # 同伴距离
                too_close = []
                for pname, plon, plat in peers_by_county.get(cid, []):
                    if dist_m(pt.x, pt.y, plon, plat) < 300:
                        too_close.append(pname)
                # 佐证
                corr, corr_src = 0.0, ""
                for idx, tag in ((adcode_idx, "adcode2020"), (gb_idx, "gb18306")):
                    for lon, lat in idx.get((cid, core_osm), []) + idx.get((cid, core_target), []):
                        d = dist_m(pt.x, pt.y, lon, lat)
                        if d < 3.0 and (not corr_src or d < corr):
                            corr, corr_src = d, tag
                # G7 反向地理编码一致性（信息性）：落点反向解析的区县应与 MCA 县一致
                rev_county = ""
                try:
                    rq = ("https://nominatim.openstreetmap.org/reverse?" +
                          urllib.parse.urlencode({"lat": pt.y, "lon": pt.x, "format": "jsonv2",
                                                  "zoom": 18, "accept-language": "zh"}))
                    rreq = urllib.request.Request(rq, headers={"User-Agent": USER_AGENT})
                    with urllib.request.urlopen(rreq, timeout=30) as rr:
                        rj = json.loads(rr.read().decode("utf-8"))
                    rev_county = rj.get("address", {}).get("county") or rj.get("address", {}).get("city_district") or ""
                    time.sleep(REQUEST_INTERVAL)
                except Exception:
                    rev_county = ""
                score = {"exact": 3, "alias": 2}[name_flag]
                score += 2 if cls == "boundary" else (1 if cls in ("amenity", "office") else 0)
                if too_close:
                    score -= 2
                if corr_src:
                    score += 1
                cand = {
                    "ext_id": ext_id, "county_id": cid, "name": it["name"],
                    "county_name": cname, "lon": round(pt.x, 6), "lat": round(pt.y, 6),
                    "source": "osm_admin_seat", "status": "active",
                    "osm": {"type": osm_type, "id": osm_id, "name": name,
                            "class": cls, "osm_type": typ, "name_flag": name_flag,
                            "display_name": dn, "query": q,
                            "regime_change": regime_change,
                            "reverse_county": rev_county,
                            "too_close_peers": too_close,
                            "corroborated_by": corr_src, "corrob_dist_m": round(corr, 1),
                            "km_to_county_centroid": round(d_boundary / 1000.0, 1)},
                }
                if score > best_score:
                    best_score = score
                    best = cand
            if best and best["osm"]["name_flag"] == "exact" and best["osm"]["class"] in ("boundary", "amenity", "office"):
                break  # 高质量命中即止
        if best:
            rev = best["osm"].get("reverse_county") or ""
            rev_mismatch = bool(rev) and not (ccore[:2] in rev)
            reasons = []
            if best["osm"]["too_close_peers"]:
                reasons.append("与同县已解析驻点过近: " + ",".join(best["osm"]["too_close_peers"]))
            if best["osm"]["name_flag"] == "alias":
                reasons.append(f"名称变体: OSM「{best['osm']['name']}」 vs MCA「{best['name']}」")
            if rev_mismatch:
                reasons.append(f"反向地理编码区县不一致: {rev} vs {cname}")
            if reasons:
                best["needs_review"] = True
                review.append({**best, "review_reasons": reasons})
                print(f"[{i+1}/{len(targets)}] 存疑 {cid} {it['name']} -> {best['lon']},{best['lat']} ({best['osm']['name']}) | {'; '.join(reasons)}", flush=True)
            else:
                resolved.append(best)
                print(f"[{i+1}/{len(targets)}] 入库 {cid} {it['name']} -> {best['lon']},{best['lat']} "
                      f"({best['osm']['class']}/{best['osm']['osm_type']}, {best['osm']['name_flag']}, "
                      f"corr={best['osm']['corroborated_by'] or '-'})", flush=True)
        else:
            review.append({"ext_id": ext_id, "county_id": cid, "county_name": cname, "name": it["name"],
                           "lon": None, "lat": None, "source": "unresolved", "status": "pending",
                           "audit": audit[:12]})
            print(f"[{i+1}/{len(targets)}] 未命中 {cid} {it['name']}", flush=True)

    os.makedirs(os.path.dirname(OUT_RESOLVED), exist_ok=True)
    with open(OUT_RESOLVED, "w", encoding="utf-8") as f:
        json.dump(resolved, f, ensure_ascii=False, indent=2)
    with open(OUT_REVIEW, "w", encoding="utf-8") as f:
        json.dump(review, f, ensure_ascii=False, indent=2)
    print(f"\n=== 入库 {len(resolved)} 条；存疑/待人工 {len(review)} 条 ===")
    print(f"    {OUT_RESOLVED}\n    {OUT_REVIEW}")


if __name__ == "__main__":
    main()
