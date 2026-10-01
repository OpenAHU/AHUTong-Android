package com.ahu.ahutong.data.session

/**
 * 本机会话残留的清理，两种语义分开表达：
 *
 * - [clearDerivedToken]：**续期**成功后丢弃派生的校园卡令牌。重新登录会拿到新令牌，旧的必须丢掉；
 *   但它不碰第一方 Cookie——那份 Cookie 正是刚刚建立的会话本身，清掉它等于把续期结果一起抹了
 *   （master 的 TokenAuthenticator 在续期后只清令牌，这次与它对齐）。
 * - [clear]：**登出 / 清除所有数据**，令牌与第一方 Cookie 一起清。
 *
 * "怎么清"仍是各管理器的职责；会话层只表达清哪一层。
 */
interface SessionResidue {

    /** Invalidate retained service credentials after an explicit login or Web verification. */
    fun clearRetainedServiceSessions() {}

    /** 清掉派生的校园卡令牌（续期成功后调用）。 */
    fun clearDerivedToken()

    /** 清掉全部本机会话残留：令牌与第一方 Cookie（登出时调用）。 */
    suspend fun clear()
}
