# Chat multiusuario - Reto PSP

Aplicacion cliente-servidor Java para conversar por mensajes publicos y privados. El cliente grafico JavaFX esta en `reto` y el servidor de consola independiente en `Servidor`. El archivo `ChatMultiusuario.zip` contiene el proyecto preparado en las carpetas `Cliente` y `Servidor`.

## Requisitos

- JDK 11 o superior.
- Maven.

## Ejecutar el servidor

Desde una terminal, entra en `Servidor` y ejecuta:

```text
mvn package
java -cp target\classes retopspq.server.ChatServer [puerto] [maximo-clientes] [intervalo-estadisticas-segundos]
```

Los argumentos son opcionales; sus valores por defecto son `5000`, `10` y `10`. Por ejemplo, para permitir 20 clientes y mostrar estadisticas cada 5 segundos:

```text
java -cp target\classes retopspq.server.ChatServer 5000 20 5
```

El servidor muestra periodicamente el numero de usuarios conectados, su tiempo activo y el ultimo mensaje recibido. Las entradas, salidas y mensajes se guardan en `servidor.log`, en el directorio desde el que se inicia el proceso.

## Ejecutar el cliente

Desde una terminal, entra en `reto` (o en `Cliente` si has descomprimido el ZIP) y ejecuta:

```text
mvn javafx:run
```

## Uso

1. Inicia el servidor y abre tantos clientes como necesites, hasta el limite configurado.
2. En cada cliente, indica la IP, el puerto y un nombre de usuario unico y pulsa **Conectar**.
3. Escribe un mensaje y pulsa **Enviar** para mandarlo publicamente a todos los conectados.
4. Para enviar un mensaje privado, activa **Privado**, escribe el nombre del destinatario y envia el mensaje. Solo lo reciben el destinatario y el emisor.
5. Pulsa **Desconectar** para salir. Si el servidor esta lleno o el nombre ya esta en uso, el cliente muestra el motivo.

El puerto debe estar accesible entre los equipos que ejecutan el servidor y los clientes. Para probar en un solo equipo, usa `127.0.0.1`.
# Reto1PSP
