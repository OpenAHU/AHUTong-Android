package com.ahu.ahutong.data.network

/** Localized version names must not introduce invalid HTTP header characters. */
fun asciiUserAgent(value: String): String =
    value.map { if (it == '\t' || it in ' '..'~') it else '?' }.joinToString("")
