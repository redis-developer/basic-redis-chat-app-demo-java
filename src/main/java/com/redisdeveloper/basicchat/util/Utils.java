package com.redisdeveloper.basicchat.util;

import com.google.gson.Gson;
import com.redisdeveloper.basicchat.model.Message;
import com.redisdeveloper.basicchat.model.Room;
import com.redisdeveloper.basicchat.model.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

public class Utils {

    @FunctionalInterface
    public interface TwoParameterFunction<T, U, R> {
        public R apply(T t, U u);
    }

    @FunctionalInterface
    public interface FourParameterFunction<T, U, V, W, R> {
        public R apply(T t, U u, V v, W w);
    }

    @FunctionalInterface
    public interface FourParameterCallable<T, U, V, W> {
        public void call(T t, U u, V v, W w);
    }

    public static void createDemoData(StringRedisTemplate redisTemplate) throws Exception {
        // We store a counter for the total users and increment it on each register
        var totalUsersKeyExist = redisTemplate.hasKey("total_users"); // Client.KeyExistsAsync("total_users");
        if (!totalUsersKeyExist) {
            System.out.print("Initializing demo data...\n");
            // This counter is used for the id
            redisTemplate.opsForValue().set("total_users", "0");
            // Some rooms have pre-defined names. When the clients attempts to fetch a room, an additional lookup
            // is handled to resolve the name.
            // Rooms with private messages don't have a name
            redisTemplate.opsForValue().set("room:0:name", "General");

            // Create demo data with the default users
            {
                var gson = new Gson();
                var rnd = new Random();
                Callable<Double> rand = rnd::nextDouble;
                Callable<Integer> getTimestamp = () -> Long.valueOf((System.currentTimeMillis() / 1000L)).intValue();

                var demoPassword = "password123";
                var demoUsers = new String[]{"Pablo", "Joe", "Mary", "Alex"
                };

                var greetings = new String[]{"Hello", "Hi", "Yo", "Hola"
                };

                var messages = new String[]{
                        "Hello!",
                        "Hi, How are you? What about our next meeting?",
                        "Yeah everything is fine",
                        "Next meeting tomorrow 10.00AM",
                        "Wow that's great"
                };
                Callable<String> getGreeting = () -> greetings[(int) Math.floor(rand.call() * greetings.length)];
                FourParameterCallable<String, String, String, Integer> addMessage = (String roomId, String fromId, String content, Integer timeStamp) ->
                {
                    var roomKey = String.format("room:%s", roomId);
                    var message = new Message(
                            fromId,
                            timeStamp,
                            content,
                            roomId
                    );
                    redisTemplate.opsForZSet().add(roomKey, gson.toJson(message), message.date);
                };

                var encoder = new BCryptPasswordEncoder();
                TwoParameterFunction<String, String, User> createUser = (String username, String password) ->
                {
                    var usernameKey = String.format("username:%s", username);

                    // Yeah, bcrypt generally ins't used in .NET, this one is mainly added to be compatible with Node and Python demo servers.
                    var hashedPassword = encoder.encode(password);

                    var nextId = redisTemplate.opsForValue().increment("total_users");
                    var userKey = String.format("user:%s", nextId);

                    redisTemplate.opsForValue().set(usernameKey, userKey);
                    redisTemplate.opsForHash().put(userKey, "username", username);
                    redisTemplate.opsForHash().put(userKey, "password", hashedPassword);


                    var roomsKey = String.format("user:%s:rooms", nextId);
                    redisTemplate.opsForSet().add(roomsKey, "0");

                    return new User(
                            nextId.intValue(),
                            username,
                            false
                    );
                };

                TwoParameterFunction<Integer, Integer, String> getPrivateRoomId = (user1, user2) ->
                {
                    var minUserId = user1 > user2 ? user2 : user1;
                    var maxUserId = user1 > user2 ? user1 : user2;
                    return String.format("%d:%d", minUserId, maxUserId);
                };

                TwoParameterFunction<Integer, Integer, Room> createPrivateRoom = (user1, user2) ->
                {
                    var roomId = getPrivateRoomId.apply(user1, user2);
                    {
                        var key1 = String.format("user:%d:rooms", user1);
                        var key2 = String.format("user:%d:rooms", user2);

                        redisTemplate.opsForSet().add(key1, roomId);
                        redisTemplate.opsForSet().add(key2, roomId);
                    }

                    var key1 = String.format("user:%d", user1);
                    var key2 = String.format("user:%d", user2);

                    return new Room(
                            roomId,
                            (String) redisTemplate.opsForHash().get(key1, "username"),
                            (String) redisTemplate.opsForHash().get(key2, "username")
                    );
                };


                var users = new LinkedList<User>();
                // For each name create a user.
                for (var demoUser : demoUsers) {
                    var user = createUser.apply(demoUser, demoPassword);
                    // This one should go to the session
                    users.add(user);
                }

                var rooms = new Hashtable<String, Room>();
                for (var user : users) {
                    var otherUsers = users.stream().filter(x -> x.id != user.id).collect(Collectors.toList());
                    for (var otherUser : otherUsers) {
                        var privateRoomId = getPrivateRoomId.apply(user.id, otherUser.id);
                        Room room = null;
                        if (!rooms.containsKey(privateRoomId)) {
                            room = createPrivateRoom.apply(user.id, otherUser.id);
                            rooms.put(privateRoomId, room);
                        } else {
                            room = rooms.get(privateRoomId);
                        }
                        addMessage.call
                                (privateRoomId, String.format("%d", otherUser.id), getGreeting.call(), (int) (getTimestamp.call() - rand.call() * 222));
                    }
                }
                Callable<Integer> getRandomUserId = () -> users.get((int) Math.floor(users.size() * rand.call())).id;
                for (var messageIndex = 0; messageIndex < messages.length; messageIndex++) {
                    addMessage.call
                            ("0", String.format("%d", getRandomUserId.call()), messages[messageIndex], getTimestamp.call() - ((messages.length - messageIndex) * 200))
                    ;
                }
            }

        }

    }
}
