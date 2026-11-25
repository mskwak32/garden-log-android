package com.mskwak.pdf.source_impl

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageBreakTest {

    private val page = 730f

    @Test
    fun `빈 페이지면 새 페이지를 만들지 않는다`() {
        assertFalse(PageBreak.shouldStartNewPage(900f, 100f, 50f, page, isPageEmpty = true))
    }

    @Test
    fun `남은 공간에 들어가면 이어서 그린다`() {
        assertFalse(PageBreak.shouldStartNewPage(200f, 50f, 300f, page, isPageEmpty = false))
    }

    @Test
    fun `남은 공간엔 안 들어가고 한 페이지엔 들어가면 새 페이지`() {
        assertTrue(PageBreak.shouldStartNewPage(400f, 50f, 300f, page, isPageEmpty = false))
    }

    @Test
    fun `한 페이지보다 길어도 앞부분이 들어가면 이어서 그린다`() {
        assertFalse(PageBreak.shouldStartNewPage(900f, 200f, 300f, page, isPageEmpty = false))
    }

    @Test
    fun `한 페이지보다 길고 앞부분이 안 들어가면 새 페이지`() {
        assertTrue(PageBreak.shouldStartNewPage(900f, 200f, 150f, page, isPageEmpty = false))
    }
}
