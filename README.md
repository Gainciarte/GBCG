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
- **Carpeta de ROM:** en vez de guardar sus propias copias, GBCG pide una vez la
  carpeta donde tienes tus ROM (con el selector de Android) y las abre desde ahí,
  con sus subcarpetas. La lista se actualiza sola si agregas, quitas o renombras
  juegos. Las partidas siguen dentro de GBCG, asociadas al nombre de cada juego.
  Mientras un juego está abierto, GBCG usa una copia temporal en su caché (el
  núcleo abre las ROM por ruta) que se borra al cerrarlo.
- **Grupos:** arriba de la lista, "All games", "GBC" y "GB" (se arman solos según
  la extensión de cada ROM) y los grupos que crees ("+ New group"; mantener
  presionado un grupo para renombrarlo o borrarlo). GBC y GB también se pueden
  borrar, y se recuperan desde el menú ⋮ de arriba. Los juegos jugados aparecen
  primero, del más reciente al más antiguo, y los que tienen partida llevan un
  ícono.
- **Menú más simple:** la barra superior deja a la vista solo Ajustes; elegir
  carpeta, importar y exportar partidas están en su menú ⋮. Cada juego tiene un
  botón ⋮ (o mantener presionado) con grupos, renombrar, opciones del juego
  (configuración y trucos), borrar partida y acceso directo. Renombrar cambia solo
  el nombre que muestra GBCG: el archivo de la ROM no cambia, así que sus partidas
  y los otros emuladores que usan la carpeta no se ven afectados. Se quitaron la
  selección múltiple, mover, crear carpetas y "Multiplayer", que no funcionaba; "Importar"
  ahora importa solo partidas (`.sav`, estados y `.zip` de partidas). Ya no se
  incluye la ROM tutorial de GBCC.
- **Idiomas:** inglés, español, francés, alemán, italiano y portugués de Brasil,
  con un selector en Settings > Language (o en el idioma por app de Android 13 y
  superior). Los mensajes que dibuja el núcleo dentro del juego siguen en inglés.
- **Ajustes reorganizados:** Controls, Picture, Sound, Advanced y About (versión,
  código fuente y licencias), en vez de las cinco categorías originales. Un solo
  ajuste de color pinta la carcasa y la app.
- **Android 16 (API 36):** la app apunta a la versión que exige Google Play desde
  agosto de 2026, con herramientas al día (Android Gradle Plugin 8.13, Gradle 8.14,
  Kotlin 2.2), el núcleo compilado con el NDK r28 (alineado a páginas de 16 KB) y
  las pantallas adaptadas al modo de borde a borde de Android 15.
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

Requisitos: JDK 17 y el SDK de Android con la plataforma 36, el NDK
`28.2.13676358` y CMake 3.22.1 (Gradle y sus plugins los baja el propio proyecto).

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
