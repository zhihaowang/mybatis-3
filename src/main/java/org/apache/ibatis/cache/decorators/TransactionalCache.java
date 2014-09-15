/*
 *    Copyright 2009-2014 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.cache.decorators;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;

import org.apache.ibatis.cache.Cache;

/**
 * @author Clinton Begin
 */
/**
 * 事务缓存
 * 一次性存入多个缓存，移除多个缓存
 *
 */
public class TransactionalCache implements Cache {

  private Cache delegate;
  //commit时要不要清缓存
  private boolean clearOnCommit;
  //commit时要添加的元素
  private Map<Object, AddEntry> entriesToAddOnCommit;
  //commit时要移除的元素
  private Map<Object, RemoveEntry> entriesToRemoveOnCommit;

  public TransactionalCache(Cache delegate) {
    this.delegate = delegate;
    //默认commit时不清缓存
    this.clearOnCommit = false;
    this.entriesToAddOnCommit = new HashMap<Object, AddEntry>();
    this.entriesToRemoveOnCommit = new HashMap<Object, RemoveEntry>();
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  @Override
  public Object getObject(Object key) {
    if (clearOnCommit) return null; // issue #146
    return delegate.getObject(key);
  }

  @Override
  public ReadWriteLock getReadWriteLock() {
    return null;
  }

  @Override
  public void putObject(Object key, Object object) {
    //如果又有删，又有加这个key，则只记录加，看谁最后调用
    entriesToRemoveOnCommit.remove(key);
    entriesToAddOnCommit.put(key, new AddEntry(delegate, key, object));
  }

  @Override
  public Object removeObject(Object key) {
    //如果又有删，又有加这个key，则只记录删，看谁最后调用
    entriesToAddOnCommit.remove(key);
    entriesToRemoveOnCommit.put(key, new RemoveEntry(delegate, key));
    return delegate.getObject(key);
  }

  @Override
  public void clear() {
    reset();
    clearOnCommit = true;
  }

  //多了commit方法，提供事务功能
  public void commit() {
    if (clearOnCommit) {
      delegate.clear();
    } else {
        //commit时要移除的元素
      for (RemoveEntry entry : entriesToRemoveOnCommit.values()) {
        entry.commit();
      }
    }
    //commit时要添加的元素
    for (AddEntry entry : entriesToAddOnCommit.values()) {
      entry.commit();
    }
    reset();
  }

  public void rollback() {
    reset();
  }

  private void reset() {
    clearOnCommit = false;
    entriesToRemoveOnCommit.clear();
    entriesToAddOnCommit.clear();
  }

  private static class AddEntry {
    private Cache cache;
    private Object key;
    private Object value;

    public AddEntry(Cache cache, Object key, Object value) {
      this.cache = cache;
      this.key = key;
      this.value = value;
    }

    public void commit() {
      cache.putObject(key, value);
    }
  }

  private static class RemoveEntry {
    private Cache cache;
    private Object key;

    public RemoveEntry(Cache cache, Object key) {
      this.cache = cache;
      this.key = key;
    }

    public void commit() {
      cache.removeObject(key);
    }
  }

}
