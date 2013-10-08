/*
* Licensed to the Apache Software Foundation (ASF) under one
* or more contributor license agreements.  See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership.  The ASF licenses this file
* to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License.  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.apache.cassandra.utils;

import org.apache.cassandra.io.util.DataOutputBuffer;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class BloomFilterTest
{
    public BloomFilter bf;

    public BloomFilterTest()
    {
        bf = (BloomFilter) FilterFactory.getFilter(10000L, FilterTestHelper.MAX_FAILURE_RATE, true);
    }

    public static BloomFilter testSerialize(BloomFilter f) throws IOException
    {
        f.add(ByteBufferUtil.bytes("a"));
        DataOutputBuffer out = new DataOutputBuffer();
        Murmur3BloomFilter.serializer.serialize(f, out);

        ByteArrayInputStream in = new ByteArrayInputStream(out.getData(), 0, out.getLength());
        BloomFilter f2 = Murmur3BloomFilter.serializer.deserialize(new DataInputStream(in));

        assert f2.isPresent(ByteBufferUtil.bytes("a"));
        assert !f2.isPresent(ByteBufferUtil.bytes("b"));
        return f2;
    }


    @Before
    public void clear()
    {
        bf.clear();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testBloomLimits1()
    {
        int maxBuckets = BloomCalculations.probs.length - 1;
        int maxK = BloomCalculations.probs[maxBuckets].length - 1;

        // possible
        BloomCalculations.computeBloomSpec(maxBuckets, BloomCalculations.probs[maxBuckets][maxK]);

        // impossible, throws
        BloomCalculations.computeBloomSpec(maxBuckets, BloomCalculations.probs[maxBuckets][maxK] / 2);
    }

    @Test
    public void testOne()
    {
        bf.add(ByteBufferUtil.bytes("a"));
        assert bf.isPresent(ByteBufferUtil.bytes("a"));
        assert !bf.isPresent(ByteBufferUtil.bytes("b"));
    }

    @Test
    public void testFalsePositivesInt()
    {
        FilterTestHelper.testFalsePositives(bf, FilterTestHelper.intKeys(), FilterTestHelper.randomKeys2());
    }

    @Test
    public void testFalsePositivesRandom()
    {
        FilterTestHelper.testFalsePositives(bf, FilterTestHelper.randomKeys(), FilterTestHelper.randomKeys2());
    }

    @Test
    public void testWords()
    {
        if (KeyGenerator.WordGenerator.WORDS == 0)
        {
            return;
        }
        BloomFilter bf2 = (BloomFilter) FilterFactory.getFilter(KeyGenerator.WordGenerator.WORDS / 2, FilterTestHelper.MAX_FAILURE_RATE, false);
        int skipEven = KeyGenerator.WordGenerator.WORDS % 2 == 0 ? 0 : 2;
        FilterTestHelper.testFalsePositives(bf2,
                                            new KeyGenerator.WordGenerator(skipEven, 2),
                                            new KeyGenerator.WordGenerator(1, 2));
    }

    @Test
    public void testSerialize() throws IOException
    {
        BloomFilterTest.testSerialize(bf);
    }

    public void testManyHashes(Iterator<ByteBuffer> keys)
    {
        int MAX_HASH_COUNT = 128;
        Set<Long> hashes = new HashSet<Long>();
        long collisions = 0;
        while (keys.hasNext())
        {
            hashes.clear();
            ByteBuffer buf = keys.next();
            for (long hashIndex : bf.getHashBuckets(buf, MAX_HASH_COUNT, 1024 * 1024))
            {
                hashes.add(hashIndex);
            }
            collisions += (MAX_HASH_COUNT - hashes.size());
        }
        assert collisions <= 100;
    }

    @Test
    public void testManyRandom()
    {
        testManyHashes(FilterTestHelper.randomKeys());
    }
}