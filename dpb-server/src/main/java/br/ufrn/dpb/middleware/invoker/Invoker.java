package br.ufrn.dpb.middleware.invoker;

import br.ufrn.dpb.middleware.marshaller.Marshaller;
import br.ufrn.dpb.middleware.protocol.Message;
import br.ufrn.dpb.middleware.annotation.OnMessage;

import java.util.Map;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Objects;
import java.nio.charset.StandardCharsets;

public class Invoker {
    private final Marshaller marshaller = new Marshaller();
    private record Route(Object target, Method method) {}
    private final Map<Message.Type, Route> routes = new HashMap<>();

    public void registerRoutes(Object target){
        Objects.requireNonNull(target);
        Class<?> clazz = target.getClass();
        for (Method method : clazz.getDeclaredMethods()) {
            if(method.isAnnotationPresent(OnMessage.class)){
                Message.Type type = method.getAnnotation(OnMessage.class).value();
                method.setAccessible(true);
                routes.put(type, new Route(target, method));
            }
        }

    }

    public byte[] invoke(byte[] requestData, int length){
        Message response;
        int requestId = 0;

        try {
            Message request = marshaller.unmarshall(requestData, length);
            requestId = request.getRequestId();
            switch(request.getType()) {
                case HEARTBEAT:
                    response = new Message(Message.Type.HEARTBEAT_ACK, request.getRequestId(), new byte[0]);
                    break;
                case REQUEST:
                    Route route = routes.get(request.getType());
                    if(route == null) {
                        throw new IllegalArgumentException("No handler registered for request type: "+request.getType());    
                    }
                    byte[] responsePayload = (byte[]) route.method().invoke(route.target(), request.getPayload());
                    response = new Message(Message.Type.RESPONSE, request.getRequestId(), responsePayload);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported request type: " + request.getType());
            } 
        } catch (java.lang.reflect.InvocationTargetException e) {
            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            byte[] payload = String.valueOf(msg).getBytes(StandardCharsets.UTF_8);
            response = new Message(Message.Type.ERROR, requestId, payload);
        } catch (Exception e) {
            byte[] payload = String.valueOf(e.getMessage()).getBytes(StandardCharsets.UTF_8);
            response = new Message(Message.Type.ERROR, requestId, payload);
        } 
        return marshaller.marshall(response);
    }
}
