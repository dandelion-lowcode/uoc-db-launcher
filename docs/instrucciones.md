---
language: spanish
---

# Instalación y ejecución del lanzador de bases de datos

:::steps

1. **Instala Docker.**

   Te recomendamos hacerlo a través de 
   [Docker Desktop](https://docs.docker.com/get-started/get-docker/). Si utilizas cualquier otro método, te expones a problemas y es bajo tu responsabilidad.

2. **Descarga el launcher** correspondiente a tu sistema operativo.

    * Si tienes **Mac**: ejecuta `uname -m` en una terminal. Si devuelve `arm64`, descarga `macos-arm64.zip`; si devuelve `x86_64`, descarga `macos-x64.zip`.

3. **Descomprime** el lanzador en un directorio de tu elección.

4.  **Ajusta los permisos de ejecución del lanzador.**

    Solo debes hacerlo la primera vez.

    - Si tienes **Windows**: botón derecho sobre `UOCDBLauncher.exe` $\to$ Propiedades $\to$ General $\to$ Desbloquear $\to$ Aplicar. 
    - Si tienes **Mac**: ejecuta en la terminal:

        ```shell
        sudo xattr -dr com.apple.quarantine UOCDBLauncher.app
        ```
    - Si tienes **Linux**: ejecuta en la terminal:
        ```shell
        sudo chmod +x bin/UOCDBLauncher
        ```

5. **Asegúrate de que Docker Desktop esté ejecutándose.**
   
   Si no lo está, ábrelo y espera a que termine de arrancar.

6. **Ejecuta el lanzador** haciendo doble clic sobre el binario `UOCDBLauncher`.

7. Selecciona tu **idioma** en el menú superior.

8. Haz clic en **Tutorial** en el menú superior y sigue las instrucciones para aprender a usar el lanzador.

:::

> [!IMPORTANT]
> Si experimentas cualquier problema durante la instalación, debes ponerte inmediatamente en contacto con el profesor de la asignatura vía email (Francisco Martínez Lasaca, fmartinezlasa@uoc.edu).

## _Troubleshooting_ en Windows 11

### Docker Desktop no arranca: falta el hipervisor

Al arrancar `UOCDBLauncher.exe` **indica que Docker no está en marcha**, y al intentar arrancar Docker Desktop aparece este error:

![](images/troubleshooting-img-2.png)

Para solucionarlo:

:::steps

1. **Activa las características de Windows.**

   En «**Activar o desactivar las características de Windows**» —puedes buscarlo literalmente pulsando Windows + S— activa «**Hyper-V**», «**Plataforma de máquina virtual**» y «**Plataforma del hipervisor de Windows**», como se ilustra en la siguiente imagen:

   ![](images/troubleshooting-img-3.png)

2. Pulsa «**No reiniciar**».

3. **Activa la integridad de memoria.**

   En «**Aislamiento del núcleo**» —de nuevo puedes buscarlo a través de Windows + S— actívala como se ilustra a continuación:

   ![](images/troubleshooting-img-4.png)

4. **Reinicia el equipo** cuando el sistema lo pida.

    Asegúrate antes de guardar todo el trabajo importante y cerrar todas las aplicaciones.

5. **Comprueba que arranca.**

   Tras el reinicio, Docker Desktop debería arrancar con normalidad y, tras ello, `UOCDBLauncher.exe` correctamente.

:::

> [!WARNING] Docker y VirtualBox pueden no convivir
> Aplicar este procedimiento hace que Docker funcione, pero **es posible que otras aplicaciones de virtualización dejen de hacerlo**: el procesador presta sus extensiones de virtualización a un solo programa a la vez, y Hyper-V las reclama al arrancar Windows. Con VirtualBox, por ejemplo, el síntoma es este error al abrir una máquina virtual:
>
> ![](images/troubleshooting-img-5.png)
>
> Si lo prefieres al lanzador, deshaz los cambios; si necesitas los dos, VirtualBox 6 y posteriores funcionan **sobre** Hyper-V, aunque de manera menos eficiente.

:::pagebreak

### El lanzador no detecta Docker aunque esté en marcha

Ejecuta desde una terminal el comando `docker info`.

- **Si en la terminal también falla**, el problema está en Docker y no en el lanzador: arráncalo y, si aun así no responde, revisa el apartado anterior.
- **Si en la terminal responde y el lanzador sigue sin verlo**, falta Docker en el PATH de tu sesión, porque la abriste antes de instalarlo. Cierra la sesión de Windows y vuelve a entrar —o reinicia el equipo— y abre el lanzador otra vez.



