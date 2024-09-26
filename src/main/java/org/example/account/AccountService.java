package org.example.account;

import org.example.TransactionHelper;
import org.example.user.User;
import org.hibernate.SessionFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AccountService {


    private final SessionFactory sessionFactory;
    private final AccountProperties accountProperties;
    private final TransactionHelper transactionHelper;


    public AccountService(SessionFactory sessionFactory, AccountProperties accountProperties, TransactionHelper transactionHelper) {
        this.sessionFactory = sessionFactory;
        this.accountProperties = accountProperties;
        this.transactionHelper = transactionHelper;
    }

    public Account createAccount(User user) {
        return transactionHelper.executeInTransaction(() -> {
            Account newAccount = new Account(accountProperties.getDefaultAccountAmount(), user);
            sessionFactory.getCurrentSession().persist(newAccount);
            return newAccount;
        });

    }

    public Optional<Account> findAccountById(int id) {
        Account account = sessionFactory.getCurrentSession().get(Account.class, id);
        return Optional.of(account);
    }

//    public List<Account> getAllUserAccounts(int userId) {
//        Transaction transaction = null;
//        List<Account> accountList = null;
//        try (Session session = sessionFactory.openSession()) {
//            transaction = session.beginTransaction();
//            User user = session.get(User.class, userId);
//            if (user == null) {
//                throw new RuntimeException("No such user with id=" + userId);
//            }
//            accountList = user.getAccountList();
//            transaction.commit();
//        } catch (Exception e) {
//            if (transaction != null) {
//                transaction.rollback();
//            }
//            throw new RuntimeException("Error while getting all user accounts, UserId=" + userId, e);
//
//        }
//        return accountList;
//    }

    public void depositAccount(int accountId, int moneyToDeposit) {
        transactionHelper.executeInTransaction(() -> {
            if (moneyToDeposit <= 0) {
                throw new IllegalArgumentException("Cannot deposit not positive amount: amount=%s"
                        .formatted(moneyToDeposit));
            }
            var acc = findAccountById(accountId)
                    .orElseThrow(()-> new IllegalArgumentException("No such account: id=%s" .formatted(accountId)));
            int curBalance = acc.getMoneyAmount();
            acc.setMoneyAmount(curBalance + moneyToDeposit);
            return 0;
        });
    }


    public void withdrawFromAccount(int accountId, int amountToWithdraw) {
        transactionHelper.executeInTransaction(()->{
            if (amountToWithdraw <= 0) {
                throw new IllegalArgumentException("Cannot withdraw not positive amount: amount=%s"
                        .formatted(amountToWithdraw));
            }
            Account accountToWithdraw = findAccountById(accountId)
                    .orElseThrow(()->new IllegalArgumentException("No such account: id=%s" .formatted(accountId)));
            int curBalance = accountToWithdraw.getMoneyAmount();
            if (curBalance < amountToWithdraw) {
                throw new IllegalArgumentException(
                        "Cannot withdraw from account: id=%s, moneyAmount=%s, attemptedWithdraw=%s"
                                .formatted(accountId, accountToWithdraw.getMoneyAmount(), amountToWithdraw)
                );
            }
            accountToWithdraw.setMoneyAmount(curBalance - amountToWithdraw);
            return 0;
        });
        //
    }

    public Account closeAccount(int accountId) { //чекнуть
        return transactionHelper.executeInTransaction(() -> {
            var accountToRemove = findAccountById(accountId)
                    .orElseThrow(() -> new IllegalArgumentException("No such account: id=%s" .formatted(accountId)));
            int curBalance = accountToRemove.getMoneyAmount();
            User user = accountToRemove.getOwner();
            List<Account> accountList = user.getAccountList();
            if (accountList.size() == 1) {
                throw new IllegalArgumentException("Cannot close the only one account");
            }
            accountList.remove(accountToRemove);
            Account accountToDeposit = accountList.getFirst();
            accountToDeposit.setMoneyAmount(accountToDeposit.getMoneyAmount() + curBalance);
            sessionFactory.getCurrentSession().remove(accountToRemove);
            return accountToRemove;
        });
    }

    public void transfer(int fromAccountId, int toAccountId, int amountToTransfer) {
        transactionHelper.executeInTransaction(() -> {
            if (amountToTransfer <= 0) {
                throw new IllegalArgumentException("Cannot transfer not positive amount: amount=%s"
                        .formatted(amountToTransfer));
            }
            Account accountFrom = findAccountById(fromAccountId)
                    .orElseThrow(() -> new IllegalArgumentException("No such account: id=%s" .formatted(fromAccountId)));
            Account accountTo = findAccountById(toAccountId)
                    .orElseThrow(() -> new IllegalArgumentException("No such account: id=%s" .formatted(toAccountId)));
            if (accountFrom.getMoneyAmount() < amountToTransfer) {
                throw new IllegalArgumentException("Cannot transfer from account because amountToTransfer more than balance");
            }

            int totalAmountToDeposit = !(accountTo.getOwner().getId() == accountFrom.getOwner().getId())
                    ? (int) (amountToTransfer * (1 - accountProperties.getTransferCommission()))
                    : amountToTransfer;
            accountFrom.setMoneyAmount(accountFrom.getMoneyAmount() - amountToTransfer);
            accountTo.setMoneyAmount(accountTo.getMoneyAmount() + totalAmountToDeposit);
            return 0;

        });
    }
}