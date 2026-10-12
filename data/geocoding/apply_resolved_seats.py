#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SkyPulse 离线逆地理编码 - 批量应用已严格校验补全点位
--------------------------------------------------
将 GB18306 (37条，经县多边形包含校验与民族去后缀核验)、
OSM Nominatim (6条，经反向地理编码与面质心/驻地核验)、
新疆兵团师市合一 (12条，经多边形包含与同市驻点间距校验)
共计 55 个高精度离散点位合并入民政部底册字典：
  - 更新 townships_2026.json 与 townships_2026.json.gz
  - 更新 townships_active.json 与 townships_active.json.gz
  - 更新 unresolved_townships.json 与 unresolved_townships.csv
  - 更新 build_stats.json
  - 触发 build_mobile_assets.py 重构移动端 townships.bin
"""

import csv
import gzip
import json
import os
import sys

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
PATH_TOWNS_ALL = os.path.join(BASE_DIR, "townships_2026.json")
PATH_TOWNS_ACTIVE = os.path.join(BASE_DIR, "townships_active.json")
PATH_UNRES_JSON = os.path.join(BASE_DIR, "unresolved_townships.json")
PATH_UNRES_CSV = os.path.join(BASE_DIR, "unresolved_townships.csv")
PATH_UNRES_XJ_CSV = os.path.join(BASE_DIR, "unresolved_townships_xinjiang.csv")
PATH_L3 = os.path.join(BASE_DIR, "sources", "ok_data_level3.csv")
PATH_STATS = os.path.join(BASE_DIR, "build_stats.json")

# 55 个经过 100% 空间几何复核与多门禁校验的高置信离散驻地点位
RESOLVED_POINTS = {
    # 1. GB18306 国家抗震标准点位（民族称谓解耦与历史区划代码别名匹配，全量通过天地图县多边形包含校验）
    "130881201000": (118.635384, 41.181304, "gb18306_name"),  # 七家岱满族乡
    "130881202000": (118.839416, 41.199184, "gb18306_name"),  # 茅兰沟满族蒙古族乡
    "130929208000": (116.247983, 38.342400, "gb18306_name"),  # 本斋回族乡
    "130984206000": (116.045920, 38.427068, "gb18306_name"),  # 果子洼回族乡
    "210624202000": (125.521163, 40.899017, "gb18306_name"),  # 下露河朝鲜族乡
    "220112203000": (125.586280, 43.596228, "gb18306_name"),  # 双营子回族乡
    "220303110000": (124.531416, 42.944568, "gb18306_name"),  # 叶赫满族镇
    "230881204000": (132.841112, 47.932724, "gb18306_name"),  # 街津口赫哲族乡
    "330781202000": (119.270416, 29.181418, "gb18306_name"),  # 水亭畲族乡
    "331102207000": (119.692816, 28.483676, "gb18306_name"),  # 丽新畲族乡
    "350604201000": (118.056136, 24.255426, "gb18306_name"),  # 隆教畲族乡
    "411003208000": (113.650960, 34.145952, "gb18306_name"),  # 艾庄回族乡
    "429021203000": (110.251677, 31.371610, "gb18306_name"),  # 下谷坪土家族乡
    "430821218000": (110.780568, 29.279354, "gb18306_name"),  # 甘堰土家族乡
    "431222207000": (110.280896, 28.530854, "gb18306_name"),  # 二酉苗族乡
    "441625200000": (114.922840, 24.103156, "gb18306_name"),  # 漳溪畲族乡
    "451203208000": (108.400256, 24.410748, "gb18306_name"),  # 北牙瑶族乡
    "500237200000": (109.713096, 30.851854, "gb18306_name"),  # 红椿土家族乡
    "511526212000": (104.806618, 28.196990, "gb18306_name"),  # 观斗苗族乡
    "520201203000": (105.002680, 26.756398, "gb18306_name"),  # 南开苗族彝族乡
    "520204204000": (104.925640, 26.077452, "gb18306_name"),  # 花戛苗族布依族彝族乡
    "520204207000": (104.924576, 26.237872, "gb18306_name"),  # 野钟苗族彝族布依族乡
    "520204208000": (104.986984, 26.269188, "gb18306_name"),  # 果布戛彝族苗族布依族乡
    "520403201000": (106.321208, 26.346230, "gb18306_name"),  # 羊昌布依族苗族乡
    "522302200000": (105.040896, 25.341512, "gb18306_name"),  # 鲁础营回族乡
    "530502214000": (98.866904, 25.448406, "gb18306_name"),   # 芒宽彝族傣族乡
    "533124200000": (97.894368, 24.468612, "gb18306_name"),   # 户撒阿昌族乡
    "621025207000": (108.461600, 35.400836, "gb18306_name"),  # 五顷塬回族乡
    "621202201000": (104.666968, 33.508964, "gb18306_name"),  # 坪垭藏族乡
    "520581208000": (106.131760, 27.170584, "gb18306_name"),  # 定新彝族苗族乡
    "520581202000": (106.094296, 26.858602, "gb18306_name"),  # 新仁苗族乡
    "520581204000": (106.339176, 27.065220, "gb18306_name"),  # 太来彝族苗族乡
    "520581205000": (106.200504, 27.104120, "gb18306_name"),  # 永燊彝族苗族乡
    "520621200000": (108.557240, 27.787528, "gb18306_name"),  # 德旺土家族苗族乡
    "520621201000": (108.657008, 27.528906, "gb18306_name"),  # 官和侗族土家族苗族乡
    "520626211000": (108.163880, 28.355056, "gb18306_name"),  # 荆角土家族乡
    "520626209000": (107.878720, 28.275964, "gb18306_name"),  # 楠杆土家族乡

    # 2. OSM 双通道严格校验点位（通过行政多边形质心 / 驻地建筑 / 空间包含核验）
    "150784203000": (119.842819, 50.863796, "osm_admin_seat"),  # 恩和俄罗斯族民族乡
    "220721001000": (124.826426, 45.115797, "osm_admin_seat"),  # 阿穆尔街道
    "220721002000": (124.844440, 45.103502, "osm_admin_seat"),  # 萨日朗街道
    "340722119000": (117.398548, 30.770970, "osm_point"),       # 藕山镇
    "341524113000": (116.007061, 31.437302, "osm_point"),       # 流波幢镇
    "130731400000": (115.292314, 39.885544, "osm_point"),       # 赵家蓬区公所

    # 3. 新疆生产建设兵团团场驻地（师市合一、团镇合一，通过多边形包含校验，距同市驻地>5km）
    "659001412000": (86.043458, 44.184712, "xpcc_regiment_osm"),  # 一五二团
    "659002106000": (81.560310, 40.626590, "xpcc_regiment_osm"),  # 十一团花桥
    "659002107000": (81.497400, 40.638500, "xpcc_regiment_osm"),  # 十三团幸福
    "659002108000": (81.769450, 40.690330, "xpcc_regiment_osm"),  # 十四团金杨
    "659002114000": (80.841140, 40.493260, "xpcc_regiment_osm"),  # 十六团新开岭
    "659003107000": (79.247952, 39.945900, "xpcc_regiment_osm"),  # 五十团夏河
    "659003110000": (79.144006, 39.975147, "xpcc_regiment_osm"),  # 五十一团唐驿
    "659005101000": (87.982856, 47.274041, "xpcc_regiment_osm"),  # 一八七团丰庆
    "659006105000": (86.530447, 42.298966, "xpcc_regiment_osm"),  # 二二三团开泽
    "659009100000": (79.643220, 37.515770, "xpcc_regiment_osm"),  # 四十七团老兵
    "659009104000": (79.300741, 37.206313, "xpcc_regiment_osm"),  # 二二四团玉园
    "659011102000": (93.735711, 42.797048, "xpcc_regiment_osm"),  # 黄田农场黄田
}


def main():
    print("=== SkyPulse 批量应用已校验补全点位 ===")
    with open(PATH_TOWNS_ALL, "r", encoding="utf-8") as f:
        towns_all = json.load(f)
    with open(PATH_TOWNS_ACTIVE, "r", encoding="utf-8") as f:
        towns_active = json.load(f)
    with open(PATH_UNRES_JSON, "r", encoding="utf-8") as f:
        unres_json = json.load(f)

    active_exts = {t["ext_id"] for t in towns_active}
    all_by_ext = {t["ext_id"]: t for t in towns_all}

    applied_count = 0
    newly_active = []

    for ext_id, (lon, lat, src) in RESOLVED_POINTS.items():
        if ext_id in all_by_ext:
            rec = all_by_ext[ext_id]
            rec["lon"] = round(lon, 6)
            rec["lat"] = round(lat, 6)
            rec["source"] = src
            rec["status"] = "active"
            rec.pop("reason", None)

            if ext_id not in active_exts:
                active_item = {
                    "ext_id": ext_id,
                    "county_id": rec["county_id"],
                    "name": rec["name"],
                    "ext_name": rec.get("ext_name") or rec["name"],
                    "lon": rec["lon"],
                    "lat": rec["lat"],
                    "source": src,
                    "status": "active",
                }
                newly_active.append(active_item)
                active_exts.add(ext_id)
            applied_count += 1

    # 合并并按 (county_id, ext_id) 排序激活列表
    all_active = towns_active + newly_active
    all_active.sort(key=lambda x: (x["county_id"], x["ext_id"]))

    # 剩余未完成条目
    new_unresolved = [u for u in unres_json if u["ext_id"] not in RESOLVED_POINTS]

    print(f"    成功匹配并补全: {applied_count} 个乡镇街道点位")
    print(f"    激活字典扩充: {len(towns_active)} -> {len(all_active)} (+{len(newly_active)})")
    print(f"    未完成清单递减: {len(unres_json)} -> {len(new_unresolved)} (-{len(unres_json) - len(new_unresolved)})")

    # 1. 保存 townships_2026.json
    with open(PATH_TOWNS_ALL, "w", encoding="utf-8") as f:
        json.dump(towns_all, f, ensure_ascii=False, indent=2)
    with open(PATH_TOWNS_ALL, "rb") as f_in, gzip.open(PATH_TOWNS_ALL + ".gz", "wb") as f_out:
        f_out.writelines(f_in)

    # 2. 保存 townships_active.json
    with open(PATH_TOWNS_ACTIVE, "w", encoding="utf-8") as f:
        json.dump(all_active, f, ensure_ascii=False, indent=2)
    with open(PATH_TOWNS_ACTIVE, "rb") as f_in, gzip.open(PATH_TOWNS_ACTIVE + ".gz", "wb") as f_out:
        f_out.writelines(f_in)

    # 3. 保存 unresolved_townships.json
    with open(PATH_UNRES_JSON, "w", encoding="utf-8") as f:
        json.dump(new_unresolved, f, ensure_ascii=False, indent=2)

    # 4. 保存 unresolved_townships.csv 及 unresolved_townships_xinjiang.csv
    hierarchy_map = {}
    if os.path.exists(PATH_L3):
        with open(PATH_L3, "r", encoding="utf-8-sig") as f:
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

    header = ["ext_id", "county_id", "province", "city_name", "county_name", "official_name", "reason"]
    rows_main = []
    rows_xj = []
    for u in sorted(new_unresolved, key=lambda x: (0 if not str(x["county_id"]).startswith(("71", "81", "82", "99", "91")) else 1, x.get("province", ""), str(x["county_id"]), x.get("ext_id", ""))):
        cid = str(u["county_id"])
        city, county = hierarchy_map.get(cid, ("", u.get("county_name", "")))
        off_name = u.get("ext_name") or u.get("name", "")
        reason = u.get("reason", "缺少可信离散驻地坐标，暂不参与最近邻空间匹配")
        prov = u.get("province", "")
        row = [u["ext_id"], cid, prov, city, county, off_name, reason]
        if prov == "新疆维吾尔自治区":
            rows_xj.append(row)
        else:
            rows_main.append(row)

    with open(PATH_UNRES_CSV, "w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(header)
        writer.writerows(rows_main)

    with open(PATH_UNRES_XJ_CSV, "w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(header)
        writer.writerows(rows_xj)

    # 5. 更新 build_stats.json
    stats = {}
    for t in towns_all:
        src = t.get("source", "unresolved_pending")
        if t.get("status") == "pending":
            src = "unresolved_pending"
        stats[src] = stats.get(src, 0) + 1
    with open(PATH_STATS, "w", encoding="utf-8") as f:
        json.dump(dict(sorted(stats.items(), key=lambda x: -x[1])), f, ensure_ascii=False, indent=2)

    print(">>> 数据集更新完成！正在重新编译移动端二进制资产 (townships.bin)...")
    cmd = f"{sys.executable} {os.path.join(BASE_DIR, 'build_mobile_assets.py')}"
    ret = os.system(cmd)
    if ret != 0:
        print("错误: build_mobile_assets.py 构建失败")
        sys.exit(1)
    print("=== 全流程点位补全与二进制资产编译成功完成！ ===")


if __name__ == "__main__":
    main()
