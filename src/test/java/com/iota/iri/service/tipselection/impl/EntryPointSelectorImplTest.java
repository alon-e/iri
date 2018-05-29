package com.iota.iri.service.tipselection.impl;

import com.iota.iri.Milestone;
import com.iota.iri.hash.SpongeFactory;
import com.iota.iri.model.Hash;
import com.iota.iri.model.IntegerIndex;
import com.iota.iri.service.tipselection.EntryPointSelector;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.Pair;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EntryPointSelectorImplTest {

    @Mock
    private Milestone milestone;
    @Mock
    private Tangle tangle;


    @Test
    public void testEntryPointWithTangleData() throws Exception {
        //TODO This can't be mocked until Hash is rewritten as an interface
        Hash milestoneHash = Hash.calculate(SpongeFactory.Mode.CURLP27, new int[0]);
        mockTangleBehavior(milestoneHash);
        mockMilestoneTrackerBehavior();

        EntryPointSelector entryPointSelector = new EntryPointSelectorImpl(tangle, milestone, false, 0);
        Hash entryPoint = entryPointSelector.getEntryPoint(10);

        Assert.assertEquals(milestoneHash, entryPoint);
    }

    @Test
    public void testEntryPointWithoutTangleData() throws Exception {
        mockMilestoneTrackerBehavior();

        EntryPointSelector entryPointSelector = new EntryPointSelectorImpl(tangle, milestone, false, 0);
        Hash entryPoint = entryPointSelector.getEntryPoint(10);

        Assert.assertEquals(Hash.NULL_HASH, entryPoint);
    }


    private void mockMilestoneTrackerBehavior() {
        //TODO this should be mocked via getter methods
        milestone.latestSolidSubtangleMilestoneIndex = 0;
        milestone.latestSolidSubtangleMilestone = Hash.NULL_HASH;
    }

    private void mockTangleBehavior(Hash milestoneModelHash) throws Exception {
        //TODO rename com.iota.iri.Milestone -> com.iota.iri.MilestoneTracker
        com.iota.iri.model.Milestone milestoneModel = new com.iota.iri.model.Milestone();
        milestoneModel.index = new IntegerIndex(0);
        milestoneModel.hash = milestoneModelHash;
        Pair<Indexable, Persistable> indexMilestoneModel = new Pair<>(new IntegerIndex(0), milestoneModel);
        Mockito.when(tangle.getFirst(com.iota.iri.model.Milestone.class, IntegerIndex.class)).thenReturn(indexMilestoneModel);
    }
}