"""Genera un icono simple (inicial sobre fondo de color) para una extensión.
Uso: python3 tools/icono.py "Nombre" salida.png"""
import hashlib, sys
from PIL import Image, ImageDraw, ImageFont

def crear(nombre, salida, tam=192):
    h = hashlib.md5(nombre.encode()).digest()
    color = (60 + h[0] % 140, 60 + h[1] % 140, 60 + h[2] % 140)
    img = Image.new("RGBA", (tam, tam), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((0, 0, tam - 1, tam - 1), radius=tam // 4, fill=color)
    letra = (nombre.strip()[:1] or "?").upper()
    try:
        f = ImageFont.truetype("DejaVuSans-Bold.ttf", int(tam * 0.55))
    except OSError:
        f = ImageFont.load_default(size=int(tam * 0.55))
    d.text((tam / 2, tam / 2), letra, font=f, fill="white", anchor="mm")
    img.save(salida)

if __name__ == "__main__":
    crear(sys.argv[1], sys.argv[2])
