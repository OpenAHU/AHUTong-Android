package com.ahu.ahutong.data.mail

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class StudentMailBodyTest {
    private fun detail(text: String = "", html: String = "") = MailDetail(
        "synthetic-mid", "Example", emptyList(), emptyList(), emptyList(), text, html, emptyList()
    )

    @Test fun repliesQuoteReadableHtmlInsteadOfTemplateMarkupOrStyles() {
        val result = StudentMailBody.replyText(detail(text = "<mj-raw><!-- template --></mj-raw>", html = """
            <html><head><style>.banner{color:blue}</style></head><body>
            <mj-raw><!-- template --></mj-raw><h2>Hello &amp; welcome</h2><p>First paragraph.</p><p>Second paragraph.<br>Next line.</p>
            <script>mustNotAppear()</script></body></html>
        """))
        assertTrue(result.contains("Hello & welcome"))
        assertTrue(result.contains("First paragraph.\n"))
        assertTrue(result.contains("Second paragraph.\nNext line."))
        assertFalse(result.contains("<mj-raw>"))
        assertFalse(result.contains(".banner"))
        assertFalse(result.contains("mustNotAppear"))
    }

    @Test fun repliesPreservePlainAndMarkdownSources() {
        val text = "  code indentation\n\n# Heading\n**Bold**"
        assertEquals(text, StudentMailBody.replyText(detail(text = text)))
    }

    @Test fun htmlKeepsTablesAndFormattingInsteadOfFlatteningIntoText() {
        val result = StudentMailBody.render(detail(html = """
            <table width="100%" cellpadding="3"><tr><th>Title</th><td><b>Bold</b><br><em>Italic</em></td></tr></table>
            <blockquote>Quoted reply</blockquote><pre><code>val example = 1</code></pre>
        """))
        val document = Jsoup.parse(result.html)
        assertEquals(MailBodyFormat.HTML, result.format)
        assertEquals("Bold", document.selectFirst("table td b")!!.text())
        assertEquals("100%", document.selectFirst("table")!!.attr("width"))
        assertEquals("Quoted reply", document.selectFirst("blockquote")!!.text())
        assertTrue(document.selectFirst("pre code")!!.text().contains("val example"))
    }

    @Test fun onlyMarkdownTablesReceiveDefaultCellBordersAndPadding() {
        val html = StudentMailBody.render(detail(html = """
            <table cellpadding="0" cellspacing="4" style="border-collapse:separate"><tr><td style="padding:3px;border:0">Layout</td></tr></table>
        """))
        val document = Jsoup.parse(html.html)
        val baseCss = document.selectFirst("style")!!.data()
        assertTrue(baseCss.contains("table{max-width:100%}"))
        assertFalse(baseCss.contains("border-collapse"))
        assertFalse(baseCss.contains("th,td{"))
        assertEquals("border-collapse:separate", document.selectFirst("table")!!.attr("style"))
        assertEquals("padding:3px;border:0", document.selectFirst("td")!!.attr("style"))
        assertEquals("0", document.selectFirst("table")!!.attr("cellpadding"))
        assertEquals("4", document.selectFirst("table")!!.attr("cellspacing"))
        val markdown = StudentMailBody.render(detail(text = "| Title |\n| --- |\n| Value |"), MailBodyFormat.MARKDOWN)
        val markdownCss = Jsoup.parse(markdown.html).selectFirst("style")!!.data()
        assertTrue(markdownCss.contains("table{border-collapse:collapse}"))
        assertTrue(markdownCss.contains("th,td{padding:6px 10px;border:1px solid"))
    }

    @Test fun markdownSupportsCommonAndGfmFormatting() {
        val result = StudentMailBody.render(detail(text = """
            # Heading

            **bold**, *italic*, ~~obsolete~~ and [link](https://example.test/page)

            - First
            - Second

            > Quoted

            ```kotlin
            println("<not an element>")
            ```

            | Item | Count |
            | --- | ---: |
            | One | 2 |
        """.trimIndent()))
        val document = Jsoup.parse(result.html)
        assertEquals(MailBodyFormat.MARKDOWN, result.format)
        assertEquals("Heading", document.selectFirst("h1")!!.text())
        assertEquals("bold", document.selectFirst("strong")!!.text())
        assertEquals("obsolete", document.selectFirst("del")!!.text())
        assertEquals(2, document.select("ul li").size)
        assertEquals("Quoted", document.selectFirst("blockquote")!!.text())
        assertTrue(document.selectFirst("pre code")!!.text().contains("<not an element>"))
        assertEquals("2", document.selectFirst("tbody td:last-child")!!.text())
        assertEquals("right", document.selectFirst("tbody td:last-child")!!.attr("align"))
    }

    @Test fun htmlInTextFallbackIsDetectedWithoutServerHtmlField() {
        val result = StudentMailBody.render(detail(text = "<p>Welcome <strong>student</strong></p>"))
        assertEquals(MailBodyFormat.HTML, result.format)
        assertEquals("student", Jsoup.parse(result.html).selectFirst("strong")!!.text())
    }

    @Test fun htmlAlternativeWinsOverMarkdownLookingPlainAlternative() {
        assertEquals(MailBodyFormat.HTML, StudentMailBody.detect(detail(text = "# Subject", html = "<p>Actual HTML</p>")))
    }

    @Test fun markdownWrappedByMailEditorIsDetectedWhenTextAlternativeAgrees() {
        val markdown = "# Heading\n\n- First\n- Second"
        val mail = detail(text = markdown, html = "<div># Heading<br><br>- First<br>- Second</div>")
        val result = StudentMailBody.render(mail)
        assertEquals(MailBodyFormat.MARKDOWN, result.format)
        assertEquals("Heading", Jsoup.parse(result.html).selectFirst("h1")!!.text())
        assertEquals(2, Jsoup.parse(result.html).select("li").size)
        assertEquals(MailBodyFormat.HTML, StudentMailBody.detect(mail.copy(html = "<div><b># Heading</b><br><br>- First<br>- Second</div>")))
    }

    @Test fun legacyFontMarkupAndSetextMarkdownAreDetected() {
        val html = StudentMailBody.render(detail(html = "<font color='#334455'><u>Legacy mail</u></font>"))
        assertEquals(MailBodyFormat.HTML, html.format)
        assertEquals("Legacy mail", Jsoup.parse(html.html).selectFirst("font u")!!.text())
        val markdown = StudentMailBody.render(detail(text = "Heading\n=======\n\nDescription"))
        assertEquals(MailBodyFormat.MARKDOWN, markdown.format)
        assertEquals("Heading", Jsoup.parse(markdown.html).selectFirst("h1")!!.text())
    }

    @Test fun plainMailDoesNotAccidentallyBecomeMarkdownForAnIsolatedSymbol() {
        val mail = detail(text = "Hello,\nThe order is #123; 2 * 3 = 6.\n- One isolated item\nThanks")
        assertEquals(MailBodyFormat.TEXT, StudentMailBody.detect(mail))
        val document = Jsoup.parse(StudentMailBody.render(mail).html)
        assertTrue(document.selectFirst(".mail-plain")!!.wholeText().contains("2 * 3 = 6"))
        assertTrue(document.select("ul").isEmpty())
    }

    @Test fun manualTextOverrideDisplaysHtmlAsReadableMarkup() {
        val mail = detail(text = "<b>example</b> & more")
        val result = StudentMailBody.render(mail, MailBodyFormat.TEXT)
        val document = Jsoup.parse(result.html)
        assertEquals(MailBodyFormat.TEXT, result.format)
        assertEquals("<b>example</b> & more", document.selectFirst(".mail-plain")!!.wholeText())
        assertTrue(document.select("b").isEmpty())
    }

    @Test fun manualMarkdownHandlesMessagesWithOnlyOneMarkdownSpan() {
        val result = StudentMailBody.render(detail(text = "**Important**"), MailBodyFormat.MARKDOWN)
        assertEquals("Important", Jsoup.parse(result.html).selectFirst("strong")!!.text())
    }

    @Test fun activeHtmlAndNavigationCapabilitiesAreRemoved() {
        val result = StudentMailBody.render(detail(html = """
            <base href="https://tracker.example/"><meta http-equiv="refresh" content="0;url=https://tracker.example/">
            <script>alert('bad')</script><iframe src="https://tracker.example/"></iframe>
            <svg onload="alert(1)"><a href="javascript:alert(1)">SVG</a></svg>
            <object data="file:///secret"></object><embed src="content://secret">
            <form action="https://tracker.example/"><input name="password"><button>Submit</button><p>Readable label</p></form>
            <p onclick="alert(1)" onmouseover="alert(1)">Safe text</p>
            <a href="javascript:alert(1)">Unsafe link</a><a href="intent://app">App intent</a>
            <a href="file:///secret">File</a><a href="content://secret">Content</a>
        """))
        val document = Jsoup.parse(result.html)
        assertTrue(document.select("script,iframe,svg,object,embed,form,input,button,base").isEmpty())
        assertEquals(2, document.select("meta:not([http-equiv])").size)
        assertEquals(1, document.select("meta[http-equiv]").size)
        assertTrue(document.select("[onclick],[onmouseover],a[href]").isEmpty())
        assertTrue(document.body().text().contains("Readable label"))
        val csp = document.selectFirst("meta[http-equiv]")!!.attr("content")
        assertTrue(csp.contains("default-src 'none'"))
        assertTrue(csp.contains("form-action 'none'"))
        assertTrue(csp.contains("base-uri 'none'"))
    }

    @Test fun htmlLinksKeepOnlyOrdinaryExternalSchemesAndNeverSourceTargets() {
        val result = StudentMailBody.render(detail(html = """
            <a href="https://example.test/a" target="_top">Web</a>
            <a href="http://example.test/b">HTTP</a>
            <a href="mailto:student@example.test">Mail</a><a href="tel:+12345">Phone</a>
            <a href="data:text/html,bad">Data</a><a href="//example.test">Relative</a>
        """))
        val links = Jsoup.parse(result.html).select("a[href]")
        assertEquals(4, links.size)
        assertTrue(links.all { it.attr("rel") == "noopener noreferrer" && !it.hasAttr("target") })
    }

    @Test fun headAndInlineCssKeepLayoutButCannotFetchResourcesOrOverlayApp() {
        val result = StudentMailBody.render(detail(html = """
            <head><style onload="alert(1)">
            .card { color: #345678; width: 100%; background-image: url(https://tracker.example/pixel); }
            @media (max-width: 600px) { .card { padding: 12px; font-size: 18px; } }
            @font-face { font-family: tracking; src:url(https://tracker.example/font); }
            .escape { background:u\72l(https://tracker.example/escape); color:red; }
            </style></head>
            <body><div class="card" style="color:red;position:fixed;z-index:999;left:0;behavior:url(x);font-size:18px;background:u/**/rl(https://tracker.example/pixel)">Example</div></body>
        """))
        val document = Jsoup.parse(result.html)
        val css = document.select("style").joinToString { it.data().ifBlank { it.text() } }
        assertTrue(css.contains("width:100%"))
        assertTrue(css.contains("@media (max-width: 600px)"))
        assertTrue(css.contains("padding:12px"))
        assertFalse(css.contains("tracker.example"))
        assertFalse(css.contains("@font-face"))
        assertFalse(css.contains("u\\72l"))
        assertTrue(document.select("style[onload]").isEmpty())
        val inline = document.selectFirst(".card")!!.attr("style")
        assertTrue(inline.contains("color:red"))
        assertTrue(inline.contains("font-size:18px"))
        assertFalse(inline.contains("fixed"))
        assertFalse(inline.contains("behavior"))
        assertTrue(inline.contains("url(\"${StudentMailBody.ORIGIN}/image/"))
    }

    @Test fun remoteAndCidImagesAreRewrittenWithoutLeakingResourceUrlsIntoHtml() {
        val remote = "https://images.example.test/tracker.png?recipient=synthetic"
        val result = StudentMailBody.render(detail(html = """
            <p>Images</p><img src="$remote" srcset="https://tracker.example/2x 2x" onerror="alert(1)">
            <img src="cid:embedded-image@example.test" alt="Inline diagram"><img src="$remote">
            <img src="file:///private.png"><img src="javascript:alert(1)">
        """))
        val document = Jsoup.parse(result.html)
        assertEquals(2, result.images.size)
        assertEquals(remote, result.images["${StudentMailBody.ORIGIN}/image/0"])
        assertEquals("cid:embedded-image@example.test", result.images["${StudentMailBody.ORIGIN}/image/1"])
        assertTrue(result.hasRemoteImages)
        assertEquals(3, document.select("img[src]").size)
        assertTrue(document.select("img[src]").all { it.attr("src").startsWith("${StudentMailBody.ORIGIN}/image/") })
        assertFalse(result.html.contains(remote))
        assertFalse(result.html.contains("tracker.example"))
        assertFalse(result.html.contains("srcset"))
        assertFalse(result.html.contains("onerror"))
    }

    @Test fun protocolRelativeImagesBecomeHttpsWithoutAllowingArbitraryRelativePaths() {
        val result = StudentMailBody.render(detail(html = """
            <img src="//public.example.test/logo.png"><img src="images/unbound.png">
            <img src="//user:password@public.example.test/private.png"><img src="////invalid-host/path.png">
            <p style="background-image:url('//public.example.test/hero.png')">Hero</p>
        """))
        assertEquals(setOf("https://public.example.test/logo.png", "https://public.example.test/hero.png"), result.images.values.toSet())
        assertTrue(result.hasRemoteImages)
        assertEquals(1, Jsoup.parse(result.html).select("img[src]").size)
        assertFalse(result.html.contains("public.example.test"))
        assertFalse(result.html.contains("images/unbound.png"))
        assertFalse(result.html.contains("password"))
    }

    @Test fun mailBackgroundImagesAreProxiedInHeadInlineAndLegacyMarkup() {
        val hero = "https://images.example.test/hero.png"
        val texture = "https://images.example.test/texture.png"
        val png = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/lZEAAAAASUVORK5CYII="
        val result = StudentMailBody.render(detail(html = """
            <head><style>.hero{background-image:url('$hero');background-size:cover;}</style></head>
            <body background="$texture"><table><tr><td background="cid:logo@example.test">Hello</td></tr></table>
            <div style="background: #eee url('$png') center no-repeat; padding:12px">Data background</div></body>
        """))
        val document = Jsoup.parse(result.html)
        assertEquals(setOf(hero, texture, "cid:logo@example.test", png), result.images.values.toSet())
        assertTrue(result.hasRemoteImages)
        assertTrue(document.select("style").any { it.data().contains("background-size:cover") })
        assertTrue(document.selectFirst(".mail-body")!!.attr("style").contains("${StudentMailBody.ORIGIN}/image/"))
        assertTrue(document.selectFirst("td")!!.attr("style").contains("${StudentMailBody.ORIGIN}/image/"))
        assertTrue(document.select("div[style]").any { it.attr("style").contains("center no-repeat") })
        assertTrue(document.select("[background]").isEmpty())
        assertFalse(result.html.contains("images.example.test"))
        assertFalse(result.html.contains("base64,"))
        assertFalse(result.html.contains("cid:logo"))
    }

    @Test fun unsafeBackgroundUrlsAndNonImageCssResourcesStayBlocked() {
        val result = StudentMailBody.render(detail(html = """
            <p style="background-image:url(javascript:alert);color:blue">JavaScript</p>
            <p style="background:url('data:image/svg+xml;base64,PHN2Zz4=');color:green">SVG</p>
            <p style="background-image:url('unbound/image.png');color:red">Unbound relative</p>
            <p style="background-image:image-set('unbound/image.png' 1x);color:red">Unsupported image set</p>
            <p style="list-style:url('https://images.example.test/list.png');cursor:url('https://images.example.test/cursor.png')">Other resources</p>
            <table background="file:///secret"><tr><td>Private</td></tr></table>
            <style>@import 'https://images.example.test/style.css'; .injected{color:red}</style>
        """))
        assertTrue(result.images.isEmpty())
        assertFalse(result.hasRemoteImages)
        assertFalse(result.html.contains("url("))
        assertFalse(result.html.contains("images.example.test"))
        assertTrue(Jsoup.parse(result.html).select("p[style]").all { !it.attr("style").contains("background") })
    }

    @Test fun smallRasterDataImagesAreMappedButSvgAndInvalidBinaryAreRejected() {
        val png = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/lZEAAAAASUVORK5CYII="
        val result = StudentMailBody.render(detail(html = """
            <img src="$png"><img src="data:image/svg+xml;base64,PHN2Zz48L3N2Zz4=">
            <img src="data:image/png;base64,PGh0bWw+"> <img src="data:image/png;base64,invalid">
        """))
        assertEquals(listOf(png), result.images.values.toList())
        assertFalse(result.hasRemoteImages)
        assertEquals(1, Jsoup.parse(result.html).select("img[src]").size)
        assertFalse(result.html.contains("base64,"))
    }

    @Test fun attachmentContentLocationsUseInlineImagesInsteadOfRemoteImagePermission() {
        val relative = "images/logo.png"
        val absolute = "https://cdn.example.test/logo.png"
        val mail = detail(html = "<img src='$relative'><img src='$absolute'><img src='unbound/image.png'>").copy(
            attachments = listOf(relative, absolute).mapIndexed { index, location ->
                MailAttachment("${index + 2}", "logo.png", 123, messageId = "synthetic-mid", partId = "${index + 2}",
                    contentLocation = location, contentType = "image/png", inlined = true)
            }
        )
        val result = StudentMailBody.render(mail)
        assertEquals(setOf(relative, absolute), result.images.values.toSet())
        assertFalse(result.hasRemoteImages)
        assertEquals(2, Jsoup.parse(result.html).select("img[src]").size)
    }

    @Test fun authenticatedInlineImageUrlIsKeptOutOfHtmlAndDoesNotCountAsRemote() {
        val source = "https://${StudentMailSession.MAIL_HOST}/js6/s?func=mbox:getMessageData&mid=synthetic-mid&part=2&sid=obsolete"
        val mail = detail(html = "<img src='$source'>").copy(attachments = listOf(
            MailAttachment("2", "diagram.png", 123, messageId = "synthetic-mid", partId = "2", contentType = "image/png")
        ))
        val result = StudentMailBody.render(mail)
        assertEquals(source, result.images.values.single())
        assertFalse(result.hasRemoteImages)
        assertFalse(result.html.contains("obsolete"))
        assertTrue(StudentMailBody.render(mail.copy(attachments = emptyList())).hasRemoteImages)
    }

    @Test fun plainTextUrlsAreLinkifiedAndMailWhitespaceIsPreserved() {
        val mail = detail(text = "Hello & welcome\n  indented line\nRead https://example.test/?a=1&b=2.\nEnd")
        val document = Jsoup.parse(StudentMailBody.render(mail).html)
        assertEquals("https://example.test/?a=1&b=2", document.selectFirst(".mail-plain a")!!.attr("href"))
        assertEquals(mail.text, document.selectFirst(".mail-plain")!!.wholeText())
    }

    @Test fun attackerSuppliedCspIsReplacedAndThemeCannotInjectHtmlOrCss() {
        val result = StudentMailBody.render(
            detail(html = "<meta http-equiv='Content-Security-Policy' content=\"default-src *\"><p>Example</p>"),
            theme = MailBodyTheme(text = "red;}</style><script>alert(1)</script>", textSize = 10000)
        )
        val document = Jsoup.parse(result.html)
        assertEquals(1, document.select("meta[http-equiv]").size)
        assertTrue(document.select("script").isEmpty())
        assertTrue(document.selectFirst("style")!!.data().ifBlank { document.selectFirst("style")!!.text() }.contains("font-size:24px"))
        assertFalse(result.html.contains("default-src *"))
    }
}
