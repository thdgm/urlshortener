package es.unizar.urlshortener.infrastructure.delivery

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import es.unizar.urlshortener.core.ClickProperties
import es.unizar.urlshortener.core.ShortUrl
import es.unizar.urlshortener.core.ShortUrlProperties
import es.unizar.urlshortener.core.usecases.LogClickUseCase
import es.unizar.urlshortener.core.usecases.CreateShortUrlUseCase
import es.unizar.urlshortener.core.usecases.RedirectUseCase
import es.unizar.urlshortener.core.usecases.InfoShortUrlUseCase
import org.springframework.hateoas.server.mvc.linkTo
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.URI
import javax.servlet.http.HttpServletRequest
import java.time.OffsetDateTime

/**
 * The specification of the controller.
 */
interface UrlShortenerController {

    /**
     * Redirects and logs a short url identified by its [id].
     *
     * **Note**: Delivery of use cases [RedirectUseCase] and [LogClickUseCase].
     */
    fun redirectTo(id: String, request: HttpServletRequest): ResponseEntity<Void>

    /**
     * Creates a short url from details provided in [data].
     *
     * **Note**: Delivery of use case [CreateShortUrlUseCase].
     */
    fun shortener(data: ShortUrlDataIn, request: HttpServletRequest): ResponseEntity<ShortUrlDataOut>

    /**
     * Dumps info about short url identified by its [id].
     *
     * **Note**: Delivery of use case [GetInfoShortenedURL].
     */
    fun getURLinfo(id: String): ResponseEntity<ShortUrlInfoData>

}

/**
 * Data required to create a short url.
 */
data class ShortUrlDataIn(
    val url: String,
    val sponsor: String? = null
)

/**
 * Data returned after the creation of a short url.
 */
data class ShortUrlDataOut(
    val url: URI? = null,
    val properties: Map<String, Any> = emptyMap()
)

/**
 * Data returned after getting the info of a shortened URL.
 */
data class ShortUrlInfoData(
    val numClicks: Int,
    val creationDate: String,
    val uriDestino: URI
)


/**
 * The implementation of the controller.
 *
 * **Note**: Spring Boot is able to discover this [RestController] without further configuration.
 */
@RestController
class UrlShortenerControllerImpl(
    val redirectUseCase: RedirectUseCase,
    val logClickUseCase: LogClickUseCase,
    val createShortUrlUseCase: CreateShortUrlUseCase,
    val infoShortUrlUseCase: InfoShortUrlUseCase
) : UrlShortenerController {

    @GetMapping("/tiny-{id:.*}")
    override fun redirectTo(@PathVariable id: String, request: HttpServletRequest): ResponseEntity<Void> =
        redirectUseCase.redirectTo(id).let {
            logClickUseCase.logClick(id, ClickProperties(ip = request.remoteAddr))
            val h = HttpHeaders()
            h.location = URI.create(it.target)
            //println("redirecttttt")
            ResponseEntity<Void>(h, HttpStatus.valueOf(it.mode))
        }

    @PostMapping("/api/link", consumes = [ MediaType.APPLICATION_FORM_URLENCODED_VALUE ])
    override fun shortener(data: ShortUrlDataIn, request: HttpServletRequest): ResponseEntity<ShortUrlDataOut> =
        createShortUrlUseCase.create(
            url = data.url,
            data = ShortUrlProperties(
                ip = request.remoteAddr,
                sponsor = data.sponsor
            )
        ).let {
            //println(it.hash)
            val h = HttpHeaders()
            val url = linkTo<UrlShortenerControllerImpl> { redirectTo(it.hash, request) }.toUri()
            h.location = url
            val response = ShortUrlDataOut(
                url = url,
                properties = mapOf(
                    "safe" to it.properties.safe
                )
            )
            ResponseEntity<ShortUrlDataOut>(response, h, HttpStatus.CREATED)
        }

    //idea: cambiar {id} por {id:^[a-z][a-z0-9][a-z0-9][a-z0-9][a-z0-9][a-z0-9][a-z0-9][a-z0-9]$}
    //le decimos al controlador que el id empieza por letra min y que tiene 8 caracteres.
    @GetMapping("/info/{id}")
    override fun getURLinfo(@PathVariable id: String): ResponseEntity<ShortUrlInfoData> {
        infoShortUrlUseCase.info(id).let {
            val h = HttpHeaders()
            val response = ShortUrlInfoData (numClicks = it.clicks, creationDate=it.created, uriDestino=URI.create(it.uri))
            return ResponseEntity<ShortUrlInfoData>(response, h, HttpStatus.OK)
        }
    }
}
