package com.iota.iri.service.tipselection.impl;

import com.iota.iri.LedgerValidator;
import com.iota.iri.TransactionValidator;
import com.iota.iri.controllers.ApproveeViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.tipselection.TailFinder;
import com.iota.iri.service.tipselection.WalkValidator;
import com.iota.iri.service.tipselection.Walker;
import com.iota.iri.storage.Tangle;
import com.iota.iri.zmq.MessageQ;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

public class WalkerAlpha implements Walker {

    private double alpha;
    private final Random random;

    private final Tangle tangle;
    private final MessageQ messageQ;
    private final Logger log = LoggerFactory.getLogger(Walker.class);

    private final TailFinder tailFinder;

    public WalkerAlpha(double alpha, Random random, Tangle tangle, MessageQ messageQ, TailFinder tailFinder) {

        this.alpha = alpha;
        //TODO, check if random (secureRandom) is thread safe
        this.random = random;

        this.tangle = tangle;
        this.messageQ = messageQ;

        this.tailFinder = tailFinder;

    }

    public double getAlpha() {
        return alpha;
    }

    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    @Override
    public Hash walk(Hash entryPoint, Map<Hash, Integer> ratings, WalkValidator walkValidator) throws Exception {
        // check entryPoint is valid
        if (!walkValidator.isValid(entryPoint)) {
            throw new RuntimeException("entry point failed consistency check: " + entryPoint.toString());
        }
        
        Optional<Hash> nextStep;
        LinkedList<Hash> traversedTails = new LinkedList<>(Collections.singleton(entryPoint));
        
        //Walk
        do {
            nextStep = selectApprover(traversedTails.getLast(), ratings, walkValidator);
            nextStep.ifPresent(traversedTails::add);
         } while (nextStep.isPresent());
        
        log.info("Tx traversed to find tip: " + traversedTails.size());
        messageQ.publish("mctn %d", traversedTails.size());

        return traversedTails.getLast();
    }

    private Optional<Hash> selectApprover(Hash tailHash, Map<Hash, Integer> ratings, WalkValidator walkValidator) throws Exception {
        Set<Hash> approvers = getApprovers(tailHash);
        return findNextValidTail(ratings, approvers, walkValidator);
    }

    private Set<Hash> getApprovers(Hash tailHash) throws Exception {
        ApproveeViewModel approveeViewModel = ApproveeViewModel.load(tangle, tailHash);
        return approveeViewModel.getHashes();
    }

    private Optional<Hash> findNextValidTail(Map<Hash, Integer> ratings, Set<Hash> approvers, WalkValidator walkValidator) throws Exception {
        Optional<Hash> nextTailHash = Optional.empty();

        //select next tail to step to
        while (!nextTailHash.isPresent()) {
            Optional<Hash> nextTxHash = select(ratings, approvers);
            if (!nextTxHash.isPresent()) {
                //no existing approver = tip
                return Optional.empty();
            }

            nextTailHash = findTailIfValid(nextTxHash.get(), walkValidator);
            approvers.remove(nextTxHash.get());
            //if next tail is not valid, re-select while removing it from approvers set
        }

        return nextTailHash;
    }

    private Optional<Hash> select(Map<Hash, Integer> ratings, Set<Hash> approversSet) {

        if (approversSet.size() == 0) {
            return Optional.empty();
        }

        //filter based on tangle state when starting the walk
        List<Hash> approvers = approversSet.stream().filter(ratings::containsKey).collect(Collectors.toList());

        //calculate the probabilities
        List<Integer> walkRatings = approvers.stream().map(ratings::get).collect(Collectors.toList());

        Integer maxRating = walkRatings.stream().max(Integer::compareTo).orElse(0);
        //walkRatings.stream().reduce(0, Integer::max);

        //transition probability function (normalize ratings based on Hmax)
        List<Integer> normalizedWalkRatings = walkRatings.stream().map(w -> w - maxRating).collect(Collectors.toList());
        List<Double> weights = normalizedWalkRatings.stream().map(w -> Math.exp(alpha * w)).collect(Collectors.toList());

        //select the next transaction
        Double weightsSum = weights.stream().reduce(0.0, Double::sum);
        double target = random.nextDouble() * weightsSum;

        int approverIndex;
        for (approverIndex = 0; approverIndex < weights.size() - 1; approverIndex++) {
            target -= weights.get(approverIndex);
            if (target <= 0) {
                break;
            }
        }

        return Optional.of(approvers.get(approverIndex));
    }

    private Optional<Hash> findTailIfValid(Hash transactionHash, WalkValidator validator) throws Exception {
        Optional<Hash> tailHash = tailFinder.findTail(transactionHash);
        if (tailHash.isPresent()) {
            if (validator.isValid(tailHash.get())) {
                return tailHash;
            }
        }

        return Optional.empty();
    }
}