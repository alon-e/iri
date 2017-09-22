package com.iota.iri.service;

import com.iota.iri.LedgerValidator;
import com.iota.iri.Milestone;
import com.iota.iri.Snapshot;
import com.iota.iri.TransactionValidator;
import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
import com.iota.iri.zmq.MessageQ;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.*;

import static com.iota.iri.controllers.TransactionViewModelTest.getRandomTransactionHash;
import static com.iota.iri.controllers.TransactionViewModelTest.getRandomTransactionTrits;
import static com.iota.iri.controllers.TransactionViewModelTest.getRandomTransactionWithTrunkAndBranch;

/**
 * Created by paul on 4/27/17.
 */
public class TipsManagerTest {

    private static final TemporaryFolder dbFolder = new TemporaryFolder();
    private static final TemporaryFolder logFolder = new TemporaryFolder();
    private static Tangle tangle;
    private static TipsManager tipsManager;

    @Test
    public void capSum() throws Exception {
        long a = 0, b, max = Long.MAX_VALUE/2;
        for(b = 0; b < max; b+= max/100) {
            a = TipsManager.capSum(a, b, max);
            Assert.assertTrue("a should never go above max", a <= max);
        }
    }

    @BeforeClass
    public static void setUp() throws Exception {
        tangle = new Tangle();
        dbFolder.create();
        logFolder.create();
        tangle.addPersistenceProvider(new RocksDBPersistenceProvider(dbFolder.getRoot().getAbsolutePath(), logFolder.getRoot().getAbsolutePath(),1000));
        tangle.init();
        TipsViewModel tipsViewModel = new TipsViewModel();
        MessageQ messageQ = new MessageQ(0, null, 1, false);
        TransactionRequester transactionRequester = new TransactionRequester(tangle, messageQ);
        TransactionValidator transactionValidator = new TransactionValidator(tangle, tipsViewModel, transactionRequester, messageQ);
        Milestone milestone = new Milestone(tangle, Hash.NULL_HASH, transactionValidator, true, messageQ);
        LedgerValidator ledgerValidator = new LedgerValidator(tangle, new Snapshot(Snapshot.initialSnapshot), milestone, transactionRequester, messageQ);
        tipsManager = new TipsManager(tangle, ledgerValidator, transactionValidator, tipsViewModel, milestone, 15, messageQ);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        tangle.shutdown();
        dbFolder.delete();
    }

    @Test
    public void updateLinearRatingsTestWorks() throws Exception {
        TransactionViewModel transaction, transaction1, transaction2;
        List<TransactionViewModel> transactions = new LinkedList<>(Collections.singleton(new TransactionViewModel(getRandomTransactionTrits(), getRandomTransactionHash())));
        Hash[] hashes = { transactions.get(0).getHash(), transactions.get(0).getHash()};
        for(int i = 1; i < 3; i++) {
            transactions.add(new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(hashes[0], hashes[1]), getRandomTransactionHash()));
            hashes[0] = transactions.get(i).getHash();
            hashes[1] = transactions.get(i).getHash();
        }
        for(TransactionViewModel tx: transactions) {
            tx.store(tangle);
        }
        for(int i = 0; i < transactions.size(); i++) {
            Assert.assertEquals(tipsManager.getCumulativeWeight(transactions.get(i).getHash()), transactions.size() - i);
        }
    }

    @Test
    public void cumulativeWeightTestWorks() throws Exception {
        TransactionViewModel transaction, transaction1, transaction2, transaction3, transaction4;
        transaction = new TransactionViewModel(getRandomTransactionTrits(), getRandomTransactionHash());
        transaction1 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction.getHash(), transaction.getHash()), getRandomTransactionHash());
        transaction2 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction1.getHash(), transaction1.getHash()), getRandomTransactionHash());
        transaction3 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction2.getHash(), transaction1.getHash()), getRandomTransactionHash());
        transaction4 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction2.getHash(), transaction3.getHash()), getRandomTransactionHash());
        transaction.store(tangle);
        transaction1.store(tangle);
        transaction2.store(tangle);
        transaction3.store(tangle);
        transaction4.store(tangle);
        Assert.assertEquals(5, tipsManager.getCumulativeWeight(transaction.getHash()));
        Assert.assertEquals(4, tipsManager.getCumulativeWeight(transaction1.getHash()));
        Assert.assertEquals(3, tipsManager.getCumulativeWeight(transaction2.getHash()));
        Assert.assertEquals(2, tipsManager.getCumulativeWeight(transaction3.getHash()));
        Assert.assertEquals(1, tipsManager.getCumulativeWeight(transaction4.getHash()));
    }

    @Test
    public void updateRatingsSerialWorks2() throws Exception {
        Hash[] hashes = new Hash[5];
        hashes[0] = getRandomTransactionHash();
        new TransactionViewModel(getRandomTransactionTrits(), hashes[0]).store(tangle);
        for(int i = 1; i < hashes.length; i ++) {
            hashes[i] = getRandomTransactionHash();
            new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(hashes[i-1], hashes[i-(i > 1 ?2:1)]), hashes[i]).store(tangle);
        }

        Assert.assertEquals(5, tipsManager.getCumulativeWeight(hashes[0]));

    }

    //@Test
    public void testGetCumulativeWeightTime() throws Exception {
        int max = 100001;
        long time;
        List<Long> times = new LinkedList<>();
        for(int size = 1; size < max; size *= 10) {
            time = getCumulativeWeightTime(size);
            times.add(time);
        }
        Assert.assertEquals(1, 1);
    }

    public long getCumulativeWeightTime(int size) throws Exception {
        Hash[] hashes = new Hash[size];
        hashes[0] = getRandomTransactionHash();
        new TransactionViewModel(getRandomTransactionTrits(), hashes[0]).store(tangle);
        Random random = new Random();
        for(int i = 1; i < hashes.length; i ++) {
            hashes[i] = getRandomTransactionHash();
            new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(hashes[i-random.nextInt(i)-1], hashes[i-random.nextInt(i)-1]), hashes[i]).store(tangle);
        }
        long start = System.currentTimeMillis();
        tipsManager.getCumulativeWeight(hashes[0]);
        return System.currentTimeMillis() - start;
    }
}