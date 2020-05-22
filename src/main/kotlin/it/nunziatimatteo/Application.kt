package it.nunziatimatteo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.*
import javax.persistence.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.http.FullHttpRequest
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpMethod.GET
import io.micronaut.http.HttpMethod.PUT
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType.*
import io.micronaut.http.annotation.*
import io.micronaut.http.uri.UriMatchTemplate
import io.micronaut.runtime.Micronaut
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.inject.Inject
import javax.inject.Singleton
import javax.persistence.Entity

/*---------------------------------------------*/
/*-- the program is composed by an Application object and a decorated HttpRouter class --*/

object Application {
    @JvmStatic fun main(args: Array<String>) {
        Micronaut.build()
                .packages("it.nunziatimatteo")
                .mainClass(Application.javaClass)
                .start()
    }
}

// example of expressive no-exec flow-based routing (any more buzzwords?!)
// this must be a class for micronaut to not go nuts!
@Flow class ApplicationRouter : HttpRouter() {
    @Inject lateinit var bookRepo: BookRepository

    //as minimum you need to override the routing method
    override fun routing(request: HttpRequest<*>): HttpRequestHandler {

        /*this is a simple example but you can combine any complex logic, like request.run {...},
        multiple 'when' blocks in case first block returns null, ... */
        return when {
            /* a super expressive match rule by using 'and', infix calls and parentheses.
               mind that this is more plain english-ish but slower as 'and' doesn't short circuit */
            (request uriMatches "/api/v10/hello") and (request methodIs GET) and (request mediaIs APPLICATION_JSON) -> ::getHello

            /* or you can just use the 'infix' calling convention and std. && operators */
            request uriMatches "/api/v10/hello/{name}{?age}" && request methodIs GET -> ::getHelloName

            /* and you can make complex code with services and repositories */
            request uriMatches "/api/v10/book" -> BookHandler(bookRepo).run {
                return@run when {
                    //of course you can implement handlers as part of a class/object like this getBooks() method
                    (request.method == GET) -> ::getBooks

                    /* if you want you can nest expressions multiple times */
                    request methodIs PUT -> ::saveBook
//                    request methodIs PUT -> when {
//                        request mediaIs APPLICATION_JSON -> ::saveBook
//                        request mediaIs TEXT_PLAIN -> ::saveBook
//                        else -> ::return404
//                    }

                    else -> ::return404
                }
            }

            /* if routing becomes really complex, nested 'when's become quite confusing...
               then sub-routing is also super-simple! */
            request uriMatches "/api/v10/uri/with/complex/management" -> complexSubRouter(request)

            /* return a system-default 404 response */
            else -> ::return404
        }
    }
}

/*example of sub-router: here it has the same signature of the main routing fun,
 but it can actually accept anything in its input. What is important is:
 it must return an HttpRequestHandler*/
fun complexSubRouter(request: HttpRequest<*>): HttpRequestHandler {
    return ::getHelloFromSubRouter //ok not really complex :P
}

/*---------------------------------------------*/
/*-- actual end points, they must implement the HttpRequestHandler signature --*/

@Produces(TEXT_HTML) fun getHelloName(request: HttpRequest<*>): HttpResponse<*> {
    val name = request.pathVariables["name"] ?: "unknown user"

    //this is ugly would be better to find a workaround
    val tmp = request.queryParams.getOrElse("age") { listOf("unknown") }
    val age = (tmp as List<String>).elementAt(0)

    return HttpResponse.ok<Any>().body("""
         <h1>Hello $name, your age is $age!</h1>
        """.trimIndent())
}

@Produces(TEXT_HTML) fun getHello(request: HttpRequest<*>): HttpResponse<*> {
    return HttpResponse.ok<Any>().body("""
         <h1>Hello World from Micronaut 101!</h1>
        """.trimIndent())
}

@Produces(TEXT_HTML) fun getHelloFromSubRouter(request: HttpRequest<*>): HttpResponse<*> {
    return HttpResponse.ok<Any>().body("""
         <h1>Hello World from Mr. Subrouter!</h1>
        """.trimIndent())
}

//TODO: implement an example with CRUD and repo injection
class BookHandler {
    var bookRepo: BookRepository
    constructor(bookRepository: BookRepository) { this.bookRepo = bookRepository }

    fun getBooks(request: HttpRequest<*>): HttpResponse<*> {
        val ourBooks: MutableIterable<Book> = this.bookRepo.findAll()
        return HttpResponse.ok(ourBooks)
    }

    fun saveBook(request: HttpRequest<*>): HttpResponse<*> {
        val book: Book? = request.bodyAs(Book::class.java)
        //val book = Book("Odissey", 1750, null)

        return if (book != null) {
            this.bookRepo.save(book)
            HttpResponse.ok("")

        } else {
            HttpResponse.badRequest(request.body)
        }
    }
}

@Entity data class Book (
        var title: String,
        var first_edition: Int,

        @field:Id @GeneratedValue //this must be bound to IDENTITY in Postgres
        var id: Int?
)

@JdbcRepository(dialect = Dialect.POSTGRES)
interface BookRepository : CrudRepository<Book, Int> {
    fun update(@Id id: Int?, title: String)
}

/*---------------------------------------------*/
/* this is the library itself */

/** decorator for the programmatic non-exec expressive routing */
@Singleton @Controller("{+path}") annotation class Flow

/** a type alias to make our definitions a bit more clear */
typealias HttpRequestHandler = (HttpRequest<*>) -> HttpResponse<*>

/** empty class: simple trick to let netty parse the body of an http message */
class HttpRequestBody

/** router interface, a "kitchen-sink" controller */
open class HttpRouter {
    /*-- next methods have to be open --*/

    /** MANDATORY: you have to override this function to setup your routes */
    open fun routing(request: HttpRequest<*>): HttpRequestHandler {
        return ::return404 //by default everything is 404
    }

    /** optionally override to install a global method used to manipulate requests *BEFORE* they reach your routing */
    open fun preRouting(request: HttpRequest<*>): HttpRequest<*> = request
    /** optionally override to install a global method used to manipulate your handlers *BEFORE* they are executed */
    open fun preHandling(handler: HttpRequestHandler): HttpRequestHandler = handler
    /** optionally override to install a global method used to manipulate responses *AFTER* they have been processed by the routing */
    open fun postRouting(response: HttpResponse<*>): HttpResponse<*> = response
    /** optionally override the global 404 response */
    open fun return404(request: HttpRequest<*>): HttpResponse<*> = request.notImplemented

    /*-- next methods must stay final but public for micronaut to use them --*/

    /* workaround: micronaut doesn't accept multiple HTTP verbs per function. Just use wrappers around the same func. */
    @Get    @Consumes(ALL) fun getWrapper   (request: HttpRequest<HttpRequestBody>): HttpResponse<*> { return this.routingWrapper(request) }
    @Put    @Consumes(ALL) fun putWrapper   (request: HttpRequest<HttpRequestBody>): HttpResponse<*> { return this.routingWrapper(request) }
    @Post   @Consumes(ALL) fun postWrapper  (request: HttpRequest<HttpRequestBody>): HttpResponse<*> { return this.routingWrapper(request) }
    @Delete @Consumes(ALL) fun deleteWrapper(request: HttpRequest<HttpRequestBody>): HttpResponse<*> { return this.routingWrapper(request) }
    //any other method...

    /*-- this must stay final and private --*/

    private fun routingWrapper(req: HttpRequest<*>): HttpResponse<*> {
        /* any request modification here */
        val request = this.preRouting(req)

        /* any re-routing here */
        val handle = this.preHandling(this.routing(request))
        val response = handle(request)

        /* any response modification here */
        return this.postRouting(response)
    }
}

/*-- HttpRequest extensions --*/

inline fun <T, B> HttpRequest<T>.bodyAs(objType: Class<B>): B? {
    var b: B? = null

    val dataTree = (this as FullHttpRequest).delegate.body.orElse(null)
    if (dataTree != null)
        b = ObjectMapper().registerKotlinModule().treeToValue(dataTree as com.fasterxml.jackson.core.TreeNode, objType )

    return b
}

infix fun <T> HttpRequest<T>.uriMatches(tmplt: String): Boolean {
    val parser = UriMatchTemplate(tmplt).match(this.uri)
    val doMatches = parser.isPresent

    //if we match, substitute the "kitchen-sink" template of the controller with this specific template
    if (doMatches) this.attributes.put("micronaut.http.route.template", tmplt)

    return doMatches
}

//these are only syntactic sugar
infix fun <T> HttpRequest<T>.methodIs(verb: HttpMethod): Boolean { return this.method == verb }
infix fun <T> HttpRequest<T>.mediaIs(type: String): Boolean { return this.mediaType == type }

val <T> HttpRequest<T>.pathVariables: Map<String, Any>
get() {
    //get the matching template
    val tmplt = this.attributes.getValue("micronaut.http.route.template").toString()
    //parse the variable segments of the uri against the template
    val parser = UriMatchTemplate(tmplt).match(this.uri)

    return if (parser.isPresent) parser.get().variableValues else emptyMap()
}

//TODO: make params as List<String>?
val <T> HttpRequest<T>.queryParams: Map<String, Any> get() { return this.parameters.asMap() }

val <T> HttpRequest<T>.mediaType: String
get () { return if (this.contentType.isPresent) this.contentType.get().toString() else "" }

/** default 404 response */
//TODO: nothing here is HATEOAS and usually REST is not in real life, but default micronaut error responses are...
val <T> HttpRequest<T>.notImplemented: HttpResponse<*>
get() {
    val mediaString = if(this.mediaType != "") " with content type ${this.mediaType}" else ""
    val msg = "${this.method} on ${this.uri}${mediaString} not implemented yet!"

    //maybe slow but this is a 404...
    val logger: Logger = LoggerFactory.getLogger(HttpRouter::class.java)
    logger.info(msg)

    return HttpResponse.notFound(msg)
}
