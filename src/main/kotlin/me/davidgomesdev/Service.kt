package me.davidgomesdev

import dev.langchain4j.data.document.loader.FileSystemDocumentLoader

class Service {

    fun importTexts() {
        val loads = FileSystemDocumentLoader.loadDocument("")
    }
}
