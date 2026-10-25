package ec.edu.espe.billeterax;


public class ApiException extends RuntimeException {
    final int status;
    final String code;
    ApiException(int status, String code, String message) { super(message); this.status=status; this.code=code; }
    static ApiException missing() { return new ApiException(404,"ACCOUNT_NOT_FOUND","La cuenta no existe"); }
}
