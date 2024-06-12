package vn.edu.iuh.fit.chatservice.config.websocket;

import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import vn.edu.iuh.fit.chatservice.service.UserSessionService;
import vn.edu.iuh.fit.chatservice.util.JwtUtil;

@Component
//@GlobalChannelInterceptor
public class Interceptor implements ChannelInterceptor {
    private final JwtUtil jwtUtil;
    private final Logger log = LoggerFactory.getLogger(Interceptor.class);
    private final UserSessionService userSessionService;

    public Interceptor(JwtUtil jwtUtil, UserSessionService userSessionService) {
        this.jwtUtil = jwtUtil;
        this.userSessionService = userSessionService;
    }

    @Override
    public Message<?> preSend(Message<?> msg, MessageChannel mc) {
        StompHeaderAccessor headerAccessor = MessageHeaderAccessor.getAccessor(msg, StompHeaderAccessor.class);
//        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(msg);
        if (!StompCommand.SUBSCRIBE.equals(headerAccessor.getCommand())) {
            try {
                if (StompCommand.CONNECT.equals(headerAccessor.getCommand())) {
                    String token = headerAccessor.getNativeHeader("Authorization").get(0);
                    Claims claims = jwtUtil.getAllClaimsFromToken(token);
                    String userId = claims.getSubject();
                    headerAccessor.addNativeHeader("sub", userId);
                    userSessionService.addUserSession(userId, headerAccessor.getSessionId());
                }
                if (StompCommand.SEND.equals(headerAccessor.getCommand())) {
                    String token = headerAccessor.getNativeHeader("Authorization").get(0);
                    Claims claims = jwtUtil.getAllClaimsFromToken(token);
                    String userId = claims.getSubject();
                    headerAccessor.addNativeHeader("sub", userId);

//            return MessageBuilder.createMessage(msg.getPayload(), headerAccessor.getMessageHeaders());
                }
                if (StompCommand.DISCONNECT.equals(headerAccessor.getCommand())) {
                    userSessionService.removeUserSession(headerAccessor.getSessionId());
                }
            } catch (NullPointerException e) {
                throw new RuntimeException(e.getMessage());
            }
        }
        return msg;
    }

    @Override
    public void postSend(Message<?> msg, MessageChannel mc, boolean bln) {
        log.info("In postSend");
    }

    @Override
    public void afterSendCompletion(Message<?> msg, MessageChannel mc, boolean bln, Exception excptn) {
        log.info("In afterSendCompletion");
    }

    @Override
    public boolean preReceive(MessageChannel mc) {
        log.info("In preReceive");
        return true;
    }
}