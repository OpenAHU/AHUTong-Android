package com.ahu.ahutong.assistant.model

/** 模型服务方因内容策略拒绝请求；这是正常拦截，不是网络故障。 */
class ModelContentRejectedException(message: String) : RuntimeException(message)
