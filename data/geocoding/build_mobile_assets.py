#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SkyPulse Mobile Offline Geocoding Asset Compiler
将高精度区县图层 (counties_2026.geojson) 与激活乡镇驻地 (townships_active.json)
编译为移动端高性能二进制资产:
  - app/src/main/assets/geo/counties.bin  (含 0.5° 空间哈希网格索引与 100% 原始测绘精度多边形顶点流)
  - app/src/main/assets/geo/townships.bin (按区县聚集排列的高紧凑乡镇驻地坐标字典)
"""

import os
import sys
import json
import struct
import time
import zlib
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
OUTPUT_COUNTIES_BIN = os.path.join(ASSETS_DIR, "counties.bin")
OUTPUT_TOWNSHIPS_BIN = os.path.join(ASSETS_DIR, "townships.bin")

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

    # 只有市辖区 (以"区"结尾且末两位 01-20) 属于非唯一名称，需要保留地级市或直辖市前缀
    # 县级市 (以"市"结尾，如大理市、延吉市、仙桃市) 与县/自治县/旗/林区全国唯一，省略上级
    if 1 <= tail <= 20 and cname.endswith("区"):
        if prov_id in [11, 12, 31, 50]:
            return f"{prov_name}{cname}"
        else:
            return f"{city_name}{cname}" if city_name else cname
    else:
        return cname

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
    print(f">>> 移动端离线地理资产构建成功！总 APK 压缩包体增量仅约 {(len(zlib.compress(t_buf)) + len(zlib.compress(c_buf)))/1024/1024:.2f} MB")

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
