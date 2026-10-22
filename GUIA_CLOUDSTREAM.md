# 📱 GUÍA TÉCNICA: PROVEEDOR CLOUDSTREAM (RAAVA)

Este documento detalla la arquitectura, ingeniería inversa, estructura de código y proceso de compilación del proveedor de contenido para **CloudStream 3**.

---

## 1. 🏗️ Arquitectura y Estructura del Proyecto

El proveedor se ubica como un submódulo Gradle dentro del repositorio:

```text
RAVAA EXT/
├── HackstoreProvider/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   └── kotlin/com/raava/
│   │       ├── HackstoreProvider.kt         <-- Lógica principal de scraping y reproducción
│   │       └── HackstoreProviderPlugin.kt   <-- Registro del plugin en CloudStream
│   └── build.gradle.kts                     <-- Configuración Gradle del módulo
├── build.gradle.kts                         <-- Configuración raíz de CloudStream
└── gradlew.bat                              <-- Script de compilación para Windows
```

---

## 2. 🔍 Descubrimiento de la API REST Interna

A diferencia de la mayoría de páginas web que requieren parsear HTML con selectores CSS lentos y propensos a bloqueos por Cloudflare, se descubrió que el sitio opera mediante una **API REST JSON interna** completamente abierta:

| Endpoint | Parámetros | Propósito |
| :--- | :--- | :--- |
| `GET /api/rest/listing` | `post_type={movies\|tvshows\|animes}&page={n}` | Catálogo de página principal |
| `GET /api/rest/search` | `post_type={type}&query={texto}` | Búsqueda por título |
| `GET /api/rest/single` | `post_name={slug}&post_type={type}` | Metadatos y detalles de una película o serie |
| `GET /api/rest/episodes`| `post_id={show_id}` | Lista de temporadas y episodios |
| `GET /api/rest/player`  | `post_id={item_id}` | Servidores de streaming en línea (Vimeos, Goodstream, etc.) |
| `GET /api/rest/downloads`| `post_id={item_id}` | Enlaces directos y Torrents P2P |

> [!IMPORTANT]
> **Campo `_id` en JSON:** En las respuestas de la API, el identificador único viene nombrado como `_id` con guión bajo. En Kotlin Jackson es indispensable mapearlo con `@JsonProperty("_id") val id: Long?`.

---

## 3. 🔓 Desempaquetado de Reproductores (Reverse Engineering)

Los servidores de vídeo principales devuelven código JavaScript ofuscado mediante `eval(function(p,a,c,k,e,d)...)` (Dean Edwards Packer).

### Proceso de extracción implementado en `HackstoreProvider.kt`:
1. **Detección y Petición:** Se solicita la URL incrustada del servidor con encabezados `Referer` y `User-Agent` de navegador.
2. **Desempaquetado:** Se utiliza la función nativa de CloudStream `getAndUnpack(html)` para evaluar y desempaquetar el script ofuscado.
3. **Extracción de Stream M3U8:** Mediante expresiones regulares:
   ```kotlin
   val m3u8 = Regex("""file:\s*["']([^"']+\.m3u8[^"']*)["']""").find(unpacked)?.groupValues?.getOrNull(1)
   ```
4. **Extracción de Subtítulos VTT:**
   ```kotlin
   Regex("""file:\s*["']([^"']+\.vtt)["'],\s*label:\s*["']([^"']+)["']""").findAll(unpacked)
   ```
5. **Inyección de Encabezados:** Se retorna un `ExtractorLink` tipo `ExtractorLinkType.M3U8` con las cabeceras `Referer` y `Origin` correctas para evitar errores 403 Forbidden.

---

## 4. ⚙️ Cómo Compilar el Proveedor (.cs3)

Para generar el archivo ejecutable del plugin compatible con CloudStream:

1. Abre una terminal de comandos en la raíz del proyecto (`RAVAA EXT`).
2. Ejecuta:
   ```cmd
   .\gradlew.bat make
   ```
3. El archivo generado se ubicará en:
   ```text
   build/HackstoreProvider.cs3
   ```
4. Este archivo `.cs3` (o el repositorio completo enlazado en GitHub) es el que se instala en CloudStream a través de **Ajustes > Extensiones > Añadir Repositorio**.

---

## 5. 🛡️ Mantenimiento y Actualizaciones Futuras

Si el sitio web cambia de dominio o sufren caídas:
- Cambiar la variable `mainUrl` en `HackstoreProvider.kt` (línea 14):
  ```kotlin
  override var mainUrl = "https://nuevo-dominio.com"
  ```
- No es necesario tocar los selectores CSS ya que la estructura de la API REST `/api/rest/...` se mantiene fija entre cambios de dominio.
