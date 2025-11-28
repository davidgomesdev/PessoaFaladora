package me.davidgomesdev

import io.quarkus.runtime.Quarkus
import io.quarkus.runtime.QuarkusApplication
import io.quarkus.runtime.annotations.QuarkusMain


@QuarkusMain
object JavaMain {
    @JvmStatic
    fun main(args: Array<String>) {
        Quarkus.run(HelloWorldMain::class.java, *args)
    }
}

@QuarkusMain
class HelloWorldMain(private val service: Service) : QuarkusApplication {
    @Throws(Exception::class)
    override fun run(vararg args: String): Int {
        service.importTexts()
        return 0
    }
}
