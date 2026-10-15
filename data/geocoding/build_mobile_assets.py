#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SkyPulse Mobile Offline Geocoding Asset Compiler
将高精度区县图层 (counties_2026.geojson)、激活乡镇驻地 (townships_active.json)
与全量四级行政区划字典 (ok_data_level3.csv + townships_2026.json)
编译为移动端高性能二进制资产:
  - app/src/main/assets/geo/counties.bin  (含 0.5° 空间哈希网格索引与 100% 原始测绘精度多边形顶点流)
  - app/src/main/assets/geo/townships.bin (按区县聚集排列的高紧凑乡镇驻地坐标字典)
  - app/src/main/assets/geo/divisions.bin (全国省/市/县/乡四级完整行政区划树与正向名称检索引擎资产)
"""

import os
import sys
import csv
import json
import struct
import time
import zlib
from collections import defaultdict
import geopandas as gpd
from shapely.geometry import Polygon, MultiPolygon, box

SCALE = 1000000  # 坐标定点化缩放因子 1e6 (1微度 ≈ 0.1米精度)
GRID_MIN_LON = 73.0
GRID_MIN_LAT = 18.0
GRID_MAX_LON = 136.0
GRID_MAX_LAT = 54.0
GRID_STEP = 0.5
GRID_COLS = int((GRID_MAX_LON - GRID_MIN_LON) / GRID_STEP)  # 126
GRID_ROWS = int((GRID_MAX_LAT - GRID_MIN_LAT) / GRID_STEP)  # 72

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(BASE_DIR, "..", ".."))
ASSETS_DIR = os.path.join(PROJECT_ROOT, "app", "src", "main", "assets", "geo")

INPUT_COUNTIES = os.path.join(BASE_DIR, "counties_2026.geojson")
INPUT_TOWNSHIPS = os.path.join(BASE_DIR, "townships_active.json")
INPUT_TOWNSHIPS_ALL = os.path.join(BASE_DIR, "townships_2026.json")
INPUT_LEVEL3_CSV = os.path.join(BASE_DIR, "sources", "ok_data_level3.csv")
OUTPUT_COUNTIES_BIN = os.path.join(ASSETS_DIR, "counties.bin")
OUTPUT_TOWNSHIPS_BIN = os.path.join(ASSETS_DIR, "townships.bin")
OUTPUT_DIVISIONS_BIN = os.path.join(ASSETS_DIR, "divisions.bin")

def get_canonical_county_prefix(cid, cname, p_map):
    """
    依据民政部层级规范格式化区县前缀:
    1. 市辖区 (01-20 且以"区"结尾，或特定新区): 全国存在重名 (如鼓楼区、朝阳区)，必须保留上级地级市或直辖市 (如 "合肥市蜀山区", "北京市朝阳区")
    2. 县/县级市/旗/林区/特区: 全国名称唯一，彻底省略上级省市 (如 "青龙满族自治县", "昆山市", "神农架林区", "大理市")
    3. 特殊建制: 重庆市两江新区 (500157)、大兴安岭地区加格达奇区 (232761)
    """
    if cid == "500157":
        return "重庆市两江新区"
    if cid == "232761":
        return "大兴安岭地区加格达奇区"

    prov_id = int(cid[:2])
    city_id = int(cid[:4])
    tail = int(cid[4:6])

    prov_name = p_map.get(prov_id, "")
    city_name = p_map.get(city_id, "")

    if prov_id == 81:
        return "香港特别行政区"
    if prov_id == 82:
        return "澳门特别行政区"

    # 只有市辖区 (以"区"结尾且非林区/特区/自治区/特别行政区) 属于非唯一名称，需要保留地级市或直辖市前缀
    # 县级市 (以"市"结尾，如大理市、延吉市、仙桃市) 与县/自治县/旗/林区全国唯一，省略上级
    if cname.endswith("区") and not cname.endswith(("林区", "特区", "自治区", "特别行政区")):
        if prov_id in [11, 12, 31, 50]:
            return f"{prov_name}{cname}"
        else:
            return f"{city_name}{cname}" if city_name else cname
    else:
        return cname


def build_divisions_bin(gdf):
    """
    构建全国省/市/县/乡四级完整行政区划树与正向名称检索引擎二进制资产 (divisions.bin)
    格式 (Magic: SPGD, Little-Endian):
      Header (12 bytes):
        magic: 4B ("SPGD")
        version: uint16 (1)
        reserved: uint16 (0)
        num_nodes: uint32
      Node Stream (num_nodes 条记录):
        parent_idx: int16 (-1 表示省级根节点)
        first_child_idx: int32 (-1 表示无下辖子节点)
        child_count: uint16
        flags: uint8 (bits 0..1: admin_rank 0=省/1=地市/2=区县/3=乡镇街道; bit 2: is_fallback_center; bit 3: is_urban_district)
        lon_i: int32 (lon * 1e6)
        lat_i: int32 (lat * 1e6)
        code_int: int64 (12位民政部标准代码数值，保障任意层级严格按民政部代码升序排列)
        ext_name_len: uint8 + ext_name_utf8
        short_name_len: uint8 + short_name_utf8 (若与 ext_name 相同则为 0)
    """
    print(">>> 4b. 正在构建 divisions.bin (全国四级行政区划树与离线正向检索引擎资产)...")
    county_centers = {}
    for _, row in gdf.iterrows():
        geom = row.geometry
        c = geom.centroid
        if not geom.contains(c):
            c = geom.representative_point()
        county_centers[row["code"]] = (round(c.x, 6), round(c.y, 6))

    for k, v in {
        "460302": "460321",
        "460303": "460322",
        "540481": "540422",
        "540581": "540530",
        "632825": "632857",
    }.items():
        if v in county_centers:
            county_centers[k] = county_centers[v]

    tw_l1_to_gdf = {
        "7101": "710009", "7102": "710001", "7103": "710014", "7104": "710016",
        "7106": "710008", "7107": "710004", "7108": "710019", "7109": "710005",
        "7111": "710012", "7112": "710021", "7113": "710020", "7114": "710018",
        "7115": "710007", "7117": "710023", "7119": "710006", "7121": "710022",
        "7124": "710011", "7125": "710013", "7126": "710003", "7127": "710010",
    }
    for k, v in tw_l1_to_gdf.items():
        if v in county_centers:
            county_centers[k] = county_centers[v]

    with open(INPUT_LEVEL3_CSV, "r", encoding="utf-8-sig") as f:
        l3 = [r for r in csv.DictReader(f) if not r["id"].startswith("91")]

    with open(INPUT_TOWNSHIPS_ALL, "r", encoding="utf-8") as f:
        towns_all = [t for t in json.load(f) if t["county_id"] != "910000"]

    d0_raw = sorted([r for r in l3 if r["deep"] == "0"], key=lambda x: x["id"])
    d1_raw = sorted([r for r in l3 if r["deep"] == "1"], key=lambda x: x["id"])
    d2_raw = sorted([r for r in l3 if r["deep"] == "2"], key=lambda x: x["id"])
    d1_by_id = {r["id"]: r for r in d1_raw}

    dummy_d1_ids = {"1101", "1201", "3101", "5001", "5002", "8100", "8200"}
    dummy_d2_to_d1 = {
        r["id"]: r["pid"]
        for r in d2_raw
        if r["id"] not in ("810000", "820000")
        and r["ext_name"] == d1_by_id.get(r["pid"], {}).get("ext_name")
    }

    nodes = []
    idx_by_key = {}

    # 1. Level 0 (34 省级行政区)
    for r in d0_raw:
        rid = r["id"]
        k = "L0:" + rid
        idx_by_key[k] = len(nodes)
        nodes.append({
            "key": k,
            "code": rid,
            "parent_idx": -1,
            "admin_rank": 0,
            "name": r["name"].strip(),
            "ext_name": r["ext_name"].strip(),
            "lon": None,
            "lat": None,
            "is_fallback": False,
            "is_urban_district": False,
        })

    # 2. Level 1 (地级行政区 / 省直辖县级行政区 / 台湾 20 县市)
    for r in d1_raw:
        rid = r["id"]
        if rid in dummy_d1_ids:
            continue
        k = "L1:" + rid
        pk = "L0:" + r["pid"]
        pt = county_centers.get(rid) or county_centers.get(rid[:6])
        # 省直辖县级行政区 (如 4190xx, 4290xx, 4690xx, 6590xx): 在省下展示为直接子级，同时本身属于县级单位
        is_direct_county = len(rid) >= 6
        idx_by_key[k] = len(nodes)
        nodes.append({
            "key": k,
            "code": rid,
            "parent_idx": idx_by_key[pk],
            "admin_rank": 2 if is_direct_county else 1,
            "name": r["name"].strip(),
            "ext_name": r["ext_name"].strip(),
            "lon": pt[0] if pt else None,
            "lat": pt[1] if pt else None,
            "is_fallback": False,
            "is_urban_district": False,
        })

    # 3. Level 2 (大陆区县 + 香港 18 区 + 澳门 8 堂区)
    l2_items = []
    for r in d2_raw:
        rid = r["id"]
        if rid in ("810000", "820000") or rid in dummy_d2_to_d1 or rid.startswith("71"):
            continue
        pid = r["pid"]
        pk = ("L0:" + pid[:2]) if pid in ("1101", "1201", "3101", "5001", "5002") else ("L1:" + pid)
        pt = county_centers.get(rid) or county_centers.get(rid[:6])
        ext_name = r["ext_name"].strip()
        is_urban = ext_name.endswith("区") and not ext_name.endswith(("林区", "特区", "自治区", "特别行政区"))
        l2_items.append((rid, pk, r["name"].strip(), ext_name, pt[0], pt[1], is_urban))

    for t in towns_all:
        ext_id = str(t["ext_id"])
        if ext_id.startswith("81"):
            l2_items.append((ext_id[:6], "L0:81", t["name"].strip(), t["ext_name"].strip(), t["lon"], t["lat"], True))
        elif ext_id.startswith("82"):
            l2_items.append((ext_id[:6], "L0:82", t["name"].strip(), t["ext_name"].strip(), t["lon"], t["lat"], True))

    l2_items.sort(key=lambda x: x[0])
    for rid, pk, name, ext_name, lon, lat, is_urban in l2_items:
        k = "L2:" + rid
        idx_by_key[k] = len(nodes)
        nodes.append({
            "key": k,
            "code": rid,
            "parent_idx": idx_by_key[pk],
            "admin_rank": 2,
            "name": name,
            "ext_name": ext_name,
            "lon": lon,
            "lat": lat,
            "is_fallback": False,
            "is_urban_district": is_urban,
        })

    # 4. Level 3 (大陆乡镇街道 + 台湾 358 乡镇市区)
    l3_items = []
    for t in towns_all:
        ext_id = str(t["ext_id"])
        if ext_id.startswith(("81", "82")):
            continue
        cid = str(t["county_id"])
        if ext_id.startswith("71"):
            pk = "L1:" + ext_id[:4]
            rank = 2  # 台湾省下辖县市的下一级为乡镇市区
        elif cid in dummy_d2_to_d1:
            pk = "L1:" + dummy_d2_to_d1[cid]
            rank = 3
        else:
            pk = "L2:" + cid
            rank = 3

        p_idx = idx_by_key[pk]
        is_active = (t.get("status") == "active" and t.get("lon") is not None and t.get("lat") is not None)
        if is_active:
            lon, lat = t["lon"], t["lat"]
            is_fb = False
        else:
            p_node = nodes[p_idx]
            lon, lat = p_node["lon"], p_node["lat"]
            is_fb = True

        l3_items.append((ext_id, p_idx, rank, t["name"].strip(), (t.get("ext_name") or t["name"]).strip(), lon, lat, is_fb))

    l3_items.sort(key=lambda x: x[0])
    for ext_id, p_idx, rank, name, ext_name, lon, lat, is_fb in l3_items:
        k = "L3:" + ext_id
        idx_by_key[k] = len(nodes)
        nodes.append({
            "key": k,
            "code": ext_id,
            "parent_idx": p_idx,
            "admin_rank": rank,
            "name": name,
            "ext_name": ext_name,
            "lon": lon,
            "lat": lat,
            "is_fallback": is_fb,
            "is_urban_district": False,
        })

    # 计算每个父节点的 [first_child_idx, child_count]
    children_map = defaultdict(list)
    for idx, node in enumerate(nodes):
        p_idx = node["parent_idx"]
        if p_idx >= 0:
            children_map[p_idx].append(idx)

    for idx, node in enumerate(nodes):
        ch = children_map.get(idx, [])
        if ch:
            assert ch == list(range(ch[0], ch[0] + len(ch))), f"Non-contiguous children for node {node['key']}"
            node["first_child_idx"] = ch[0]
            node["child_count"] = len(ch)
        else:
            node["first_child_idx"] = -1
            node["child_count"] = 0

    # 回填地级市与省级行政中心坐标 (取民政部排序首位下辖区县/首府中心点)
    for node in nodes:
        if node["admin_rank"] == 1 and (node["lon"] is None or node["lat"] is None):
            first_c = nodes[node["first_child_idx"]]
            node["lon"], node["lat"] = first_c["lon"], first_c["lat"]
    for node in nodes:
        if node["admin_rank"] == 0 and (node["lon"] is None or node["lat"] is None):
            first_c = nodes[node["first_child_idx"]]
            node["lon"], node["lat"] = first_c["lon"], first_c["lat"]

    # 序列化为 divisions.bin
    d_buf = bytearray()
    d_buf.extend(b"SPGD")
    d_buf.extend(struct.pack("<HHI", 1, 0, len(nodes)))

    fallback_count = 0
    for node in nodes:
        flags = (node["admin_rank"] & 0x03)
        if node["is_fallback"]:
            flags |= 0x04
            fallback_count += 1
        if node["is_urban_district"]:
            flags |= 0x08

        lon_i = int(round(node["lon"] * SCALE))
        lat_i = int(round(node["lat"] * SCALE))
        code_str = str(node["code"]).ljust(12, "0")[:12]
        code_int = int(code_str)

        ext_bytes = node["ext_name"].encode("utf-8")
        short_str = node["name"] if node["name"] != node["ext_name"] else ""
        short_bytes = short_str.encode("utf-8")

        d_buf.extend(struct.pack(
            "<hiHBiiqB",
            node["parent_idx"],
            node["first_child_idx"],
            node["child_count"],
            flags,
            lon_i,
            lat_i,
            code_int,
            len(ext_bytes)
        ))
        d_buf.extend(ext_bytes)
        d_buf.extend(struct.pack("<B", len(short_bytes)))
        if short_bytes:
            d_buf.extend(short_bytes)

    with open(OUTPUT_DIVISIONS_BIN, "wb") as f:
        f.write(d_buf)

    print(f"    divisions.bin 写入完成: 共 {len(nodes):,} 个行政区节点 (含 {fallback_count} 个回退上级中心点的待补全乡镇), "
          f"{len(d_buf):,} 字节 ({len(d_buf)/1024:.1f} KB, gzip: {len(zlib.compress(d_buf))/1024:.1f} KB)")
    return d_buf

def main():
    print("=== SkyPulse 离线逆地理移动端二进制资产构建器 ===")
    os.makedirs(ASSETS_DIR, exist_ok=True)

    print(">>> 1. 正在加载源数据...")
    if not os.path.exists(INPUT_COUNTIES) or not os.path.exists(INPUT_TOWNSHIPS):
        print(f"错误: 缺少输入文件 {INPUT_COUNTIES} 或 {INPUT_TOWNSHIPS}")
        sys.exit(1)

    gdf = gpd.read_file(INPUT_COUNTIES)
    gdf["code"] = gdf["gb"].astype(str).str.replace("^156", "", regex=True)

    with open(INPUT_TOWNSHIPS, "r", encoding="utf-8") as f:
        towns = json.load(f)

    # 加载民政部三级关系表以推导市辖区所属地级市/直辖市
    path_l3 = os.path.join(BASE_DIR, "sources", "ok_data_level3.csv")
    p_map = {}
    if os.path.exists(path_l3):
        import pandas as pd
        df_l3 = pd.read_csv(path_l3)
        p_map = dict(zip(df_l3["id"], df_l3["ext_name"]))

    print(f"    成功加载区县: {len(gdf)} 个, 激活乡镇: {len(towns)} 个, 上级映射: {len(p_map)} 条")

    # 1. 构建 townships.bin (使用法定标准官方全名 ext_name)
    print(">>> 2. 正在构建 townships.bin (使用民政部法定官方全名)...")
    towns_by_cid = {}
    for t in towns:
        cid = t["county_id"]
        if cid not in towns_by_cid:
            towns_by_cid[cid] = []
        towns_by_cid[cid].append(t)

    t_buf = bytearray()
    t_buf.extend(b"SPGT")  # Magic: SkyPulse Geo Townships
    t_buf.extend(struct.pack("<HI", 1, len(towns)))  # version: 1, num_towns: uint32

    county_township_meta = {}  # cid -> (byte_offset, count)

    for idx, row in gdf.iterrows():
        cid = row["code"]
        c_towns = towns_by_cid.get(cid, [])
        c_offset = len(t_buf)
        c_count = len(c_towns)
        county_township_meta[cid] = (c_offset, c_count)

        for t in c_towns:
            lon_i = int(round(t["lon"] * SCALE))
            lat_i = int(round(t["lat"] * SCALE))
            # 必须使用法定标准全称 (如 "东华门街道", "都阳路街道", "玉山镇")
            official_name = t.get("ext_name") or t["name"]
            name_bytes = official_name.encode("utf-8")
            t_buf.extend(struct.pack("<iiB", lon_i, lat_i, len(name_bytes)))
            t_buf.extend(name_bytes)

    with open(OUTPUT_TOWNSHIPS_BIN, "wb") as f:
        f.write(t_buf)

    print(f"    townships.bin 写入完成: {len(t_buf):,} 字节 ({len(t_buf)/1024:.1f} KB, gzip: {len(zlib.compress(t_buf))/1024:.1f} KB)")

    # 2. 构建 0.5° 空间哈希网格索引
    print(">>> 3. 正在构建 0.5° 空间自适应哈希网格索引...")
    grid_cells = [[] for _ in range(GRID_COLS * GRID_ROWS)]
    for c_idx, row in gdf.iterrows():
        geom = row.geometry
        minx, miny, maxx, maxy = geom.bounds
        c_start = max(0, int((minx - GRID_MIN_LON) / GRID_STEP))
        c_end = min(GRID_COLS - 1, int((maxx - GRID_MIN_LON) / GRID_STEP))
        r_start = max(0, int((miny - GRID_MIN_LAT) / GRID_STEP))
        r_end = min(GRID_ROWS - 1, int((maxy - GRID_MIN_LAT) / GRID_STEP))
        for r in range(r_start, r_end + 1):
            cell_miny = GRID_MIN_LAT + r * GRID_STEP
            cell_maxy = cell_miny + GRID_STEP
            for c in range(c_start, c_end + 1):
                cell_minx = GRID_MIN_LON + c * GRID_STEP
                cell_maxx = cell_minx + GRID_STEP
                cell_box = box(cell_minx, cell_miny, cell_maxx, cell_maxy)
                if geom.intersects(cell_box):
                    grid_idx = r * GRID_COLS + c
                    grid_cells[grid_idx].append(c_idx)

    non_empty = [len(x) for x in grid_cells if len(x) > 0]
    print(f"    空间网格构建完毕: 总单元格 {len(grid_cells)}, 陆地活跃网格 {len(non_empty)}, 平均候选区县 {sum(non_empty)/len(non_empty):.2f} 个, 最大 {max(non_empty)} 个")

    # 3. 构建 counties.bin
    print(">>> 4. 正在构建 counties.bin (高精多边形顶点流与网格索引)...")
    c_buf = bytearray()
    c_buf.extend(b"SPGC")  # Magic: SkyPulse Geo Counties
    # Header (32 bytes): version(uint16), num_counties(uint16), grid_min_lon(int32), grid_min_lat(int32), grid_step(int32), cols(uint16), rows(uint16), grid_data_start(uint32), county_offsets_start(uint32)
    header_pos = len(c_buf)
    c_buf.extend(struct.pack(
        "<HHiIIHHII",
        1,
        len(gdf),
        int(GRID_MIN_LON * SCALE),
        int(GRID_MIN_LAT * SCALE),
        int(GRID_STEP * SCALE),
        GRID_COLS,
        GRID_ROWS,
        0, # placeholder for grid_data_start
        0  # placeholder for county_offsets_start
    ))

    # Grid offsets 占位符 (GRID_COLS * GRID_ROWS * 4 bytes)
    grid_offsets_pos = len(c_buf)
    c_buf.extend(b"\x00" * (len(grid_cells) * 4))

    # Grid 候选区县数据流
    grid_data_start = len(c_buf)
    grid_offsets = []
    for cell in grid_cells:
        if not cell:
            grid_offsets.append(-1)
        else:
            offset = len(c_buf) - grid_data_start
            grid_offsets.append(offset)
            c_buf.extend(struct.pack("<H", len(cell)))
            for c_idx in cell:
                c_buf.extend(struct.pack("<H", c_idx))

    # 回填网格偏移量
    struct.pack_into(f"<{len(grid_offsets)}i", c_buf, grid_offsets_pos, *grid_offsets)

    # Counties 偏移量占位符 (2900 * 4 bytes)
    county_offsets_pos = len(c_buf)
    c_buf.extend(b"\x00" * (len(gdf) * 4))

    # 回填 Header 中的两个关键起始位置
    struct.pack_into("<II", c_buf, header_pos + 20, grid_data_start, county_offsets_pos)

    county_offsets = []
    for c_idx, row in gdf.iterrows():
        offset = len(c_buf)
        county_offsets.append(offset)
        cid = row["code"]
        canonical_name = get_canonical_county_prefix(cid, row["name"], p_map)
        cname = canonical_name.encode("utf-8")
        geom = row.geometry
        minx, miny, maxx, maxy = geom.bounds

        t_offset, t_count = county_township_meta.get(cid, (0, 0))

        rings = []
        if isinstance(geom, Polygon):
            rings.append(list(geom.exterior.coords))
            for ring in geom.interiors:
                rings.append(list(ring.coords))
        elif isinstance(geom, MultiPolygon):
            for poly in geom.geoms:
                rings.append(list(poly.exterior.coords))
                for ring in poly.interiors:
                    rings.append(list(ring.coords))

        # 打包区县记录头: code(6 bytes), name_len(1 byte), name, bbox(4*int32), township_offset(uint32), township_count(uint16), num_rings(uint16)
        c_buf.extend(cid.encode("ascii")[:6].ljust(6, b" "))
        c_buf.extend(struct.pack("<B", len(cname)))
        c_buf.extend(cname)
        c_buf.extend(struct.pack("<iiiiIH", int(minx * SCALE), int(miny * SCALE), int(maxx * SCALE), int(maxy * SCALE), t_offset, t_count))
        c_buf.extend(struct.pack("<H", len(rings)))

        for ring in rings:
            c_buf.extend(struct.pack("<I", len(ring)))
            for pt in ring:
                c_buf.extend(struct.pack("<ii", int(pt[0] * SCALE), int(pt[1] * SCALE)))

    # 回填区县偏移量
    struct.pack_into(f"<{len(county_offsets)}I", c_buf, county_offsets_pos, *county_offsets)

    with open(OUTPUT_COUNTIES_BIN, "wb") as f:
        f.write(c_buf)

    print(f"    counties.bin 写入完成: {len(c_buf):,} 字节 ({len(c_buf)/1024/1024:.2f} MB, gzip: {len(zlib.compress(c_buf))/1024/1024:.2f} MB)")

    d_buf = build_divisions_bin(gdf)
    total_gz_mb = (len(zlib.compress(t_buf)) + len(zlib.compress(c_buf)) + len(zlib.compress(d_buf))) / 1024 / 1024
    print(f">>> 移动端离线地理资产构建成功！总 APK 压缩包体增量仅约 {total_gz_mb:.2f} MB")

    # 4. 执行多点位真实反查性能测试与校验
    print(">>> 5. 正在执行二进制资产快速反查基准测试...")
    test_points = [
        ("北京天安门", 116.397455, 39.909187),
        ("重庆两江新区(鸳鸯街道)", 106.5516, 29.6648),
        ("黑龙江加格达奇区", 124.1256, 50.4208),
        ("上海陆家嘴", 121.5036, 31.2397),
        ("西藏拉萨布达拉宫", 91.1175, 29.6555),
        ("新疆乌鲁木齐大巴扎", 87.6168, 43.7845),
        ("广州天河体育中心", 113.3245, 23.1389),
        ("青龙满族自治县都阳路", 118.9476, 40.4058),
        ("台北101", 121.5645, 25.0339),
        ("高雄85大楼", 120.3005, 22.6115),
        ("香港中环IFC", 114.1588, 22.2849),
        ("香港旺角朗豪坊", 114.1687, 22.3188),
        ("澳门大三巴", 113.5408, 22.1977),
        ("澳门氹仔威尼斯人", 113.5598, 22.1481),
        ("四川石厢子彝族乡", 105.423366, 27.788718),
        ("广东揭阳京冈街道", 116.425000, 23.491700),
        ("河南封丘荆隆宫镇", 114.321828, 34.963898),
        ("黑龙江大庆格林街道", 125.108400, 46.731200),
    ]

    for label, lon, lat in test_points:
        t0 = time.perf_counter()
        # 网格粗筛
        lon_i = int(round(lon * SCALE))
        lat_i = int(round(lat * SCALE))
        col = int((lon - GRID_MIN_LON) / GRID_STEP)
        row = int((lat - GRID_MIN_LAT) / GRID_STEP)

        matched_county = None
        matched_township = None

        if 0 <= col < GRID_COLS and 0 <= row < GRID_ROWS:
            cell_offset = grid_offsets[row * GRID_COLS + col]
            if cell_offset >= 0:
                # 读取该单元格候选区县
                c_data_pos = grid_data_start + cell_offset
                cand_count = struct.unpack_from("<H", c_buf, c_data_pos)[0]
                cand_indices = struct.unpack_from(f"<{cand_count}H", c_buf, c_data_pos + 2)

                for c_idx in cand_indices:
                    c_pos = county_offsets[c_idx]
                    code_str = c_buf[c_pos:c_pos+6].decode("ascii").strip()
                    cname_len = c_buf[c_pos+6]
                    cname_str = c_buf[c_pos+7:c_pos+7+cname_len].decode("utf-8")
                    pos_after_name = c_pos + 7 + cname_len
                    b_minx, b_miny, b_maxx, b_maxy, t_off, t_cnt, num_rings = struct.unpack_from("<iiiiIHH", c_buf, pos_after_name)

                    # Bbox 快速测试
                    if not (b_minx <= lon_i <= b_maxx and b_miny <= lat_i <= b_maxy):
                        continue

                    # Ray-Casting 射线法精准命中
                    crossings = 0
                    r_pos = pos_after_name + 24
                    for _ in range(num_rings):
                        num_pts = struct.unpack_from("<I", c_buf, r_pos)[0]
                        r_pos += 4
                        # 遍历环的各边
                        for p_idx in range(num_pts - 1):
                            x1, y1 = struct.unpack_from("<ii", c_buf, r_pos + p_idx * 8)
                            x2, y2 = struct.unpack_from("<ii", c_buf, r_pos + (p_idx + 1) * 8)
                            if (y1 > lat_i) != (y2 > lat_i):
                                x_cross = x1 + (lat_i - y1) * (x2 - x1) / (y2 - y1)
                                if lon_i < x_cross:
                                    crossings += 1
                        r_pos += num_pts * 8

                    if crossings % 2 == 1:
                        matched_county = (code_str, cname_str)
                        # 在该区县内搜索最近乡镇
                        if t_cnt > 0:
                            min_dist_sq = float("inf")
                            best_tname = None
                            t_curr_pos = t_off
                            for _ in range(t_cnt):
                                t_lon_i, t_lat_i, t_nlen = struct.unpack_from("<iiB", t_buf, t_curr_pos)
                                t_curr_pos += 9
                                t_name = t_buf[t_curr_pos:t_curr_pos+t_nlen].decode("utf-8")
                                t_curr_pos += t_nlen
                                d_sq = (lon_i - t_lon_i) ** 2 + (lat_i - t_lat_i) ** 2
                                if d_sq < min_dist_sq:
                                    min_dist_sq = d_sq
                                    best_tname = t_name
                            matched_township = best_tname
                        break

        dur_us = (time.perf_counter() - t0) * 1e6
        res_str = f"{matched_county[1] if matched_county else '未命中'} {matched_township or ''}".strip()
        print(f"    [{label}] -> {res_str} (耗时: {dur_us:.1f} 微秒 / {dur_us/1000:.3f} 毫秒)")

if __name__ == "__main__":
    main()
