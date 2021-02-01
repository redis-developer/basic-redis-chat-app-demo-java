// @ts-check
import { useCallback, useEffect, useRef, useState } from "react";
// eslint-disable-next-line no-unused-vars
import io, { Socket } from "socket.io-client";
import { emitMessage, getEventSource } from "./api";
import { parseRoomName } from "./utils";
/**
 * @param {import('./state').UserEntry} newUser
 */
const updateUser = (newUser, user, dispatch, infoMessage) => {
  if (user.username === newUser.username) {
    return;
  }
  dispatch({ type: "set user", payload: newUser });
  if (infoMessage !== undefined) {
    dispatch({
      type: "append message",
      payload: {
        id: "0",
        message: {
          /** Date isn't shown in the info message, so we only need a unique value */
          date: Math.random() * 10000,
          from: "info",
          message: infoMessage,
        },
      },
    });
  }
};


const onShowRoom = (room, username, dispatch) => dispatch({
  type: "add room",
  payload: {
    id: room.id,
    name: parseRoomName(room.names, username),
  },
});

const onMessage = (message, dispatch) => {
  /** Set user online */
  dispatch({
    type: "make user online",
    payload: message.from,
  });
  dispatch({
    type: "append message",
    payload: { id: message.roomId === undefined ? "0" : message.roomId, message },
  });
};

/** @returns {[Socket, boolean, () => void]} */
const useSocket = (user, dispatch, onLogOutBase) => {
  const [connected, setConnected] = useState(false);
  const eventSourceRef = useRef(null);

  const emit = useCallback(async (type, message) => {
    await emitMessage(type, user, message);
    return {};
  }, [user]);

  const onLogOut = useCallback(async () => {
    await emit("user.disconnected", user);
    onLogOutBase();
  }, [onLogOutBase, emit, user]);

  /** First of all it's necessary to handle the socket io connection */
  useEffect(() => {
    if (user === null) {
      setConnected(false);
      if (eventSourceRef.current !== null) {

        window.onbeforeunload = undefined;
        eventSourceRef.current.close();
        eventSourceRef.current = null;
      }
    } else {

      if (eventSourceRef.current === null) {
        eventSourceRef.current = getEventSource(user.id);
        /** Handle non socket.io messages */
        eventSourceRef.current.onmessage = function (e) {
          const { type, data } = JSON.parse(e.data);
          switch (type) {
            case "user.connected": updateUser(data, user, dispatch, `${data.username} connected`);
              break;
            case "user.disconnected": updateUser(data, user, dispatch, `${data.username} left`);
              break;
            case "show.room": onShowRoom(data, user.username, dispatch);
              break;
            case 'message': onMessage(data, dispatch);
              break;
            default:
              break;
          }
        };

        window.onbeforeunload = () => {
          if (eventSourceRef.current) {
            eventSourceRef.current.close();
          }
          emit("user.disconnected", user);
          return undefined;
        };

        emit("user.connected", user);
      }
      setConnected(true);
    }
  }, [user, dispatch, onLogOut, emit]);


  return [
    {
      // @ts-ignore
      emit
    }
    , connected, onLogOut];
};

export { useSocket };
