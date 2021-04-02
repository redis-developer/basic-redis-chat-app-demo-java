# Basic Redis Chat App Demo  (Java/Spring)
Showcases how to implement chat app in Java (Spring Boot) and Redis. This example uses **pub/sub** feature combined with **server-side events** for implementing the message communication between client and server.

<a href="https://raw.githubusercontent.com/redis-developer/basic-redis-chat-app-demo-dotnet/main/docs/screenshot000.png?raw=true"><img src="https://raw.githubusercontent.com/redis-developer/basic-redis-chat-app-demo-dotnet/main/docs/screenshot000.png?raw=true" width="49%"></a>
<a href="https://raw.githubusercontent.com/redis-developer/basic-redis-chat-app-demo-dotnet/main/docs/screenshot001.png?raw=true"><img src="https://raw.githubusercontent.com/redis-developer/basic-redis-chat-app-demo-dotnet/main/docs/screenshot001.png?raw=true" width="49%"></a>


## Technical Stacks
* Frontend - *React*, *Socket* (@microsoft/signalr)
* Backend - *Spring Boot 2*, *Redis*

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

## How document and each data type is stored in Redis.

### How do you store a document?
We want to store a document(like a user) in redis.
Basically, *indexable* and *sortable* fields are stored in hash while we store rest of the fields in RedisJSON. We can apply RediSearch queries once we store in hash.

Redis is used mainly as a database to keep the user/messages data and for sending messages between connected servers.

The real-time functionality is handled by **Server Sent Events** for server->client messaging. Additionally each server instance subscribes to the `MESSAGES` channel of pub/sub and dispatches messages once they arrive.

- The chat data is stored in various keys and various data types.
  - User data is stored in a hash set where each user entry contains the next values:
    - `username`: unique user name;
    - `password`: hashed password
  - Additionally a set of rooms is associated with user
  - **Rooms** are sorted sets which contains messages where score is the timestamp for each message
    - Each room has a name associated with it
  - **Online** set is global for all users is used for keeping track on which user is online.

* User hash set is accessed by key `user:{userId}`. The data for it stored with `HSET key field data`. User id is calculated by incrementing the `total_users`.
  * E.g `INCR total_users`

* Username is stored as a separate key (`username:{username}`) which returns the userId for quicker access.
  * E.g `SET username:Alex 4`

* Rooms which user belongs too are stored at `user:{userId}:rooms` as a set of room ids.
  * E.g `SADD user:Alex:rooms 1`

* Messages are stored at `room:{roomId}` key in a sorted set (as mentioned above). They are added with `ZADD room:{roomId} {timestamp} {message}` command. Message is serialized to an app-specific JSON string.
  * E.g `ZADD room:0 1617197047 { "From": "2", "Date": 1617197047, "Message": "Hello", "RoomId": "1:2" }`

### How the data is accessed:

**Get User** `HGETALL user:{id}`. Example: `HGETALL user:2`, where we get data for the user with id: 2.

**Online users:** `SMEMBERS online_users`. This will return ids of users which are online

**Get room ids of a user:** `SMEMBERS user:{id}:rooms`. Example: `SMEMBERS user:2:rooms`. This will return IDs of rooms for user with ID: 2

**Get list of messages** `ZREVRANGE room:{roomId} {offset_start} {offset_end}`.
Example: `ZREVRANGE room:1:2 0 50` will return 50 messages with 0 offsets for the private room between users with IDs 1 and 2.


## How it works?

### Sign in
![How it works](docs/screenshot000.png)

### Chats
![How it works](docs/screenshot001.png)

The chat server works as a basic *REST* API which involves keeping the session and handling the user state in the chat rooms (besides the WebSocket/real-time part).

When the server starts, the initialization step occurs. At first, a new Redis connection is established and it's checked whether it's needed to load the demo data.

## Using in Java
### Startup
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

### Initialization
For simplicity, a key with **total_users** value is checked: if it does not exist, we fill the Redis database with initial data.
```EXISTS total_users``` (checks if the key exists)


The demo data initialization is handled in multiple steps:

**Creating of demo users:**
We create a new user id: `INCR total_users`. Then we set a user ID lookup key by user name: ***e.g.*** `SET username:nick user:1`. And finally, the rest of the data is written to the hash set: ***e.g.*** `HSET user:1 username "nick" password "bcrypt_hashed_password"`.

Additionally, each user is added to the default "General" room. For handling rooms for each user, we have a set that holds the room ids. Here's an example command of how to add the room: ***e.g.*** `SADD user:1:rooms "0"`.

**Populate private messages between users.**
At first, private rooms are created: if a private room needs to be established, for each user a room id: `room:1:2` is generated, where numbers correspond to the user ids in ascending order.

***E.g.*** Create a private room between 2 users: `SADD user:1:rooms 1:2` and `SADD user:2:rooms 1:2`.

Then we add messages to this room by writing to a sorted set:
***E.g.*** `ZADD room:1:2 1615480369 "{'from': 1, 'date': 1615480369, 'message': 'Hello', 'roomId': '1:2'}"`.

We use a stringified *JSON* for keeping the message structure and simplify the implementation details for this demo-app.

**Populate the "General" room with messages.** Messages are added to the sorted set with id of the "General" room: `room:0`

### Example: Prepare User Data in Redis HashSet
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

### Example: Prepare Room Data in Redis SortedSet
```Java
public class DemoDataCreator {
    
    //...
    
    private Room createPrivateRoom(Integer user1, Integer user2) {
        String roomId = getPrivateRoomId(user1, user2);

        String userRoomkey1 = String.format("user:%d:rooms", user1);
        String userRoomkey2 = String.format("user:%d:rooms", user2);

        redisTemplate.opsForSet().add(userRoomkey1, roomId);
        redisTemplate.opsForSet().add(userRoomkey2, roomId);

        String key1 = String.format("user:%d", user1);
        String key2 = String.format("user:%d", user2);

        return new Room(
                roomId,
                (String) redisTemplate.opsForHash().get(key1, "username"),
                (String) redisTemplate.opsForHash().get(key2, "username")
        );
    }
    //...
}
```

### Example: Get all My Rooms
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

### Example: Send Message
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

### Pub/sub
After initialization, a pub/sub subscription is created: `SUBSCRIBE MESSAGES`. At the same time, each server instance will run a listener on a message on this channel to receive real-time updates.

Again, for simplicity, each message is serialized to ***JSON***, which we parse and then handle in the same manner, as WebSocket messages.

Pub/sub allows connecting multiple servers written in different platforms without taking into consideration the implementation detail of each server.

### Real-time chat and session handling

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

___

## How to run it locally?

#### Write in environment variable or Dockerfile actual connection to Redis:
```
   REDIS_ENDPOINT_URL = "Redis server URI"
   REDIS_PASSWORD = "Password to the server"
```

#### Run full app

```sh
./run.sh
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
