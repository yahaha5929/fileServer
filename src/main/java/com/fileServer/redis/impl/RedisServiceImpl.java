package com.fileServer.redis.impl;

import com.fileServer.redis.RedisService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.support.atomic.RedisAtomicLong;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class RedisServiceImpl implements RedisService {

    Logger log = LoggerFactory.getLogger(RedisServiceImpl.class);

    @Autowired
    private StringRedisTemplate StringredisTemplate;

    @Autowired
    RedisTemplate<Object, Object> redisTemplate;

    @Override
    public Long incr(String key, long liveTime, long delta, TimeUnit timeUnit) {
        RedisAtomicLong entityIdCounter = new RedisAtomicLong(key, StringredisTemplate.getConnectionFactory());
        Long increment = entityIdCounter.addAndGet(delta);

        if ((null == increment || increment.longValue() == 0) && liveTime > 0) {// 初始设置过期时间
            if (timeUnit == null) {
                entityIdCounter.expire(liveTime, TimeUnit.MILLISECONDS);

            } else {
                entityIdCounter.expire(liveTime, timeUnit);

            }
        }

        return increment;
    }

    @Override
    public void putString(String dir, String key, String value, int minute) {
        StringredisTemplate.opsForValue().set(dir + ":" + key, value, minute, TimeUnit.MINUTES);
    }

    @Override
    public void putString(String dir, String key, String value, TimeUnit timeUnit, int timeout) {
        StringredisTemplate.opsForValue().set(dir + ":" + key, value, timeout, timeUnit);
    }

    @Override
    public String getString(String dir, String key) {
        return StringredisTemplate.opsForValue().get(dir + ":" + key);
    }

    @Override
    public void put(Object k, Object v) {
        redisTemplate.opsForValue().set(k, v);
    }

    @Override
    public Object get(Object k) {
        return redisTemplate.opsForValue().get(k);
    }

    @Override
    public void put(Object k, Object v, long timeout, TimeUnit timeUnit) {
        redisTemplate.opsForValue().set(k, v, timeout, timeUnit);
    }

    @Override
    public Object remove(Object k) {
        return redisTemplate.delete(k);
    }

    @Override
    public void delete(String k) {
        StringredisTemplate.delete(k);
    }

    @Override
    public Set<String> getKeys(String prefix) {
        return StringredisTemplate.keys(prefix + "*");
    }

    @Override
    public Set<String> scan(String key, long count) {
        Set<String> keys = redisTemplate.execute(
                (RedisCallback<Set<String>>) connection -> {
                    Set<String> keyTmp = new HashSet<>();
                    Cursor<byte[]> cursor = connection.scan(new ScanOptions.ScanOptionsBuilder().match(key + "*").count(count).build());
                    while (cursor.hasNext()) {
                        keyTmp.add(new String(cursor.next()));
                    }
                    return keyTmp;
                });
        return keys;
    }


    @Override
    public List<String> getValues(Set<String> keys) {
        return StringredisTemplate.opsForValue().multiGet(keys);
    }

    @Override
    public boolean lock(String key, String value) {
        // 加锁成功
        if (StringredisTemplate.opsForValue().get(key) == null) {
            StringredisTemplate.opsForValue().set(key, value);
            return true;
        }
        // 假如currentValue=A先占用了锁 其他两个线程的value都是B,保证其中一个线程拿到锁
        String currentValue = StringredisTemplate.opsForValue().get(key);
        // 锁过期 防止出现死锁
        if (!StringUtils.isEmpty(currentValue) && Long.parseLong(currentValue) < System.currentTimeMillis()) {
            // 获取上一步锁的时间
            String oldValue = StringredisTemplate.opsForValue().getAndSet(key, value);
            if (!StringUtils.isEmpty(oldValue) && oldValue.equals(currentValue)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void unlock(String key, String value) {
        try {
            String currentValue = StringredisTemplate.opsForValue().get(key);
            if (!StringUtils.isEmpty(currentValue) && currentValue.equals(value)) {
                StringredisTemplate.opsForValue().getOperations().delete(key);
            }
        } catch (Exception e) {
            log.error("【redis分布式锁】 解锁异常" + e.getMessage());
        }
    }

    /**
     * 获取hashKey对应的所有键值
     * 
     * @param key
     *            键
     * @return 对应的多个键值
     */
    public Map<Object, Object> hmget(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    /**
     * HashSet
     * 
     * @param key
     *            键
     * @param map
     *            对应多个键值
     * @return true 成功 false 失败
     */
    public boolean hmset(String key, Map<String, Long> map) {
        try {
            redisTemplate.opsForHash().putAll(key, map);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * HashGet
     * 
     * @param key
     *            键 不能为null
     * @param item
     *            项 不能为null
     * @return 值
     */
    public Object hget(String key, String item) {
        return redisTemplate.opsForHash().get(key, item);
    }

    /**
     * 向一张hash表中放入数据,如果不存在将创建
     * 
     * @param key
     *            键
     * @param item
     *            项
     * @param value
     *            值
     * @return true 成功 false失败
     */
    public boolean hset(String key, String item, Long value) {
        try {
            redisTemplate.opsForHash().put(key, item, value);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * hash递增 如果不存在,就会创建一个 并把新增后的值返回
     * 
     * @param key
     *            键
     * @param item
     *            项
     * @param by
     *            要增加几(大于0)
     * @return
     */
    public double hincr(String key, String item, Long by) {
        return redisTemplate.opsForHash().increment(key, item, by);
    }

}
