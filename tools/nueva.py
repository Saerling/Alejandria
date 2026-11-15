"""Crea una extensión nueva.
Uso: python3 tools/nueva.py "Nombre de la web" https://web.com es [--nsfw]"""
import pathlib, re, sys, unicodedata
sys.path.insert(0, str(pathlib.Path(__file__).parent))
from icono import crear

args = [a for a in sys.argv[1:] if not a.startswith("--")]
if len(args) < 2:
    sys.exit(__doc__)
nombre, url = args[0].strip(), args[1].strip().rstrip("/")
idioma = (args[2] if len(args) > 2 else "es").strip().lower()
nsfw = "--nsfw" in sys.argv
ident = re.sub(r"[^a-z0-9]", "", unicodedata.normalize("NFKD", nombre).encode("ascii", "ignore").decode().lower())
if not ident or ident[0].isdigit():
    ident = "w" + ident
if not url.startswith("http"):
    url = "https://" + url
# Validación: evita rutas fuera de src/ e inyección de líneas en extension.properties
url = re.sub(r"^http://", "https://", url)
if not re.fullmatch(r"[a-z]{2,3}(-[a-z]{2})?|all", idioma):
    sys.exit("Idioma inválido (usa es, en, pt-br...)")
if not re.fullmatch(r"https://[A-Za-z0-9.-]+(:\d+)?(/[\w\-./%~]*)?", url):
    sys.exit("Dirección inválida")
if len(nombre) > 60 or re.search(r"[\r\n=\\]", nombre):
    sys.exit("Nombre inválido (máx. 60 caracteres, sin saltos de línea ni '=')")
carpeta = pathlib.Path(__file__).resolve().parent.parent / "src" / idioma / ident
if carpeta.exists():
    sys.exit(f"Ya existe {carpeta}")
(carpeta / "res/mipmap-xxxhdpi").mkdir(parents=True)
(carpeta / "extension.properties").write_text(f"""nombre={nombre}
url={url}
# Súbelo en 1 cada vez que cambies algo, para que Shiori ofrezca la actualización.
version=1
nsfw={'true' if nsfw else 'false'}

# Opcionales: descoméntalos (quita el #) si la web no usa los valores normales.
# Página con todas las obras:
#biblioteca=/manga/
# Página de últimas actualizaciones:
#recientes=/
# Búsqueda ({{q}} = lo que se busca):
#busqueda=/?s={{q}}
# Selector CSS de las imágenes de un capítulo:
#imagenes=.reading-content img
""", encoding="utf-8")
crear(nombre, carpeta / "res/mipmap-xxxhdpi/ic_launcher.png")
print(f"Creada src/{idioma}/{ident}")
