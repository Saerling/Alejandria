package alejandria.core

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Fuente "fácil": sirve para la mayoría de webs de manga/manhwa sin escribir código.
 *
 * En vez de depender de clases CSS (que cambian cada vez que la web se rediseña) reconoce
 * las obras y los capítulos por la FORMA de sus enlaces:
 *   obra:     https://web.com/manga/<obra>/
 *   capítulo: https://web.com/manga/<obra>/<capitulo>/
 *
 * Si una web necesita algo distinto, crea una clase que herede de esta y sobrescribe solo
 * lo que haga falta (las propiedades "open" de abajo o cualquier función).
 */
abstract class FuenteFacil : HttpSource() {

    override val supportsLatest = true

    /** Página con el listado completo de obras (la "biblioteca"). */
    open val rutaBiblioteca = "/manga/"

    /** Página con las últimas actualizaciones. */
    open val rutaRecientes = "/"

    /** Búsqueda; {q} se reemplaza por lo que se busca. */
    open val rutaBusqueda = "/?s={q}"

    /** Primera parte de la ruta de las obras: /manga/, /manhwa/, /series/... */
    open val prefijosObra = listOf("manga", "mangas", "manhwa", "manhwas", "series", "serie", "comic", "comics", "obra", "obras", "webtoon", "webtoons", "titulo", "title")

    /** Imágenes de un capítulo. Si nada coincide se buscan solas. */
    open val selectorImagenes =
        ".reading-content img, #readerarea img, .chapter-content img, .reader-area img, #chapter-images img, .reader img, .page-break img"

    /** Palabras que nunca son una obra (paginación, filtros...). */
    open val noSonObras = setOf("page", "pagina", "feed", "genero", "generos", "genre", "genres", "tag", "tags", "estado", "status", "year", "anio", "author", "autor", "filter", "filtro", "list", "lista")

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun getFilterList() = FilterList()

    // ───────────────────────── Listados ─────────────────────────

    // Dirección de la página siguiente que se vio en cada listado (así se respeta la paginación real de la web).
    private val siguientes = ConcurrentHashMap<String, String>()

    private fun listadoRequest(clave: String, ruta: String, page: Int): Request {
        val url = if (page == 1) absoluta(ruta) else siguientes["$clave#$page"] ?: paginar(absoluta(ruta), page)
        return GET(url, headers)
    }

    /** Dirección de la página N si la web no la indica (WordPress usa /page/N/). */
    protected open fun paginar(url: String, page: Int): String {
        val u = url.toHttpUrlOrNull() ?: return url
        return if (u.querySize > 0 && u.encodedPath.trimEnd('/').isEmpty()) {
            u.newBuilder().addEncodedPathSegments("page/$page/").build().toString()
        } else if (u.querySize > 0) {
            u.newBuilder().setQueryParameter("page", page.toString()).build().toString()
        } else {
            u.toString().trimEnd('/') + "/page/$page/"
        }
    }

    private fun listadoParse(clave: String, response: Response): MangasPage {
        val doc = html(response)
        val page = paginaDe(response.request.url.toString())
        val mangas = obrasEn(doc)
        val sig = siguienteDe(doc, page)
        if (sig != null) siguientes["$clave#${page + 1}"] = sig
        return MangasPage(mangas, sig != null && mangas.isNotEmpty())
    }

    override fun popularMangaRequest(page: Int) = listadoRequest("bib", rutaBiblioteca, page)
    override fun popularMangaParse(response: Response) = listadoParse("bib", response)

    override fun latestUpdatesRequest(page: Int) = listadoRequest("rec", rutaRecientes, page)
    override fun latestUpdatesParse(response: Response) = listadoParse("rec", response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val ruta = rutaBusqueda.replace("{q}", URLEncoder.encode(query.trim(), "UTF-8"))
        return listadoRequest("bus:$query", ruta, page)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val q = response.request.url.queryParameterValues("s").firstOrNull()
            ?: response.request.url.queryParameterValues("q").firstOrNull().orEmpty()
        return listadoParse("bus:$q", response)
    }

    private fun paginaDe(url: String): Int =
        Regex("""(?:/page/|[?&]page=|/pagina/)(\d+)""").find(url)?.groupValues?.get(1)?.toIntOrNull() ?: 1

    /** Busca el enlace a la página siguiente del listado. */
    protected open fun siguienteDe(doc: Document, page: Int): String? {
        doc.selectFirst("a[rel=next], link[rel=next], a.next, .pagination .next a, a.next-page")
            ?.absUrl("href")?.takeIf { it.isNotBlank() }?.let { return it }
        val n = page + 1
        return doc.select("a[href]").map { it.absUrl("href") }.firstOrNull { href ->
            mismoSitio(href) && Regex("""(?:/page/|[?&]page=|/pagina/)$n(?:\D|$)""").containsMatchIn(href)
        }
    }

    /** Todas las obras enlazadas en una página, sin repetir, en el orden en que aparecen. */
    protected open fun obrasEn(doc: Document): List<SManga> {
        val obras = LinkedHashMap<String, SManga>()
        for (a in doc.select("a[href]")) {
            val ruta = rutaDe(a.absUrl("href")) ?: continue
            val slug = slugObra(ruta) ?: continue
            if (a.parents().any { it.tagName() == "nav" || it.tagName() == "footer" }) continue
            val m = obras.getOrPut(slug) { SManga.create().apply { url = "/" + ruta.trim('/') + "/" ; title = "" } }
            if (m.title.isBlank()) m.title = tituloDe(a)
            if (m.thumbnail_url.isNullOrBlank()) m.thumbnail_url = portadaDe(a)
        }
        return obras.values.filter { it.title.isNotBlank() }
    }

    private fun tituloDe(a: Element): String {
        val tarjeta = tarjetaDe(a)
        val candidatos = listOfNotNull(
            a.attr("title"),
            a.selectFirst("h1, h2, h3, h4, h5, .title, .name, [class*=title]")?.text(),
            a.ownText(),
            a.selectFirst("img")?.attr("alt"),
            tarjeta?.selectFirst("h2, h3, h4, h5, .title, [class*=title]")?.text(),
        )
        return candidatos.map { limpiarTitulo(it) }.firstOrNull { it.length > 1 } ?: ""
    }

    protected open fun limpiarTitulo(t: String) = t
        .replace(Regex("""★\s*[\d.,]+"""), "")
        .replace(Regex("""^(Completo|Completado|Finalizado|Nuevo|NUEVO|Hot|En curso)\s+""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s*(portada|cover)$""", RegexOption.IGNORE_CASE), "")
        .trim()

    private fun portadaDe(a: Element): String? =
        imagenDe(a.selectFirst("img")) ?: tarjetaDe(a)?.selectFirst("img")?.let { imagenDe(it) }

    // El bloque que contiene la portada y el título de una obra (sube hasta 3 niveles).
    private fun tarjetaDe(a: Element): Element? {
        var e: Element? = a.parent()
        repeat(3) {
            val x = e ?: return null
            val slugs = x.select("a[href]").mapNotNull { rutaDe(it.absUrl("href"))?.let(::slugObra) }.toSet()
            if (slugs.size > 1) return null
            if (x.selectFirst("img") != null) return x
            e = x.parent()
        }
        return null
    }

    // ───────────────────────── Obra ─────────────────────────

    override fun mangaDetailsParse(response: Response): SManga {
        val doc = html(response)
        val principal = doc.selectFirst("main, article, #content, .content, body") ?: doc.body()
        return SManga.create().apply {
            title = limpiarTitulo(
                doc.selectFirst("h1")?.text()
                    ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.substringBefore(" - ")?.substringBefore(" | ")
                    ?: "",
            )
            thumbnail_url = doc.selectFirst("meta[property=og:image]")?.attr("abs:content")?.ifBlank { null }
                ?: principal.selectFirst("img")?.let { imagenDe(it) }
            description = listOfNotNull(
                principal.selectFirst(".summary__content, .description, .sinopsis, .synopsis, .entry-content > p, [itemprop=description]")?.text(),
                doc.selectFirst("meta[property=og:description]")?.attr("content"),
                doc.selectFirst("meta[name=description]")?.attr("content"),
            ).firstOrNull { it.isNotBlank() }
            genre = principal.select(
                "a[href*=/genero/], a[href*=/generos/], a[href*=/genre/], a[href*=/genres/], a[href*=genero=], a[href*=genre=], a[rel=tag]",
            ).map { it.text().trim() }.filter { it.isNotEmpty() && it.length < 30 }.distinct().joinToString().ifBlank { null }
            author = Regex(""""author"\s*:\s*\{[^}]*"name"\s*:\s*"([^"]+)"""").find(doc.html())?.groupValues?.get(1)
            val texto = (principal.selectFirst("h1")?.parent()?.text() ?: principal.text()).take(3000)
            status = when {
                Regex("""\b(Completo|Completado|Finalizado|Terminado|Completed)\b""", RegexOption.IGNORE_CASE).containsMatchIn(texto) -> SManga.COMPLETED
                Regex("""\b(En curso|En emisión|Emisión|Publicándose|Activo|Ongoing)\b""", RegexOption.IGNORE_CASE).containsMatchIn(texto) -> SManga.ONGOING
                Regex("""\b(Pausado|En pausa|Hiatus)\b""", RegexOption.IGNORE_CASE).containsMatchIn(texto) -> SManga.ON_HIATUS
                Regex("""\b(Cancelado|Abandonado|Cancelled)\b""", RegexOption.IGNORE_CASE).containsMatchIn(texto) -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }
    }

    // ───────────────────────── Capítulos ─────────────────────────

    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = html(response)
        val slug = rutaDe(response.request.url.toString())?.let(::slugObra)
        val caps = capitulosEn(doc, slug)
        if (caps.isNotEmpty()) return caps
        // Plan B: webs con el tema Madara cargan la lista aparte.
        val ajax = response.request.url.toString().trimEnd('/') + "/ajax/chapters/"
        return runCatching {
            client.newCall(POST(ajax, headers)).execute().use { capitulosEn(html(it), slug) }
        }.getOrDefault(emptyList())
    }

    protected open fun capitulosEn(doc: Document, slug: String?): List<SChapter> {
        val caps = LinkedHashMap<String, SChapter>()
        for (a in doc.select("a[href]")) {
            val ruta = rutaDe(a.absUrl("href")) ?: continue
            val partes = ruta.trim('/').split('/')
            if (partes.size != 3 || partes[0].lowercase() !in prefijosObra) continue
            if (slug != null && partes[1] != slug) continue
            if (partes[2].lowercase() in noSonObras || partes[2].startsWith("#")) continue
            val url = "/" + partes.joinToString("/") + "/"
            if (caps.containsKey(url)) continue
            val texto = a.text().ifBlank { a.attr("title") }
            val contexto = a.parent()?.text().orEmpty()
            caps[url] = SChapter.create().apply {
                this.url = url
                name = nombreCapitulo(texto, partes[2])
                chapter_number = numeroDe(name) ?: numeroDe(partes[2].replace('-', ' ')) ?: -1f
                date_upload = fechaDe(texto).takeIf { it > 0 } ?: fechaDe(contexto)
            }
        }
        // De más nuevo a más viejo.
        return caps.values.sortedByDescending { it.chapter_number }
    }

    protected open fun nombreCapitulo(texto: String, slugCap: String): String {
        val limpio = texto
            .replace(Regex("""\b(NUEVO|NEW|Nuevo)\b"""), "")
            .replace(Regex("""\bhace\s+\d+\s+\w+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\b(ayer|hoy)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\d{1,2}/\d{1,2}/\d{2,4}"""), "")
            .replace(Regex("""\s+"""), " ").trim()
        return limpio.ifBlank { slugCap.replace('-', ' ').replaceFirstChar { it.uppercase() } }
    }

    private fun numeroDe(t: String): Float? =
        Regex("""(?:cap[ií]tulo|chapter|cap\.?|ch\.?|episodio|ep\.?)\s*(\d+(?:[.,]\d+)?)""", RegexOption.IGNORE_CASE)
            .find(t)?.groupValues?.get(1)?.replace(',', '.')?.toFloatOrNull()
            ?: Regex("""(\d+(?:[.,]\d+)?)""").find(t)?.groupValues?.get(1)?.replace(',', '.')?.toFloatOrNull()

    /** Entiende "hace 3 días", "ayer", "NUEVO", "12/05/2025" y "12 de mayo de 2025". */
    protected open fun fechaDe(t: String): Long {
        val c = Calendar.getInstance()
        Regex("""hace\s+(\d+)\s+(seg|min|hora|h\b|d[ií]a|semana|mes|año|ano)""", RegexOption.IGNORE_CASE).find(t)?.let {
            val n = it.groupValues[1].toInt()
            val u = it.groupValues[2].lowercase()
            when {
                u.startsWith("seg") -> c.add(Calendar.SECOND, -n)
                u.startsWith("min") -> c.add(Calendar.MINUTE, -n)
                u.startsWith("h") -> c.add(Calendar.HOUR_OF_DAY, -n)
                u.startsWith("d") -> c.add(Calendar.DAY_OF_YEAR, -n)
                u.startsWith("sem") -> c.add(Calendar.WEEK_OF_YEAR, -n)
                u.startsWith("mes") -> c.add(Calendar.MONTH, -n)
                else -> c.add(Calendar.YEAR, -n)
            }
            return c.timeInMillis
        }
        if (Regex("""\bayer\b""", RegexOption.IGNORE_CASE).containsMatchIn(t)) {
            c.add(Calendar.DAY_OF_YEAR, -1); return c.timeInMillis
        }
        if (Regex("""\b(hoy|NUEVO|new)\b""", RegexOption.IGNORE_CASE).containsMatchIn(t)) return c.timeInMillis
        Regex("""\d{1,2}/\d{1,2}/\d{4}""").find(t)?.let {
            return runCatching { SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).parse(it.value)!!.time }.getOrDefault(0L)
        }
        Regex("""\d{1,2} de \p{L}+ de \d{4}""").find(t)?.let {
            return runCatching { SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale("es")).parse(it.value)!!.time }.getOrDefault(0L)
        }
        return 0L
    }

    // ───────────────────────── Páginas ─────────────────────────

    override fun pageListParse(response: Response): List<Page> {
        val doc = html(response)
        var imgs = doc.select(selectorImagenes).mapNotNull { imagenDe(it) }
        if (imgs.isEmpty()) {
            val todas = doc.select("img").filter { img -> img.parents().none { it.tagName() in setOf("header", "nav", "footer", "aside") } }
            // Si las imágenes dicen "página N" en su texto alternativo, son esas.
            val numeradas = todas.filter { Regex("""(p[áa]gina|page|p\.)\s*\d+""", RegexOption.IGNORE_CASE).containsMatchIn(it.attr("alt")) }
            imgs = (numeradas.ifEmpty { todas }).mapNotNull { imagenDe(it) }.filterNot { pareceAdorno(it) }
        }
        if (imgs.isEmpty()) {
            // Último recurso: lectores que guardan la lista de imágenes en un script.
            imgs = Regex("""https?:\\?/\\?/[^"'\s]+?\.(?:jpe?g|png|webp|avif)""", RegexOption.IGNORE_CASE)
                .findAll(doc.select("script").joinToString("\n") { it.data() })
                .map { it.value.replace("\\/", "/") }.filterNot { pareceAdorno(it) }.toList()
        }
        return imgs.distinct().mapIndexed { i, url -> Page(i, imageUrl = url) }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    private fun pareceAdorno(url: String) =
        Regex("""logo|avatar|icon|favicon|banner|emoji|gravatar|placeholder|loading|\.svg""", RegexOption.IGNORE_CASE).containsMatchIn(url)

    // ───────────────────────── Utilidades ─────────────────────────

    protected fun html(response: Response): Document =
        Jsoup.parse(response.body.string(), response.request.url.toString())

    /** Dirección real de una imagen (las webs usan carga diferida con muchos nombres distintos). */
    protected open fun imagenDe(img: Element?): String? {
        img ?: return null
        for (attr in listOf("data-src", "data-lazy-src", "data-original", "data-cfsrc", "data-url", "src")) {
            val v = img.attr("abs:$attr").trim()
            if (v.isNotEmpty() && !v.startsWith("data:")) return v
        }
        val set = img.attr("data-srcset").ifBlank { img.attr("srcset") }
        return set.split(',').map { it.trim().substringBefore(' ') }.lastOrNull { it.isNotBlank() }
            ?.let { img.absUrl("src").ifBlank { null }?.toHttpUrlOrNull()?.resolve(it)?.toString() ?: it }
    }

    private fun absoluta(ruta: String) = if (ruta.startsWith("http")) ruta else baseUrl + "/" + ruta.trimStart('/')

    private val host by lazy { baseUrl.toHttpUrlOrNull()?.host?.removePrefix("www.").orEmpty() }

    private fun mismoSitio(url: String) = url.toHttpUrlOrNull()?.host?.removePrefix("www.") == host

    /** Ruta de un enlace del mismo sitio (sin dominio ni parámetros), o null si es de otro sitio. */
    protected fun rutaDe(url: String): String? {
        val u = url.toHttpUrlOrNull() ?: return null
        if (u.host.removePrefix("www.") != host) return null
        return u.encodedPath
    }

    /** Identificador de la obra si la ruta es /<prefijo>/<obra>/, o null. */
    protected open fun slugObra(ruta: String): String? {
        val partes = ruta.trim('/').split('/')
        if (partes.size != 2 || partes[0].lowercase() !in prefijosObra) return null
        val slug = partes[1]
        if (slug.isBlank() || slug.lowercase() in noSonObras) return null
        return slug
    }
}
