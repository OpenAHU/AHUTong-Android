package com.ahu.ahutong.data.laundry

import org.junit.Assert.assertEquals
import org.junit.Test

class LaundryParkMatcherTest {

    private val parks = listOf(
        "蕙园", "梅园", "桔园", "桂园", "桃园", "李园", "榴园", "竹园",
        "松园", "枫园", "槐园", "枣园", "杏园", "11号楼", "南7号楼",
        "兰园A栋", "兰园B栋", "兰园C栋", "兰园D栋"
    )

    @Test
    fun `楼栋名带园区直接命中`() {
        assertEquals(LaundryParkMatch.Hit(6), LaundryParkMatcher.match("榴园2号楼", parks))
        assertEquals(LaundryParkMatch.Hit(8), LaundryParkMatcher.match("松园12号楼", parks))
        assertEquals(LaundryParkMatch.Hit(3), LaundryParkMatcher.match("桂园19号", parks))
    }

    @Test
    fun `整栋园区按名字命中`() {
        assertEquals(LaundryParkMatch.Hit(13), LaundryParkMatcher.match("11号楼", parks))
        assertEquals(LaundryParkMatch.Hit(14), LaundryParkMatcher.match("南7号楼", parks))
    }

    @Test
    fun `兰园号楼给候选不猜`() {
        val matched = LaundryParkMatcher.match("兰园2号楼", parks)
        assertEquals(LaundryParkMatch.Multiple(listOf(15, 16, 17, 18)), matched)
    }

    @Test
    fun `兰园带栋号精确命中`() {
        assertEquals(LaundryParkMatch.Hit(16), LaundryParkMatcher.match("兰园B栋", parks))
        assertEquals(LaundryParkMatch.Hit(16), LaundryParkMatcher.match("兰园B栋宿舍", parks))
    }

    @Test
    fun `校区前缀与全角数字不影响匹配`() {
        assertEquals(LaundryParkMatch.Hit(6), LaundryParkMatcher.match("磬苑校区榴园2号楼", parks))
        assertEquals(LaundryParkMatch.Hit(13), LaundryParkMatcher.match("１１号楼", parks))
    }

    @Test
    fun `没有住宿信息或认不出园区返回 None`() {
        assertEquals(LaundryParkMatch.None, LaundryParkMatcher.match(null, parks))
        assertEquals(LaundryParkMatch.None, LaundryParkMatcher.match("", parks))
        assertEquals(LaundryParkMatch.None, LaundryParkMatcher.match("红楼", parks))
        assertEquals(LaundryParkMatch.None, LaundryParkMatcher.match("榴园2号楼", emptyList()))
    }
}
