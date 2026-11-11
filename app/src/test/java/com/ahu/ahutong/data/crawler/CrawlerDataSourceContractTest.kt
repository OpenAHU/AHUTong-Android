package com.ahu.ahutong.data.crawler

import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.data.crawler.api.adwmh.AdwmhApi
import com.ahu.ahutong.data.crawler.api.adwmh.createAdwmhApi
import com.ahu.ahutong.data.crawler.api.jwxt.JwxtApi
import com.ahu.ahutong.data.crawler.api.jwxt.createJwxtApi
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundPublishRequest
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 网关的响应级契约测试（P2.4）。
 *
 * 用固定 fixture 驱动 [CrawlerDataSource]，把"上游返回什么 → 领域结果是什么"钉住。
 * 考试页有两套上游格式（服务端渲染表格 + 旧 JS 变量），且校方改版频繁，
 * 这类解析必须由测试固定，而不是依赖人工回归。
 *
 * 覆盖范围说明：Rust SDK 网关（`SdkDataSource`）需要 native 库与 loopback 服务，
 * 无法在 JVM 单测中运行；它与本网关的等价性属于 instrumentation 测试（见计划文档）。
 */
class CrawlerDataSourceContractTest {

    private var examProfiles = emptyList<com.ahu.ahutong.data.model.GradeStudentProfile>()

    private lateinit var server: MockWebServer

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    /** 只注入协议客户端：测试关心解析，不装配 Cookie / 会话续期。 */
    private fun gateway(): CrawlerDataSource {
        val client = OkHttpClient()
        val baseUrl = server.url("/").toString()
        return CrawlerDataSource(
            jwxt = createJwxtApi(client, baseUrl),
            adwmh = createAdwmhApi(client, baseUrl),
            saveExamProfiles = { examProfiles = it }
        )
    }

    private fun enqueueHtml(body: String) {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody(body)
        )
    }

    @Test
    fun `server rendered exam table becomes exams`() = runBlocking {
        enqueueHtml(EXAM_TABLE_HTML)

        val exams = gateway().getExamInfo("2021", "张三").valueOrNull()

        assertNotNull(exams)
        assertEquals(1, exams.size)
        val exam = exams.first()
        assertEquals("高等数学(期末考试)", exam.course)
        assertEquals("2026-01-10 09:00~11:00", exam.time)
        assertEquals("A-101", exam.seatNum)
        assertEquals("磬苑校区-博学楼-A101", exam.location)
        assertEquals(false, exam.finished)
    }

    @Test
    fun `legacy exam payload is still parsed`() = runBlocking {
        enqueueHtml(LEGACY_EXAM_HTML)

        val exams = gateway().getExamInfo("2021", "张三").valueOrNull()

        assertNotNull(exams)
        assertEquals(1, exams.size)
        assertEquals("大学物理(期中)", exams.first().course)
        assertEquals("B-202", exams.first().seatNum)
        assertEquals("龙河校区-文西楼201", exams.first().location)
    }

    @Test
    fun `page without exams is a successful empty result`() = runBlocking {
        enqueueHtml("<html><body>暂无考试安排</body></html>")

        val result = gateway().getExamInfo("2021", "张三")

        assertTrue(result.isSuccess)
        assertEquals(emptyList(), result.valueOrNull())
    }

    @Test
    fun `upstream failure becomes a server error`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

        val result = gateway().getExamInfo("2021", "张三")

        assertEquals(AhuError.Server(-1, "请求失败"), result.errorOrNull())
    }

    private val profileSelection = """
        <html><body>
        <div class="student-panel-block"><dd>主修</dd><dd>学院甲</dd><dd>专业甲</dd>
        <button onclick="myFunction(this)" value="101">查看考试</button></div>
        <div class="student-panel-block"><dd>微专业</dd><dd>学院乙</dd><dd>专业乙</dd>
        <button onclick="myFunction(this)" value="202">查看考试</button></div>
        </body></html>
    """.trimIndent()

    @Test
    fun `all profiles are fetched and identical exams remain in separate groups`() = runBlocking {
        enqueueHtml(profileSelection)
        enqueueHtml(EXAM_TABLE_HTML)
        enqueueHtml(EXAM_TABLE_HTML)
        val exams = assertNotNull(gateway().getExamInfo("2021", "张三").valueOrNull())
        assertEquals(listOf("101", "202"), exams.map { it.studentProfile.id })
        assertEquals(listOf("专业甲 (主修)", "专业乙 (微专业)"), examProfiles.map { it.displayName })
        assertEquals("/student/for-std/exam-arrange", server.takeRequest().path)
        assertEquals("/student/for-std/exam-arrange/info/101", server.takeRequest().path)
        assertEquals("/student/for-std/exam-arrange/info/202", server.takeRequest().path)
    }

    @Test
    fun `empty profiles are retained alongside populated profiles`() = runBlocking {
        enqueueHtml(profileSelection)
        enqueueHtml("<table id='exams'><tbody></tbody></table>")
        enqueueHtml(EXAM_TABLE_HTML)
        val exams = assertNotNull(gateway().getExamInfo("2021", "张三").valueOrNull())
        assertEquals("202", exams.single().studentProfile.id)
        assertEquals(2, examProfiles.size)
    }

    @Test
    fun `profile failure does not publish an incomplete snapshot`() = runBlocking {
        enqueueHtml(profileSelection)
        enqueueHtml(EXAM_TABLE_HTML)
        server.enqueue(MockResponse().setResponseCode(500))
        assertTrue(gateway().getExamInfo("2021", "张三").isFailure)
        assertTrue(examProfiles.isEmpty())
    }

    @Test
    fun `expired detail session is not treated as empty exams`() = runBlocking {
        enqueueHtml(profileSelection)
        enqueueHtml("<form><input type='password'></form>")
        assertTrue(gateway().getExamInfo("2021", "张三").isFailure)
        assertTrue(examProfiles.isEmpty())
    }

    /** 安大智慧的列表接口是 JSON 直出，字段与分页结构与页面一一对应，改版即断。 */
    @Test
    fun `lost found list keeps the upstream paging shape`() = runBlocking {
        enqueueJson(LOST_FOUND_LIST_JSON)

        val page = assertNotNull(
            gateway().getLostFoundList(pageNo = 1, pageSize = 20, state = 1).valueOrNull()
        )
        assertEquals(0, page.code)
        assertEquals(37, page.data.total)
        assertEquals(1, page.data.pageNum)
        val item = page.data.list.single()
        assertEquals("1001", item.id)
        assertEquals("捡到一张校园卡", item.title)
        assertEquals("磬苑校区", item.campusName)
        assertEquals(1, item.state)
        assertEquals(emptyList(), item.imgs)
    }

    /**
     * 写接口的 {code,msg,object} 包装：码值约定决定用户看到的是"重新登录"还是"服务器开小差"，
     * 因此四个分支都要钉住，不能只在成功路径上测。
     */
    @Test
    fun `lost found write maps the upstream wrapper to a domain result`() = runBlocking {
        enqueueJson("""{"code":0,"msg":"","data":{"id":"1001"}}""")
        assertTrue(gateway().publishLostFound(publishRequest()).isSuccess)

        enqueueJson("""{"code":401,"msg":"登录已失效","data":null}""")
        assertEquals(
            AhuError.Unauthorized("登录已失效"),
            gateway().publishLostFound(publishRequest()).errorOrNull()
        )

        // code=0 但没有数据：上游改了结构，属于协议变化而不是成功。
        enqueueJson("""{"code":0,"msg":"","data":null}""")
        assertEquals(
            AhuError.ProtocolChanged("响应缺少数据"),
            gateway().publishLostFound(publishRequest()).errorOrNull()
        )

        enqueueJson("""{"code":500,"msg":"服务器开小差","data":null}""")
        assertEquals(
            AhuError.Server(500, "服务器开小差"),
            gateway().publishLostFound(publishRequest()).errorOrNull()
        )
    }

    private fun publishRequest() = LostFoundPublishRequest(
        linkman = "张同学",
        phone = "13800000000",
        typeid = "1",
        campusid = "1",
        title = "捡到一张校园卡",
        state = "1"
    )

    private fun enqueueJson(body: String) {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(body)
        )
    }

    private companion object {
        val LOST_FOUND_LIST_JSON = """
            {
              "code": 0,
              "msg": "",
              "object": {
                "pageNum": 1,
                "pageSize": 20,
                "size": 1,
                "startRow": 1,
                "endRow": 1,
                "total": 37,
                "pages": 2,
                "list": [
                  {
                    "id": "1001",
                    "title": "捡到一张校园卡",
                    "phone": "13800000000",
                    "linkman": "张同学",
                    "createuser": "20210001",
                    "createtime": "2026-09-01 10:00:00",
                    "state": 1,
                    "audituser": null,
                    "auditresult": null,
                    "typeid": "1",
                    "campusid": "1",
                    "num1": null,
                    "num2": null,
                    "campusName": "磬苑校区",
                    "audituserName": null,
                    "imgs": [],
                    "pubuser": null,
                    "lostType": null
                  }
                ]
              }
            }
        """.trimIndent()

        val EXAM_TABLE_HTML = """
            <html><body>
            <script>var studentExamList = [{"id":"123","seatNo":"A-101"}];</script>
            <table>
              <tr data-finished="false">
                <td>
                  <span id="seat-123">考场</span>
                  <span>磬苑校区</span><span>博学楼</span><span>A101</span>
                </td>
                <td>
                  <div class="time">2026-01-10 09:00~11:00</div>
                  <span style="font-weight:bold">高等数学</span>
                  <span class="tag-span">期末考试</span>
                </td>
              </tr>
            </table>
            </body></html>
        """.trimIndent()

        val LEGACY_EXAM_HTML = """
            <html><body>
            <script>
            studentExamInfoVms = [{"course":{"nameZh":"大学物理"},"examType":{"nameZh":"期中"},"examTime":"2026-03-01 14:00~16:00","seatNo":"B-202","requiredCampus":{"nameZh":"龙河校区"},"room":"文西楼201","finished":false}];
            </script>
            </body></html>
        """.trimIndent()
    }
}
