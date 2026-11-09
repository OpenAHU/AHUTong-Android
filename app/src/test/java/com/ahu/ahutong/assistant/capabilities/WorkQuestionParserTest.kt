package com.ahu.ahutong.assistant.capabilities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkQuestionParserTest {

    @Test
    fun `parses answer page stems images and options`() {
        val questions = WorkQuestionParser.parse(HTML)
        assertEquals(3, questions.size)

        val first = questions[0]
        assertEquals(1, first.number)
        assertEquals("简答题", first.type)
        assertEquals("20分", first.score)
        assertTrue(first.stem.contains("用小规模门电路实现函数"))
        assertTrue(first.stem.contains("[公式]"))
        assertTrue(first.hasImages)
        assertEquals(listOf("/ananas/latex/p/3937309"), first.imageUrls)

        val choice = questions[1]
        assertEquals(2, choice.number)
        assertEquals("单选题", choice.type)
        assertEquals("2分", choice.score)
        assertEquals("下列哪个是组合逻辑电路？", choice.stem)
        assertEquals(
            listOf("A、编码器", "B、触发器", "C、寄存器", "D、计数器"),
            choice.options,
        )
        assertFalse(choice.hasImages)

        val viewStyle = questions[2]
        assertEquals("判断题", viewStyle.type)
        assertEquals("逻辑函数化简后唯一。", viewStyle.stem)
        assertFalse(viewStyle.hasImages)
        assertTrue(viewStyle.imageUrls.isEmpty())
    }

    @Test
    fun `never reads answer or knowledge point blocks`() {
        val text = WorkQuestionParser.parse(HTML)
            .joinToString("\n") { it.stem + "\n" + it.options.joinToString("\n") }
        assertFalse(text.contains("正确答案"))
        assertFalse(text.contains("知识点"))
        assertFalse(text.contains("官方解析"))
    }

    private val HTML = """
        <html><body>
          <div class="padBom50 questionLi fontLabel singleQuesId" typename="简答题" id="question214712955">
            <h3 class="mark_name colorDeep">
              1.
              <span class="colorShallow">(简答题, 20分)</span>
              <p>4-3</p>
              <p>用小规模门电路实现函数</p>
              <p><img class="ans-formula-moudle" src="/ananas/latex/p/3937309"></p>
              <p>要求用以下 6 种方案实现：<br></p>
            </h3>
            <div class="stem_answer">
              <div class="edui-editor"><div class="edui-toolbar">清除格式</div></div>
            </div>
          </div>
          <div class="padBom50 questionLi fontLabel singleQuesId" typename="单选题" id="question2">
            <h3 class="mark_name colorDeep">
              2.
              <span class="colorShallow">(单选题, 2分)</span>
              <p>下列哪个是组合逻辑电路？</p>
            </h3>
            <ul class="zy_ulTop">
              <li><label><input type="radio" name="q2">A、编码器</label></li>
              <li><label><input type="radio" name="q2">B、触发器</label></li>
              <li><label><input type="radio" name="q2">C、寄存器</label></li>
              <li><label><input type="radio" name="q2">D、计数器</label></li>
            </ul>
          </div>
          <div class="marBom60 questionLi singleQuesId" id="question3">
            <h3 class="mark_name colorDeep">
              3.
              <span class="colorShallow">(判断题, 2分)</span>
              <span class="qtContent workTextWrap"><p>逻辑函数化简后唯一。</p></span>
            </h3>
            <div class="mark_answer">
              <div class="mark_answer_key">
                <dl class="mark_fill colorGreen">
                  <dt><i>正确答案</i></dt>
                  <dd class="rightAnswerContent">错误</dd>
                </dl>
                <img src="https://p.ananas.chaoxing.com/star3/origin/answer.png">
              </div>
              <div class="aiArea">官方解析</div>
            </div>
            <div class="knowledgePointBx">
              <span>知识点</span>
              <ul class="knowledgeList"><li>组合逻辑</li></ul>
            </div>
          </div>
        </body></html>
    """.trimIndent()
}
