# Instalación del lanzador de bases de datos

1. **Descarga Docker Desktop** desde [https://docs.docker.com/get-started/get-docker/](https://docs.docker.com/get-started/get-docker/) para tu sistema operativo e instálalo siguiendo las instrucciones del instalador.

2. **Ejecuta Docker Desktop**.

3. **Descarga** el `uoc-db-launcher` correspondiente a tu sistema operativo:

      * **Windows** → el archivo acabado en `windows-x64.zip`
      * **Linux** → el archivo acabado en `linux-x64.tar.gz`
      * **Mac** → hay dos, y debes coger el que corresponda a tu procesador. En el menú Apple, arriba a la izquierda, elige "Acerca de este Mac": si donde pone "Chip" lees Apple (M1, M2, M3, M4...), el archivo es el acabado en `macos-arm64.zip`; si pone Intel, es el acabado en `macos-x64.zip`.

    Después **descomprímelo** en un directorio de tu elección. Por ejemplo, en tu home o en el escritorio.
   
4.  Si tienes **Windows**...
      * Antes de descomprimir, haz clic derecho sobre el archivo `.zip` que has descargado, selecciona "Propiedades", en la pestaña "General" marca la casilla "Desbloquear" (abajo del todo) y haz clic en "Aplicar". Descomprime después de hacerlo.
      * Abre la carpeta descomprimida `UOCDBLauncher` y haz doble clic en `UOCDBLauncher.exe`.
      * Si te aparece el mensaje "Windows protegió su PC", haz clic en "Más información" y luego en "Ejecutar de todas formas". 

    Si tienes **Mac**...
      * Abre una terminal en la carpeta donde has descomprimido la aplicación y ejecuta:

        ```shell
        xattr -dr com.apple.quarantine UOCDBLauncher.app
        ```

        Solo debes hacerlo la primera vez que abras la aplicación.

      * Haz doble clic en `UOCDBLauncher.app` o ejecuta `open UOCDBLauncher.app` desde la terminal.

    Si tienes **Linux**...
      * Abre una terminal en la carpeta `UOCDBLauncher` que has descomprimido y ejecuta `./bin/UOCDBLauncher`.

5. Selecciona tu **idioma** en el menú superior. Después, haz clic en **Tutorial** en el menú superior y sigue las instrucciones.

> Si experimentas cualquier problema durante la instalación, debes ponerte inmediatamente en contacto con el profesor de la asignatura vía email (Francisco Martínez Lasaca, fmartinezlasa@uoc.edu).


---

# Instal·lació del llançador de bases de dades

1. **Descarregueu Docker Desktop** des de [https://docs.docker.com/get-started/get-docker/](https://docs.docker.com/get-started/get-docker/) per al vostre sistema operatiu i instal·leu-lo seguint les instruccions de l'instal·lador.

2. **Executeu Docker Desktop**.

3. **Descarregueu** el `uoc-db-launcher` corresponent al vostre sistema operatiu:

      * **Windows** → el fitxer que acaba en `windows-x64.zip`
      * **Linux** → el fitxer que acaba en `linux-x64.tar.gz`
      * **Mac** → n'hi ha dos, i heu d'agafar el que correspongui al vostre processador. Al menú Apple, a dalt a l'esquerra, trieu "Quant a aquest Mac": si on posa "Xip" llegiu Apple (M1, M2, M3, M4...), el fitxer és el que acaba en `macos-arm64.zip`; si hi posa Intel, és el que acaba en `macos-x64.zip`.

    Després **descomprimiu-lo** en un directori de la vostra elecció. Per exemple, al vostre home o a l'escriptori.
   
4.  Si teniu **Windows**...
      * Abans de descomprimir, feu clic dret sobre el fitxer `.zip` que heu descarregat, seleccioneu "Propiedades", a la pestanya "General" marqueu la casella "Desbloquear" (a la part inferior) i feu clic a "Aplicar". Descomprimiu després d'haver-ho fet.
      * Obriu la carpeta descomprimida `UOCDBLauncher` i feu doble clic a `UOCDBLauncher.exe`.
      * Si us apareix el missatge "Windows protegió su PC", feu clic a "Más información" i després a "Ejecutar de todas formas".

    Si teniu **Mac**...
      * Obriu un terminal a la carpeta on heu descomprimit l'aplicació i executeu:

        ```shell
        xattr -dr com.apple.quarantine UOCDBLauncher.app
        ```

        Només heu de fer-ho la primera vegada que obriu l'aplicació.

      * Feu doble clic a `UOCDBLauncher.app` o executeu `open UOCDBLauncher.app` des del terminal.

    Si teniu **Linux**...
      * Obriu un terminal a la carpeta `UOCDBLauncher` que heu descomprimit i executeu `./bin/UOCDBLauncher`.

5. Seleccioneu el vostre **idioma** al menú superior. Després, feu clic a **Tutorial** al menú superior i seguiu les instruccions.

> Si experimenteu qualsevol problema durant la instal·lació, heu de posar-vos immediatament en contacte amb el professor de l'assignatura via email (Francisco Martínez Lasaca, fmartinezlasa@uoc.edu).