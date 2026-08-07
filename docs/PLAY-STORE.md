# Guía para publicar Control QR en Google Play

Todo lo que sigue son pasos que **debes hacer tú**, porque van ligados a tu identidad y a tu
cuenta de Google. El material que necesitas ya está preparado en este repositorio.

---

## 0. Antes de empezar: el calendario real

| Etapa | Tiempo |
|---|---|
| Alta de cuenta de desarrollador y verificación de identidad | 1 a 3 días |
| **Pruebas cerradas obligatorias: 12 probadores durante 14 días seguidos** | **14 días mínimo** |
| Solicitud de acceso a producción | 1 a 7 días |
| Revisión de la aplicación | 1 a 7 días |

El requisito de los 12 probadores aplica a **cuentas personales** creadas desde noviembre de
2023. Si te das de alta como **organización** (con documentación fiscal de la empresa), ese
requisito no aplica y el proceso se acorta mucho. Si la app es para una empresa, vale la pena
darse de alta como organización.

**No borres el APK de GitHub hasta que la aplicación esté publicada y verificada en Play.**
De lo contrario te quedas sin ninguna vía para instalar o actualizar la app en el intervalo.

---

## 1. Crear la cuenta de desarrollador

1. Entra a **https://play.google.com/console**
2. Paga la cuota única de **25 USD**
3. Elige tipo de cuenta:
   - **Organización** — requiere número D-U-N-S de la empresa. Evita el requisito de los 12
     probadores. Recomendado si la app es para una empresa.
   - **Personal** — más rápido de abrir, pero obliga a las pruebas cerradas de 14 días.
4. Completa la verificación de identidad con documento oficial

---

## 2. ⚠️ Firma: el paso que no se puede deshacer

Ya distribuiste la versión 1.0.0 firmada con `controlqr.jks`. Para que la versión de Play se
instale **encima** de esas instalaciones sin borrar los datos de cada teléfono, Play tiene que
firmar con **esa misma llave**.

Al crear la aplicación en Play Console, en el apartado de **Firma de aplicaciones**:

- **NO** elijas "Permitir que Google genere una clave de firma"
- **SÍ** elige **"Usar una clave existente"** / *Exportar y subir una clave desde un almacén
  de claves de Java*
- Sube el archivo `C:\Users\skate\OneDrive\ControlQR-llave\controlqr.jks`
- Alias: `controlqr`
- Contraseña del almacén y de la clave: **la misma** (está en `CONTRASENAS.txt`)

**Esta elección se hace una sola vez y no se puede cambiar después.** Si dejas que Google
genere una llave nueva, todos los vigilantes tendrán que desinstalar la app y perderán los
registros guardados en su teléfono.

Como beneficio, a partir de ese momento Google custodia una copia de la llave: deja de ser un
archivo irrecuperable.

---

## 3. Generar el archivo que se sube

Play no acepta APK para aplicaciones nuevas; necesita un **AAB** (Android App Bundle).

Publica una versión con una etiqueta, por ejemplo:

```bash
git tag v1.1.0 && git push origin v1.1.0
```

Cuando termine el workflow, descarga el artefacto llamado
**`ControlQR-1.1.0-playstore`** desde la pestaña *Actions* del repositorio. Dentro está el
archivo `.aab` que se sube a Play Console.

---

## 4. Ficha de Play Store

### Nombre de la aplicación (máx. 30 caracteres)

```
Control QR - Accesos
```

### Descripción breve (máx. 80 caracteres)

```
Control de entradas y salidas con pases QR de un solo uso. Funciona sin internet.
```

### Descripción completa (máx. 4000 caracteres)

```
Control QR administra el acceso de personal y transportistas a plantas, bodegas, obras y
sitios controlados mediante códigos QR de un solo ciclo.

CÓMO FUNCIONA

El administrador genera un pase con los datos del visitante: nombre completo, línea
transportista, placas o unidad, empresa de destino y la vigencia autorizada. El pase se
entrega impreso o por mensajería.

En la caseta, el vigilante escanea el pase al entrar y lo vuelve a escanear al salir. Un
tercer escaneo se rechaza automáticamente. El sistema calcula el tiempo de permanencia de
cada visita.

FUNCIONA SIN INTERNET

Los pases llevan una firma criptográfica que el teléfono del vigilante verifica sin conexión.
No hace falta cobertura ni wifi en la caseta para validar un acceso. Opcionalmente puede
activarse la sincronización entre dispositivos para que el administrador vea los movimientos
en tiempo real.

DOS TIPOS DE USUARIO

El usuario administrador emite pases y tiene acceso completo a reportes, historial y
configuración. El usuario vigilante solo ve dos pantallas: escanear entrada y escanear salida.

REPORTES

- Ocupación en tiempo real: cuántas personas están dentro del sitio
- Entradas y salidas por día, semana y mes
- Tiempo de permanencia promedio
- Bitácora de todos los escaneos, incluidos los rechazados y su motivo
- Exportación a CSV para abrir en Excel

CONTROL Y SEGURIDAD

Cada pase sirve exactamente una vez para entrar y una vez para salir. Los pases vencidos no
permiten entrar, pero siempre permiten salir a quien ya está dentro. El administrador puede
cancelar un pase en cualquier momento.

Los datos permanecen en los dispositivos de la organización. La aplicación no muestra
publicidad, no incorpora seguimiento y no comparte información con terceros.
```

### Categoría

`Empresa` (Business)

### Etiquetas sugeridas

`control de acceso`, `códigos QR`, `seguridad`, `visitantes`, `caseta`

---

## 5. Recursos gráficos que faltan

Estos hay que producirlos; son obligatorios y Play rechaza la ficha sin ellos:

| Recurso | Formato | Notas |
|---|---|---|
| Icono | PNG 512 × 512 | Sin transparencia |
| Gráfico principal | PNG/JPG 1024 × 500 | Se muestra arriba de la ficha |
| Capturas de teléfono | mínimo 2, máx. 8 | Entre 320 y 3840 px de lado |

Las capturas se pueden tomar directamente de la app instalada. Las más representativas:
el tablero del administrador, la pantalla de emisión con el QR generado, la pantalla de
escaneo con el resultado de acceso autorizado, y la de reportes.

---

## 6. Cuestionario de Seguridad de los datos

Play pregunta qué datos maneja la app. Respuestas que corresponden a Control QR:

| Pregunta | Respuesta |
|---|---|
| ¿Recopila datos? | **Sí** |
| Tipo de datos | Información personal → **Nombre**; Otros → datos de vehículo |
| ¿Se transmiten a terceros? | **Solo si el operador activa la sincronización** |
| ¿Se cifran en tránsito? | **Sí** (HTTPS, cuando la sincronización está activa) |
| ¿El usuario puede solicitar su eliminación? | **Sí** |
| ¿Datos obligatorios u opcionales? | Obligatorios para la función principal |
| ¿Uso de la cámara? | Solo lectura de códigos QR; no se guardan imágenes |
| ¿Publicidad o analítica? | **No** |

**URL de política de privacidad:**

```
https://dace077.github.io/ControlQR/privacidad
```

Completa el apartado de contacto de esa página antes de enviar a revisión: Google exige un
correo válido y accesible.

---

## 7. Consideración de fondo

Control QR es una herramienta interna: sin la llave de un sitio, la aplicación no le sirve de
nada a quien la descargue. Publicarla abiertamente en Play funciona, pero no es su encaje
natural y el revisor puede pedir aclaraciones sobre su propósito.

Si la app es solo para tu empresa, la opción diseñada para ese caso es **Google Play
administrado (aplicación privada)**, que la distribuye únicamente dentro de la organización y
no pasa por revisión pública. Requiere Google Workspace.

Y si el objetivo era simplemente "que se instale fácil", el QR de descarga que ya tienes
cumple esa función sin cuota, sin revisión y sin espera.
