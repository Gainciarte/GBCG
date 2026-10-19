# GBCG

Emulador de **Game Boy y Game Boy Color** para Android. Es un fork de
[GBCC Android](https://github.com/philj56/gbcc-android), de Philip Jones, que usa
el núcleo [GBCC](https://github.com/philj56/gbcc): un emulador escrito en C con
foco en la precisión.

GBCG mantiene el emulador original y se instala **junto a GBCC**, sin
reemplazarlo, porque tiene su propio identificador (`io.github.gainciarte.gbcg`).

## Estado

El fork recién empieza. Por ahora, lo distinto del original es:

- **Nombre, ícono e identificador propios:** la app se llama GBCG, se instala
  aparte de GBCC y tiene su propio ícono (una consola gris con una "G" en la
  pantalla, generado con [`sources/make_icon.py`](sources/make_icon.py)). Los
  colores de la carcasa usan nombres genéricos, y los archivos exportados se llaman
  `gbcg_saves_….zip` y `gbcg_printer_….png`.
- **Lista de archivos:** cada ROM y carpeta tiene un botón ⋮ que abre sus opciones
  (antes solo se abrían manteniendo presionado, lo que también sigue funcionando).
  Ya no se incluye la ROM tutorial de GBCC.
- **Reportes de errores:** cuando el emulador falla, el botón "Send feedback" abre
  el correo con el registro del error, pero sin destinatario. En el original iba al
  correo del autor, y los errores de este fork no deberían llegarle a él.
- **Compilación automática:** la acción de GitHub que arma el APK se actualizó a
  las versiones vigentes de las acciones (`checkout`, `setup-java` y
  `upload-artifact` v4).
- **Errores corregidos** (de una revisión completa del código de la app):
  - Los botones táctiles y la cruceta ya no se quedan apretados cuando el sistema
    corta el toque (un gesto desde el borde, la cortina de notificaciones).
  - "Borrar partida" borra solo las partidas de ese juego: antes borraba también las
    de cualquier juego cuyo nombre empezara igual.
  - La app ya no se cierra al borrar archivos seleccionados después de entrar a otra
    carpeta.
  - Las ROM con extensión en mayúsculas (`.GB`, `.GBC`) aparecen en la lista y se
    pueden importar.
  - Los gatillos L2/R2 de un mando físico ya no se sueltan solos.
  - Los diálogos (crear carpeta, renombrar, trucos, asignar botones del mando,
    sobrescribir partidas importadas) ya no cierran la app al girar el teléfono.
  - El aviso para sobrescribir partidas importadas se abre como corresponde.
  - El sonido de la impresora y el hilo de la cámara se liberan al salir de cada
    juego.
  - Un error en el código en C podía corromper la memoria si fallaba la carga de
    una ROM.

Los cambios que vengan se irán anotando aquí.

## Descargar ![Build APK](https://github.com/Gainciarte/GBCG/actions/workflows/build-apk.yml/badge.svg)

Cada cambio en `master` compila un APK de prueba (*debug*) en GitHub Actions:

1. Entra a la pestaña [Actions](https://github.com/Gainciarte/GBCG/actions) del
   repositorio, con tu cuenta de GitHub iniciada.
2. Abre la última ejecución exitosa de **Build APK**.
3. En **Artifacts**, descarga `gbcg` (un `.zip` con `gbcg-universal-debug.apk`).

Para instalarlo en Android hay que permitir la instalación desde fuentes
desconocidas en el navegador o el explorador de archivos que uses.

## Compilar

Requisitos: JDK 17, el SDK de Android con el NDK `25.2.9519653`, CMake y Ninja.

```sh
git clone --recurse-submodules https://github.com/Gainciarte/GBCG.git
cd GBCG
./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/` (`gbcg-universal-debug.apk` sirve
para cualquier procesador).

Si ya clonaste sin submódulos:

```sh
git submodule update --init --recursive
```

## Mantenerse al día con el original

El repositorio original se agrega como `upstream`:

```sh
git remote add upstream https://github.com/philj56/gbcc-android.git   # solo la primera vez
git fetch upstream
git merge upstream/master
```

## Créditos y licencia

- **GBCC y GBCC Android:** © 2017-2020 Philip Jones, con licencia MIT
  ([gbcc.github.io](https://gbcc.github.io)). Todo el emulador es trabajo suyo.
- **GBCG:** cambios © 2026 Gustavo Inciarte, con la misma licencia MIT. Ver
  [LICENSE](LICENSE); la app muestra ambas licencias en Settings > Miscellaneous > Licenses.

Las ROM no se incluyen: usa copias de tus propios cartuchos.
