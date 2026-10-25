package ec.edu.espe.billeterax;


import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.ConcurrencyFailureException;
import static ec.edu.espe.billeterax.ApiModels.*;
@RestControllerAdvice
public class ErrorHandler {
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ResponseEntity<ErrorBody> duplicate(org.springframework.dao.DataIntegrityViolationException e) {
        return ResponseEntity.status(409).body(new ErrorBody("REGISTRATION_CONFLICT","Ese usuario ya está registrado; elige otro nombre"));
    }
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorBody> business(ApiException e) {
        return ResponseEntity.status(e.status).body(new ErrorBody(e.code,e.getMessage()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class})
    ResponseEntity<ErrorBody> invalid(Exception e) {
        return ResponseEntity.badRequest().body(new ErrorBody("INVALID_REQUEST","Revise cuenta, identificador y monto positivo con máximo dos decimales"));
    }
    @ExceptionHandler(ConcurrencyFailureException.class)
    ResponseEntity<ErrorBody> busy(Exception e) {
        return ResponseEntity.status(503).body(new ErrorBody("RETRY_SAME_OPERATION","Operación ocupada. Reintente con el mismo identificador"));
    }
}
