package com.nttdata.msaccount.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Example;
import org.springframework.http.HttpStatus;

import com.nttdata.msaccount.client.CustomerClient;
import com.nttdata.msaccount.client.CustomerClient.CustomerInfo;
import com.nttdata.msaccount.config.AccountProperties;
import com.nttdata.msaccount.event.AccountEventPublisher;
import com.nttdata.msaccount.event.AccountOpened;
import com.nttdata.msaccount.exception.BusinessException;
import com.nttdata.msaccount.exception.DependencyUnavailableException;
import com.nttdata.msaccount.model.Account;
import com.nttdata.msaccount.model.AccountStatus;
import com.nttdata.msaccount.model.AccountType;
import com.nttdata.msaccount.model.CustomerProfile;
import com.nttdata.msaccount.model.CustomerType;
import com.nttdata.msaccount.model.Member;
import com.nttdata.msaccount.repository.AccountRepository;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final String CUSTOMER_ID = "64b000000000000000000001";
    private static final String ACCOUNT_ID = "64b000000000000000000002";

    @Mock
    private AccountRepository repository;

    @Mock
    private CustomerClient customerClient;

    @Mock
    private CreditProjectionService creditProjections;

    @Mock
    private AccountEventPublisher publisher;

    private AccountService service;

    @BeforeEach
    void setUp() {
        lenient().when(creditProjections.isOverdue(anyString())).thenReturn(Single.just(false));
        lenient().when(publisher.publish(anyString(), any(), any())).thenReturn(Completable.complete());
        service = serviceWithMinimum(BigDecimal.ZERO);
    }

    private AccountService serviceWithMinimum(BigDecimal minimum) {
        AccountProperties properties = new AccountProperties(
                new AccountProperties.Rules(minimum),
                new AccountProperties.Clients("http://customer", Duration.ofSeconds(2)));
        return new AccountService(repository, customerClient, creditProjections, publisher, properties);
    }

    @Test
    void openPersonalSavingsAccountPersistsActiveAccount() {
        when(customerClient.findById(CUSTOMER_ID))
                .thenReturn(Mono.just(customer(CustomerType.PERSONAL, CustomerProfile.STANDARD)));
        when(repository.countByCustomerIdAndAccountTypeAndStatus(
                CUSTOMER_ID, AccountType.SAVINGS, AccountStatus.ACTIVE))
                .thenReturn(Mono.just(0L));
        when(repository.save(any(Account.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        TestObserver<Account> observer = service.open(CUSTOMER_ID, AccountType.SAVINGS, new BigDecimal("100.00"))
                .test();

        observer.assertNoErrors().assertValueCount(1);
        Account saved = observer.values().get(0);
        assertThat(saved.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(saved.getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(saved.getCustomerType()).isEqualTo(CustomerType.PERSONAL);
        assertThat(saved.getBalance()).isEqualByComparingTo("100.00");
        assertThat(saved.getHolders()).isEmpty();
        assertThat(saved.getSigners()).isEmpty();
    }

    @Test
    void openAccountWithoutAmountStartsWithZeroBalance() {
        when(customerClient.findById(CUSTOMER_ID))
                .thenReturn(Mono.just(customer(CustomerType.PERSONAL, CustomerProfile.STANDARD)));
        when(repository.save(any(Account.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        Account saved = service.open(CUSTOMER_ID, AccountType.FIXED_TERM, null).blockingGet();

        assertThat(saved.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void openBelowMinimumAmountFailsWithBadRequest() {
        AccountService strict = serviceWithMinimum(new BigDecimal("50.00"));

        assertFailsWith(strict.open(CUSTOMER_ID, AccountType.SAVINGS, new BigDecimal("10.00")), HttpStatus.BAD_REQUEST);

        verifyNoInteractions(customerClient, repository);
    }

    @Test
    void openForUnknownCustomerFailsWithBadRequest() {
        when(customerClient.findById(CUSTOMER_ID)).thenReturn(Mono.empty());

        assertFailsWith(service.open(CUSTOMER_ID, AccountType.SAVINGS, BigDecimal.TEN), HttpStatus.BAD_REQUEST);

        verify(repository, never()).save(any(Account.class));
    }

    @Test
    void openBusinessSavingsAccountFailsWithBadRequest() {
        when(customerClient.findById(CUSTOMER_ID))
                .thenReturn(Mono.just(customer(CustomerType.BUSINESS, CustomerProfile.STANDARD)));

        assertFailsWith(service.open(CUSTOMER_ID, AccountType.SAVINGS, BigDecimal.TEN), HttpStatus.BAD_REQUEST);

        verify(repository, never()).save(any(Account.class));
    }

    @Test
    void openSecondActivePersonalCurrentAccountFailsWithConflict() {
        when(customerClient.findById(CUSTOMER_ID))
                .thenReturn(Mono.just(customer(CustomerType.PERSONAL, CustomerProfile.STANDARD)));
        when(repository.countByCustomerIdAndAccountTypeAndStatus(
                CUSTOMER_ID, AccountType.CURRENT, AccountStatus.ACTIVE))
                .thenReturn(Mono.just(1L));

        assertFailsWith(service.open(CUSTOMER_ID, AccountType.CURRENT, BigDecimal.TEN), HttpStatus.CONFLICT);

        verify(repository, never()).save(any(Account.class));
    }

    @Test
    void openFixedTermAccountSkipsPersonalLimit() {
        when(customerClient.findById(CUSTOMER_ID))
                .thenReturn(Mono.just(customer(CustomerType.PERSONAL, CustomerProfile.STANDARD)));
        when(repository.save(any(Account.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        Account saved = service.open(CUSTOMER_ID, AccountType.FIXED_TERM, BigDecimal.TEN).blockingGet();

        assertThat(saved.getAccountType()).isEqualTo(AccountType.FIXED_TERM);
        verify(repository, never()).countByCustomerIdAndAccountTypeAndStatus(anyString(), any(), any());
    }

    @Test
    void openVipSavingsWithoutCreditCardFailsWithBadRequest() {
        when(customerClient.findById(CUSTOMER_ID))
                .thenReturn(Mono.just(customer(CustomerType.PERSONAL, CustomerProfile.VIP)));
        when(repository.countByCustomerIdAndAccountTypeAndStatus(
                CUSTOMER_ID, AccountType.SAVINGS, AccountStatus.ACTIVE))
                .thenReturn(Mono.just(0L));
        when(creditProjections.hasActiveCard(CUSTOMER_ID)).thenReturn(Single.just(false));

        assertFailsWith(service.open(CUSTOMER_ID, AccountType.SAVINGS, BigDecimal.TEN), HttpStatus.BAD_REQUEST);

        verify(repository, never()).save(any(Account.class));
    }

    @Test
    void openVipSavingsWithCreditCardPersistsAccount() {
        when(customerClient.findById(CUSTOMER_ID))
                .thenReturn(Mono.just(customer(CustomerType.PERSONAL, CustomerProfile.VIP)));
        when(repository.countByCustomerIdAndAccountTypeAndStatus(
                CUSTOMER_ID, AccountType.SAVINGS, AccountStatus.ACTIVE))
                .thenReturn(Mono.just(0L));
        when(creditProjections.hasActiveCard(CUSTOMER_ID)).thenReturn(Single.just(true));
        when(repository.save(any(Account.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        Account saved = service.open(CUSTOMER_ID, AccountType.SAVINGS, BigDecimal.TEN).blockingGet();

        assertThat(saved.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void openPymeCurrentAccountWithCardAddsOpenerAsHolder() {
        when(customerClient.findById(CUSTOMER_ID))
                .thenReturn(Mono.just(customer(CustomerType.BUSINESS, CustomerProfile.PYME)));
        when(creditProjections.hasActiveCard(CUSTOMER_ID)).thenReturn(Single.just(true));
        when(repository.save(any(Account.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        Account saved = service.open(CUSTOMER_ID, AccountType.CURRENT, BigDecimal.TEN).blockingGet();

        assertThat(saved.getCustomerType()).isEqualTo(CustomerType.BUSINESS);
        assertThat(saved.getHolders()).hasSize(1);
        assertThat(saved.getHolders().get(0).customerId()).isEqualTo(CUSTOMER_ID);
    }

    @Test
    void openOverdueCustomerFailsWithConflict() {
        when(customerClient.findById(CUSTOMER_ID))
                .thenReturn(Mono.just(customer(CustomerType.PERSONAL, CustomerProfile.STANDARD)));
        when(repository.countByCustomerIdAndAccountTypeAndStatus(
                CUSTOMER_ID, AccountType.SAVINGS, AccountStatus.ACTIVE))
                .thenReturn(Mono.just(0L));
        when(creditProjections.isOverdue(CUSTOMER_ID)).thenReturn(Single.just(true));

        assertFailsWith(service.open(CUSTOMER_ID, AccountType.SAVINGS, BigDecimal.TEN), HttpStatus.CONFLICT);

        verify(repository, never()).save(any(Account.class));
    }

    @Test
    void openPublishesAccountOpenedEvent() {
        when(customerClient.findById(CUSTOMER_ID))
                .thenReturn(Mono.just(customer(CustomerType.PERSONAL, CustomerProfile.STANDARD)));
        when(repository.countByCustomerIdAndAccountTypeAndStatus(
                CUSTOMER_ID, AccountType.SAVINGS, AccountStatus.ACTIVE))
                .thenReturn(Mono.just(0L));
        when(repository.save(any(Account.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        service.open(CUSTOMER_ID, AccountType.SAVINGS, BigDecimal.TEN).blockingGet();

        verify(publisher).publish(eq(AccountOpened.TOPIC), any(), any(AccountOpened.class));
    }

    @Test
    void openSucceedsWhenEventPublishingFails() {
        when(customerClient.findById(CUSTOMER_ID))
                .thenReturn(Mono.just(customer(CustomerType.PERSONAL, CustomerProfile.STANDARD)));
        when(repository.countByCustomerIdAndAccountTypeAndStatus(
                CUSTOMER_ID, AccountType.SAVINGS, AccountStatus.ACTIVE))
                .thenReturn(Mono.just(0L));
        when(repository.save(any(Account.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(publisher.publish(anyString(), any(), any()))
                .thenReturn(Completable.error(new IllegalStateException("kafka unavailable")));

        Account saved = service.open(CUSTOMER_ID, AccountType.SAVINGS, BigDecimal.TEN).blockingGet();

        assertThat(saved.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void openPropagatesDependencyFailure() {
        when(customerClient.findById(CUSTOMER_ID))
                .thenReturn(Mono.error(new DependencyUnavailableException("ms-customer is unavailable")));

        TestObserver<Account> observer = service.open(CUSTOMER_ID, AccountType.SAVINGS, BigDecimal.TEN).test();

        observer.assertError(DependencyUnavailableException.class);
    }

    @Test
    void listFiltersAccountsByExample() {
        Account first = account(ACCOUNT_ID, CustomerType.PERSONAL, AccountType.SAVINGS, AccountStatus.ACTIVE);
        Account second = account("64b000000000000000000003", CustomerType.PERSONAL, AccountType.SAVINGS,
                AccountStatus.ACTIVE);
        when(repository.findAll(any(Example.class))).thenReturn(Flux.just(first, second));

        List<Account> accounts = service.list(CUSTOMER_ID, AccountType.SAVINGS, AccountStatus.ACTIVE).blockingGet();

        assertThat(accounts).containsExactly(first, second);
    }

    @Test
    void findByIdReturnsAccount() {
        Account account = account(ACCOUNT_ID, CustomerType.PERSONAL, AccountType.SAVINGS, AccountStatus.ACTIVE);
        when(repository.findById(ACCOUNT_ID)).thenReturn(Mono.just(account));

        assertThat(service.findById(ACCOUNT_ID).blockingGet()).isSameAs(account);
    }

    @Test
    void findByIdMissingAccountFailsWithNotFound() {
        when(repository.findById(ACCOUNT_ID)).thenReturn(Mono.empty());

        assertFailsWith(service.findById(ACCOUNT_ID), HttpStatus.NOT_FOUND);
    }

    @Test
    void updateStatusChangesStatusAndPersists() {
        Account account = account(ACCOUNT_ID, CustomerType.PERSONAL, AccountType.SAVINGS, AccountStatus.ACTIVE);
        when(repository.findById(ACCOUNT_ID)).thenReturn(Mono.just(account));
        when(repository.save(any(Account.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        Account updated = service.updateStatus(ACCOUNT_ID, AccountStatus.INACTIVE).blockingGet();

        assertThat(updated.getStatus()).isEqualTo(AccountStatus.INACTIVE);
        assertThat(updated.getUpdatedAt()).isNotNull();
    }

    @Test
    void deactivateActiveAccountSetsInactive() {
        Account account = account(ACCOUNT_ID, CustomerType.PERSONAL, AccountType.SAVINGS, AccountStatus.ACTIVE);
        when(repository.findById(ACCOUNT_ID)).thenReturn(Mono.just(account));
        when(repository.save(any(Account.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        service.deactivate(ACCOUNT_ID).test().assertComplete();

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(AccountStatus.INACTIVE);
    }

    @Test
    void deactivateInactiveAccountIsIdempotent() {
        Account account = account(ACCOUNT_ID, CustomerType.PERSONAL, AccountType.SAVINGS, AccountStatus.INACTIVE);
        when(repository.findById(ACCOUNT_ID)).thenReturn(Mono.just(account));

        service.deactivate(ACCOUNT_ID).test().assertComplete();

        verify(repository, never()).save(any(Account.class));
    }

    @Test
    void deactivateMissingAccountFailsWithNotFound() {
        when(repository.findById(ACCOUNT_ID)).thenReturn(Mono.empty());

        assertFailsWith(service.deactivate(ACCOUNT_ID), HttpStatus.NOT_FOUND);
    }

    @Test
    void addHolderToBusinessAccountAddsMember() {
        Account account = businessAccount(new ArrayList<>(List.of(new Member(CUSTOMER_ID, Instant.now()))),
                new ArrayList<>());
        when(repository.findById(ACCOUNT_ID)).thenReturn(Mono.just(account));
        String newCustomer = "64b000000000000000000004";
        when(customerClient.findById(newCustomer))
                .thenReturn(Mono.just(customer(CustomerType.PERSONAL, CustomerProfile.STANDARD)));
        when(repository.save(any(Account.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        Member member = service.addHolder(ACCOUNT_ID, newCustomer).blockingGet();

        assertThat(member.customerId()).isEqualTo(newCustomer);
        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getHolders()).hasSize(2);
    }

    @Test
    void addDuplicateHolderFailsWithConflict() {
        Account account = businessAccount(new ArrayList<>(List.of(new Member(CUSTOMER_ID, Instant.now()))),
                new ArrayList<>());
        when(repository.findById(ACCOUNT_ID)).thenReturn(Mono.just(account));
        when(customerClient.findById(CUSTOMER_ID))
                .thenReturn(Mono.just(customer(CustomerType.PERSONAL, CustomerProfile.STANDARD)));

        assertFailsWith(service.addHolder(ACCOUNT_ID, CUSTOMER_ID), HttpStatus.CONFLICT);

        verify(repository, never()).save(any(Account.class));
    }

    @Test
    void addHolderToPersonalAccountFailsWithBadRequest() {
        Account account = account(ACCOUNT_ID, CustomerType.PERSONAL, AccountType.SAVINGS, AccountStatus.ACTIVE);
        when(repository.findById(ACCOUNT_ID)).thenReturn(Mono.just(account));

        assertFailsWith(service.addHolder(ACCOUNT_ID, CUSTOMER_ID), HttpStatus.BAD_REQUEST);

        verifyNoInteractions(customerClient);
    }

    @Test
    void addHolderWithUnknownCustomerFailsWithBadRequest() {
        Account account = businessAccount(new ArrayList<>(), new ArrayList<>());
        when(repository.findById(ACCOUNT_ID)).thenReturn(Mono.just(account));
        when(customerClient.findById(CUSTOMER_ID)).thenReturn(Mono.empty());

        assertFailsWith(service.addHolder(ACCOUNT_ID, CUSTOMER_ID), HttpStatus.BAD_REQUEST);
    }

    @Test
    void addHolderToMissingAccountFailsWithNotFound() {
        when(repository.findById(ACCOUNT_ID)).thenReturn(Mono.empty());

        assertFailsWith(service.addHolder(ACCOUNT_ID, CUSTOMER_ID), HttpStatus.NOT_FOUND);
    }

    @Test
    void listHoldersReturnsMembersOfBusinessAccount() {
        Account account = businessAccount(new ArrayList<>(List.of(new Member(CUSTOMER_ID, Instant.now()))),
                new ArrayList<>());
        when(repository.findById(ACCOUNT_ID)).thenReturn(Mono.just(account));

        List<Member> holders = service.listHolders(ACCOUNT_ID).blockingGet();

        assertThat(holders).hasSize(1);
        assertThat(holders.get(0).customerId()).isEqualTo(CUSTOMER_ID);
    }

    @Test
    void listSignersOfPersonalAccountFailsWithBadRequest() {
        Account account = account(ACCOUNT_ID, CustomerType.PERSONAL, AccountType.CURRENT, AccountStatus.ACTIVE);
        when(repository.findById(ACCOUNT_ID)).thenReturn(Mono.just(account));

        assertFailsWith(service.listSigners(ACCOUNT_ID), HttpStatus.BAD_REQUEST);
    }

    @Test
    void removeHolderRemovesMemberWhenAnotherHolderRemains() {
        String other = "64b000000000000000000005";
        Account account = businessAccount(new ArrayList<>(List.of(
                new Member(CUSTOMER_ID, Instant.now()),
                new Member(other, Instant.now()))), new ArrayList<>());
        when(repository.findById(ACCOUNT_ID)).thenReturn(Mono.just(account));
        when(repository.save(any(Account.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        service.removeHolder(ACCOUNT_ID, other).test().assertComplete();

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getHolders()).extracting(Member::customerId).containsExactly(CUSTOMER_ID);
    }

    @Test
    void removeHolderNotMemberFailsWithNotFound() {
        Account account = businessAccount(new ArrayList<>(List.of(new Member(CUSTOMER_ID, Instant.now()))),
                new ArrayList<>());
        when(repository.findById(ACCOUNT_ID)).thenReturn(Mono.just(account));

        assertFailsWith(service.removeHolder(ACCOUNT_ID, "64b000000000000000000006"), HttpStatus.NOT_FOUND);

        verify(repository, never()).save(any(Account.class));
    }

    @Test
    void removeLastHolderFailsWithConflict() {
        Account account = businessAccount(new ArrayList<>(List.of(new Member(CUSTOMER_ID, Instant.now()))),
                new ArrayList<>());
        when(repository.findById(ACCOUNT_ID)).thenReturn(Mono.just(account));

        assertFailsWith(service.removeHolder(ACCOUNT_ID, CUSTOMER_ID), HttpStatus.CONFLICT);

        verify(repository, never()).save(any(Account.class));
    }

    @Test
    void addSignerToBusinessAccountAddsMember() {
        Account account = businessAccount(new ArrayList<>(), new ArrayList<>());
        when(repository.findById(ACCOUNT_ID)).thenReturn(Mono.just(account));
        when(customerClient.findById(CUSTOMER_ID))
                .thenReturn(Mono.just(customer(CustomerType.PERSONAL, CustomerProfile.STANDARD)));
        when(repository.save(any(Account.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        Member member = service.addSigner(ACCOUNT_ID, CUSTOMER_ID).blockingGet();

        assertThat(member.customerId()).isEqualTo(CUSTOMER_ID);
        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getSigners()).hasSize(1);
    }

    @Test
    void removeLastSignerIsAllowed() {
        Account account = businessAccount(new ArrayList<>(),
                new ArrayList<>(List.of(new Member(CUSTOMER_ID, Instant.now()))));
        when(repository.findById(ACCOUNT_ID)).thenReturn(Mono.just(account));
        when(repository.save(any(Account.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        service.removeSigner(ACCOUNT_ID, CUSTOMER_ID).test().assertComplete();

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getSigners()).isEmpty();
    }

    @Test
    void removeMissingSignerFailsWithNotFound() {
        Account account = businessAccount(new ArrayList<>(), new ArrayList<>());
        when(repository.findById(ACCOUNT_ID)).thenReturn(Mono.just(account));

        assertFailsWith(service.removeSigner(ACCOUNT_ID, CUSTOMER_ID), HttpStatus.NOT_FOUND);
    }

    @Test
    void listSignersReturnsMembersOfBusinessAccount() {
        Account account = businessAccount(new ArrayList<>(),
                new ArrayList<>(List.of(new Member(CUSTOMER_ID, Instant.now()))));
        when(repository.findById(ACCOUNT_ID)).thenReturn(Mono.just(account));

        List<Member> signers = service.listSigners(ACCOUNT_ID).blockingGet();

        assertThat(signers).extracting(Member::customerId).containsExactly(CUSTOMER_ID);
    }

    private static void assertFailsWith(Single<?> single, HttpStatus status) {
        single.test().assertError(error -> error instanceof BusinessException business
                && business.getStatus() == status);
    }

    private static void assertFailsWith(Completable completable, HttpStatus status) {
        completable.test().assertError(error -> error instanceof BusinessException business
                && business.getStatus() == status);
    }

    private static CustomerInfo customer(CustomerType type, CustomerProfile profile) {
        return new CustomerInfo(CUSTOMER_ID, type, profile);
    }

    private static Account account(String id, CustomerType type, AccountType accountType, AccountStatus status) {
        Instant now = Instant.now();
        return Account.builder()
                .id(id)
                .customerId(CUSTOMER_ID)
                .customerType(type)
                .accountType(accountType)
                .status(status)
                .balance(BigDecimal.TEN)
                .holders(new ArrayList<>())
                .signers(new ArrayList<>())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private static Account businessAccount(List<Member> holders, List<Member> signers) {
        Instant now = Instant.now();
        return Account.builder()
                .id(ACCOUNT_ID)
                .customerId(CUSTOMER_ID)
                .customerType(CustomerType.BUSINESS)
                .accountType(AccountType.CURRENT)
                .status(AccountStatus.ACTIVE)
                .balance(BigDecimal.TEN)
                .holders(holders)
                .signers(signers)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
