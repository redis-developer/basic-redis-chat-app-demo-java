# Basic Redis Chat App Demo (Java/Spring)

Showcases how to implement chat app in Java (Spring Boot) and Redis. This example uses **pub/sub** feature combined with **server-side events** for implementing the message communication between client and server.

<a href="https://github.com/redis-developer/basic-redis-chat-app-demo-java/raw/main/docs/screenshot000.png"><img src="https://github.com/redis-developer/basic-redis-chat-app-demo-java/raw/main/docs/screenshot000.png" width="49%"></a>
<a href="https://github.com/redis-developer/basic-redis-chat-app-demo-java/raw/main/docs/screenshot001.png"><img src="https://github.com/redis-developer/basic-redis-chat-app-demo-java/raw/main/docs/screenshot001.png" width="49%"></a>

# Overview video

Here's a short video that explains the project and how it uses Redis:

[![Watch the video on YouTube](https://github.com/redis-developer/basic-redis-chat-app-demo-java/raw/main/docs/YTThumbnail.png)](https://www.youtube.com/watch?v=miK7xDkDXF0)

## Technical Stacks

- Frontend - _React_, _Server-sent events_
- Backend - _Spring Boot 2_, _Redis_

## How it works?

## Database Schema

### User

```Java
public class User {
  private int id;
  private String username;
  private boolean isOnline;
}
```

### ChatRoom

```Java
public class Room {
  private String id;
  private String[] names;
}
```

### ChatRoomMessage

```Java
public class Message {
  private String from;
  private int date;
  private String message;
  private String roomId;
}
```

### Initialization

For simplicity, a key with **total_users** value is checked: if it does not exist, we fill the Redis database with initial data.
`EXISTS total_users` (checks if the key exists)

The demo data initialization is handled in multiple steps:

**Creating of demo users:**
We create a new user id: `INCR total_users`. Then we set a user ID lookup key by user name: **_e.g._** `SET username:nick user:1`. And finally, the rest of the data is written to the hash set: **_e.g._** `HSET user:1 username "nick" password "bcrypt_hashed_password"`.

Additionally, each user is added to the default "General" room. For handling rooms for each user, we have a set that holds the room ids. Here's an example command of how to add the room: **_e.g._** `SADD user:1:rooms "0"`.

**Populate private messages between users.**
At first, private rooms are created: if a private room needs to be established, for each user a room id: `room:1:2` is generated, where numbers correspond to the user ids in ascending order.

**_E.g._** Create a private room between 2 users: `SADD user:1:rooms 1:2` and `SADD user:2:rooms 1:2`.

Then we add messages to this room by writing to a sorted set:

**_E.g._** `ZADD room:1:2 1615480369 "{'from': 1, 'date': 1615480369, 'message': 'Hello', 'roomId': '1:2'}"`.

We use a stringified _JSON_ for keeping the message structure and simplify the implementation details for this demo-app.

**Populate the "General" room with messages.** Messages are added to the sorted set with id of the "General" room: `room:0`

### Registration

![How it works](docs/screenshot000.png)

Redis is used mainly as a database to keep the user/messages data and for sending messages between connected servers.

#### How the data is stored:

- The chat data is stored in various keys and various data types.
  - User data is stored in a hash set where each user entry contains the next values:
    - `username`: unique user name;
    - `password`: hashed password

* User hash set is accessed by key `user:{userId}`. The data for it stored with `HSET key field data`. User id is calculated by incrementing the `total_users`.

  - E.g `INCR total_users`

* Username is stored as a separate key (`username:{username}`) which returns the userId for quicker access.
  - E.g `SET username:Alex 4`

#### How the data is accessed:

- **Get User** `HGETALL user:{id}`

  - E.g `HGETALL user:2`, where we get data for the user with id: 2.

- **Online users:** will return ids of users which are online
  - E.g `SMEMBERS online_users`

#### Code Example: Prepare User Data in Redis HashSet

```Java
public class DemoDataCreator {

    //...

    private User createUser(String username) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String usernameKey = String.format("username:%s", username);

        // Yeah, bcrypt generally ins't used in .NET, this one is mainly added to be compatible with Node and Python demo servers.
        String hashedPassword = encoder.encode(DEMO_PASSWORD);

        Integer nextId = redisTemplate.opsForValue().increment("total_users").intValue();
        String userKey = String.format("user:%s", nextId);

        redisTemplate.opsForValue().set(usernameKey, userKey);
        redisTemplate.opsForHash().put(userKey, "username", username);
        redisTemplate.opsForHash().put(userKey, "password", hashedPassword);

        String roomsKey = String.format("user:%s:rooms", nextId);
        redisTemplate.opsForSet().add(roomsKey, "0");

        return new User(
                nextId,
                username,
                false
        );
    }
    //...
}
```

### Rooms

![How it works](docs/screenshot001.png)

#### How the data is stored:

Each user has a set of rooms associated with them.

**Rooms** are sorted sets which contains messages where score is the timestamp for each message. Each room has a name associated with it.

- Rooms which user belongs too are stored at `user:{userId}:rooms` as a set of room ids.

  - E.g `SADD user:Alex:rooms 1`

- Set room name: `SET room:{roomId}:name {name}`
  - E.g `SET room:1:name General`

#### How the data is accessed:

- **Get room name** `GET room:{roomId}:name`.

  - E. g `GET room:0:name`. This should return "General"

- **Get room ids of a user:** `SMEMBERS user:{id}:rooms`.
  - E. g `SMEMBERS user:2:rooms`. This will return IDs of rooms for user with ID: 2

#### Code Example: Get all My Rooms

```Java
@RestController
@RequestMapping("/rooms")
public class RoomsController {

    //...

    @GetMapping(value = "user/{userId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Room>> getRooms(@PathVariable int userId) {
        Set<String> roomIds = roomsRepository.getUserRoomIds(userId);
        if (roomIds == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        List<Room> rooms = new ArrayList<>();

        for (String roomId : roomIds) {
            boolean roomExists = roomsRepository.isRoomExists(roomId);
            if (roomExists) {
                String name = roomsRepository.getRoomNameById(roomId);
                if (name == null) {
                    // private chat case
                    Room privateRoom = handlePrivateRoomCase(roomId);
                    if (privateRoom == null) {
                        return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
                    }
                    rooms.add(privateRoom);
                } else {
                    rooms.add(new Room(roomId, name));
                }
            }
        }
        return new ResponseEntity<>(rooms, HttpStatus.OK);
    }
    //...
}
```

### Messages

#### Pub/sub

After initialization, a pub/sub subscription is created: `SUBSCRIBE MESSAGES`. At the same time, each server instance will run a listener on a message on this channel to receive real-time updates.

Again, for simplicity, each message is serialized to **_JSON_**, which we parse and then handle in the same manner, as WebSocket messages.

Pub/sub allows connecting multiple servers written in different platforms without taking into consideration the implementation detail of each server.

#### How the data is stored:

- Messages are stored at `room:{roomId}` key in a sorted set (as mentioned above). They are added with `ZADD room:{roomId} {timestamp} {message}` command. Message is serialized to an app-specific JSON string.
  - E.g `ZADD room:0 1617197047 { "From": "2", "Date": 1617197047, "Message": "Hello", "RoomId": "1:2" }`

#### How the data is accessed:

- **Get list of messages** `ZREVRANGE room:{roomId} {offset_start} {offset_end}`.
  - E.g `ZREVRANGE room:1:2 0 50` will return 50 messages with 0 offsets for the private room between users with IDs 1 and 2.

#### Code Example: Send Message

```Java
@RestController
@RequestMapping("/chat")
public class ChatController {
  @RequestMapping("/stream")
  public SseEmitter streamSseMvc(@RequestParam int userId) {
    AtomicBoolean isComplete = new AtomicBoolean(false);
    SseEmitter emitter = new SseEmitter();

    Function<String, Integer> handler = (String message) -> {
      SseEmitter.SseEventBuilder event = SseEmitter.event()
              .data(message);
      try {
        emitter.send(event);
      } catch (IOException e) {
        // This may occur when the client was disconnected.
        return 1;
      }
      return 0;
    };

    // RedisMessageSubscriber is a global class which subscribes to the "MESSAGES" channel
    // However once the /stream endpoint is invoked, it's necessary to notify the global subscriber
    // that such client-server subscription exists.
    //
    // We send the callback to the subscriber with the SSE instance for sending server-side events.
    RedisMessageSubscriber redisMessageSubscriber = (RedisMessageSubscriber) messageListener.getDelegate();
    redisMessageSubscriber.attach(handler);

    // Make sure all life-time methods are covered here and remove the handler from the global subscriber.
    Runnable onDetach = () -> {
      redisMessageSubscriber.detach(handler);
      if (!isComplete.get()) {
        isComplete.set(true);
        emitter.complete();
      }
    };

    emitter.onCompletion(onDetach);
    emitter.onError((err) -> onDetach.run());
    emitter.onTimeout(onDetach);

    return emitter;
  }
}
```

### Session handling

The chat server works as a basic _REST_ API which involves keeping the session and handling the user state in the chat rooms (besides the WebSocket/real-time part).

When a WebSocket/real-time server is instantiated, which listens for the next events:

**Connection**. A new user is connected. At this point, a user ID is captured and saved to the session (which is cached in Redis). Note, that session caching is language/library-specific and it's used here purely for persistence and maintaining the state between server reloads.

A global set with `online_users` key is used for keeping the online state for each user. So on a new connection, a user ID is written to that set:

**E.g.** `SADD online_users 1` (We add user with id 1 to the set **online_users**).

After that, a message is broadcasted to the clients to notify them that a new user is joined the chat.

**Disconnect**. It works similarly to the connection event, except we need to remove the user for **online_users** set and notify the clients: `SREM online_users 1` (makes user with id 1 offline).

**Message**. A user sends a message, and it needs to be broadcasted to the other clients. The pub/sub allows us also to broadcast this message to all server instances which are connected to this Redis:

`PUBLISH message "{'serverId': 4132, 'type':'message', 'data': {'from': 1, 'date': 1615480369, 'message': 'Hello', 'roomId': '1:2'}}"`

Note we send additional data related to the type of the message and the server id. Server id is used to discard the messages by the server instance which sends them since it is connected to the same `MESSAGES` channel.

`type` field of the serialized JSON corresponds to the real-time method we use for real-time communication (connect/disconnect/message).

`data` is method-specific information. In the example above it's related to the new message.

#### How the data is stored / accessed:

The session data is stored in Redis by utilizing the [**Letuce**](https://github.com/lettuce-io/lettuce-core) client.

##### Startup

```Java
public class RedisAppConfig {

    //...

    public RedisConnectionFactory redisConnectionFactory() {
        // Read environment variables
        String endpointUrl = System.getenv("REDIS_ENDPOINT_URL");
        if (endpointUrl == null) {
            endpointUrl = "127.0.0.1:6379";
        }
        String password = System.getenv("REDIS_PASSWORD");

        String[] urlParts = endpointUrl.split(":");

        String host = urlParts[0];
        String port = "6379";

        if (urlParts.length > 1) {
            port = urlParts[1];
        }

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, Integer.parseInt(port));

        System.out.printf("Connecting to %s:%s with password: %s%n", host, port, password);

        if (password != null) {
            config.setPassword(password);
        }
        return new LettuceConnectionFactory(config);
    }
}
```

## How to run it locally?

#### Set the Redis endpoint and password environment variables:

```
$ REDIS_ENDPOINT_URL=localhost:6379
$ REDIS_PASSWORD=foo
```

Note that the `REDIS_PASSWORD` variable is required only if your connecting to a password-protected Redis instance.

#### Run App

Ensure that you have Maven wrapper installed:

```sh
mvn -N io.takari:maven:wrapper
```

The start the application:
```sh
./mvnw spring-boot:run
```

To interact with the application, point your browser to `localhost:8080`.

#### Run Frontend

The client is bundled with the server by default, however it's possible to run the client separately for development:

```sh
cd client
yarn install
yarn start
```

## Try it out

#### Deploy to Heroku

<p>
    <a href="https://heroku.com/deploy" target="_blank">
        <img src="https://www.herokucdn.com/deploy/button.svg" alt="Deploy to Heroku" />
    </a>
</p>

#### Deploy to Google Cloud

<p>
    <a href="https://deploy.cloud.run" target="_blank">
        <img src="https://deploy.cloud.run/button.svg" alt="Run on Google Cloud" width="150px"/>
    </a>
</p>
