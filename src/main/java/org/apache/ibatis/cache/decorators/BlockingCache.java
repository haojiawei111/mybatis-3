/**
 *    Copyright 2009-2019 the original author or authors.
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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheException;

/**
 * 实现 Cache 接口，阻塞的 Cache 实现类。
 *
 * 这里的阻塞比较特殊，当线程去获取缓存值时，如果不存在，则会阻塞后续的其他线程去获取缓存key。
 * 为什么这么有这样的设计呢？因为当线程 A 在获取不到缓存值时，一般会去设置对应的缓存值，这样就避免其他也需要该缓存的线程 B、C 等，重复添加缓存。
 *
 * Simple blocking decorator
 *
 * Simple and inefficient version of EhCache's BlockingCache decorator.
 * It sets a lock over a cache key when the element is not found in cache.
 * This way, other threads will wait until this element is filled instead of hitting the database.
 *
 * @author Eduardo Macarron
 *
 */
public class BlockingCache implements Cache {

  /**
   * 阻塞等待超时时间
   */
  private long timeout;
  /**
   * 装饰的 Cache 对象
   */
  private final Cache delegate;
  /**
   * 缓存键与 ReentrantLock 锁对象的映射
   *     #getLockForKey(Object key) 方法，获得 ReentrantLock 对象。如果不存在，进行添加。
   *     #acquireLock(Object key) 方法，锁定 ReentrantLock 对象。
   *     #releaseLock(Object key) 方法，释放 ReentrantLock 对象。
   */
  private final ConcurrentHashMap<Object, ReentrantLock> locks;


  public BlockingCache(Cache delegate) {
    this.delegate = delegate;
    this.locks = new ConcurrentHashMap<>();
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
  public void putObject(Object key, Object value) {
    try {
      // 添加缓存
      delegate.putObject(key, value);
    } finally {
      // 释放锁
      releaseLock(key);
    }
  }

  @Override
  public Object getObject(Object key) {
    // 获得锁
    acquireLock(key);
    // 获得缓存值
    Object value = delegate.getObject(key);

    if (value != null) {
      // 释放锁
      releaseLock(key);
    }
    // 如果没有命中缓存，则返回null并且是不会释放锁的，
    // 需要执行#putObject(Object key, Object value)方法设置对应的缓存才会释放锁，别的线程才能拿到这个缓存
    return value;
  }

  @Override
  public Object removeObject(Object key) {
    // despite of its name, this method is called only to release locks
    // 释放锁
    releaseLock(key);
    return null;
  }

  @Override
  public void clear() {
    delegate.clear();
  }

  @Override
  public ReadWriteLock getReadWriteLock() {
    return null;
  }

  /**
   * 获得 ReentrantLock 对象。如果不存在，进行添加
   *
   * @param key 缓存键
   * @return ReentrantLock 对象
   */
  private ReentrantLock getLockForKey(Object key) {
    return locks.computeIfAbsent(key, k -> new ReentrantLock());
  }

  // 获取key对应的锁
  private void acquireLock(Object key) {
    // 获得 ReentrantLock 对象。
    Lock lock = getLockForKey(key);
    // 获得锁，直到超时
    if (timeout > 0) {
      try {
        boolean acquired = lock.tryLock(timeout, TimeUnit.MILLISECONDS);
        if (!acquired) {
          // 过了timeout时间也没有获取到锁
          throw new CacheException("Couldn't get a lock in " + timeout + " for the key " +  key + " at the cache " + delegate.getId());
        }
      } catch (InterruptedException e) {
        throw new CacheException("Got interrupted while trying to acquire lock for key " + key, e);
      }
    } else {
      lock.lock();
    }
  }

  private void releaseLock(Object key) {
    // 获得 ReentrantLock 对象
    ReentrantLock lock = locks.get(key);
    // 如果当前线程持有，进行释放
    if (lock.isHeldByCurrentThread()) {
      lock.unlock();
    }
  }

  public long getTimeout() {
    return timeout;
  }

  public void setTimeout(long timeout) {
    this.timeout = timeout;
  }
}
