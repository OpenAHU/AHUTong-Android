package com.ahu.ahutong.core.storage

/** 主页 Hero 的候选类型；列表顺序即上屏优先级，由用户拖动决定。 */
enum class HomeHeroSlot {
    ONGOING_EXAM,
    ONGOING_COURSE,
    ASSIGNMENT,
    NEXT_COURSE,
    NEXT_EXAM;

    companion object {
        val DEFAULT_ORDER: List<HomeHeroSlot> = entries.toList()

        /** 去重、丢弃未知项，并把缺失项按默认顺序补到末尾，保证永远是完整列表。 */
        fun normalize(raw: List<HomeHeroSlot>): List<HomeHeroSlot> {
            val seen = LinkedHashSet<HomeHeroSlot>(raw.size + entries.size)
            raw.forEach { seen.add(it) }
            DEFAULT_ORDER.forEach { seen.add(it) }
            return seen.toList()
        }
    }
}
