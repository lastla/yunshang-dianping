package com.ysdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.ysdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    //创建redis缓存，参数 键 值 时间  时间单位

    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    //创建redis缓存，设置逻辑过期时间

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    //解决缓存穿透
    public  <R,ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback,Long time,TimeUnit unit){
        String key = keyPrefix + id;
        //1.从redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断redis中是否命中
        if(StrUtil.isNotBlank(json)){
            //命中，返回数据
            return JSONUtil.toBean(json, type);
        }
        // 判断命中是否是空值
        if(json!=null){
            //返回一个错误信息
            return null;
        }

        //未命中，根据id查询数据库
        R r = dbFallback.apply(id);
        //3.判断商铺是否存在
        if(r==null){
            //不存在，将空值写入redis解决缓存穿透问题
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            //返回错误信息
            return null;
        }
        //存在，将查寻到的数据写入redis中，返回商铺信息
        this.set(key,r,time,unit);

        return r;
    }

    //创建线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    //逻辑过期解决缓存击穿
    public  <R,ID> R queryWithLogicalExpire(
            String keyPrefix,ID id,Class<R> type,Function<ID,R> dbFallBack,Long time,TimeUnit unit){
        String key = CACHE_SHOP_KEY + id;
        //1.从redis中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断redis中是否命中
        if(StrUtil.isBlank(shopJson)){
            //3.未命中，返回空
            return null;
        }
        //4.命中,先把json反序列化位对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData(); //未指定数据的类型，只能返回一个JsonObject对象
        //再次通过工具类转回Shop对象
        R r = JSONUtil.toBean(data, type);
        //获取逻辑过期时间
        LocalDateTime expireTime = redisData.getExpireTime();
        //5.判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            // 6.未过期，返回数据
            return r;
        }

        //7过期，尝试获取锁
        String lockKey = LOCK_SHOP_KEY+id;
        boolean isLock = tryLock(lockKey);
        //8判断获取锁是否成功
        if(isLock){
            //再次查询redis查看是否过期

            //1.从redis中查询商铺缓存
            shopJson = stringRedisTemplate.opsForValue().get(key);
            //4.命中,先把json反序列化位对象
            redisData = JSONUtil.toBean(shopJson, RedisData.class);
            data = (JSONObject) redisData.getData(); //未指定数据的类型，只能返回一个JsonObject对象
            //再次通过工具类转回Shop对象
            r = JSONUtil.toBean(data, type);
            //获取逻辑过期时间
            expireTime = redisData.getExpireTime();
            //5.判断是否过期
            if(expireTime.isAfter(LocalDateTime.now())){
                // 6.未过期，返回数据
                return r;
            }


            //获得锁成功，开启独立线程实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    R r1 = dbFallBack.apply(id);
                    this.setWithLogicalExpire(key,r1,time,unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    unlock(lockKey);
                }
            });
        }


        //失败，返回过期数据
        //返回过期数据
        return r;
    }

    //获取锁
    private boolean tryLock(String key){
        boolean flag =  stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10,TimeUnit.SECONDS);
        //直接返回flag会做自动拆箱，可能出现空指针，建议使用工具类
        return BooleanUtil.isTrue(flag);
    }
    //释放锁
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }
}
