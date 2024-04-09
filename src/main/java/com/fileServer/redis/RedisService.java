package com.fileServer.redis;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public interface RedisService {

    public Long incr(String key, long liveTime, long delta, TimeUnit timeUnit);

    public void putString(String dir, String key, String value, int minute);

    void putString(String dir, String key, String value, TimeUnit timeUnit, int timeout);

    public String getString(String dir, String key);

    void put(Object k, Object v);

    Object get(Object k);

    void put(Object k, Object v, long timeout, TimeUnit timeUnit);

    Object remove(Object k);

    void delete(String k);

    @Deprecated
    Set<String> getKeys(String prefix);

    Set<String> scan(String key, long count);

    List<String> getValues(Set<String> keys);

    boolean lock(String key, String value);

    void unlock(String key, String value);

    /**
     * 获取hashKey对应的所有键值
     *
     * @param key
     *            键
     * @return 对应的多个键值
     */
    Map<Object, Object> hmget(String key);

    /**
     * HashSet
     *
     * @param key
     *            键
     * @param map
     *            对应多个键值
     * @return true 成功 false 失败
     */
    boolean hmset(String key, Map<String, Long> map);

    /**
     * HashGet
     *
     * @param key
     *            键 不能为null
     * @param item
     *            项 不能为null
     * @return 值
     */
    Object hget(String key, String item);

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
    boolean hset(String key, String item, Long value);

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
    double hincr(String key, String item, Long by);
}
