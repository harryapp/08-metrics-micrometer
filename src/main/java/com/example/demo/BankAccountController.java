package com.example.demo;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

import static java.math.BigDecimal.valueOf;
import static java.util.Optional.ofNullable;

@RestController
public class BankAccountController implements ApplicationListener<ApplicationReadyEvent> {

    private Map<String, Account> theBank = new HashMap();

    private MeterRegistry meterRegistry;

    @Autowired
    public BankAccountController(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Transfers money from one account to another. Creates accounts at both ends if they do not exist.
     *
     * @param tx          a transaction object
     * @param fromAccount from account id
     * @param toAccount   to account id
     */
    @PostMapping(path = "/account/{fromAccount}/transfer/{toAccount}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<String> transfer(@RequestBody Transaction tx, @PathVariable String fromAccount, @PathVariable String toAccount) {
        // Increment a metric called "transfer" every time this is called, and tag with from- and to country



        Account from = getOrCreateAccount(fromAccount);
        Account to = getOrCreateAccount(toAccount);

        System.out.println("balance: " + from.getBalance());
        System.out.println("transfer amount: " + tx.getAmount());
        if(from.getBalance().compareTo(valueOf(tx.getAmount())) < 0){
            meterRegistry.counter("transfer_stopped_insufficient_fund").increment();
            return ResponseEntity.ok("insufficient funds");
        }

        from.setBalance(from.getBalance().subtract(valueOf(tx.getAmount())));
        to.setBalance(to.getBalance().add(valueOf(tx.getAmount())));

        meterRegistry.counter("transfer_completed").increment();

        meterRegistry.counter("transfer",
                "from", tx.getFromCountry(), "to", tx.getToCountry()).increment();
        return ResponseEntity.ok("Transfer completed");
    }

    /**
     * Saves an account. Will create a new account if one does not exist.
     *
     * @param a the account Object
     * @return
     */
    @PostMapping(path = "/account", consumes = "application/json",
            produces = "application/json")
    public ResponseEntity<Account> updateAccount(@RequestBody Account a) {
        meterRegistry.counter("update_account").increment();
        Account account = getOrCreateAccount(a.getId());
        account.setBalance(a.getBalance());
        account.setCurrency(a.getCurrency());
        theBank.put(a.getId(), a);
        return new ResponseEntity<>(a, HttpStatus.OK);
    }

    /**
     * Gets account info for an account
     *
     * @param accountId the account ID to get info from
     * @return
     */
    @Timed
    @GetMapping(path = "/account/{accountId}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Account> balance(@PathVariable String accountId) {
        meterRegistry.counter("balance").increment();
        Account account = ofNullable(theBank.get(accountId)).orElseThrow(AccountNotFoundException::new);
        return new ResponseEntity<>(account, HttpStatus.OK);
    }

    private Account getOrCreateAccount(String accountId) {
        if (theBank.get(accountId) == null) {
            Account a = new Account();
            a.setId(accountId);
            theBank.put(accountId, a);
        }
        return theBank.get(accountId);
    }


    /**
     * Denne meter-typen "Gauge" rapporterer en verdi hver gang noen kaller "size" metoden på
     * Verdisettet til HashMap
     *
     * @param applicationReadyEvent
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent applicationReadyEvent) {
        Gauge.builder("account_count", theBank,
                b -> b.values().size()).register(meterRegistry);
    }

    @ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "account not found")
    public static class AccountNotFoundException extends RuntimeException {
    }




}