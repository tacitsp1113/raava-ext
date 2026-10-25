# 🗡️ TACIT EXT (CloudStream 3 Latino Extension Suite)

Suite oficial de proveedores y extractores en **Kotlin** para la aplicación **CloudStream 3**, optimizada para streaming en **Español Latino**.

---

## ⚡ Novedades y Características Implementadas

* **Rebranding Integral a TACIT:**
  * Identidad limpia con el icono oficial de Tacit Ronin (`logo.png`).
  * Repositorio oficial renombrado a **TACIT** (`repo.json` y `plugins.json`).
  * Autoría y firmas unificadas bajo **TACIT**.

* **Decodificador Matemático de VOE (`VoeExtractor.kt`):**
  * Implementación nativa en Kotlin del algoritmo de descifrado matemático de VOE:
    1. Desplazamiento rotacional César/ROT13 modificado sobre el script empaquetado.
    2. Eliminación de secuencias de ruido (*junk patterns*).
    3. Decodificación Base64 (paso 1).
    4. Desplazamiento aritmético de bytes **`-3`** (`(byte - 3 + 256) % 256`).
    5. Inversión de bytes y decodificación Base64 (paso 2) $\rightarrow$ Extracción directa de URLs `.m3u8` / `.mp4`.
  * Registrado en todos los plugins (`registerExtractorAPI`) para sustituir el extractor predeterminado de CloudStream y evitar errores de reproducción.

* **Desencriptador Criptográfico Embed69:**
  * Resolución nativa en Kotlin de retos *Proof-of-Work* (PoW SHA-256) en menos de 25 ms.
  * Desencriptación AES-256-CBC con derivación de clave para extraer servidores directos (`VOE`, `Streamwish`, `VidHide`).
  * Extracción con discriminación de idioma (`Latino` vs `Subtitulado`).

* **Normalización de Servidores Espejo (`fixHostsLinks`):**
  * Mapeo y desenmascaramiento automático de dominios camuflados:
    * Espejos de VOE (`repacklab.com`, `fraudulentwelfare.com`, `yodabox.net`, `launchprivilege.com`, `simpelt.com`, `metatube.cc`, `bigwarp.io`, `audioclip.net`, `playvoe.com`, etc.) $\rightarrow$ Redirigidos a `https://voe.sx`.
    * Espejos de StreamWish (`hglink.to`, `swdyu.com`, `cybervynx.com`, `dumbalag.com`) $\rightarrow$ Redirigidos a `https://streamwish.to`.
    * Espejos de VidHide (`mivalyo.com`, `dinisglows.com`, `dhtpre.com`, `morencius.com`) $\rightarrow$ Redirigidos a `https://vidhidepro.com`.

* **Etiquetado de Idiomas (`loadSourceNameExtractor`):**
  * Presenta las opciones en la interfaz de CloudStream con su etiqueta explícita: `Latino [VOE]`, `Latino [Vimeos]`, `Castellano [Streamwish]`, etc., permitiendo elegir el audio deseado directamente desde el selector de enlaces.

---

## 📦 Proveedores Incluidos en la Suite

| Módulo | Nombre en App | Versión | Tipo de Contenido | Dominio Base |
| :--- | :--- | :---: | :--- | :--- |
| `CuevanaProvider` | **Cuevana** | v16 | Películas y Series | `https://cuevana3.sc` |
| `CinecalidadProvider` | **Cinecalidad** | v2 | Películas HD | `https://cinecalidad.am` |
| `HackstoreProvider` | **Hackstore** | v7 | Películas, Series y Anime | `https://hackstore2.com` |
| `PelisPlusProvider` | **PelisPlus** | v1 | Películas y Series Latino | `https://pelisplushd.bz` |
| `RaavaLiveProvider` | **TACIT LIVE** | v5 | Deportes y Canales en Vivo | `https://streamtp99a.sbs` |

---

## 🛠️ Compilación y Publicación Local

Para compilar los paquetes instaladores (`.cs3`) y generar el índice de repositorio `plugins.json`:

```bash
gradlew make makePluginsJson
```

Los binarios generados se ubican en:
* `CuevanaProvider/build/CuevanaProvider.cs3`
* `CinecalidadProvider/build/CinecalidadProvider.cs3`
* `HackstoreProvider/build/HackstoreProvider.cs3`
* `PelisPlusProvider/build/PelisPlusProvider.cs3`
* `RaavaLiveProvider/build/RaavaLiveProvider.cs3`
* `build/plugins.json`

---

## 📲 Instalación en CloudStream

1. Abre **CloudStream 3** en tu dispositivo Android (Celular o TV).
2. Ve a **Ajustes $\rightarrow$ Extensiones $\rightarrow$ Añadir Repositorio**.
3. Ingresa la URL de tu repositorio:
   ```
   https://raw.githubusercontent.com/tacitsp1113/raava-ext/builds/plugins.json
   ```
4. Aparecerá la lista oficial de **TACIT** lista para instalar y actualizar.

---

## 🔮 Pendientes y Hoja de Ruta (Roadmap)

- [ ] **Módulo TioAnime (`TioAnimeProvider`):** Portar el scraper de TioAnime a Kotlin para completar el catálogo de animación japonesa en CloudStream.
- [ ] **Módulo LaMovie / SeriesFlix:** Evaluar e incorporar un proveedor enfocado exclusivamente en streaming 1080p directo sin anuncios.
- [ ] **Unificación de Librería Común:** Mover `VoeExtractor`, `fixHostsLinks` y `loadSourceNameExtractor` a un módulo `CommonLibrary` compartido para eliminar código duplicado entre submódulos.

---

## 🤝 Créditos y Agradecimientos

* Lógica de desencriptación e ingeniería inversa referenciada del proyecto comunitario [Droydr13/pluggin-latino](https://github.com/Droydr13/pluggin-latino), pioneros en documentar el algoritmo de desplazamiento de bytes en VOE y la resolución de Embed69.

---

## 📜 Licencia
GPL-3.0 / MIT © TACIT EXT