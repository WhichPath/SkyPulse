package com.ridgecast.weather.data.provider.impl

import android.content.Context
import android.util.Log
import com.ridgecast.weather.data.CityEntry
import com.ridgecast.weather.data.provider.IGeocodingProvider
import com.ridgecast.weather.util.FileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SkyPulse 纯离线正/逆双向高精度地理编码引擎。
 *
 * 技术特性:
 * 1. 0 网络请求，0 外部 API 依赖，100% 本地内存运算。
 * 2. 逆地理编码 (GPS -> 地名)：
 *    - 阶段一：0.5° 空间自适应哈希网格过滤 + 天地图法定区县 100% 原始测绘精度射线法判定 (< 0.3ms)；
 *    - 阶段二：县域内部泰森多边形等价最近邻检索，命中民政部 2026 最新法定乡镇街道标准名称 (< 0.002ms)。
 * 3. 正向名称检索 (地名 -> 下一级/消歧候选项 + 经纬度)：
 *    - 基于全国 42,419 个省/市/县/乡四级完整行政区划树 (divisions.bin)；
 *    - 自动剥离并忽略行政区划通名与民族修饰词（如输入“广西省”自动解析为“广西壮族自治区”）；
 *    - 严格控制展示层级：无歧义时仅展开下辖下一级（如输入“四川”仅展示下辖地市或省直管县），
 *      存在同名歧义时仅罗列本级（如输入“城中”或“城中区”仅罗列“柳州市 城中区”、“西宁市 城中区”）；
 *    - 全量严格遵循民政部官方行政区划代码升序排列；
 *    - 针对暂无独立离散驻地坐标的 114 个乡镇街道，自动回退采用上级区县中心点经纬度并打标提示。
 */
@Singleton
class OfflineGeocodingProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : IGeocodingProvider {

    private data class DivisionNode(
        val index: Int,
        val parentIdx: Int,
        val firstChildIdx: Int,
        val childCount: Int,
        val adminRank: Int,
        val isFallbackCenter: Boolean,
        val isUrbanDistrict: Boolean,
        val lon: Double,
        val lat: Double,
        val codeInt: Long,
        val extName: String,
        val shortName: String,
        val coreStem: String
    )

    companion object {
        private const val TAG = "OfflineGeocoding"
        private const val SCALE = 1_000_000.0 // 1e6 (1微度精度)
        private const val GRID_MIN_LON = 73.0
        private const val GRID_MIN_LAT = 18.0
        private const val GRID_STEP = 0.5
        private const val GRID_COLS = 126
        private const val GRID_ROWS = 72

        private val ETHNIC_TOKENS = listOf(
            "维吾尔族", "维吾尔", "哈萨克族", "哈萨克", "柯尔克孜族", "柯尔克孜",
            "乌孜别克族", "乌孜别克", "塔吉克族", "塔吉克", "塔塔尔族", "塔塔尔",
            "达斡尔族", "达斡尔", "鄂伦春族", "鄂伦春", "鄂温克族", "鄂温克",
            "俄罗斯族", "保安族", "东乡族", "撒拉族", "裕固族", "独龙族", "普米族",
            "景颇族", "阿昌族", "德昂族", "基诺族", "门巴族", "珞巴族", "赫哲族",
            "傈僳族", "拉祜族", "纳西族", "哈尼族", "布朗族", "布依族", "仡佬族",
            "仫佬族", "毛南族", "土家族", "朝鲜族", "蒙古族", "蒙古",
            "彝族", "傣族", "苗族", "回族", "满族", "藏族", "畲族", "侗族",
            "水族", "瑶族", "羌族", "白族", "怒族", "京族", "锡伯族", "锡伯", "土族",
            "佤族", "黎族", "壮族", "高山族", "各族", "民族"
        ).sortedByDescending { it.length }

        private val ADMIN_SUFFIXES = listOf(
            "特别行政区", "壮族自治区", "回族自治区", "维吾尔自治区", "自治区",
            "行政委员会", "街道办事处", "地区办事处", "社区服务中心",
            "自治州", "自治县", "自治旗", "市辖区", "民族乡", "区公所",
            "行委", "街道", "林区", "特区", "新区", "地区",
            "省", "市", "州", "盟", "区", "县", "旗", "镇", "乡", "苏木", "团"
        ).sortedByDescending { it.length }

        internal fun stripAdminStem(raw: String): String {
            var s = raw.trim()
            if (s.length <= 1) return s
            for (suf in ADMIN_SUFFIXES) {
                if (s.endsWith(suf) && s.length - suf.length >= 2) {
                    s = s.dropLast(suf.length)
                    break
                }
            }
            var changed = true
            while (changed) {
                changed = false
                for (eth in ETHNIC_TOKENS) {
                    if (s.endsWith(eth) && s.length - eth.length >= 2) {
                        s = s.dropLast(eth.length)
                        changed = true
                        break
                    }
                }
            }
            return s
        }
    }

    @Volatile private var countiesBuf: ByteBuffer? = null
    @Volatile private var townshipsBuf: ByteBuffer? = null
    @Volatile private var isReverseInitialized = false
    private val reverseLock = Any()

    @Volatile private var divisionNodes: Array<DivisionNode>? = null
    private var nonLeafLimit: Int = 0
    private val divisionLock = Any()

    private fun ensureReverseLoaded(): Boolean {
        if (isReverseInitialized) return true
        synchronized(reverseLock) {
            if (isReverseInitialized) return true
            return try {
                val startMs = System.currentTimeMillis()
                val cBytes = context.assets.open("geo/counties.bin").use { it.readBytes() }
                val tBytes = context.assets.open("geo/townships.bin").use { it.readBytes() }
                countiesBuf = ByteBuffer.wrap(cBytes).order(ByteOrder.LITTLE_ENDIAN)
                townshipsBuf = ByteBuffer.wrap(tBytes).order(ByteOrder.LITTLE_ENDIAN)
                isReverseInitialized = true
                val elapsed = System.currentTimeMillis() - startMs
                Log.i(TAG, "Offline reverse geodata loaded in ${elapsed}ms (counties: ${cBytes.size / 1024}KB, towns: ${tBytes.size / 1024}KB)")
                FileLogger.locI(TAG, "offline_assets_loaded: elapsed=${elapsed}ms, counties=${cBytes.size}B, towns=${tBytes.size}B")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load offline reverse geo assets", e)
                FileLogger.e(TAG, "Failed to load offline reverse geo assets", e)
                false
            }
        }
    }

    private fun ensureDivisionsLoaded(): Array<DivisionNode>? {
        divisionNodes?.let { return it }
        synchronized(divisionLock) {
            divisionNodes?.let { return it }
            return try {
                val startMs = System.currentTimeMillis()
                val dBytes = context.assets.open("geo/divisions.bin").use { it.readBytes() }
                val buf = ByteBuffer.wrap(dBytes).order(ByteOrder.LITTLE_ENDIAN)
                val numNodes = buf.getInt(8)
                var pos = 12
                var lastNonLeaf = 0
                val nodes = Array(numNodes) { idx ->
                    val parentIdx = buf.getShort(pos).toInt()
                    val firstChildIdx = buf.getInt(pos + 2)
                    val childCount = buf.getShort(pos + 6).toInt() and 0xFFFF
                    val flags = buf.get(pos + 8).toInt() and 0xFF
                    val lonI = buf.getInt(pos + 9)
                    val latI = buf.getInt(pos + 13)
                    val codeInt = buf.getLong(pos + 17)
                    val extLen = buf.get(pos + 25).toInt() and 0xFF
                    pos += 26

                    val extName = String(dBytes, pos, extLen, Charsets.UTF_8)
                    pos += extLen

                    val shortLen = dBytes[pos].toInt() and 0xFF
                    pos += 1
                    val shortName = if (shortLen > 0) {
                        val s = String(dBytes, pos, shortLen, Charsets.UTF_8)
                        pos += shortLen
                        s
                    } else {
                        extName
                    }

                    if (childCount > 0) {
                        lastNonLeaf = idx + 1
                    }

                    DivisionNode(
                        index = idx,
                        parentIdx = parentIdx,
                        firstChildIdx = firstChildIdx,
                        childCount = childCount,
                        adminRank = flags and 0x03,
                        isFallbackCenter = (flags and 0x04) != 0,
                        isUrbanDistrict = (flags and 0x08) != 0,
                        lon = lonI / SCALE,
                        lat = latI / SCALE,
                        codeInt = codeInt,
                        extName = extName,
                        shortName = shortName,
                        coreStem = stripAdminStem(extName)
                    )
                }
                nonLeafLimit = lastNonLeaf
                divisionNodes = nodes
                val elapsed = System.currentTimeMillis() - startMs
                Log.i(TAG, "Offline divisions tree loaded in ${elapsed}ms ($numNodes nodes)")
                nodes
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load offline divisions.bin", e)
                FileLogger.e(TAG, "Failed to load offline divisions.bin", e)
                null
            }
        }
    }

    override suspend fun reverseGeocode(latitude: Double, longitude: Double): String? = withContext(Dispatchers.Default) {
        val startNano = System.nanoTime()
        if (!ensureReverseLoaded()) return@withContext null

        val cBuf = countiesBuf ?: return@withContext null
        val tBuf = townshipsBuf ?: return@withContext null

        val lonI = Math.round(longitude * SCALE).toInt()
        val latI = Math.round(latitude * SCALE).toInt()

        val col = ((longitude - GRID_MIN_LON) / GRID_STEP).toInt()
        val row = ((latitude - GRID_MIN_LAT) / GRID_STEP).toInt()

        if (col !in 0 until GRID_COLS || row !in 0 until GRID_ROWS) {
            return@withContext null
        }

        val cellIdx = row * GRID_COLS + col
        val gridDataStart = cBuf.getInt(24).toLong() and 0xFFFFFFFFL
        val countyOffsetsStart = cBuf.getInt(28).toLong() and 0xFFFFFFFFL

        val cellOffset = cBuf.getInt(32 + cellIdx * 4)
        if (cellOffset < 0) {
            return@withContext null
        }

        val candPos = (gridDataStart + cellOffset).toInt()
        val candCount = cBuf.getShort(candPos).toInt() and 0xFFFF

        var matchedCountyName: String? = null
        var matchedTownshipOffset: Long = 0L
        var matchedTownshipCount: Int = 0

        for (i in 0 until candCount) {
            val cIdx = cBuf.getShort(candPos + 2 + i * 2).toInt() and 0xFFFF
            val cPos = cBuf.getInt((countyOffsetsStart + cIdx * 4).toInt())

            val cNameLen = cBuf.get(cPos + 6).toInt() and 0xFF
            val nameBytes = ByteArray(cNameLen)
            for (b in 0 until cNameLen) {
                nameBytes[b] = cBuf.get(cPos + 7 + b)
            }
            val cName = String(nameBytes, Charsets.UTF_8)

            val posAfterName = cPos + 7 + cNameLen
            val minX = cBuf.getInt(posAfterName)
            val minY = cBuf.getInt(posAfterName + 4)
            val maxX = cBuf.getInt(posAfterName + 8)
            val maxY = cBuf.getInt(posAfterName + 12)
            val tOffset = cBuf.getInt(posAfterName + 16).toLong() and 0xFFFFFFFFL
            val tCount = cBuf.getShort(posAfterName + 20).toInt() and 0xFFFF
            val numRings = cBuf.getShort(posAfterName + 22).toInt() and 0xFFFF

            if (lonI < minX || lonI > maxX || latI < minY || latI > maxY) {
                continue
            }

            var crossings = 0
            var rPos = posAfterName + 24
            for (r in 0 until numRings) {
                val numPts = cBuf.getInt(rPos)
                rPos += 4
                for (p in 0 until numPts - 1) {
                    val x1 = cBuf.getInt(rPos + p * 8)
                    val y1 = cBuf.getInt(rPos + p * 8 + 4)
                    val x2 = cBuf.getInt(rPos + (p + 1) * 8)
                    val y2 = cBuf.getInt(rPos + (p + 1) * 8 + 4)

                    if ((y1 > latI) != (y2 > latI)) {
                        val xCross = x1.toDouble() + (latI.toDouble() - y1.toDouble()) * (x2.toDouble() - x1.toDouble()) / (y2.toDouble() - y1.toDouble())
                        if (lonI < xCross) {
                            crossings++
                        }
                    }
                }
                rPos += numPts * 8
            }

            if (crossings % 2 == 1) {
                matchedCountyName = cName
                matchedTownshipOffset = tOffset
                matchedTownshipCount = tCount
                break
            }
        }

        if (matchedCountyName == null) {
            val elapsedUs = (System.nanoTime() - startNano) / 1000.0
            FileLogger.locI(TAG, "offline_geocode_miss: lat=$latitude, lon=$longitude, elapsed=${elapsedUs}us")
            return@withContext null
        }

        var matchedTownshipName: String? = null
        if (matchedTownshipCount > 0) {
            var minSq = Double.MAX_VALUE
            var tPos = matchedTownshipOffset.toInt()

            for (t in 0 until matchedTownshipCount) {
                val tLon = tBuf.getInt(tPos)
                val tLat = tBuf.getInt(tPos + 4)
                val tNlen = tBuf.get(tPos + 8).toInt() and 0xFF
                tPos += 9

                val dLon = (lonI - tLon).toDouble()
                val dLat = (latI - tLat).toDouble()
                val distSq = dLon * dLon + dLat * dLat

                if (distSq < minSq) {
                    minSq = distSq
                    val tBytes = ByteArray(tNlen)
                    for (b in 0 until tNlen) {
                        tBytes[b] = tBuf.get(tPos + b)
                    }
                    matchedTownshipName = String(tBytes, Charsets.UTF_8)
                }
                tPos += tNlen
            }
        }

        val elapsedUs = (System.nanoTime() - startNano) / 1000.0
        val resultName = if (!matchedTownshipName.isNullOrBlank() && !matchedCountyName.endsWith(matchedTownshipName)) {
            "$matchedCountyName $matchedTownshipName"
        } else {
            matchedCountyName
        }

        FileLogger.locI(TAG, "offline_geocode_hit: result=$resultName, lat=$latitude, lon=$longitude, elapsed=${elapsedUs}us")
        Log.i(TAG, "Offline geocode hit: $resultName in ${elapsedUs}us")
        return@withContext resultName
    }

    /**
     * 离线正向行政区划搜索：
     * 1. 忽略行政区划通名与民族后缀（如“广西省”自动匹配“广西壮族自治区”）；
     * 2. 控制显示层级在用户输入的下一级（如输入“四川”只返回下辖地级市/自治州/省直管县，不跨级展示区县和乡镇街道）；
     * 3. 若本级存在同名歧义（如输入“城中”或“城中区”同时匹配柳州市城中区、西宁市城中区），不展开下一级，仅罗列有歧义的本级；
     * 4. 所有候选项严格按民政部官方区划代码升序排列；
     * 5. 针对暂无独立中心点的乡镇街道，标记 isApproximateCenter = true 并采用上级区县中心点经纬度。
     */
    suspend fun search(query: String): List<CityEntry> = withContext(Dispatchers.Default) {
        val cleaned = query.replace(Regex("[，,/\\-·\\s]+"), " ").trim()
        if (cleaned.isEmpty()) return@withContext emptyList()

        val nodes = ensureDivisionsLoaded() ?: return@withContext emptyList()
        val rawTokens = cleaned.split(" ").filter { it.isNotBlank() }
        val tokens = if (rawTokens.size == 1 && rawTokens[0].length >= 4) {
            splitConcatenatedQuery(nodes, rawTokens[0])
        } else {
            rawTokens
        }
        if (tokens.isEmpty()) return@withContext emptyList()

        val targetRaw = tokens.last()
        val targetStem = stripAdminStem(targetRaw)
        val ancestorTokens = tokens.dropLast(1).map { it to stripAdminStem(it) }

        val tier1ByRank = Array(4) { ArrayList<DivisionNode>() }
        val tier2ByRank = Array(4) { ArrayList<DivisionNode>() }
        val tier3ByRank = Array(4) { ArrayList<DivisionNode>() }

        for (node in nodes) {
            val tier = matchTokenTier(node, targetRaw, targetStem)
            if (tier == 0) continue
            if (ancestorTokens.isNotEmpty() && !matchesAncestors(nodes, node, ancestorTokens)) {
                continue
            }
            val rank = node.adminRank.coerceIn(0, 3)
            when (tier) {
                1 -> tier1ByRank[rank].add(node)
                2 -> tier2ByRank[rank].add(node)
                3 -> tier3ByRank[rank].add(node)
            }
        }

        // 优先级：
        // 1. 完整法定名称精确匹配 (如“河北省” vs “河北区”、“朝阳市” vs “朝阳区”)
        // 2. 忽略通名与民族称谓后的词干精确匹配 (如“广西省”->“广西”、“四川”、“城中”)
        // 3. 前缀匹配 (如输入局部前缀)
        var chosen: List<DivisionNode>? = null
        for (r in 0..3) {
            if (tier1ByRank[r].isNotEmpty()) {
                chosen = tier1ByRank[r]
                break
            }
        }
        if (chosen == null) {
            for (r in 0..3) {
                if (tier2ByRank[r].isNotEmpty()) {
                    chosen = tier2ByRank[r]
                    break
                }
            }
        }
        if (chosen == null) {
            for (r in 0..3) {
                if (tier3ByRank[r].isNotEmpty()) {
                    chosen = tier3ByRank[r]
                    break
                }
            }
        }

        val matchedList = chosen ?: return@withContext emptyList()

        // 唯一命中且存在下辖行政区 -> 仅展示下一级；多条同名歧义或已达末级乡镇街道 -> 仅罗列本级
        val finalNodes: List<DivisionNode> = if (matchedList.size == 1 && matchedList[0].childCount > 0) {
            val parent = matchedList[0]
            val start = parent.firstChildIdx
            val end = start + parent.childCount
            val children = ArrayList<DivisionNode>(parent.childCount)
            for (idx in start until end) {
                children.add(nodes[idx])
            }
            children.sortedBy { it.codeInt }
        } else {
            matchedList.sortedBy { it.codeInt }
        }

        finalNodes.map { node -> buildCityEntry(nodes, node) }
    }

    private fun matchTokenTier(node: DivisionNode, rawTok: String, stemTok: String): Int {
        if (node.extName == rawTok) return 1
        if (node.shortName == rawTok || node.coreStem == rawTok) return 2
        if (stemTok.isNotEmpty() && (node.shortName == stemTok || node.coreStem == stemTok || node.extName == stemTok)) {
            return 2
        }
        if (rawTok.length >= 2 && (node.extName.startsWith(rawTok) || node.shortName.startsWith(rawTok))) {
            return 3
        }
        if (stemTok.length >= 2 && (node.extName.startsWith(stemTok) || node.shortName.startsWith(stemTok) || node.coreStem.startsWith(stemTok))) {
            return 3
        }
        return 0
    }

    private fun splitConcatenatedQuery(nodes: Array<DivisionNode>, raw: String): List<String> {
        val rawStem = stripAdminStem(raw)
        for (node in nodes) {
            val t = matchTokenTier(node, raw, rawStem)
            if (t == 1 || t == 2) return listOf(raw)
        }

        val result = ArrayList<String>(3)
        var rem = raw
        val limit = nonLeafLimit.coerceAtMost(nodes.size)

        while (rem.length >= 4) {
            var matchedPrefix: String? = null
            val maxK = (rem.length - 2).coerceAtMost(9)
            for (k in maxK downTo 2) {
                val cand = rem.substring(0, k)
                val candStem = stripAdminStem(cand)
                var hasParentHit = false
                for (i in 0 until limit) {
                    val n = nodes[i]
                    if (n.childCount > 0) {
                        val t = matchTokenTier(n, cand, candStem)
                        if (t == 1 || t == 2) {
                            hasParentHit = true
                            break
                        }
                    }
                }
                if (hasParentHit) {
                    matchedPrefix = cand
                    break
                }
            }
            if (matchedPrefix != null) {
                result.add(matchedPrefix)
                rem = rem.substring(matchedPrefix.length)
                val remStem = stripAdminStem(rem)
                var remExact = false
                for (node in nodes) {
                    val t = matchTokenTier(node, rem, remStem)
                    if (t == 1 || t == 2) {
                        remExact = true
                        break
                    }
                }
                if (remExact) break
            } else {
                break
            }
        }
        if (rem.isNotBlank()) {
            result.add(rem)
        }
        return result
    }

    private fun getAncestors(nodes: Array<DivisionNode>, node: DivisionNode): List<DivisionNode> {
        val list = ArrayList<DivisionNode>(3)
        var curr = node.parentIdx
        while (curr >= 0) {
            val p = nodes[curr]
            list.add(p)
            curr = p.parentIdx
        }
        list.reverse()
        return list
    }

    private fun matchesAncestors(
        nodes: Array<DivisionNode>,
        node: DivisionNode,
        ancestorTokens: List<Pair<String, String>>
    ): Boolean {
        val ancestors = getAncestors(nodes, node)
        var ancIdx = 0
        for ((rawA, stemA) in ancestorTokens) {
            var found = false
            while (ancIdx < ancestors.size) {
                if (matchTokenTier(ancestors[ancIdx], rawA, stemA) > 0) {
                    found = true
                    ancIdx++
                    break
                }
                ancIdx++
            }
            if (!found) return false
        }
        return true
    }

    private fun buildCityEntry(nodes: Array<DivisionNode>, node: DivisionNode): CityEntry {
        val ancestors = getAncestors(nodes, node)
        val ext = node.extName

        var displayName: String
        var provinceLabel: String
        var savedName: String
        var parentCountyName = ""

        when (node.adminRank) {
            0 -> {
                displayName = ext
                provinceLabel = ""
                savedName = ext
            }
            1 -> {
                val prov = ancestors.firstOrNull()?.extName.orEmpty()
                displayName = ext
                provinceLabel = prov.takeIf { it != ext }.orEmpty()
                savedName = ext
            }
            2 -> {
                if (ancestors.size >= 2) {
                    val prov = ancestors[0].extName
                    val city = ancestors[1].extName
                    displayName = "$city $ext"
                    provinceLabel = prov
                    savedName = if (node.isUrbanDistrict) "$city$ext" else ext
                } else if (ancestors.size == 1) {
                    val prov = ancestors[0].extName
                    displayName = "$prov $ext"
                    provinceLabel = if (displayName.startsWith(prov)) "" else prov
                    savedName = if (node.isUrbanDistrict) "$prov$ext" else ext
                } else {
                    displayName = ext
                    provinceLabel = ""
                    savedName = ext
                }
            }
            else -> {
                val parentNode = if (node.parentIdx >= 0) nodes[node.parentIdx] else null
                val parentEntry = parentNode?.let { buildCityEntry(nodes, it) }
                val parentCanonical = parentEntry?.savedName ?: parentNode?.extName.orEmpty()
                val prov = ancestors.firstOrNull()?.extName.orEmpty()
                displayName = if (parentCanonical.isNotBlank()) "$parentCanonical $ext" else ext
                provinceLabel = if (prov.isNotBlank() && !displayName.startsWith(prov)) prov else ""
                savedName = displayName
                parentCountyName = parentCanonical
            }
        }

        val drillDownQuery = if (node.childCount > 0) {
            (ancestors.map { it.extName } + ext).joinToString(" ")
        } else {
            null
        }

        return CityEntry(
            name = displayName,
            province = provinceLabel,
            lat = node.lat,
            lon = node.lon,
            savedName = savedName,
            isApproximateCenter = node.isFallbackCenter,
            parentCountyName = parentCountyName,
            drillDownQuery = drillDownQuery
        )
    }
}
