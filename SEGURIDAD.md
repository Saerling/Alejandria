# Seguridad — Alejandría

Repositorio de extensiones para Shiori + panel web (GitHub Pages) para crearlas.

| # | Punto | Estado |
|---|---|---|
| 1 | Ocultar API keys | ✅ No hay claves en el código. La firma (`FIRMA_JKS`, `FIRMA_CLAVE`) vive en GitHub Secrets. |
| 2 | Secretos fuera de Git | ✅ Historial revisado: sin keystores ni claves. |
| 3 | Key pública para DB | N/A — no hay base de datos. |
| 4 | Row-Level Security | N/A. |
| 5 | Encriptar datos sensibles | ✅ El índice publica la huella SHA-256 del certificado para que Shiori verifique las extensiones. |
| 6 | Forzar autenticación | ✅ El panel exige un token de GitHub con permiso de escritura; el workflow solo lo lanzan colaboradores. |
| 7 | Restringir acceso | ✅ Token *fine-grained* limitado a este repo (contenido + acciones) y con caducidad. |
| 8 | Bloquear manipulación de campos | ✅ `nueva.py` valida idioma, URL y nombre (sin rutas `../` ni líneas inyectadas). |
| 9 | Session cookies | ✅ Sin cookies. El token se guarda en `sessionStorage` (se borra al cerrar la pestaña). |
| 10 | Hashear contraseñas | N/A. |
| 11 | Rate limiting | ✅ Lo aplica la API de GitHub. |
| 12 | Protección contra bots | ✅ Sin formularios públicos; solo colaboradores. |
| 13 | Queries parametrizadas | ✅ Los inputs del workflow pasan como variables de entorno, nunca interpolados en el script. |
| 14 | Validar inputs | ✅ Ver punto 8. |
| 15 | Sanitizar contenido | ✅ El panel escapa todo con `esc()` antes de pintarlo. |
| 16 | Restringir archivos | ✅ El panel solo escribe dentro de `src/`. |
| 17 | Devolver solo lo necesario | ✅ El índice solo expone nombre, versión, idioma e icono. |
| 18 | Security headers | ✅ CSP (`connect-src` solo GitHub), `no-referrer`. |
| 19 | Forzar HTTPS | ✅ GitHub Pages + URLs de webs forzadas a `https://`. |
| 20 | Escanear dependencias | ✅ Dependabot (Gradle y Actions). |
