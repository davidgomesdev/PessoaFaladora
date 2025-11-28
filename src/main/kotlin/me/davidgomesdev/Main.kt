package me.davidgomesdev

import io.quarkus.runtime.Quarkus
import io.quarkus.runtime.QuarkusApplication
import io.quarkus.runtime.annotations.QuarkusMain
import kotlin.system.exitProcess


@QuarkusMain
object JavaMain {
    @JvmStatic
    fun main(args: Array<String>) {
        Quarkus.run(HelloWorldMain::class.java, *args)
    }
}

class HelloWorldMain(private val service: Service) : QuarkusApplication {
    @Throws(Exception::class)
    override fun run(vararg args: String): Int {
        println("\uD83D\uDC4B Running")
        service.runExample()
        println("✅ Done")
        exitProcess(0)
    }
}
