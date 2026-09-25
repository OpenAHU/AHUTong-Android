package com.ahu.ahutong.data.crawler.model.adwnh

import com.google.gson.annotations.SerializedName

// Kept with the crawler wire models so the existing R8 rules protect Gson reflection.
// Nullable fields also let the login flow reject incomplete upstream success responses.
internal data class WisdomLoginResponse(
    val code: Int?,
    val msg: String?,
    @SerializedName("object") val payload: WisdomLoginPayload?
)

internal data class WisdomLoginPayload(val user: WisdomLoginProfile?)

internal data class WisdomLoginProfile(val userName: String?, val idNumber: String?)
