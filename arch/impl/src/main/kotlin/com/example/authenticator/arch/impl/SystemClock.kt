package com.example.authenticator.arch.impl

import android.os.SystemClock
import com.example.authenticator.arch.api.Clock

/** 平台时钟实现；monotonicMillis 使用系统单调时钟，不随用户改时间变化。 */
internal class SystemClock : Clock {
    override fun epochMillis(): Long = System.currentTimeMillis()
    override fun monotonicMillis(): Long = SystemClock.elapsedRealtime()
}