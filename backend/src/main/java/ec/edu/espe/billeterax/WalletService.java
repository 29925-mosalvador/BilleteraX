package ec.edu.espe.billeterax;


import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import static ec.edu.espe.billeterax.ApiModels.*;
@Service
public class WalletService {
    private final Accounts accounts;
    private final Entries entries;
    private final Operations operations;
    private final ApplicationEventPublisher publisher;
    WalletService(Accounts a, Entries e, Operations o, ApplicationEventPublisher p) {
        accounts=a; entries=e; operations=o; publisher=p;
    }
    private void authorize(String actor,String account) {
        if(!Objects.equals(actor,account)) throw new ApiException(403,"FORBIDDEN","Esta credencial no permite acceder a la cuenta solicitada");
    }
    private Balance balanceOf(Account a) { return new Balance(a.id,a.ownerName,a.balance,a.version); }
    @Transactional(readOnly=true)
    public Balance balance(String actor,String id) {
        authorize(actor,id); return balanceOf(accounts.findById(id).orElseThrow(ApiException::missing));
    }
    @Transactional
    public Snapshot snapshot(String actor,String id) {
        authorize(actor,id);
        Account a=accounts.snapshotLock(id).orElseThrow(ApiException::missing);
        return new Snapshot(balanceOf(a),entries.findByAccountIdOrderByOccurredAtDescIdDesc(id),Instant.now());
    }
    @Transactional(readOnly=true)
    public Page<LedgerEntry> history(String actor,String id,int page,int size) {
        authorize(actor,id);
        if(!accounts.existsById(id)) throw ApiException.missing();
        return entries.findByAccountId(id,PageRequest.of(Math.max(0,page),Math.max(1,Math.min(200,size)),Sort.by("occurredAt","id").descending()));
    }
    @Transactional
    public Receipt execute(String actor,String type,MoneyRequest r) {
        authorize(actor,r.accountId());
        if(r.amount()==null || r.amount().signum()<=0 || r.amount().scale()>2 || r.amount().compareTo(new BigDecimal("999999999999.99"))>0 || r.operationId()==null)
            throw new ApiException(400,"INVALID_AMOUNT","Monto positivo con máximo dos decimales");
        BigDecimal amount=r.amount().setScale(2,RoundingMode.UNNECESSARY);
        boolean transfer=type.equals("TRANSFER");
        if(transfer && (r.destinationAccountId()==null || !r.destinationAccountId().matches("CTA[0-9]{3,12}") || r.accountId().equals(r.destinationAccountId())))
            throw new ApiException(400,"INVALID_DESTINATION","Seleccione otra cuenta destino válida");
        if(type.equals("SERVICE") && (r.service()==null || !Set.of("ELECTRICITY","WATER","INTERNET").contains(r.service()) || r.contract()==null || !r.contract().matches("[A-Za-z0-9-]{6,20}")))
            throw new ApiException(400,"INVALID_CONTRACT","Seleccione servicio y contrato de 6 a 20 caracteres alfanuméricos o guiones");
        // All transfers acquire account locks in the same order to avoid A->B / B->A deadlocks.
        SortedSet<String> ids=new TreeSet<>(); ids.add(r.accountId()); if(transfer) ids.add(r.destinationAccountId());
        Map<String,Account> locked=new HashMap<>();
        for(String id:ids) locked.put(id,accounts.locked(id).orElseThrow(ApiException::missing));
        Account source=locked.get(r.accountId());
        String opKey=r.accountId()+":"+r.operationId();
        String fingerprint=String.join("|",type,r.accountId(),amount.toPlainString(),Objects.toString(r.destinationAccountId(),""),Objects.toString(r.service(),""),Objects.toString(r.contract(),""));
        Optional<WalletOperation> prior=operations.findById(opKey);
        if(prior.isPresent()) {
            if(!prior.get().fingerprint.equals(fingerprint)) throw new ApiException(409,"IDEMPOTENCY_CONFLICT","El identificador ya se utilizó con otros datos");
            LedgerEntry entry=entries.findById(prior.get().receiptId).orElseThrow();
            return new Receipt(r.operationId().toString(),entry,entry.balanceAfter,true);
        }
        BigDecimal signed=type.equals("DEPOSIT") ? amount : amount.negate();
        if(source.balance.add(signed).signum()<0) throw new ApiException(422,"INSUFFICIENT_FUNDS","Fondos insuficientes");
        if(source.balance.add(signed).compareTo(new BigDecimal("999999999999.99"))>0)
            throw new ApiException(422,"BALANCE_LIMIT","Se supera el límite de saldo del laboratorio");
        source.balance=source.balance.add(signed);
        Instant time=Instant.now();
        LedgerEntry entry=entry(r,source,transfer ? "TRANSFER_OUT":type,signed,time,r.destinationAccountId());
        List<Event> events=new ArrayList<>(); events.add(event(entry,"BALANCE_CHANGED"));
        if(transfer) {
            Account destination=locked.get(r.destinationAccountId());
            destination.balance=destination.balance.add(amount);
            if(destination.balance.compareTo(new BigDecimal("999999999999.99"))>0)
                throw new ApiException(422,"BALANCE_LIMIT","Se supera el límite de la cuenta destino");
            LedgerEntry incoming=entry(r,destination,"TRANSFER_IN",amount,time,source.id);
            events.add(event(incoming,"TRANSFER_RECEIVED"));
        }
        operations.save(new WalletOperation(opKey,fingerprint,entry.id));
        publisher.publishEvent(new Committed(events));
        return new Receipt(r.operationId().toString(),entry,source.balance,false);
    }
    private LedgerEntry entry(MoneyRequest r,Account a,String type,BigDecimal amount,Instant time,String counterparty) {
        LedgerEntry e=new LedgerEntry(); e.id=UUID.randomUUID().toString(); e.operationId=r.operationId().toString();
        e.accountId=a.id; e.type=type; e.amount=amount; e.balanceAfter=a.balance; e.occurredAt=time;
        e.category=type.equals("SERVICE") ? "Servicio Básico":"Movimiento"; e.counterparty=counterparty;
        e.service=r.service(); e.contract=r.contract(); return entries.save(e);
    }
    private Event event(LedgerEntry e,String type) {
        return new Event(UUID.randomUUID().toString(),type,e.accountId,e.id,e.amount,e.balanceAfter,e.occurredAt);
    }
}
