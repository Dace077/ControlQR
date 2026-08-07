---
title: Política de privacidad · Control QR
---

# Política de privacidad de Control QR

**Última actualización:** 5 de agosto de 2026

Control QR es una aplicación de control de accesos que registra las entradas y salidas de
personas y vehículos en un sitio mediante códigos QR. Este documento explica qué datos trata
la aplicación, dónde se guardan y quién los controla.

## Quién es responsable de los datos

La aplicación es una herramienta que se instala en los dispositivos de cada organización.
**El responsable de los datos es la empresa u organización que opera la aplicación**, no el
desarrollador de la misma. Quien la instala decide qué información captura y durante cuánto
tiempo la conserva.

El desarrollador no tiene acceso a los datos de ningún sitio, no los recibe y no puede
consultarlos.

## Qué datos se tratan

Al emitir un pase de acceso, el usuario administrador captura:

- Nombre completo de la persona autorizada
- Línea transportista
- Placas o número de unidad
- Empresa o destino
- Observaciones
- Fecha y hora de ingreso autorizada y vigencia del pase

Al escanear un pase se registra además:

- Fecha y hora de entrada y de salida
- Usuario del vigilante que realizó el registro
- Identificador del dispositivo que lo registró

La aplicación también guarda las cuentas de usuario locales (nombre de usuario y nombre para
mostrar). **Las contraseñas nunca se almacenan**: solo se guarda un derivado criptográfico
irreversible (PBKDF2-HMAC-SHA256) que permite verificarlas sin poder recuperarlas.

## Uso de la cámara

La aplicación solicita permiso de cámara **exclusivamente para leer códigos QR**.

Las imágenes se procesan en el momento, dentro del dispositivo, y **no se guardan, no se
almacenan en la galería y no se transmiten a ningún servidor**. La lectura se realiza con un
componente incluido en la propia aplicación, sin conexión a internet.

## Dónde se guardan los datos

**De forma predeterminada, todos los datos permanecen en el dispositivo**, en el
almacenamiento privado de la aplicación, al que ninguna otra aplicación puede acceder. La
llave criptográfica del sitio se guarda cifrada mediante el almacén de claves del sistema.

La aplicación está diseñada para operar sin conexión a internet.

### Sincronización opcional entre dispositivos

La organización puede activar una función que replica los registros entre los dispositivos de
un mismo sitio, para que el administrador vea los accesos en tiempo real. **Está sujeta a
activación explícita y puede desactivarse en cualquier momento** desde los ajustes.

Cuando está activada, los datos de los pases y de los escaneos se transmiten y almacenan en
**Google Cloud Firestore**, un servicio de Google. En ese caso aplica también la política de
privacidad de Google Cloud. Los datos se guardan bajo un identificador de sitio aleatorio y
solo son accesibles para dispositivos autenticados.

Si la sincronización permanece desactivada, ningún dato sale del dispositivo.

## Qué NO hace la aplicación

- No muestra publicidad
- No incorpora herramientas de analítica ni de seguimiento de uso
- No vende ni comparte datos con terceros
- No recopila la ubicación del dispositivo
- No accede a contactos, mensajes, archivos personales ni a la galería de fotos
- No crea perfiles de comportamiento

La única conexión a internet que la aplicación realiza por su cuenta es una consulta para
comprobar si existe una versión más reciente. Esa consulta no envía ningún dato personal.

## Conservación y eliminación

La organización que opera la aplicación decide cuánto tiempo conserva los registros. Dentro de
la aplicación, el administrador puede cancelar pases y exportar la información.

**Al desinstalar la aplicación se eliminan todos los datos locales del dispositivo.** Si la
sincronización estaba activada, la organización debe eliminar además los datos alojados en su
propio proyecto de Google Cloud.

## Derechos de las personas registradas

Las personas cuyos datos aparecen en los pases pueden solicitar acceso, rectificación o
supresión de su información. Dado que el responsable es la organización que opera el sitio,
**esas solicitudes deben dirigirse a dicha organización**, que es quien tiene la capacidad
técnica de atenderlas.

## Menores de edad

La aplicación es una herramienta de uso laboral y no está dirigida a menores de 13 años.

## Cambios en esta política

Cualquier modificación se publicará en esta misma dirección, actualizando la fecha del
encabezado.

## Contacto

Para dudas sobre esta política:

**[ESCRIBE AQUÍ TU CORREO DE CONTACTO]**

> Este apartado debe completarse antes de enviar la aplicación a revisión: Google exige un
> medio de contacto válido y accesible.
