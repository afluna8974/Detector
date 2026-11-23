# Obtener el APK sin Android Studio

GitHub puede compilar la app por ti, gratis. Necesitas una cuenta de GitHub y
un computador con navegador.

1. Entra a github.com e inicia sesión. Crea un repositorio nuevo (botón **New**),
   privado o público, con cualquier nombre.
2. En el repositorio, elige **Add file > Upload files**.
3. Descomprime el zip y arrastra **todo el contenido** de la carpeta
   `DetectorCampoMagnetico` (no la carpeta misma) a la página. Incluye la carpeta
   oculta `.github`. Pulsa **Commit changes**.
4. Abre la pestaña **Actions**. Verás el trabajo "Compilar APK" en marcha. Tarda
   entre 3 y 6 minutos la primera vez.
5. Cuando termine con una marca verde, entra a ese trabajo y baja hasta
   **Artifacts**. Descarga `detector-magnetico-apk` (viene dentro de un zip).
6. Descomprime ese zip, pasa `app-debug.apk` al celular y ábrelo. Android pide
   permiso para instalar apps de fuentes desconocidas la primera vez.

Si la pestaña Actions no muestra el trabajo, la carpeta `.github` no se subió.
Crea el archivo a mano: **Add file > Create new file**, escribe como nombre
`.github/workflows/build-apk.yml` y pega el contenido del archivo del mismo nombre
que viene en el proyecto. Después, en Actions, elige "Compilar APK" y pulsa
**Run workflow**.

Si el trabajo falla en rojo, abre el paso que falló, copia el error y envíamelo.
