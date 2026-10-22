# 🚀 RAAVA STR

Proveedor de streaming y perfiles de vídeo adaptables para Stremio.

---

## ✨ Características

- 🎬 **Múltiples Calidades:** 4K, 1080p, 720p, SD.
- 🔊 **Audio Multi-Canal:** Pistas de audio en alta fidelidad y subtítulos.
- ⚡ **P2P Directo:** Distribución eficiente y sin esperas.
- 🏷️ **Presentación Limpia:** Etiquetado como `RAAVA STR` sin caracteres invasivos.

---

## 🛠️ Ejecución Local

1. Entra a la carpeta del addon:
   ```bash
   cd raava-stremio
   ```
2. Instala las dependencias (si no lo has hecho):
   ```bash
   npm install
   ```
3. Inicia el servidor:
   ```bash
   npm start
   ```
4. Abre en tu navegador:
   - **Landing Page:** [http://localhost:7000/](http://localhost:7000/)
   - **Manifiesto:** [http://localhost:7000/manifest.json](http://localhost:7000/manifest.json)
5. Haz clic en el botón **"Instalar en Stremio"** en la página web o copia el enlace del manifiesto y pégalo en la barra de búsqueda de addons de Stremio.

---

## 🌐 Despliegue Gratis 24/7 en la Nube (Render.com)

Para que el addon esté siempre disponible en cualquier dispositivo (TV, celular, PC) sin depender de tu computadora encendida:

1. Ve a [Render.com](https://render.com/) e inicia sesión con tu cuenta de GitHub.
2. Haz clic en **New +** > **Web Service**.
3. Selecciona tu repositorio de GitHub (`tacitsp1113/raava-ext`).
4. Configura los siguientes campos:
   - **Root Directory:** `raava-stremio`
   - **Environment:** `Node`
   - **Build Command:** `npm install`
   - **Start Command:** `node server.js`
   - **Plan:** Free ($0/mes)
5. Haz clic en **Deploy Web Service**.
6. Render te dará una URL como: `https://raava-stremio.onrender.com`.
7. Abre esa URL en tu navegador y haz clic en **"Instalar en Stremio"**.
