# Alejandría

Repositorio de extensiones para **Shiori**, con el mismo formato que Mihon, Komikku y Tachiyomi, así que también funciona en esas apps.

**Añadirlo en Shiori:** Extensiones → Repositorios → `Saerling/Alejandria`

## Crear una extensión desde el celular (sin código)

1. En GitHub abre **Actions → Nueva extensión → Run workflow**.
2. Escribe el nombre de la web, su dirección y el idioma, y marca si es +18.
3. Espera unos 3 minutos. La extensión aparece en Shiori. Si no sale, recarga la lista.

Para cambiar algo de una extensión, edita su `src/<idioma>/<nombre>/extension.properties` y **súbele 1 a `version`**.

## Cómo funciona por dentro

Cada extensión es solo una carpeta con un `extension.properties`:

```properties
nombre=Manhwa Online
url=https://manhwa-online.com
version=1
nsfw=true
# opcionales
biblioteca=/manga/       # página con todas las obras
recientes=/              # últimas actualizaciones
busqueda=/?s={q}         # búsqueda ({q} = lo buscado)
imagenes=.reading-content img   # selector de las imágenes del capítulo
```

El motor común (`core/src/alejandria/core/FuenteFacil.kt`) no depende de las clases CSS de la web. Reconoce las obras y los capítulos por la forma de sus enlaces (`/manga/<obra>/` y `/manga/<obra>/<capítulo>/`), así que sirve para la mayoría de las webs de manga y manhwa (las de WordPress con el tema Madara, las hechas a medida…) y aguanta los rediseños.

**Si una web necesita algo especial**, crea `src/<idioma>/<nombre>/src/.../Fuente.kt` con una clase que herede de `FuenteFacil` y sobrescribe solo lo que haga falta (por ejemplo `pageListParse`). Si la carpeta tiene código, se usa ese en vez del generado.

## Compilar en tu PC (opcional)

```bash
python3 tools/nueva.py "Nombre" https://web.com es --nsfw   # crear
./gradlew assembleRelease                                  # compilar
```

## Firma

Las APK se firman en GitHub Actions con dos secretos (Settings → Secrets and variables → Actions):

- `FIRMA_JKS`: el almacén de claves en base64
- `FIRMA_CLAVE`: su contraseña

No los cambies nunca. Si cambia la firma, Shiori deja de aceptar las actualizaciones de las extensiones ya instaladas.

## Ramas

- `main`: el código.
- `repo`: lo que lee la app (`index.min.json`, `repo.json`, `apk/`, `icon/`). La genera la Action, no la edites a mano.
