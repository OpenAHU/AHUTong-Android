package com.ahu.ahutong.data.crawler.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StudentInfoHtmlParserTest {

    private val html = """
        <html><body>
        <div class="base-header-wrapper">
          <div class="base-header">
            <div class="base-header-left"><img alt="photo"></div>
            <div class="base-header-right">
              <div><span class="studentName">测试同学</span> TEST Student</div>
              <div class="darkGrey">
                <div>男 &nbsp;|&nbsp; 汉族<span>2007-03-04</span></div>
              </div>
            </div>
          </div>
          <div>
            <div>学号：P000000001<br>政治面貌：共青团员<br>证件类型：居民身份证<br>证件号码：123***********456</div>
          </div>
        </div>
        <div id="baseInfo">
          <h4>基本信息</h4>
          <table><tbody>
            <tr><td>学号</td><td>P000000001</td><td>培养类型</td><td>主修</td><td>管理部门</td><td>测试学院</td></tr>
            <tr><td>年级</td><td>2025</td><td>学历层次</td><td>本科</td><td>学生类别</td><td>普通学生</td></tr>
            <tr><td>专业院系</td><td>测试学院</td><td>专业</td><td>测试专业</td><td>专业方向</td><td>--</td></tr>
            <tr><td>行政班</td><td>25级测试1班</td><td>培养方案</td><td>测试培养方案</td><td>学制</td><td>4.0</td></tr>
            <tr><td>学籍状态</td><td>在校</td><td>是否在校</td><td>在校</td><td>校区</td><td>测试校区</td></tr>
            <tr><td>辅导员</td><td colspan="5">测试老师(10001 测试学院)</td></tr>
            <tr><td>入学日期</td><td>2025-09-10</td><td>备注</td><td>--</td></tr>
          </tbody></table>
        </div>
        <div id="recruitInfo" style="display:none">
          <table><tbody>
            <tr><td>录取年份</td><td>2025</td><td>录取批次</td><td>普通本科批</td></tr>
            <tr><td>科类</td><td>物理类</td><td>生源省市</td><td>安徽省</td></tr>
            <tr><td>毕业中学</td><td>测试中学</td></tr>
          </tbody></table>
        </div>
        <div id="graduateInfo" style="display:none">
          <table><tbody>
            <tr><td>预计毕业日期</td><td>2029-07-01</td><td>实际毕业日期</td><td>--</td></tr>
          </tbody></table>
        </div>
        <div id="contactInfo" style="display:none">
          <table><tbody>
            <tr><td>手机</td><td>139*****080</td><td>邮箱</td><td>test@example.com</td></tr>
          </tbody></table>
          <table><tbody>
            <tr><td>手机</td><td>--</td><td>邮箱</td><td>--</td></tr>
          </tbody></table>
        </div>
        </body></html>
    """.trimIndent()

    @Test
    fun `parses academic status and omits sensitive id fields`() {
        val info = requireNotNull(StudentInfoHtmlParser.parse(html))

        assertEquals("测试同学", info.name)
        assertEquals("TEST Student", info.englishName)
        assertEquals("男", info.gender)
        assertEquals("汉族", info.ethnicity)
        assertEquals("2007-03-04", info.birthDate)
        assertEquals("P000000001", info.studentNumber)
        assertEquals("共青团员", info.politicalStatus)
        assertEquals("测试学院", info.department)
        assertEquals("测试专业", info.major)
        assertEquals("25级测试1班", info.adminClass)
        assertEquals("在校", info.enrollmentStatus)
        assertEquals("2025-09-10", info.admissionDate)
        assertEquals("2029-07-01", info.expectedGraduationDate)
        assertEquals("2025", info.admissionYear)
        assertEquals("普通本科批", info.admissionBatch)
        assertEquals("安徽省", info.admissionProvince)
        assertEquals("测试中学", info.highSchool)
        assertEquals("139*****080", info.phoneMasked)
        assertEquals("test@example.com", info.email)
    }

    @Test
    fun `missing base section is treated as not logged in or protocol change`() {
        assertNull(StudentInfoHtmlParser.parse("<html><body><div>请登录</div></body></html>"))
    }
}
