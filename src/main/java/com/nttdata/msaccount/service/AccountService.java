package com.nttdata.msaccount.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.nttdata.msaccount.client.CreditCardClient;
import com.nttdata.msaccount.client.CustomerClient;
import com.nttdata.msaccount.client.CustomerClient.CustomerInfo;
import com.nttdata.msaccount.config.AccountProperties;
import com.nttdata.msaccount.exception.BusinessException;
import com.nttdata.msaccount.model.Account;
import com.nttdata.msaccount.model.AccountStatus;
import com.nttdata.msaccount.model.AccountType;
import com.nttdata.msaccount.model.CustomerProfile;
import com.nttdata.msaccount.model.CustomerType;
import com.nttdata.msaccount.model.Member;
import com.nttdata.msaccount.repository.AccountRepository;
import com.nttdata.msaccount.util.ReactiveBridge;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository repository;
    private final CustomerClient customerClient;
    private final CreditCardClient creditCardClient;
    private final AccountProperties properties;

    public Single<Account> open(String customerId, AccountType accountType, BigDecimal openingAmount) {
        BigDecimal amount = openingAmount == null ? BigDecimal.ZERO : openingAmount;
        if (amount.compareTo(properties.rules().minimumOpeningAmount()) < 0) {
            return Single.error(badRequest("opening amount must be at least " + properties.rules().minimumOpeningAmount()));
        }
        return loadCustomer(customerId).flatMap(customer -> {
            CustomerType customerType = customer.customerType();
            checkTypeAllowed(customerType, accountType);
            return checkPersonalLimit(customerId, customerType, accountType)
                    .andThen(Completable.defer(() -> requiresCreditCard(customer, accountType)
                            ? checkCreditCard(customerId)
                            : Completable.complete()))
                    .andThen(Single.defer(() -> persist(newAccount(customerId, customerType, accountType, amount))));
        });
    }

    public Single<List<Account>> list(String customerId, AccountType accountType, AccountStatus status) {
        Account probe = Account.builder()
                .customerId(customerId)
                .accountType(accountType)
                .status(status)
                .build();
        ExampleMatcher matcher = ExampleMatcher.matchingAll()
                .withIgnorePaths("id", "customerType", "balance", "holders", "signers", "createdAt", "updatedAt");
        return ReactiveBridge.toFlowable(repository.findAll(Example.of(probe, matcher))).toList();
    }

    public Single<Account> findById(String accountId) {
        return loadAccount(accountId);
    }

    public Single<Account> updateStatus(String accountId, AccountStatus status) {
        return loadAccount(accountId).flatMap(account -> {
            account.setStatus(status);
            account.setUpdatedAt(Instant.now());
            return persist(account);
        });
    }

    public Completable deactivate(String accountId) {
        return loadAccount(accountId).flatMapCompletable(account -> {
            if (account.getStatus() == AccountStatus.INACTIVE) {
                return Completable.complete();
            }
            account.setStatus(AccountStatus.INACTIVE);
            account.setUpdatedAt(Instant.now());
            return persist(account).ignoreElement();
        });
    }

    public Single<Member> addHolder(String accountId, String customerId) {
        return addMember(accountId, customerId, MemberList.HOLDERS);
    }

    public Single<List<Member>> listHolders(String accountId) {
        return listMembers(accountId, MemberList.HOLDERS);
    }

    public Completable removeHolder(String accountId, String customerId) {
        return removeMember(accountId, customerId, MemberList.HOLDERS);
    }

    public Single<Member> addSigner(String accountId, String customerId) {
        return addMember(accountId, customerId, MemberList.SIGNERS);
    }

    public Single<List<Member>> listSigners(String accountId) {
        return listMembers(accountId, MemberList.SIGNERS);
    }

    public Completable removeSigner(String accountId, String customerId) {
        return removeMember(accountId, customerId, MemberList.SIGNERS);
    }

    private void checkTypeAllowed(CustomerType customerType, AccountType accountType) {
        if (customerType == CustomerType.BUSINESS && accountType != AccountType.CURRENT) {
            throw badRequest("business customers can only open CURRENT accounts");
        }
    }

    private Completable checkPersonalLimit(String customerId, CustomerType customerType, AccountType accountType) {
        if (customerType == CustomerType.BUSINESS || accountType == AccountType.FIXED_TERM) {
            return Completable.complete();
        }
        return ReactiveBridge.toSingle(repository.countByCustomerIdAndAccountTypeAndStatus(
                        customerId, accountType, AccountStatus.ACTIVE))
                .flatMapCompletable(count -> count > 0
                        ? Completable.error(new BusinessException(HttpStatus.CONFLICT,
                                "personal customer already has an active " + accountType + " account"))
                        : Completable.complete());
    }

    private boolean requiresCreditCard(CustomerInfo customer, AccountType accountType) {
        CustomerProfile profile = customer.profile() == null ? CustomerProfile.STANDARD : customer.profile();
        return (customer.customerType() == CustomerType.PERSONAL
                && profile == CustomerProfile.VIP && accountType == AccountType.SAVINGS)
                || (customer.customerType() == CustomerType.BUSINESS
                && profile == CustomerProfile.PYME && accountType == AccountType.CURRENT);
    }

    private Completable checkCreditCard(String customerId) {
        return ReactiveBridge.toSingle(creditCardClient.hasCreditCard(customerId))
                .flatMapCompletable(hasCard -> hasCard
                        ? Completable.complete()
                        : Completable.error(badRequest("customer must have a credit card to open this account")));
    }

    private Account newAccount(String customerId, CustomerType customerType, AccountType accountType, BigDecimal amount) {
        Instant now = Instant.now();
        List<Member> holders = new ArrayList<>();
        if (customerType == CustomerType.BUSINESS) {
            holders.add(new Member(customerId, now));
        }
        return Account.builder()
                .customerId(customerId)
                .customerType(customerType)
                .accountType(accountType)
                .status(AccountStatus.ACTIVE)
                .balance(amount)
                .holders(holders)
                .signers(new ArrayList<>())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private Single<Member> addMember(String accountId, String customerId, MemberList list) {
        return loadMemberAccount(accountId, list).flatMap(account ->
                loadCustomer(customerId).flatMap(customer -> {
                    List<Member> members = new ArrayList<>(list.getter().apply(account));
                    if (members.stream().anyMatch(member -> member.customerId().equals(customerId))) {
                        throw new BusinessException(HttpStatus.CONFLICT,
                                customerId + " is already a " + list.singular() + " of this account");
                    }
                    Member member = new Member(customerId, Instant.now());
                    members.add(member);
                    list.setter().accept(account, members);
                    account.setUpdatedAt(member.addedAt());
                    return persist(account).map(saved -> member);
                }));
    }

    private Completable removeMember(String accountId, String customerId, MemberList list) {
        return loadMemberAccount(accountId, list).flatMapCompletable(account -> {
            List<Member> members = new ArrayList<>(list.getter().apply(account));
            boolean removed = members.removeIf(member -> member.customerId().equals(customerId));
            if (!removed) {
                throw new BusinessException(HttpStatus.NOT_FOUND,
                        customerId + " is not a " + list.singular() + " of this account");
            }
            if (list.keepAtLeastOne() && members.isEmpty()) {
                throw new BusinessException(HttpStatus.CONFLICT,
                        "an account must keep at least one " + list.singular());
            }
            list.setter().accept(account, members);
            account.setUpdatedAt(Instant.now());
            return persist(account).ignoreElement();
        });
    }

    private Single<List<Member>> listMembers(String accountId, MemberList list) {
        return loadMemberAccount(accountId, list).map(account -> new ArrayList<>(list.getter().apply(account)));
    }

    private Single<Account> loadMemberAccount(String accountId, MemberList list) {
        return loadAccount(accountId).flatMap(account -> {
            if (account.getCustomerType() != CustomerType.BUSINESS) {
                throw badRequest(list.plural() + " are only available for business accounts");
            }
            return Single.just(account);
        });
    }

    private Single<CustomerInfo> loadCustomer(String customerId) {
        return ReactiveBridge.toMaybe(customerClient.findById(customerId))
                .switchIfEmpty(Single.error(() -> badRequest("customer " + customerId + " does not exist")));
    }

    private Single<Account> loadAccount(String accountId) {
        return ReactiveBridge.toMaybe(repository.findById(accountId))
                .switchIfEmpty(Single.error(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "account " + accountId + " not found")));
    }

    private Single<Account> persist(Account account) {
        return ReactiveBridge.toSingle(repository.save(account));
    }

    private static BusinessException badRequest(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, message);
    }

    private record MemberList(
            String singular,
            String plural,
            Function<Account, List<Member>> getter,
            BiConsumer<Account, List<Member>> setter,
            boolean keepAtLeastOne) {

        static final MemberList HOLDERS = new MemberList(
                "holder", "holders", Account::getHolders, Account::setHolders, true);
        static final MemberList SIGNERS = new MemberList(
                "signer", "signers", Account::getSigners, Account::setSigners, false);
    }
}
