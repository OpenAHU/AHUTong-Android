plugins {
    // 版本由根 build.gradle.kts 的插件别名统一提供。
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

/*
 * 充值/缴费域的数据侧（校园卡、电费、浴室、网费）。
 *
 * 只放"界面需要问数据层的问题"与协议的词汇（ycard 的请求/响应 DTO 按原包名搬来，
 * 币种、签名与验签辅助一并同行）。协议客户端（YcardApi）仍在 :app：它要用会话 Cookie
 * 与 TokenManager，属于 P4 协议层抽取的范围。
 *
 * 计划 §3.2 给这块预留的名字是 :data:ahu（jwxt / adwmh / ycard 协议适配），
 * 这里按既有的模块命名习惯（:data:schedule / :data:grade / :data:chaoxing 都按**使用方**的域命名）
 * 取 :data:recharge——先有使用者再定接口，协议客户端的归属留到 P4。
 * 依赖方向由门禁 R25 看守。
 */
android {
    namespace = "com.ahu.ahutong.data.recharge"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // 端口用 AhuResult 表达失败；CardRechargeBank 与校园卡模型来自 :core:model。
    api(project(":core:common"))
    api(project(":core:model"))
    // 请求 DTO 自己拼 FormBody（搬迁前如此，保持原样）；端口签名里也有 FormBody，
    // 因此它对使用者必须是可见的 API 依赖。
    api(libs.okhttp)
    // 协议 DTO 用 @SerializedName 描述线上字段名（Gson 反射使用，属于契约的一部分）。
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.android)

    // 协议 DTO 的签名契约测试与 DTO 同模块：字段名与拼串顺序都是协议的一部分。
    testImplementation(kotlin("test-junit"))
}
