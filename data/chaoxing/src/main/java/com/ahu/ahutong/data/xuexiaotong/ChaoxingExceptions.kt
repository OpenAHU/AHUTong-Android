package com.ahu.ahutong.data.xuexiaotong

import java.io.IOException

/** 命中学习通风控（HTTP 202 或跳转 antispiderShowVerify）。 */
class ChaoxingRiskControlException(message: String) : IOException(message)
