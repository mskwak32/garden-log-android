package com.mskwak.pdf.source_impl

/** 페이지 나눔 판단 (Android 의존 없음) */
internal object PageBreak {

    /**
     * 하루치 블록을 새 페이지에서 시작해야 하는지 판단한다.
     *
     * @param blockHeight 블록 전체 높이
     * @param headHeight 블록 앞부분(날짜 줄 + 처음 그려지는 요소) 높이
     * @param remaining 현재 페이지의 남은 높이 (블록 위 간격 제외 후)
     * @param pageContentHeight 한 페이지 본문 높이
     * @param isPageEmpty 현재 페이지가 비어 있는지
     * @return true면 새 페이지에서 시작
     */
    fun shouldStartNewPage(
        blockHeight: Float,
        headHeight: Float,
        remaining: Float,
        pageContentHeight: Float,
        isPageEmpty: Boolean
    ): Boolean {
        if (isPageEmpty) return false
        if (blockHeight <= remaining) return false
        // 한 페이지에 들어가는 블록은 통째로 다음 페이지로
        if (blockHeight <= pageContentHeight) return true
        // 한 페이지보다 긴 블록은 앞부분이 들어갈 때만 여기서 시작해 이어 그린다
        return headHeight > remaining
    }
}
