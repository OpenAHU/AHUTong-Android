package com.ahu.ahutong.data.model

import java.io.Serializable

/**
 * Shared campus hierarchy node used by electricity room selection and cache.
 */
data class CampusDataItem(
    val name: String,
    val value: String,
) : Serializable
