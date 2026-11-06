# Chat multiusuario

Proyecto de PSP hecho con Java. Tiene un servidor de consola y un cliente con ventana JavaFX. En este repositorio, el cliente está en `reto`; en el ZIP de entrega, las carpetas se llaman `Cliente` y `Servidor`.

Los usuarios pueden enviar mensajes publicos a todos o mensajes privados a otro usuario.

## Requisitos

- JDK 11 o superior.
- Maven.

## Iniciar el servidor

Abre una terminal en la carpeta `Servidor` y ejecuta:

```text
mvn package
java -cp target\classes retopspq.server.ChatServer
```

El servidor usa el puerto `5000`, permite 10 usuarios y muestra estadisticas cada 10 segundos. Puedes cambiar esos valores pasando tres numeros: puerto, limite de usuarios e intervalo. Por ejemplo:

```text
java -cp target\classes retopspq.server.ChatServer 5000 20 5
```

Las conexiones y los mensajes se guardan en `servidor.log`.

## Iniciar el cliente

Abre otra terminal en `reto` (o en `Cliente` si estás usando el ZIP) y ejecuta:

```text
mvn javafx:run
```

## Uso

1. Inicia el servidor y luego abre un cliente por cada usuario.
2. En cada cliente, indica la IP y el puerto del servidor, escribe un nombre de usuario y pulsa **Conectar**. Para probar en el mismo ordenador, usa `127.0.0.1`.
3. Escribe un mensaje y pulsa **Enviar** para enviarlo públicamente a todos los usuarios conectados.
4. Para enviar un mensaje privado, marca **Privado**, escribe el nombre del destinatario en **Para**, redacta el mensaje y pulsa **Enviar**. El chat indica si cada mensaje es público o privado y quién lo envió.
5. Pulsa **Desconectar** para salir del chat. Si se cierra la ventana, el cliente también cierra su conexión.
