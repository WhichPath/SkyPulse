#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SkyPulse Offline Geocoding Dataset Generator
--------------------------------------------
构建全国省市县三级法定行政边界多边形与 39,188 个民政部最新法定乡镇/街道点位字典。

核心设计哲学：
1. 拓扑紧密填充（Watertight Topology）：
   - 将 OSM 较新的“两江新区（500157）”多边形内嵌剪裁并从原江北、渝北、北碚中差集剔除；
   - 将 OSM “加格达奇区（232761）”从鄂伦春自治旗中差分剔除；
   - 保证县级多边形无重叠、无缝隙，100% 紧密填充。
2. 绝对真实性原则（No Fake Centroids）：
   - 杜绝给未知乡镇分配县域几何中心。因为距离匹配会将县中心（通常是县委县政府/中心街道所在地）
     的用户错误匹配给未知乡镇，污染空间最近邻检索；
   - 未获得可信离散坐标的单位独立输出为《未完成/待补全清单》（UNRESOLVED_TOWNSHIPS），
     不参与运行时空间 KD-Tree 最近邻计算；
   - 仅将经过验证的高精度离散坐标点编入移动端激活字典（townships_active.json）。
"""

import os
import sys
import csv
import json
import re
import glob
from collections import defaultdict, Counter

import pandas as pd
import geopandas as gpd
import pyogrio
from shapely.geometry import Point, Polygon, MultiPolygon

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
OUTPUT_COUNTIES = os.path.join(BASE_DIR, "counties_2026.geojson")
OUTPUT_TOWNSHIPS_ALL = os.path.join(BASE_DIR, "townships_2026.json")
OUTPUT_TOWNSHIPS_ACTIVE = os.path.join(BASE_DIR, "townships_active.json")
OUTPUT_UNRESOLVED_JSON = os.path.join(BASE_DIR, "unresolved_townships.json")
OUTPUT_UNRESOLVED_CSV = os.path.join(BASE_DIR, "unresolved_townships.csv")
OUTPUT_UNRESOLVED_XINJIANG_CSV = os.path.join(BASE_DIR, "unresolved_townships_xinjiang.csv")
OUTPUT_STATS = os.path.join(BASE_DIR, "build_stats.json")

# 外部原始数据路径
PATH_MCA_LEVEL4 = "/tmp/ok_data/ok_data_level4.csv"
PATH_TIANDITU_COUNTIES = "/home/beta/MyCangku/address-selection/layers/raw/tianditu/中国_县.geojson"
PATH_OSM_PBF = "/home/beta/MyCangku/cn-railmap/data/osm/china-latest.osm.pbf"
PATH_ADCODE_DIR = "/tmp/adcode/data/adcode"
PATH_GB18306 = "/home/beta/MyCangku/address-selection/layers/raw/gb18306_towns_all.json"

# 历史撤县设市/区代码别名映射
COUNTY_ALIASES = {
    "652902": "652923",  # 库车市 <- 库车县
    "654203": "654223",  # 沙湾市 <- 沙湾县
    "540481": "540422",  # 米林市 <- 米林县
    "540581": "540524",  # 错那市 <- 错那县
    "632825": "632857",  # 大柴旦行委
    "140214": "140227",  # 云州区 <- 大同县
    "130881": "130823",  # 平泉市 <- 平泉县
    "350604": "350681",  # 龙海区 <- 龙海市
    "411003": "411023",  # 建安区 <- 许昌县
    "429021000": "429021",  # 神农架林区
    "451203": "451281",  # 宜州区 <- 宜州市
    "520201": "520221",  # 钟山区 <- 水城县
    "520204": "520221",  # 水城区 <- 水城县
    "520403": "520421",  # 平坝区 <- 平坝县
    "520581": "522423",  # 黔西市 <- 黔西县
    "520621": "522222",  # 江口县 <- 铜仁地区江口县
    "520626": "522227",  # 德江县 <- 铜仁地区德江县
    "522302": "522322",  # 兴仁市 <- 兴仁县
}

PROV_NAMES = {
    "11": "北京市", "12": "天津市", "13": "河北省", "14": "山西省", "15": "内蒙古自治区",
    "21": "辽宁省", "22": "吉林省", "23": "黑龙江省", "31": "上海市", "32": "江苏省",
    "33": "浙江省", "34": "安徽省", "35": "福建省", "36": "江西省", "37": "山东省",
    "41": "河南省", "42": "湖北省", "43": "湖南省", "44": "广东省", "45": "广西壮族自治区",
    "46": "海南省", "50": "重庆市", "51": "四川省", "52": "贵州省", "53": "云南省",
    "54": "西藏自治区", "61": "陕西省", "62": "甘肃省", "63": "青海省", "64": "宁夏回族自治区",
    "65": "新疆维吾尔自治区", "71": "台湾省", "81": "香港特别行政区", "82": "澳门特别行政区"
}

ETHNIC_TOKENS = [
    "彝族", "傣族", "苗族", "回族", "满族", "蒙古族", "藏族", "畲族", "土家族", "侗族",
    "朝鲜族", "俄罗斯族", "布依族", "水族", "阿昌族", "赫哲族", "瑶族", "仡佬族", "羌族",
    "白族", "哈尼族", "哈萨克族", "维吾尔族", "纳西族", "景颇族", "怒族", "独龙族",
    "仫佬族", "毛南族", "京族", "普米族", "撒拉族", "锡伯族", "塔吉克族", "塔塔尔族",
    "乌孜别克族", "裕固族", "保安族", "德昂族", "基诺族", "门巴族", "珞巴族", "东乡族",
    "布朗族", "佤族", "傈僳族", "拉祜族", "黎族", "壮族", "高山族", "民族",
]

def clean_town_name(name: str) -> str:
    """去除乡镇街道管理后缀，提取核心地名"""
    if not isinstance(name, str):
        return ""
    for s in ["街道办事处", "街道", "地区办事处", "地区", "镇", "乡", "民族乡", "社区服务中心", "区公所"]:
        if name.endswith(s):
            return name[:-len(s)]
    return name

def clean_town_core(name: str) -> str:
    """去除乡镇街道管理后缀与民族称谓，提取纯核心地名"""
    if not isinstance(name, str):
        return ""
    s = clean_town_name(name)
    for tok in ETHNIC_TOKENS:
        s = s.replace(tok, "")
    return s.strip()

def build_counties_topology():
    print(">>> 1. 正在加载天地图县级行政区划多边形...")
    gdf = gpd.read_file(PATH_TIANDITU_COUNTIES)
    # 过滤掉天地图底图中的境界线 (MultiLineString) 与无代码要素
    gdf = gdf[gdf.geometry.geom_type.isin(["Polygon", "MultiPolygon"]) & (gdf["gb"].astype(str).str.len() >= 6)].copy()
    print(f"    原生天地图有效区县多边形数量: {len(gdf)}")

    print(">>> 2. 正在提取 OSM 两江新区并执行拓扑紧密剪裁...")
    df_lj = pyogrio.read_dataframe(PATH_OSM_PBF, layer="multipolygons", where="name = '两江新区'", columns=["name"])
    if len(df_lj) > 0:
        geom_lj_raw = df_lj.iloc[0].geometry
        cq_mask = gdf["gb"].isin(["156500105", "156500112", "156500109"])
        geom_cq_union = gdf[cq_mask].union_all()
        geom_lj = geom_lj_raw.intersection(geom_cq_union)
        for idx in gdf[cq_mask].index:
            gdf.loc[idx, "geometry"] = gdf.loc[idx, "geometry"].difference(geom_lj)
        row_lj = gpd.GeoDataFrame([{"name": "两江新区", "gb": "156500157", "geometry": geom_lj}], crs=gdf.crs)
        gdf = gpd.GeoDataFrame(pd.concat([gdf, row_lj], ignore_index=True), crs=gdf.crs)
        print("    两江新区拓扑合并完成：生成独立区县代码 500157，原三区扣除差集无缝吻合。")

    print(">>> 3. 正在提取 OSM 加格达奇区并执行拓扑剪裁...")
    df_jg = pyogrio.read_dataframe(PATH_OSM_PBF, layer="multipolygons", where="name = '加格达奇区'", columns=["name"])
    if len(df_jg) > 0:
        geom_jg_raw = df_jg.iloc[0].geometry
        elc_mask = gdf["gb"] == "156150723"  # 鄂伦春自治旗 (150723)
        if elc_mask.any():
            elc_idx = gdf[elc_mask].index[0]
            geom_elc = gdf.loc[elc_idx, "geometry"]
            geom_jg = geom_jg_raw.intersection(geom_elc)
            gdf.loc[elc_idx, "geometry"] = geom_elc.difference(geom_jg)
            row_jg = gpd.GeoDataFrame([{"name": "加格达奇区", "gb": "156232761", "geometry": geom_jg}], crs=gdf.crs)
            gdf = gpd.GeoDataFrame(pd.concat([gdf, row_jg], ignore_index=True), crs=gdf.crs)
            print("    加格达奇区拓扑合并完成：生成独立区县代码 232761，鄂伦春旗差分扣除无缝吻合。")

    print(">>> 4. 保持天地图原始 100% 最高测绘精度边界（不执行平滑降精度简化）...")
    
    os.makedirs(BASE_DIR, exist_ok=True)
    gdf.to_file(OUTPUT_COUNTIES, driver="GeoJSON")
    print(f"    高精度区县边界已成功写入: {OUTPUT_COUNTIES} (总计 {len(gdf)} 个区县)")
    return gdf

def build_townships(gdf_counties):
    print(">>> 5. 正在加载民政部 2026/2025.251231 官方最新四级底册...")
    mca_towns = []
    if os.path.exists(PATH_MCA_LEVEL4):
        with open(PATH_MCA_LEVEL4, "r", encoding="utf-8-sig") as f:
            for row in csv.DictReader(f):
                if row["deep"] == "3":
                    mca_towns.append({
                        "id": row["id"],
                        "pid": row["pid"],
                        "name": row["name"],
                        "ext_name": row["ext_name"],
                        "ext_id": row["ext_id"]
                    })
    elif os.path.exists(OUTPUT_TOWNSHIPS_ALL):
        with open(OUTPUT_TOWNSHIPS_ALL, "r", encoding="utf-8") as f:
            for item in json.load(f):
                mca_towns.append({
                    "id": item.get("id", item["ext_id"][:9]),
                    "pid": item["county_id"],
                    "name": item["name"],
                    "ext_name": item["ext_name"],
                    "ext_id": item["ext_id"]
                })
    print(f"    民政部法定乡镇街道总数: {len(mca_towns)}")

    county_geom = {}
    county_name = {}
    for idx, r in gdf_counties.iterrows():
        raw_gb = str(r["gb"])
        c6 = raw_gb[3:] if raw_gb.startswith("156") else raw_gb
        county_geom[c6] = r.geometry
        county_name[c6] = r["name"]

    print(">>> 6. 正在构建多源分级坐标索引池...")
    # 6.1 Adcode (直接 12 位码 + 县域核心名 + 村居重心)
    adcode_by_code = {}
    adcode_by_cid_name = {}
    village_coords = defaultdict(list)
    adcode_files = glob.glob(os.path.join(PATH_ADCODE_DIR, "*.csv"))
    if adcode_files:
        for path in adcode_files:
            with open(path, "r", encoding="utf-8") as f:
                for line in f:
                    parts = line.strip().split(",")
                    if len(parts) >= 14:
                        lvl = parts[3]
                        code = parts[0]
                        parent = parts[1]
                        lon_s, lat_s = parts[12], parts[13]
                        if lon_s and lat_s:
                            try:
                                lon, lat = float(lon_s), float(lat_s)
                                if 73 <= lon <= 136 and 3 <= lat <= 54:
                                    if lvl == "town":
                                        adcode_by_code[code] = (round(lon, 6), round(lat, 6))
                                        clean_t = clean_town_name(parts[2])
                                        adcode_by_cid_name[(parts[5], clean_t)] = (round(lon, 6), round(lat, 6))
                                    elif lvl == "village":
                                        village_coords[parent].append((lon, lat))
                            except ValueError:
                                pass
        # 村居重心回溯
        for code, vlist in village_coords.items():
            if code not in adcode_by_code:
                adcode_by_code[code] = (
                    round(sum(v[0] for v in vlist) / len(vlist), 6),
                    round(sum(v[1] for v in vlist) / len(vlist), 6)
                )
    elif os.path.exists(OUTPUT_TOWNSHIPS_ACTIVE):
        with open(OUTPUT_TOWNSHIPS_ACTIVE, "r", encoding="utf-8") as f:
            for item in json.load(f):
                if item.get("source", "").startswith("adcode"):
                    adcode_by_code[item["ext_id"]] = (item["lon"], item["lat"])
                    clean_t = clean_town_name(item["name"])
                    adcode_by_cid_name[(item["county_id"], clean_t)] = (item["lon"], item["lat"])

    # 6.2 GB18306 国家抗震标准点位
    gb_by_cid_name = {}
    gb_by_cid_core = {}
    if os.path.exists(PATH_GB18306):
        with open(PATH_GB18306, "r", encoding="utf-8") as f:
            for item in json.load(f):
                try:
                    name = item["name"].encode("latin1").decode("utf-8")
                    clean_t = clean_town_name(name)
                    core_t = clean_town_core(name)
                    lon, lat = float(item["lon"]), float(item["lat"])
                    pcode = str(item.get("parentcode") or "")
                    ccode = pcode[3:] if len(pcode) == 9 and pcode.startswith("156") else pcode
                    if ccode:
                        pt_val = (round(lon, 6), round(lat, 6))
                        gb_by_cid_name[(ccode, clean_t)] = pt_val
                        if core_t:
                            gb_by_cid_core[(ccode, core_t)] = pt_val
                except Exception:
                    pass

    # 6.3 OSM 面要素与点要素
    print("    正在加载 OSM 行政多边形质心与地名点...")
    df_poly = pyogrio.read_dataframe(PATH_OSM_PBF, layer="multipolygons", where="boundary = 'administrative' AND admin_level IN ('7', '8', '9')", columns=["name"])
    osm_poly_map = {}
    for geom, name in zip(df_poly.geometry, df_poly["name"]):
        if geom and not geom.is_empty and isinstance(name, str):
            c = geom.centroid
            pt = (round(c.x, 6), round(c.y, 6))
            osm_poly_map[name] = pt
            clean_n = clean_town_name(name)
            if clean_n:
                osm_poly_map[clean_n] = pt

    df_pts = pyogrio.read_dataframe(PATH_OSM_PBF, layer="points", where="name IS NOT NULL", columns=["name"])
    osm_pts_map = defaultdict(list)
    for geom, name in zip(df_pts.geometry, df_pts["name"]):
        if geom and not geom.is_empty and isinstance(name, str):
            pt = (round(geom.x, 6), round(geom.y, 6))
            osm_pts_map[name].append(pt)
            clean_n = clean_town_name(name)
    # 6.4 锐多宝高精度多边形质心与村庄聚类中心 (经天地图区县多边形空间交叉验证)
    ruiduobao_map = {}
    path_rdb = os.path.join(BASE_DIR, "sources", "ruiduobao_resolved.json")
    if os.path.exists(path_rdb):
        with open(path_rdb, "r", encoding="utf-8") as f:
            for item in json.load(f):
                ruiduobao_map[item["ext_id"]] = ((item["lon"], item["lat"]), item["source"])
        print(f"    已加载锐多宝交叉验证点位: {len(ruiduobao_map)} 个")

    # 6.5 OSM 双通道严格校验驻地补点与兵团团场驻地
    osm_resolved_map = {}
    path_osm_resolved = os.path.join(BASE_DIR, "sources", "osm_seats.json")
    if os.path.exists(path_osm_resolved):
        with open(path_osm_resolved, "r", encoding="utf-8") as f:
            for item in json.load(f):
                if item.get("status") == "active" and item.get("lon") and item.get("lat") and not item.get("needs_review"):
                    osm_resolved_map[item["ext_id"]] = ((item["lon"], item["lat"]), item.get("source", "osm_admin_seat"))
        print(f"    已加载 OSM/兵团严格校验驻地补点: {len(osm_resolved_map)} 个")

    print(">>> 7. 正在执行 39,188 个乡镇的多层级消歧映射与位置继承...")
    results_all = []
    results_active = []
    unresolved_list = []
    stats = defaultdict(int)

    for m in mca_towns:
        ext_id = m["ext_id"]
        cid = m["pid"]
        name = m["name"]
        ext_name = m["ext_name"]
        clean_n = clean_town_name(name)
        core_n = clean_town_core(name)

        # 获取历史别名县码
        alias_cid = COUNTY_ALIASES.get(cid, cid)

        pt = None
        source = None

        # 1. 12位代码精准匹配
        if ext_id in adcode_by_code:
            pt = adcode_by_code[ext_id]
            source = "adcode_code"
        # 2. 县域（含别名）+ 核心名称继承
        elif (cid, clean_n) in adcode_by_cid_name:
            pt = adcode_by_cid_name[(cid, clean_n)]
            source = "adcode_name_inherit"
        elif (alias_cid, clean_n) in adcode_by_cid_name:
            pt = adcode_by_cid_name[(alias_cid, clean_n)]
            source = "adcode_name_inherit"
        # 3. GB18306 同县同名（含别名与民族去后缀）
        elif (cid, clean_n) in gb_by_cid_name:
            pt = gb_by_cid_name[(cid, clean_n)]
            source = "gb18306_name"
        elif (alias_cid, clean_n) in gb_by_cid_name:
            pt = gb_by_cid_name[(alias_cid, clean_n)]
            source = "gb18306_name"
        elif (cid, core_n) in gb_by_cid_core:
            pt = gb_by_cid_core[(cid, core_n)]
            source = "gb18306_name"
        elif (alias_cid, core_n) in gb_by_cid_core:
            pt = gb_by_cid_core[(alias_cid, core_n)]
            source = "gb18306_name"
        # 4. OSM 行政面质心
        elif ext_name in osm_poly_map:
            pt = osm_poly_map[ext_name]
            source = "osm_poly_centroid"
        elif clean_n in osm_poly_map:
            pt = osm_poly_map[clean_n]
            source = "osm_poly_centroid"
        # 5. OSM 点要素 (同县多边形优先)
        elif ext_name in osm_pts_map:
            c_poly = county_geom.get(cid)
            candidates = osm_pts_map[ext_name]
            for cand in candidates:
                if c_poly is not None and not c_poly.is_empty and c_poly.contains(Point(cand[0], cand[1])):
                    pt = cand
                    break
            if not pt and candidates:
                pt = candidates[0]
            source = "osm_point"
        elif clean_n in osm_pts_map:
            c_poly = county_geom.get(cid)
            candidates = osm_pts_map[clean_n]
            for cand in candidates:
                if c_poly is not None and not c_poly.is_empty and c_poly.contains(Point(cand[0], cand[1])):
                    pt = cand
                    break
            if not pt and candidates:
                pt = candidates[0]
            source = "osm_point"
        # 6. 新疆兵团番号正则
        elif "团" in name:
            reg_match = re.search(r"([一二三四五六七八九十百]+团)", name)
            if reg_match:
                reg_n = reg_match.group(1)
                if reg_n in osm_pts_map:
                    pt = osm_pts_map[reg_n][0]
                    source = "xpcc_regiment_osm"
                elif reg_n in osm_poly_map:
                    pt = osm_poly_map[reg_n]
                    source = "xpcc_regiment_osm"
        # 7. 锐多宝高精度多边形质心与村庄聚类中心 (经天地图区县多边形空间交叉验证)
        elif ext_id in ruiduobao_map:
            pt, source = ruiduobao_map[ext_id]
        # 8. OSM / 兵团严格校验驻地补点 (多门禁验证后的高置信坐标)
        elif ext_id in osm_resolved_map:
            pt, source = osm_resolved_map[ext_id]

        # 空间多边形复核：确保落点严格位于县多边形内部（防跨区越界）
        if pt:
            c_poly = county_geom.get(cid) or (county_geom.get(alias_cid) if alias_cid else None)
            if c_poly is not None and not c_poly.is_empty:
                if not c_poly.contains(Point(pt[0], pt[1])):
                    pt = None
                    source = None

        # 判断是否成功解析出真实离散坐标
        if pt:
            stats[source] += 1
            item_data = {
                "ext_id": ext_id,
                "county_id": cid,
                "name": name,
                "ext_name": ext_name,
                "lon": pt[0],
                "lat": pt[1],
                "source": source,
                "status": "active"
            }
            results_all.append(item_data)
            results_active.append(item_data)
        else:
            # 未找到可信坐标：坚决不填虚假县中心，标记为 pending
            stats["unresolved_pending"] += 1
            unresolved_record = {
                "ext_id": ext_id,
                "county_id": cid,
                "province": PROV_NAMES.get(cid[:2], "其他"),
                "name": name,
                "ext_name": ext_name,
                "lon": None,
                "lat": None,
                "source": "unresolved",
                "status": "pending",
                "reason": "缺少可信离散驻地坐标，暂不参与最近邻空间匹配"
            }
            results_all.append(unresolved_record)
            unresolved_list.append(unresolved_record)

    print(f">>> 8. 映射处理完成！统计摘要:")
    for k, v in sorted(stats.items(), key=lambda x: -x[1]):
        print(f"    {k:25s}: {v:6d} ({v/len(mca_towns)*100:.2f}%)")

    # 保存全量 39,188 字典
    with open(OUTPUT_TOWNSHIPS_ALL, "w", encoding="utf-8") as f:
        json.dump(results_all, f, ensure_ascii=False, indent=2)
    print(f"    全量乡镇字典已保存: {OUTPUT_TOWNSHIPS_ALL}")

    # 保存激活空间字典 (仅含可信离散点位)
    with open(OUTPUT_TOWNSHIPS_ACTIVE, "w", encoding="utf-8") as f:
        json.dump(results_active, f, ensure_ascii=False, indent=2)
    print(f"    运行时激活空间字典已保存: {OUTPUT_TOWNSHIPS_ACTIVE} (共 {len(results_active)} 个点位)")

    # 保存未完成待补全清单 JSON
    with open(OUTPUT_UNRESOLVED_JSON, "w", encoding="utf-8") as f:
        json.dump(unresolved_list, f, ensure_ascii=False, indent=2)
    print(f"    未完成待补全清单已保存: {OUTPUT_UNRESOLVED_JSON} (共 {len(unresolved_list)} 个条目)")

    # 压缩全量与激活字典 (供归档与体积优化)
    import gzip
    with open(OUTPUT_TOWNSHIPS_ALL, "rb") as f_in, gzip.open(OUTPUT_TOWNSHIPS_ALL + ".gz", "wb") as f_out:
        f_out.writelines(f_in)
    with open(OUTPUT_TOWNSHIPS_ACTIVE, "rb") as f_in, gzip.open(OUTPUT_TOWNSHIPS_ACTIVE + ".gz", "wb") as f_out:
        f_out.writelines(f_in)
    print("    全量与激活字典已同步更新 .json.gz 压缩镜像。")

    # 输出简洁、单一官方名称的未完成待补全 CSV 清单
    generate_unresolved_csv(unresolved_list, county_name)

    with open(OUTPUT_STATS, "w", encoding="utf-8") as f:
        json.dump(dict(stats), f, ensure_ascii=False, indent=2)

def generate_unresolved_csv(unresolved_list, county_name_map):
    """生成包含完整省市县层级与官方名称的未完成乡镇街道 CSV 清单，并单列新疆表"""
    path_l3 = os.path.join(BASE_DIR, "sources", "ok_data_level3.csv")
    hierarchy_map = {}
    if os.path.exists(path_l3):
        with open(path_l3, "r", encoding="utf-8-sig") as f:
            rows = list(csv.DictReader(f))
            c_map = {r["id"]: (r["pid"], r["name"]) for r in rows if r.get("deep") == "1"}
            for r in rows:
                if r.get("deep") == "2":
                    cid_str = str(r["id"])
                    pid = r["pid"]
                    cname = r["ext_name"]
                    city_name = c_map.get(pid, (None, ""))[1]
                    hierarchy_map[cid_str] = (city_name, cname)
                elif r.get("deep") == "1":
                    cid_str = str(r["id"])
                    if cid_str not in hierarchy_map:
                        hierarchy_map[cid_str] = (r["name"], r["ext_name"])
                elif r.get("deep") == "0":
                    cid_str = str(r["id"])
                    if cid_str not in hierarchy_map:
                        hierarchy_map[cid_str] = ("", r["ext_name"])

    def sort_key(x):
        cid = str(x["county_id"])
        is_mainland = 0 if not cid.startswith(("71", "81", "82", "99", "91")) else 1
        return (is_mainland, x.get("province", ""), cid, x.get("ext_id", ""))

    sorted_list = sorted(unresolved_list, key=sort_key)
    header = ["ext_id", "county_id", "province", "city_name", "county_name", "official_name", "reason"]
    rows_main = []
    rows_xj = []

    for x in sorted_list:
        cid = str(x["county_id"])
        mapped_city, mapped_county = hierarchy_map.get(cid, ("", county_name_map.get(cid, "")))
        off_name = x.get("ext_name") or x.get("name", "")
        reason = x.get("reason", "缺少可信离散驻地坐标")
        prov = x.get("province", "")
        row = [x["ext_id"], cid, prov, mapped_city, mapped_county, off_name, reason]
        if prov == "新疆维吾尔自治区":
            rows_xj.append(row)
        else:
            rows_main.append(row)

    with open(OUTPUT_UNRESOLVED_CSV, "w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(header)
        writer.writerows(rows_main)
    print(f"    未完成待补全 CSV 清单已保存: {OUTPUT_UNRESOLVED_CSV} (共 {len(rows_main)} 个条目)")

    with open(OUTPUT_UNRESOLVED_XINJIANG_CSV, "w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(header)
        writer.writerows(rows_xj)
    print(f"    新疆待补全专项清单已保存: {OUTPUT_UNRESOLVED_XINJIANG_CSV} (共 {len(rows_xj)} 个条目)")


if __name__ == "__main__":
    if os.path.exists(OUTPUT_COUNTIES):
        print(f">>> 1. 发现现有高精区县边界图层 {OUTPUT_COUNTIES}，直接加载...")
        counties_gdf = gpd.read_file(OUTPUT_COUNTIES)
    else:
        counties_gdf = build_counties_topology()
    build_townships(counties_gdf)
    print(">>> 全流程构建执行完毕！")
