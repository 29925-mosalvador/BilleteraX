package ec.edu.espe.billeterax;

import com.fasterxml.jackson.databind.*;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import org.springframework.dao.*;
import java.time.Instant;
import java.util.Map;
import static ec.edu.espe.billeterax.protocol.GatewayProtocol.*;

/** Runs the same transactional services as local REST; an Internet auxiliary never owns a balance. */
@Component
public class GatewayCommandDispatcher {
    private final UserAuthService users;
    private final AccountAuth auth;
    private final WalletService wallet;
    private final SocketTickets tickets;
    private final ObjectMapper mapper;
    private final Validator validator;
    GatewayCommandDispatcher(UserAuthService users,AccountAuth auth,WalletService wallet,SocketTickets tickets,ObjectMapper mapper,Validator validator){
        this.users=users;this.auth=auth;this.wallet=wallet;this.tickets=tickets;this.mapper=mapper;this.validator=validator;
    }
    private <T>T body(JsonNode body,Class<T> type)throws Exception{
        if(body==null || !body.isObject())throw new ApiException(400,"INVALID_REQUEST","Revisa los datos de la solicitud");
        T value=mapper.treeToValue(body,type);
        if(!validator.validate(value).isEmpty())throw new ApiException(400,"INVALID_REQUEST","Revisa usuario, cuenta y monto con dos decimales");
        return value;
    }
    private String account(JsonNode body){return body==null?"":body.path("accountId").asText();}
    public Result dispatch(Command command){
        try{
            if(command.id()==null || command.deadline()==null || !command.deadline().isAfter(Instant.now()))return result(command,408,Map.of("code","EXPIRED_REQUEST","message","La solicitud caducó antes de ejecutarse"));
            Object value;int status=200;
            if("REGISTER".equals(command.kind())){value=users.register(body(command.body(),AuthModels.Registration.class));status=201;}
            else if("LOGIN".equals(command.kind()))value=users.login(body(command.body(),AuthModels.Login.class));
            else if("CONSUME_TICKET".equals(command.kind())){
                value=tickets.consume(command.body()==null?null:command.body().path("ticket").asText());
                if(value==null)throw new ApiException(401,"UNAUTHORIZED","Inicia sesión para continuar");
            }else{
                var principal=auth.principal(command.authorization());
                if(principal==null)throw new ApiException(401,"UNAUTHORIZED","Inicia sesión para continuar");
                String actor=principal.accountId();
                value=switch(command.kind()){
                    case "PROFILE" -> users.profile(actor);
                    case "AUTH_SOCKET" -> principal;
                    case "WS_TICKET" -> tickets.issue(principal);
                    case "LOGOUT" -> {users.logout(command.authorization());status=204;yield null;}
                    case "BALANCE" -> wallet.balance(actor,account(command.body()));
                    case "SNAPSHOT" -> wallet.snapshot(actor,account(command.body()));
                    case "HISTORY" -> wallet.history(actor,account(command.body()),command.body().path("page").asInt(),command.body().path("size").asInt(50));
                    case "DEPOSIT","WITHDRAW","TRANSFER","SERVICE" -> wallet.execute(actor,command.kind(),body(command.body(),ApiModels.MoneyRequest.class));
                    default -> throw new ApiException(400,"INVALID_COMMAND","Operación no permitida");
                };
            }
            return result(command,status,value);
        }catch(ApiException e){return result(command,e.status,Map.of("code",e.code,"message",e.getMessage()));}
        catch(DataIntegrityViolationException e){return result(command,409,Map.of("code","REGISTRATION_CONFLICT","message","Ese usuario ya está registrado; elige otro nombre"));}
        catch(ConcurrencyFailureException e){return result(command,503,Map.of("code","RETRY_SAME_OPERATION","message","Operación ocupada. Reintenta con el mismo identificador"));}
        catch(com.fasterxml.jackson.core.JsonProcessingException|IllegalArgumentException e){return result(command,400,Map.of("code","INVALID_REQUEST","message","Revisa los datos de la solicitud"));}
        catch(Exception e){return result(command,500,Map.of("code","INTERNAL_ERROR","message","No se pudo completar la solicitud"));}
    }
    private Result result(Command command,int status,Object value){return new Result(command.id(),status,mapper.valueToTree(value));}
}
