package com.example.authenticator.arch.api

interface Clock {
    fun epochMillis(): Long
    fun monotonicMillis(): Long
}
