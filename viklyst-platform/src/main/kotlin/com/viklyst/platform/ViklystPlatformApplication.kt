package com.viklyst.platform

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ViklystPlatformApplication

fun main(args: Array<String>) {
    runApplication<ViklystPlatformApplication>(*args)
}
