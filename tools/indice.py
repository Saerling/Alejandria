"""Arma la carpeta del repositorio (lo que lee Shiori/Mihon/Komikku) a partir de las APK compiladas.
Uso: python3 tools/indice.py <carpeta_salida> <huella_sha256_de_la_firma>"""
import hashlib, json, pathlib, shutil, sys

RAIZ = pathlib.Path(__file__).resolve().parent.parent
salida = pathlib.Path(sys.argv[1]); huella = sys.argv[2] if len(sys.argv) > 2 else ""
(salida / "apk").mkdir(parents=True, exist_ok=True); (salida / "icon").mkdir(exist_ok=True)

def leer_props(f):
    p = {}
    for linea in f.read_text(encoding="utf-8").splitlines():
        linea = linea.strip()
        if linea and not linea.startswith("#") and "=" in linea:
            k, v = linea.split("=", 1); p[k.strip()] = v.strip()
    return p

def id_fuente(nombre, idioma, version_id=1):
    # Igual que HttpSource.generateId
    d = hashlib.md5(f"{nombre.lower()}/{idioma}/{version_id}".encode()).digest()
    return str(int.from_bytes(d[:8], "big") & 0x7FFFFFFFFFFFFFFF)

indice = []
for props in sorted(RAIZ.glob("src/*/*/extension.properties")):
    carpeta = props.parent; idioma = carpeta.parent.name; ext = carpeta.name
    p = leer_props(props)
    apk = carpeta / "build/outputs/apk/release" / f"{idioma}-{ext}-release.apk"
    if not apk.exists():
        print(f"AVISO: falta la APK de {idioma}/{ext}"); continue
    pkg = f"eu.kanade.tachiyomi.extension.{idioma}.{ext}"
    code = int(p.get("version", "1")); version = f"1.4.{code}"
    nombre_apk = f"tachiyomi-{idioma}.{ext}-v{version}.apk"
    shutil.copy(apk, salida / "apk" / nombre_apk)
    icono = carpeta / "res/mipmap-xxxhdpi/ic_launcher.png"
    shutil.copy(icono if icono.exists() else RAIZ / "core/res/mipmap-xxxhdpi/ic_launcher.png", salida / "icon" / f"{pkg}.png")
    indice.append({
        "name": p["nombre"], "pkg": pkg, "apk": nombre_apk, "lang": idioma, "code": code, "version": version,
        "nsfw": 1 if p.get("nsfw", "false").lower() == "true" else 0,
        "sources": [{"name": p["nombre"], "lang": idioma, "id": id_fuente(p["nombre"], idioma), "baseUrl": p["url"].rstrip("/")}],
    })

(salida / "index.min.json").write_text(json.dumps(indice, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
(salida / "index.json").write_text(json.dumps(indice, ensure_ascii=False, indent=2), encoding="utf-8")
(salida / "repo.json").write_text(json.dumps({"meta": {
    "name": "Alejandria", "shortName": "Alejandria", "website": "https://github.com/Saerling/Alejandria",
    "signingKeyFingerprint": huella}}, indent=2), encoding="utf-8")
print(f"{len(indice)} extensiones en el índice")
