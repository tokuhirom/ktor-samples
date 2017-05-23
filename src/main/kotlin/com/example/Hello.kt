package com.example

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import freemarker.cache.ClassTemplateLoader
import freemarker.cache.FileTemplateLoader
import freemarker.cache.MultiTemplateLoader
import freemarker.core.HTMLOutputFormat
import freemarker.template.TemplateExceptionHandler
import org.jetbrains.ktor.application.Application
import org.jetbrains.ktor.application.ApplicationCallPipeline
import org.jetbrains.ktor.application.call
import org.jetbrains.ktor.application.install
import org.jetbrains.ktor.content.TextContent
import org.jetbrains.ktor.features.Compression
import org.jetbrains.ktor.features.DefaultHeaders
import org.jetbrains.ktor.freemarker.FreeMarker
import org.jetbrains.ktor.freemarker.FreeMarkerContent
import org.jetbrains.ktor.host.embeddedServer
import org.jetbrains.ktor.http.ContentType
import org.jetbrains.ktor.jetty.Jetty
import org.jetbrains.ktor.logging.CallLogging
import org.jetbrains.ktor.response.respondText
import org.jetbrains.ktor.routing.Routing
import org.jetbrains.ktor.routing.get
import org.jetbrains.ktor.transform.transform
import java.io.File

class JsonResponse(val data: Any)
data class Model(val name: String, val items: List<Item>)
data class Item(val key: String, val value: String)

fun Application.module() {
    install(DefaultHeaders)
    install(Compression)
    install(CallLogging)
    install(FreeMarker, {
        templateLoader = MultiTemplateLoader(
                arrayOf(
                        FileTemplateLoader(File("src/main/resources/templates/")),
                        ClassTemplateLoader(Application::class.java.classLoader, "templates")
                )
        )
        // for debugging
        // TODO automatically change debugging options by configuration
        templateExceptionHandler = TemplateExceptionHandler.HTML_DEBUG_HANDLER
        outputFormat = HTMLOutputFormat.INSTANCE
    })
    install(Routing)
    {
        val objectMapper = ObjectMapper()
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        intercept(ApplicationCallPipeline.Infrastructure) { call ->
            call.transform.register<JsonResponse> { value ->
                TextContent(objectMapper.writeValueAsString(value.data), ContentType.Application.Json)
            }
        }

        // TODO path variables
        get("/") {
            call.respondText("Hello, World!")
        }
        get("/greeting") {
            val name = call.request.queryParameters["name"]
            call.respondText("Hello, $name!")
        }

        // @PathParam
        /*
         -   {param?} – optional path segment, if exists captured into parameter
         -   * – wildcard, any segment will match, but shouldn't be missing
         -   {...}– tailcard, matches all the rest of the URI, should be last. Can be empty.
         -   {param...} – captured tailcard, matches all the rest of the URI and puts multiple values for each path segment into parameters using param as key. Use call.parameters.getAll("param") to get all values.
         */
        get("/user/{login}") {
            val login = call.parameters["login"]
            call.respondText("OK, $login")
        }

        get("/hello") {
            val name = call.request.queryParameters["name"] ?: "anonymous"
            call.respond(FreeMarkerContent("hello.ftl", mapOf<String, Any>(
                    "name" to name
            ), etag = ""))
        }

        get("/json") {
            call.respond(JsonResponse(Model("root", listOf(Item("A", "Apache"), Item("B", "Bing")))))
        }
    }
}

fun main(args: Array<String>) {
    embeddedServer(Jetty, 8080, module = Application::module)
            .start()
}
