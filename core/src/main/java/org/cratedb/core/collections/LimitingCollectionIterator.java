/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed any another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package org.cratedb.core.collections;

import java.util.Collection;
import java.util.Iterator;

public class LimitingCollectionIterator<E> implements Collection<E>, Iterator<E> {

    private final Collection<E> collection;
    private final int limit;
    private final Iterator<E> iterator;
    private int currentIdx;

    public LimitingCollectionIterator(Collection<E> collection, int limit) {
        this.collection = collection;
        this.iterator = collection.iterator();
        this.limit = limit;
        this.currentIdx = 0;
    }

    @Override
    public int size() {
        return Math.min(limit, collection.size());
    }

    @Override
    public boolean isEmpty() {
        return limit == 0 || collection.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<E> iterator() {
        return this;
    }

    @Override
    public Object[] toArray() {
        Object[] result = new Object[size()];
        int idx = -1;
        while (hasNext()) {
            idx++;
            result[idx] = next();
        }
        return result;
    }

    @Override
    public <T> T[] toArray(T[] ts) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean add(E e) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(Collection<?> objects) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(Collection<? extends E> es) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> objects) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> objects) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasNext() {
        return currentIdx < size();
    }

    @Override
    public E next() {
        currentIdx++;
        return iterator.next();
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }
}
