# micronaut-kotlin-expressive
a PoC for a more expressive programmatic routing API for micronaut in kotlin

## Warning
Basically I was curious about micronaut (and micronaut-data-jdbc) but I have the Annotated Controller Pattern. The programmatic API in micronaut sucks for my taste, while I've seen very nice APIs like the one by Vert.x Web or Javalin.

Therefore, I've started this PoC. The code is rude, single file. It contains both the library for the expressive programmatic API and the example implementation.
Also everything is based off the public micornaut API rather than its internals so it is expected to be suboptimal.

Again this is just a PoC, don't use it! Just comment on it!

## The cool part
with this approach a micronaut routing logic looks like:
```kotlin
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
                    request methodIs PUT -> when {
                        request mediaIs APPLICATION_JSON -> ::saveBook
                        request mediaIs TEXT_PLAIN -> ::saveBook
                        else -> ::return404
                   }

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
```
While a functional end point is like:
```kotlin
@Produces(TEXT_HTML) fun getHelloName(request: HttpRequest<*>): HttpResponse<*> {
    val name = request.pathVariables["name"] ?: "unknown user"

    //this is ugly would be better to find a workaround
    val tmp = request.queryParams.getOrElse("age") { listOf("unknown") }
    val age = (tmp as List<String>).elementAt(0)

    return HttpResponse.ok<Any>().body("""
         <h1>Hello $name, your age is $age!</h1>
        """.trimIndent())
}
```

## The library
* It uses a lot of Kotlin specific (amazing) stuff
* the core is made by:
  * a new annotation: `@Flow`
  * `HttpRouter`, a "new" base class which manages the routing and requires the user to override the `routing()` method
  * some extension methods and members to the `HttpRequest` object, to make it easier to manage it in a programmatic fashion
    * those extensions make use of the Kotlin infix syntax to make the code more readable
* You have to manage 2 micronaut items: `HttpRequest` and `HttpResponse`. these are the only form of input and output
* The core, the `routing()` method is expected to receive an `HttpRequest` and return a function (a pointer) of type `(HttpRequest<*>) -> HttpResponse<*>` this function is aliases as `HttpRequestHandler`

## The source
As said it is a single file including both the lib and an example. The example of this PoC includes a repository and requires a Postgres DB setup.
