package com.ahu.ahutong.data.campuscard

interface CampusCardLocalStore {
    fun isMockMode(): Boolean

    fun isLoggedIn(): Boolean

    fun getCachedBalance(): Double?

    fun saveBalance(balance: Double)
}
