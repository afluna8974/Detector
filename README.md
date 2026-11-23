# Detector de campo magnético (Android)

App nativa en Kotlin que lee el magnetómetro del celular en tiempo real y avisa
cuando algo altera el campo magnético cerca: imanes, bocinas, motores,
cargadores, baterías y otros aparatos electrónicos.

## Cómo compilarla

Necesitas Android Studio (Ladybug 2024.2 o más nuevo).

1. Abre Android Studio y elige **Open**. Selecciona la carpeta `DetectorCampoMagnetico`.
2. Espera a que termine la sincronización de Gradle. Si pide instalar el SDK de
   Android 35, acepta.
3. Para probarla directo en tu celular, activa la **depuración USB**
   (Ajustes > Opciones de desarrollador), conéctalo por cable y pulsa **Run**.
4. Para sacar un APK que puedas instalar o compartir, usa
   **Build > Build Bundle(s) / APK(s) > Build APK(s)**. El archivo queda en
   `app/build/outputs/apk/debug/app-debug.apk`.
5. Pasa el APK al celular y ábrelo. Android pide permiso para instalar apps de
   fuentes desconocidas la primera vez.

Por línea de comandos: define `ANDROID_HOME` con la ruta de tu SDK y ejecuta
`./gradlew assembleDebug`.

## Cómo se usa

1. Pulsa **Iniciar y calibrar** con el celular quieto y lejos de otros aparatos.
   La calibración dura 3 segundos.
2. Acerca el celular despacio a lo que quieres revisar. La desviación sube
   cuanto más cerca esté la fuente.
3. El **umbral de alerta** define desde cuántos µT de desviación salta el aviso
   (débil) y a partir de 3 veces ese valor el aviso fuerte.
4. Si cambias de lugar, pulsa **Recalibrar**.

## Qué hace el código

- `Detector.kt`: calibra la base y calcula la desviación. Usa la magnitud total
  del campo, que no cambia al girar el celular.
- `MainActivity.kt`: lee el sensor, dibuja la pantalla y dispara el sonido y la
  vibración.
- `Views.kt`: la barra y el gráfico de las últimas muestras.

## Límites

- Alcance de pocos centímetros.
- No capta radio (Wi-Fi, Bluetooth, llamadas).
- No distingue un teléfono de otro objeto metálico o imantado.
- Si Android marca el sensor con poca precisión, la app lo avisa. Mueve el
  celular dibujando un ocho en el aire para recalibrar el sensor.
